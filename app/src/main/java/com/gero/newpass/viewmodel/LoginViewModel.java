package com.gero.newpass.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.security.crypto.EncryptedSharedPreferences;

import com.gero.newpass.R;
import com.gero.newpass.encryption.HashUtils;
import com.gero.newpass.repository.ResourceRepository;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import android.util.Base64;
import com.gero.newpass.encryption.BiometricHelper;

public class LoginViewModel extends ViewModel {

    public enum AuthUiState {
        IDLE,
        VERIFYING_MASTER,
        VERIFYING_RECOVERY,
        MASTER_LOCKED,
        RECOVERY_LOCKED,
        AUTHENTICATED
    }

    private final MutableLiveData<String> loginMessageLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loginSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> recoveryCodeLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> databaseKeyLiveData = new MutableLiveData<>();
    private final MutableLiveData<AuthUiState> authUiStateLiveData = new MutableLiveData<>(AuthUiState.IDLE);
    private final ResourceRepository resourceRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean passwordLoginInProgress = false;
    private boolean recoveryResetInProgress = false;

    // Brute-force protection
    private static final int MASTER_LOCK_ATTEMPTS = 6;
    private static final int RECOVERY_LOCK_ATTEMPTS = 6;
    private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;
    private static final long FIFTEEN_MINUTES_MS = 15 * 60 * 1000L;
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;
    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;
    private static final String MASTER_FAILED_ATTEMPTS = "master_failed_attempts";
    private static final String MASTER_LOCKED = "master_locked";
    private static final String MASTER_LAST_FAILED_AT = "master_last_failed_at";
    private static final String RECOVERY_FAILED_ATTEMPTS = "recovery_failed_attempts";
    private static final String RECOVERY_LAST_FAILED_AT = "recovery_last_failed_at";


    public LoginViewModel(ResourceRepository resourceRepository) {
        this.resourceRepository =  resourceRepository;
    }


    public LiveData<String> getLoginMessageLiveData() {
        return loginMessageLiveData;
    }
    public LiveData<Boolean> getLoginSuccessLiveData() {
        return loginSuccessLiveData;
    }
    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }
    public LiveData<String> getRecoveryCodeLiveData() {
        return recoveryCodeLiveData;
    }
    public LiveData<String> getDatabaseKeyLiveData() {
        return databaseKeyLiveData;
    }
    public LiveData<AuthUiState> getAuthUiStateLiveData() {
        return authUiStateLiveData;
    }


    public void createUser(String password, EncryptedSharedPreferences sharedPreferences) throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (password.length() >= 6) {
            loadingLiveData.setValue(true);
            
            executor.execute(() -> {
                try {
                    String hashedPassword = HashUtils.hashPassword(password);
                    
                    mainHandler.post(() -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("password", hashedPassword);

                        // Encrypt the hash for biometric login
                        try {
                            BiometricHelper.createBiometricKey();
                            Cipher encCipher = BiometricHelper.getEncryptionCipher();
                            byte[] encrypted = encCipher.doFinal(hashedPassword.getBytes(StandardCharsets.UTF_8));
                            String biometricWrapped = Base64.encodeToString(encrypted, Base64.DEFAULT);
                            editor.putString("biometric_wrapped_password", biometricWrapped);
                        } catch (Exception e) {
                            // Biometric keystore not available
                            editor.remove("biometric_wrapped_password");
                        }

                        editor.apply();

                        // Generate and store a one-time recovery key
                        String plainRecoveryCode = generateAndStoreRecoveryCode(sharedPreferences);

                        loadingLiveData.setValue(false);
                        // Emit plaintext code FIRST so the activity observer can see pendingCode != null
                        recoveryCodeLiveData.setValue(plainRecoveryCode);
                        databaseKeyLiveData.setValue(hashedPassword);
                        loginSuccessLiveData.setValue(true);
                        loginMessageLiveData.setValue(resourceRepository.getString(R.string.user_created_successfully));
                    });
                } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                    mainHandler.post(() -> {
                        loadingLiveData.setValue(false);
                        loginSuccessLiveData.setValue(false);
                        loginMessageLiveData.setValue("Error creating password");
                    });
                }
            });
        } else {
            loginSuccessLiveData.setValue(false);
            loginMessageLiveData.setValue(resourceRepository.getString(R.string.password_must_be_at_least_4_characters_long));
        }
    }

    public void loginUserWithPassword(String password, EncryptedSharedPreferences sharedPreferences) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (passwordLoginInProgress) {
            return;
        }

        String hashedPassword = sharedPreferences.getString("password", "");

        if (isMasterPasswordLocked(sharedPreferences)) {
            databaseKeyLiveData.setValue(null);
            loginSuccessLiveData.setValue(false);
            authUiStateLiveData.setValue(AuthUiState.MASTER_LOCKED);
            loginMessageLiveData.setValue(resourceRepository.getString(R.string.master_password_locked));
            return;
        }

        performLogin(password, hashedPassword, sharedPreferences);
    }

    private void performLogin(String password, String hashedPassword, EncryptedSharedPreferences sharedPreferences) {
        passwordLoginInProgress = true;
        authUiStateLiveData.setValue(AuthUiState.VERIFYING_MASTER);
        loadingLiveData.setValue(true);
        
        executor.execute(() -> {
            try {
                boolean verified = HashUtils.verifyPassword(password, hashedPassword);
                
                mainHandler.post(() -> {
                    passwordLoginInProgress = false;
                    loadingLiveData.setValue(false);
                    if (verified) {
                        clearAuthFailureState(sharedPreferences);
                        databaseKeyLiveData.setValue(hashedPassword);
                        
                        // Encrypt the hash for biometric login
                        try {
                            BiometricHelper.createBiometricKey();
                            Cipher encCipher = BiometricHelper.getEncryptionCipher();
                            byte[] encrypted = encCipher.doFinal(hashedPassword.getBytes(StandardCharsets.UTF_8));
                            String biometricWrapped = Base64.encodeToString(encrypted, Base64.DEFAULT);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("biometric_wrapped_password", biometricWrapped);
                            editor.apply();
                        } catch (Exception e) {
                            // Ignored: Biometrics not supported on this device
                        }

                        loginSuccessLiveData.setValue(true);
                        authUiStateLiveData.setValue(AuthUiState.AUTHENTICATED);
                    } else {
                        int failedAttempts = sharedPreferences.getInt(MASTER_FAILED_ATTEMPTS, 0) + 1;
                        SharedPreferences.Editor editor = sharedPreferences.edit()
                                .putInt(MASTER_FAILED_ATTEMPTS, failedAttempts)
                                .putLong(MASTER_LAST_FAILED_AT, System.currentTimeMillis());
                        if (failedAttempts >= MASTER_LOCK_ATTEMPTS) {
                            editor.putBoolean(MASTER_LOCKED, true);
                            editor.remove("biometric_wrapped_password");
                        }
                        editor.apply();
                        databaseKeyLiveData.setValue(null);
                        loginSuccessLiveData.setValue(false);
                        if (failedAttempts >= MASTER_LOCK_ATTEMPTS) {
                            authUiStateLiveData.setValue(AuthUiState.MASTER_LOCKED);
                            loginMessageLiveData.setValue(resourceRepository.getString(R.string.master_password_locked));
                        } else {
                            authUiStateLiveData.setValue(AuthUiState.IDLE);
                            int attemptsLeft = MASTER_LOCK_ATTEMPTS - failedAttempts;
                            loginMessageLiveData.setValue(resourceRepository.getString(R.string.access_denied) + " (" + attemptsLeft + " attempts left)");
                        }
                    }
                });
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                mainHandler.post(() -> {
                    passwordLoginInProgress = false;
                    loadingLiveData.setValue(false);
                    loginSuccessLiveData.setValue(false);
                    authUiStateLiveData.setValue(AuthUiState.IDLE);
                    loginMessageLiveData.setValue("Verification error");
                });
            }
        });
    }

    public void loginUserWithBiometricAuth(Context context, EncryptedSharedPreferences sharedPreferences) {
        if (passwordLoginInProgress) {
            return;
        }

        if (isMasterPasswordLocked(sharedPreferences)) {
            databaseKeyLiveData.setValue(null);
            loginSuccessLiveData.setValue(false);
            authUiStateLiveData.setValue(AuthUiState.MASTER_LOCKED);
            loginMessageLiveData.setValue(resourceRepository.getString(R.string.master_password_locked));
            return;
        }

        Cipher decryptCipher;
        try {
            decryptCipher = BiometricHelper.getDecryptionCipher();
        } catch (Exception e) {
            loginMessageLiveData.postValue("Biometrics not set up securely yet. Please login with your password once.");
            return;
        }

        BiometricPrompt.CryptoObject cryptoObject = new BiometricPrompt.CryptoObject(decryptCipher);

        java.util.concurrent.Executor biometricExecutor = ContextCompat.getMainExecutor(context);
        BiometricPrompt biometricPrompt = new BiometricPrompt((FragmentActivity) context, biometricExecutor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                loginSuccessLiveData.postValue(false);
                loginMessageLiveData.postValue(errString.toString());
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                
                try {
                    Cipher unlockedCipher = result.getCryptoObject().getCipher();
                    String wrappedPassword = sharedPreferences.getString("biometric_wrapped_password", null);
                    
                    if (wrappedPassword == null) {
                        throw new Exception("Missing biometric configuration.");
                    }
                    
                    byte[] decryptedBytes = unlockedCipher.doFinal(Base64.decode(wrappedPassword, Base64.DEFAULT));
                    String originalHashedPassword = new String(decryptedBytes, StandardCharsets.UTF_8);

                    clearAuthFailureState(sharedPreferences);
                    databaseKeyLiveData.setValue(originalHashedPassword);
                    loginSuccessLiveData.setValue(true);
                    authUiStateLiveData.setValue(AuthUiState.AUTHENTICATED);
                } catch (Exception e) {
                    databaseKeyLiveData.setValue(null);
                    loginSuccessLiveData.setValue(false);
                    authUiStateLiveData.setValue(AuthUiState.IDLE);
                    loginMessageLiveData.setValue("Failed to decrypt biometric credentials");
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                databaseKeyLiveData.setValue(null);
                loginSuccessLiveData.setValue(false);
                authUiStateLiveData.setValue(AuthUiState.IDLE);
                loginMessageLiveData.setValue(resourceRepository.getString(R.string.access_denied));
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.login))
                .setSubtitle(context.getString(R.string.use_your_biometric_or_device_credentials))
                .setNegativeButtonText(context.getString(R.string.cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        biometricPrompt.authenticate(promptInfo, cryptoObject);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    // ── Recovery Key ──────────────────────────────────────────────────────────

    private static final String RECOVERY_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous I/1/O/0
    private static final int RECOVERY_CODE_LENGTH = 24;

    /**
     * Generates a random 24-character alphanumeric recovery code, hashes it with PBKDF2,
     * stores the hash in EncryptedSharedPreferences, and returns the plaintext code.
     * The plaintext is never stored — it is returned once and expected to be shown to the user.
     */
    public String generateAndStoreRecoveryCode(EncryptedSharedPreferences sharedPreferences) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            sb.append(RECOVERY_CODE_CHARS.charAt(random.nextInt(RECOVERY_CODE_CHARS.length())));
        }
        String plainCode = sb.toString();

        // Format as XXXX-XXXX-XXXX-XXXX-XXXX-XXXX for readability
        String formatted = plainCode.substring(0, 4) + "-" +
                plainCode.substring(4, 8) + "-" +
                plainCode.substring(8, 12) + "-" +
                plainCode.substring(12, 16) + "-" +
                plainCode.substring(16, 20) + "-" +
                plainCode.substring(20, 24);

        try {
            String hashedCode = HashUtils.hashPassword(plainCode); // hash the unformatted code
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("recovery_code_hash", hashedCode);
            editor.apply();
        } catch (Exception e) {
            // Hashing failed — store nothing; recovery simply won't be available
        }

        return formatted;
    }

    /**
     * Verifies the entered recovery code, resets the password, and generates a fresh recovery code.
     * Calls back with (success, newRecoveryCodeOrNull).
     */
    public interface RecoveryCallback {
        void onResult(boolean success, String newFormattedCode);
    }

    public void verifyAndResetWithRecoveryCode(
            Context context,
            String enteredCode,
            String newPassword,
            EncryptedSharedPreferences sharedPreferences,
            RecoveryCallback callback) {

        if (recoveryResetInProgress) {
            return;
        }

        String storedHash = sharedPreferences.getString("recovery_code_hash", "");

        if (storedHash.isEmpty()) {
            callback.onResult(false, null);
            return;
        }

        if (newPassword.length() < 6) {
            callback.onResult(false, null);
            return;
        }

        if (isRecoveryTemporarilyLocked(sharedPreferences)) {
            authUiStateLiveData.setValue(AuthUiState.RECOVERY_LOCKED);
            callback.onResult(false, null);
            return;
        }

        loadingLiveData.setValue(true);
        recoveryResetInProgress = true;
        authUiStateLiveData.setValue(AuthUiState.VERIFYING_RECOVERY);

        // Strip dashes from entered code before verifying
        String stripped = enteredCode.replace("-", "").toUpperCase(Locale.ROOT);

        executor.execute(() -> {
            try {
                boolean valid = HashUtils.verifyPassword(stripped, storedHash);

                if (valid) {
                    String newHashedPassword = HashUtils.hashPassword(newPassword);

                    mainHandler.post(() -> {
                        recoveryResetInProgress = false;
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("password", newHashedPassword);
                        // Invalidate old biometric wrap
                        editor.remove("biometric_wrapped_password");
                        editor.apply();
                        
                        // IMPORTANT: Update the SQLCipher database key!
                        com.gero.newpass.database.DatabaseHelper.changeDBPassword(newHashedPassword, context);

                        // Generate a brand-new recovery code (old one is now consumed)
                        String newCode = generateAndStoreRecoveryCode(sharedPreferences);

                        loadingLiveData.setValue(false);
                        authUiStateLiveData.setValue(AuthUiState.AUTHENTICATED);
                        callback.onResult(true, newCode);
                    });
                } else {
                    mainHandler.post(() -> {
                        recoveryResetInProgress = false;
                        int attempts = sharedPreferences.getInt(RECOVERY_FAILED_ATTEMPTS, 0) + 1;
                        sharedPreferences.edit()
                                .putInt(RECOVERY_FAILED_ATTEMPTS, attempts)
                                .putLong(RECOVERY_LAST_FAILED_AT, System.currentTimeMillis())
                                .apply();
                        loadingLiveData.setValue(false);
                        authUiStateLiveData.setValue(isRecoveryTemporarilyLocked(sharedPreferences)
                                ? AuthUiState.RECOVERY_LOCKED
                                : AuthUiState.IDLE);
                        callback.onResult(false, null);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    recoveryResetInProgress = false;
                    loadingLiveData.setValue(false);
                    authUiStateLiveData.setValue(AuthUiState.IDLE);
                    callback.onResult(false, null);
                });
            }
        });
    }

    public boolean isMasterPasswordLocked(EncryptedSharedPreferences sharedPreferences) {
        return sharedPreferences.getBoolean(MASTER_LOCKED, false)
                || sharedPreferences.getInt(MASTER_FAILED_ATTEMPTS, 0) >= MASTER_LOCK_ATTEMPTS;
    }

    public boolean isRecoveryTemporarilyLocked(EncryptedSharedPreferences sharedPreferences) {
        return getRecoveryLockoutRemainingMs(sharedPreferences) > 0;
    }

    public long getRecoveryLockoutRemainingMs(EncryptedSharedPreferences sharedPreferences) {
        int attempts = sharedPreferences.getInt(RECOVERY_FAILED_ATTEMPTS, 0);
        if (attempts < RECOVERY_LOCK_ATTEMPTS) {
            return 0L;
        }

        long lockDuration = getRecoveryLockDurationMs(attempts);
        long lastFailedAt = sharedPreferences.getLong(RECOVERY_LAST_FAILED_AT, 0L);
        long elapsed = System.currentTimeMillis() - lastFailedAt;
        return Math.max(0L, lockDuration - elapsed);
    }

    public boolean shouldShowManualWipe(EncryptedSharedPreferences sharedPreferences) {
        return sharedPreferences.getInt(RECOVERY_FAILED_ATTEMPTS, 0) >= RECOVERY_LOCK_ATTEMPTS
                && isRecoveryTemporarilyLocked(sharedPreferences);
    }

    public String getFormattedRecoveryLockout(EncryptedSharedPreferences sharedPreferences) {
        long remainingMs = getRecoveryLockoutRemainingMs(sharedPreferences);
        if (remainingMs <= 0) {
            return "";
        }
        return DateUtils.formatElapsedTime((remainingMs + 999L) / 1000L);
    }

    public int getRecoveryFailedAttempts(EncryptedSharedPreferences sharedPreferences) {
        return sharedPreferences.getInt(RECOVERY_FAILED_ATTEMPTS, 0);
    }

    public void wipeVault(Context context, EncryptedSharedPreferences sharedPreferences) {
        com.gero.newpass.database.DatabaseHelper.deleteVaultDatabase(context);
        sharedPreferences.edit().clear().apply();
        com.gero.newpass.utilities.StringHelper.clearSharedString();
        try {
            BiometricHelper.deleteBiometricKey();
        } catch (Exception ignored) {
        }
        databaseKeyLiveData.setValue(null);
        recoveryCodeLiveData.setValue(null);
        loginSuccessLiveData.setValue(false);
        authUiStateLiveData.setValue(AuthUiState.IDLE);
    }

    private static long getRecoveryLockDurationMs(int attempts) {
        if (attempts == 6) {
            return FIVE_MINUTES_MS;
        }
        if (attempts == 7) {
            return FIFTEEN_MINUTES_MS;
        }
        if (attempts == 8) {
            return ONE_HOUR_MS;
        }
        return ONE_DAY_MS;
    }

    private static void clearMasterLockout(EncryptedSharedPreferences sharedPreferences) {
        clearMasterLockout(sharedPreferences.edit()).apply();
    }

    public void clearAuthFailureState(EncryptedSharedPreferences sharedPreferences) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        clearMasterLockout(editor);
        clearRecoveryLockout(editor);
        editor.apply();
    }

    private static SharedPreferences.Editor clearMasterLockout(SharedPreferences.Editor editor) {
        return editor.remove(MASTER_FAILED_ATTEMPTS)
                .remove(MASTER_LOCKED)
                .remove(MASTER_LAST_FAILED_AT);
    }

    private static SharedPreferences.Editor clearRecoveryLockout(SharedPreferences.Editor editor) {
        return editor.remove(RECOVERY_FAILED_ATTEMPTS)
                .remove(RECOVERY_LAST_FAILED_AT);
    }
}
