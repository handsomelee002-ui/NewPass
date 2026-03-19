package com.gero.newpass.utilities;

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

    public static void showChangePasswordDialog(Context context, EncryptedSharedPreferences encryptedSharedPreferences) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        EditText firstInput = dialogView.findViewById(R.id.first_input);
        EditText secondInput = dialogView.findViewById(R.id.second_input);
        EditText thirdInput = dialogView.findViewById(R.id.third_input);

        builder.setTitle(R.string.settings_change_password)
                .setPositiveButton(R.string.update_alertdialog_yes, (dialog, id) -> {

                    String inputOldPassword = firstInput.getText().toString();
                    String inputNewPassword = secondInput.getText().toString();
                    String inputConfirmNewPassword = thirdInput.getText().toString();

                    if (inputNewPassword.length() < 4) {
                        Toast.makeText(context, R.string.password_must_be_at_least_4_characters_long, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!inputNewPassword.equals(inputConfirmNewPassword)) {
                        Toast.makeText(context, R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show();
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
                                    editor.apply();

                                    DatabaseHelper.changeDBPassword(hashedPassword, context);

                                    if (loadingDialog.isShowing()) {
                                        loadingDialog.dismiss();
                                    }
                                });
                            } else {
                                mainHandler.post(() -> {
                                    if (loadingDialog.isShowing()) {
                                        loadingDialog.dismiss();
                                    }
                                    Toast.makeText(context, R.string.wrong_password, Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                            mainHandler.post(() -> {
                                if (loadingDialog.isShowing()) {
                                    loadingDialog.dismiss();
                                }
                                Toast.makeText(context, "Error changing password", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.update_alertdialog_no, (dialog, id) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static void showExportingDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_export_or_import_db, null);
        builder.setView(dialogView);
        EditText input = dialogView.findViewById(R.id.input);

        builder.setTitle(R.string.export_database)
                .setPositiveButton(R.string.confirm, (dialog, id) -> {
                    String password = input.getText().toString();

                    if (password.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.password_cannot_be_empty), Toast.LENGTH_LONG).show();
                    } else {
                        // Show loading dialog and run export on background thread
                        AlertDialog loadingDialog = showLoadingDialog(context, "Exporting...");
                        
                        executor.execute(() -> {
                            DatabaseHelper.exportDatabaseToJson(context, password);
                            
                            mainHandler.post(() -> {
                                if (loadingDialog.isShowing()) {
                                    loadingDialog.dismiss();
                                }
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
                            Log.e("8953467", "Error: ", e);
                        }
                        
                        final int[] finalResults = results;
                        mainHandler.post(() -> {
                            if (loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }
                            
                            if (finalResults != null) {
                                showImportSummaryDialog(context, finalResults[0], finalResults[1], finalResults[2]);
                            } else {
                                Toast.makeText(context, R.string.error_importing_database, Toast.LENGTH_LONG).show();
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
}
