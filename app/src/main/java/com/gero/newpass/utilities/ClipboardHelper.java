package com.gero.newpass.utilities;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class ClipboardHelper {
    public static void copyToClipboardWithTimeout(Context context, String label, String text, int timeoutMs) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;

        ClipData clip = ClipData.newPlainText(label, text);
        // Add sensitive flag for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);

        // Clear clipboard after timeout if it's still the same password
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (clipboard.hasPrimaryClip()) {
                    ClipData currentClip = clipboard.getPrimaryClip();
                    if (currentClip != null && currentClip.getItemCount() > 0) {
                        CharSequence currentText = currentClip.getItemAt(0).getText();
                        if (currentText != null && text.equals(currentText.toString())) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore clipboard access exceptions if app is in background
            }
        }, timeoutMs);
    }
}
