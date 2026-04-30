package com.gero.newpass.utilities;

import com.gero.newpass.BuildConfig;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.security.crypto.EncryptedSharedPreferences;

import com.gero.newpass.R;
import com.gero.newpass.database.DatabaseHelper;
import com.gero.newpass.encryption.HashUtils;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DialogHelper {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static void dismissSafe(AlertDialog dialog, Context context) {
        if (dialog != null && dialog.isShowing()) {
            if (context instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) context;
                if (activity.isFinishing() || activity.isDestroyed()) return;
            }
            try { dialog.dismiss(); } catch (Exception ignored) {}
        }
    }

    public static void showChangePasswordDialog(Context context, EncryptedSharedPreferences encryptedSharedPreferences) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        EditText firstInput = dialogView.findViewById(R.id.first_input);
        EditText secondInput = dialogView.findViewById(R.id.second_input);
        EditText thirdInput = dialogView.findViewById(R.id.third_input);

        // Reset timer when user types
        android.text.TextWatcher resetTimerWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (context instanceof com.gero.newpass.view.activities.MainViewActivity) {
                    ((com.gero.newpass.view.activities.MainViewActivity) context).startInactivityTimer();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        firstInput.addTextChangedListener(resetTimerWatcher);
        secondInput.addTextChangedListener(resetTimerWatcher);
        thirdInput.addTextChangedListener(resetTimerWatcher);

        builder.setTitle(R.string.settings_change_password)
                .setPositiveButton(R.string.update_alertdialog_yes, (dialog, id) -> {

                    String inputOldPassword = firstInput.getText().toString();
                    String inputNewPassword = secondInput.getText().toString();
                    String inputConfirmNewPassword = thirdInput.getText().toString();

                    if (inputNewPassword.length() < 6) {
                        com.gero.newpass.utilities.ToastHelper.showToast(context, R.string.password_must_be_at_least_4_characters_long, Toast.LENGTH_SHORT);
                        return;
                    }
                    if (!inputNewPassword.equals(inputConfirmNewPassword)) {
                        com.gero.newpass.utilities.ToastHelper.showToast(context, R.string.passwords_do_not_match, Toast.LENGTH_SHORT);
                        return;
                    }

                    String hashedPasswordFromSharedPrefs = encryptedSharedPreferences.getString("password", "");

                    // Show loading while verifying + hashing on background thread
                    AlertDialog loadingDialog = showLoadingDialog(context, "Changing password...");

                    executor.execute(() -> {
                        try {
                            boolean verified = HashUtils.verifyPassword(inputOldPassword, hashedPasswordFromSharedPrefs);

                            if (verified) {
                                String hashedPassword = HashUtils.hashPassword(inputNewPassword);

                                mainHandler.post(() -> {
                                    SharedPreferences.Editor editor = encryptedSharedPreferences.edit();
                                    editor.putString("password", hashedPassword);
                                    // Invalidate biometric wrap — user must re-login once to re-register biometrics
                                    editor.remove("biometric_wrapped_password");
                                    editor.apply();

                                    DatabaseHelper.changeDBPassword(hashedPassword, context);

                                    // Generate a fresh recovery code and show it once
                                    com.gero.newpass.viewmodel.LoginViewModel tempVm =
                                            new com.gero.newpass.viewmodel.LoginViewModel(
                                                    new com.gero.newpass.repository.ResourceRepository(context));
                                    String newCode = tempVm.generateAndStoreRecoveryCode(encryptedSharedPreferences);

                                    dismissSafe(loadingDialog, context);
                                    showRecoveryCodeAfterPasswordChange(context, newCode);
                                });
                            } else {
                                mainHandler.post(() -> {
                                    dismissSafe(loadingDialog, context);
                                    com.gero.newpass.utilities.ToastHelper.showToast(context, R.string.wrong_password, Toast.LENGTH_SHORT);
                                });
                            }
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            mainHandler.post(() -> {
                                dismissSafe(loadingDialog, context);
                                com.gero.newpass.utilities.ToastHelper.showToast(context, "Error changing password", Toast.LENGTH_SHORT);
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.update_alertdialog_no, (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void showExportingDialog(Context context, Uri targetUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_export_or_import_db, null);
        builder.setView(dialogView);
        EditText input = dialogView.findViewById(R.id.input);
        
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (context instanceof com.gero.newpass.view.activities.MainViewActivity) {
                    ((com.gero.newpass.view.activities.MainViewActivity) context).startInactivityTimer();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        builder.setTitle(R.string.export_database)
                .setPositiveButton(R.string.confirm, (dialog, id) -> {
                    String password = input.getText().toString();

                    if (password.isEmpty()) {
                        com.gero.newpass.utilities.ToastHelper.showToast(context, context.getString(R.string.password_cannot_be_empty), Toast.LENGTH_LONG);
                    } else {
                        // Show loading dialog and run export on background thread
                        AlertDialog loadingDialog = showLoadingDialog(context, "Exporting...");
                        
                        executor.execute(() -> {
                            DatabaseHelper.exportDatabaseToJson(context, password, targetUri);
                            
                            mainHandler.post(() -> {
                                dismissSafe(loadingDialog, context);
                            });
                        });
                    }

                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void showImportingDialog(Context context, Uri fileURL) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_export_or_import_db, null);
        builder.setView(dialogView);
        EditText input = dialogView.findViewById(R.id.input);
        
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (context instanceof com.gero.newpass.view.activities.MainViewActivity) {
                    ((com.gero.newpass.view.activities.MainViewActivity) context).startInactivityTimer();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        builder.setTitle(R.string.import_database)
                .setPositiveButton(R.string.confirm, (dialog, id) -> {
                    String password = input.getText().toString();
                    
                    // Show loading dialog and run import on background thread
                    AlertDialog loadingDialog = showLoadingDialog(context, "Importing...");
                    
                    executor.execute(() -> {
                        int[] results = null;
                        try {
                            results = DatabaseHelper.importJsonToDatabase(context, fileURL, password);
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            if (BuildConfig.DEBUG) Log.e("DialogHelper", "Import error", e);
                        }
                        
                        final int[] finalResults = results;
                        mainHandler.post(() -> {
                            dismissSafe(loadingDialog, context);
                            
                            if (finalResults != null) {
                                if (context instanceof android.app.Activity && !((android.app.Activity)context).isFinishing() && !((android.app.Activity)context).isDestroyed()) {
                                    showImportSummaryDialog(context, finalResults[0], finalResults[1], finalResults[2]);
                                }
                            } else {
                                com.gero.newpass.utilities.ToastHelper.showToast(context, R.string.error_importing_database, Toast.LENGTH_LONG);
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Creates and shows a beautiful non-cancellable loading dialog with an accent-colored spinner
     * and a status message.
     */
    private static AlertDialog showLoadingDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_loading, null);
        
        TextView loadingMessage = dialogView.findViewById(R.id.loading_message);
        loadingMessage.setText(message);
        
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_rounded_input);
        }

        if (context instanceof androidx.lifecycle.LifecycleOwner) {
            ((androidx.lifecycle.LifecycleOwner) context).getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@androidx.annotation.NonNull androidx.lifecycle.LifecycleOwner owner) {
                    dismissSafe(dialog, context);
                }
            });
        }

        dialog.show();
        
        return dialog;
    }

    /**
     * Shows a beautiful import summary dialog with stats for added, ignored, and conflict entries.
     */
    private static void showImportSummaryDialog(Context context, int added, int ignored, int conflicts) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_import_summary, null);

        TextView countAdded = dialogView.findViewById(R.id.count_added);
        TextView countIgnored = dialogView.findViewById(R.id.count_ignored);
        TextView countConflicts = dialogView.findViewById(R.id.count_conflicts);

        countAdded.setText(String.valueOf(added));
        countIgnored.setText(String.valueOf(ignored));
        countConflicts.setText(String.valueOf(conflicts));

        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        dialogView.findViewById(R.id.btn_done).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
    /**
     * Shows the new recovery code to the user immediately after a Settings → Change Password success.
     */
    private static void showRecoveryCodeAfterPasswordChange(Context context, String formattedCode) {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_recovery_code, null);
        TextView tvCode = dialogView.findViewById(R.id.tv_recovery_code);
        android.widget.Button btnSaved = dialogView.findViewById(R.id.btn_saved);
        android.widget.ImageButton btnCopy = dialogView.findViewById(R.id.btn_copy_code);

        tvCode.setText(formattedCode);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        if (context instanceof androidx.lifecycle.LifecycleOwner) {
            ((androidx.lifecycle.LifecycleOwner) context).getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@androidx.annotation.NonNull androidx.lifecycle.LifecycleOwner owner) {
                    dismissSafe(dialog, context);
                }
            });
        }

        btnCopy.setOnClickListener(v -> {
            if (context instanceof com.gero.newpass.view.activities.MainViewActivity) {
                ((com.gero.newpass.view.activities.MainViewActivity) context).startInactivityTimer();
            }
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("RecoveryKey", formattedCode));
            ToastHelper.showToast(context, R.string.recovery_key_copied, Toast.LENGTH_SHORT);
        });

        btnSaved.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
