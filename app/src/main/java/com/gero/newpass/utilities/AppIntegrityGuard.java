package com.gero.newpass.utilities;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.annotation.SuppressLint;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.gero.newpass.BuildConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * AppIntegrityGuard — A purely client-side, zero-cost security utility that
 * applies layers of defense to protect the app.
 *
 * <p><b>Layer 1:</b> Android 16+ Advanced Protection Mode awareness.</p>
 * <p><b>Layer 2:</b> Runtime SHA-256 signing certificate fingerprint verification.</p>
 */
public final class AppIntegrityGuard {

    private static final String TAG = "AppIntegrityGuard";

    // ───────────────────────── Configuration ─────────────────────────

    /**
     * The official SHA-256 fingerprint of the release signing certificate.
     * <p><b>⚠️ REPLACE THIS</b> with the real fingerprint before distributing your app.</p>
     *
     * <p>To obtain it, run:
     * <pre>keytool -list -v -keystore your-release.keystore -alias your-alias</pre>
     * and copy the SHA-256 value (upper-case, colon-separated).</p>
     */
    private static final String OFFICIAL_SIGNATURE_SHA256 = BuildConfig.OFFICIAL_SIGNATURE_SHA256;

    // Prevent instantiation
    private AppIntegrityGuard() {}

    // ───────────────────────── Public API ─────────────────────────

    /**
     * Run all integrity checks synchronously and return a consolidated report.
     *
     * @param context Application or Activity context.
     * @return A {@link SecurityReport} summarising the result.
     */
    public static SecurityReport runAllChecks(Context context) {

        // Layer 1 — Advanced Protection Mode (Android 16+ / API 36+)
        SecurityReport advancedReport = checkAdvancedProtection(context);
        if (!advancedReport.passed) {
            return advancedReport;
        }

        // Layer 2 — Signature fingerprint
        SecurityReport signatureReport = checkSignature(context);
        if (!signatureReport.passed) {
            return signatureReport;
        }

        return SecurityReport.pass();
    }

    // ──────────── Layer 1: Advanced Protection (API 36+) ─────────────

    /**
     * On Android 16+ (API 36), checks whether the device has Advanced Protection Mode
     * enabled via {@code AdvancedProtectionManager}. Also registers a callback to be
     * notified if the user toggles the setting while the app is running.
     *
     * <p>On older API levels this check is a no-op and always passes.</p>
     */
    @SuppressLint({"WrongConstant", "NewApi"})
    public static SecurityReport checkAdvancedProtection(Context context) {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                Object service = context.getSystemService("advanced_protection");
                if (service != null) {
                    // Use reflection to call the API so compilation doesn't hard-fail
                    // on SDK < 36. At runtime on API 36+ the class will be present.
                    java.lang.reflect.Method isEnabled =
                            service.getClass().getMethod("isAdvancedProtectionEnabled");
                    boolean enabled = (boolean) isEnabled.invoke(service);

                    if (com.gero.newpass.BuildConfig.DEBUG) Log.d(TAG, "Advanced Protection enabled: " + enabled);

                    if (enabled) {
                        // Register callback to monitor toggle changes while app is alive
                        registerAdvancedProtectionCallback(context, service);
                    }
                }
            } catch (Exception e) {
                // Reflection may fail on some OEM ROMs — not a hard failure
                if (com.gero.newpass.BuildConfig.DEBUG) Log.w(TAG, "Advanced Protection check unavailable", e);
            }
        } else {
            if (com.gero.newpass.BuildConfig.DEBUG) Log.d(TAG, "Advanced Protection check skipped (API " +
                    Build.VERSION.SDK_INT + " < 36)");
        }
        return SecurityReport.pass();
    }

    /**
     * Registers a callback for Advanced Protection Mode changes via reflection.
     * If the user disables AP while the app is running, the change is logged.
     */
    private static void registerAdvancedProtectionCallback(Context context, Object service) {
        try {
            // AdvancedProtectionManager.registerAdvancedProtectionCallback(Executor, Consumer<Boolean>)
            java.lang.reflect.Method registerMethod = service.getClass().getMethod(
                    "registerAdvancedProtectionCallback",
                    java.util.concurrent.Executor.class,
                    java.util.function.Consumer.class);

            java.util.concurrent.Executor mainExecutor = ContextCompat.getMainExecutor(context);
            java.util.function.Consumer<Boolean> callback = isEnabled -> {
                if (com.gero.newpass.BuildConfig.DEBUG) Log.i(TAG, "Advanced Protection toggled — now " +
                        (isEnabled ? "ENABLED" : "DISABLED"));
                if (!isEnabled) {
                    if (com.gero.newpass.BuildConfig.DEBUG) Log.w(TAG, "User disabled Advanced Protection while app is running");
                    // You can take further action here, e.g. show a warning dialog
                }
            };

            registerMethod.invoke(service, mainExecutor, callback);
            if (com.gero.newpass.BuildConfig.DEBUG) Log.d(TAG, "Registered Advanced Protection callback");
        } catch (Exception e) {
            if (com.gero.newpass.BuildConfig.DEBUG) Log.w(TAG, "Could not register Advanced Protection callback", e);
        }
    }

    // ──────────── Layer 2: Signature Verification ─────────────

    /**
     * Calculates the SHA-256 fingerprint of the app's current signing certificate and
     * compares it against {@link #OFFICIAL_SIGNATURE_SHA256}.
     */
    public static SecurityReport checkSignature(Context context) {
        try {
            String currentFingerprint = getAppSignatureFingerprint(context);
            if (currentFingerprint == null) {
                return SecurityReport.fail(2,
                        "Unable to read the app's signing certificate.\n\n" +
                        "The app may have been tampered with.");
            }

            if (com.gero.newpass.BuildConfig.DEBUG) Log.d(TAG, "Current signature SHA-256: " + currentFingerprint);

            // Skip check if placeholder is still in place (development convenience)
            if (OFFICIAL_SIGNATURE_SHA256.equals("REPLACE_ME_WITH_YOUR_RELEASE_FINGERPRINT")) {
                if (com.gero.newpass.BuildConfig.DEBUG) Log.w(TAG, "⚠ Signature check SKIPPED — official fingerprint not configured.");
                return SecurityReport.pass();
            }

            if (!OFFICIAL_SIGNATURE_SHA256.equalsIgnoreCase(currentFingerprint)) {
                return SecurityReport.fail(2,
                        "The app's signing certificate does not match the official release.\n\n" +
                        "This copy may have been tampered with or re-signed.");
            }

            return SecurityReport.pass();

        } catch (Exception e) {
            if (com.gero.newpass.BuildConfig.DEBUG) Log.e(TAG, "Signature verification error", e);
            return SecurityReport.fail(2,
                    "Signature verification failed unexpectedly.");
        }
    }

    /**
     * Reads the app's signing certificate and returns its SHA-256 fingerprint
     * as an upper-case, colon-separated hex string (e.g. {@code "AB:CD:EF:..."}).
     *
     * <p>Uses {@code GET_SIGNING_CERTIFICATES} on API ≥ 28, falls back to
     * {@code GET_SIGNATURES} on older APIs.</p>
     *
     * @return The SHA-256 hex fingerprint, or {@code null} on failure.
     */
    public static String getAppSignatureFingerprint(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            Signature signature;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
                PackageInfo packageInfo = pm.getPackageInfo(packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo.signingInfo == null) return null;

                // Use the first signer in the current scheme
                Signature[] signers = packageInfo.signingInfo.getApkContentsSigners();
                if (signers == null || signers.length == 0) return null;
                signature = signers[0];
            } else {
                @SuppressWarnings("deprecation")
                PackageInfo packageInfo = pm.getPackageInfo(packageName,
                        PackageManager.GET_SIGNATURES);
                @SuppressWarnings("deprecation")
                Signature[] signatures = packageInfo.signatures;
                if (signatures == null || signatures.length == 0) return null;
                signature = signatures[0];
            }

            return sha256Hex(signature.toByteArray());

        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException e) {
            if (com.gero.newpass.BuildConfig.DEBUG) Log.e(TAG, "Failed to get signature fingerprint", e);
            return null;
        }
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * Computes the SHA-256 digest of {@code data} and returns it as an upper-case,
     * colon-separated hex string.
     */
    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 3 - 1);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", digest[i]));
        }
        return sb.toString();
    }

    // ───────────────────────── Report ─────────────────────────

    /**
     * Immutable result of a security check.
     */
    public static final class SecurityReport {
        /** {@code true} if all executed checks passed. */
        public final boolean passed;
        /** Human-readable reason for failure (empty string if passed). */
        public final String failureReason;
        /** Which layer failed (1 or 2). 0 if all passed. */
        public final int failedLayer;

        private SecurityReport(boolean passed, int failedLayer, String failureReason) {
            this.passed = passed;
            this.failedLayer = failedLayer;
            this.failureReason = failureReason;
        }

        static SecurityReport pass() {
            return new SecurityReport(true, 0, "");
        }

        static SecurityReport fail(int layer, String reason) {
            return new SecurityReport(false, layer, reason);
        }
    }
}
