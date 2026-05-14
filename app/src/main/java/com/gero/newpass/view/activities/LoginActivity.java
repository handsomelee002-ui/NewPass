package com.gero.newpass.view.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.gero.newpass.BuildConfig;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.security.crypto.EncryptedSharedPreferences;

import com.gero.newpass.R;
import com.gero.newpass.SharedPreferences.SharedPreferencesHelper;
import com.gero.newpass.databinding.ActivityLoginBinding;
import com.gero.newpass.encryption.EncryptionHelper;
import com.gero.newpass.factory.ViewMoldelsFactory;
import com.gero.newpass.repository.ResourceRepository;
import com.gero.newpass.utilities.AnimationsUtility;
import com.gero.newpass.utilities.AppIntegrityGuard;
import com.gero.newpass.utilities.StringHelper;
import com.gero.newpass.utilities.SystemBarColorHelper;
import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.viewmodel.LoginViewModel;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class LoginActivity extends AppCompatActivity {

    private EditText passwordEntry;
    private ImageButton buttonRegisterOrUnlock, buttonPasswordVisibility, biometricButton;
    private ImageView passwordBox, bgImage;
    private TextView welcomeTextView, textViewRegisterOrUnlock, forgotPasswordTv;
    private FrameLayout loadingOverlay;
    private EncryptedSharedPreferences encryptedSharedPreferences;
    private LoginViewModel loginViewModel;
    private Boolean isPasswordVisible = false;


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);

        // ──── Security gate: block sideloaded / tampered installs ────
        boolean isDebuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        AppIntegrityGuard.SecurityReport securityReport = AppIntegrityGuard.runAllChecks(this);
        
        if (!securityReport.passed) {
            if (isDebuggable) {
                // Bypass the block strictly for debug development builds
                Log.w("AppIntegrityGuard", "(Debug Bypass) Security Check Failed: " + securityReport.failureReason);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Security Alert")
                        .setMessage(securityReport.failureReason)
                        .setCancelable(false)
                        .setPositiveButton("Close", (dialog, which) -> finishAffinity())
                        .show();
                return; // Do NOT initialise any UI or data
            }
        }
        // ──── End security gate ────

        ActivityLoginBinding binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SystemBarColorHelper.changeBarsColor(this, R.color.background_primary);

        initViews(binding);

        textViewRegisterOrUnlock.setText(getString(R.string.create_password_button_text));

        welcomeTextView.setText(getString(R.string.welcome_newpass_text));

        loginViewModel = new ViewModelProvider(this, new ViewMoldelsFactory(new ResourceRepository(getApplicationContext()))).get(LoginViewModel.class);

        loginViewModel.getLoginMessageLiveData().observe(this, message -> {
             if (message != null && !message.isEmpty() && !isFinishing() && !isDestroyed()) {
                 com.gero.newpass.utilities.ToastHelper.showToast(this, message, Toast.LENGTH_SHORT);
             }
        });

        loginViewModel.getLoginSuccessLiveData().observe(this, success -> {
            if (success) {
                // If a recovery code is about to be shown (first account creation or recovery reset),
                // let the recoveryCodeLiveData observer handle navigation via the dialog's confirm button.
                String pendingCode = loginViewModel.getRecoveryCodeLiveData().getValue();
                if (pendingCode != null && !pendingCode.isEmpty()) {
                    return; // navigation handled by showRecoveryCodeDialog
                }
                navigateToMain();
            } else {
                if (encryptedSharedPreferences != null && loginViewModel.isMasterPasswordLocked(encryptedSharedPreferences)) {
                    applyMasterLockoutUi();
                    showForgotPasswordDialog();
                }
                AnimationsUtility.errorAnimation(buttonRegisterOrUnlock, textViewRegisterOrUnlock);
            }
        });

        // Show one-time recovery code dialog when a new code is generated
        loginViewModel.getRecoveryCodeLiveData().observe(this, code -> {
            if (code != null && !code.isEmpty()) {
                showRecoveryCodeDialog(code);
            }
        });

        // Observe loading state
        loginViewModel.getLoadingLiveData().observe(this, isLoading -> {
            if (isLoading) {
                showLoading();
            } else {
                hideLoading();
            }
        });

        encryptedSharedPreferences = EncryptionHelper.getEncryptedSharedPreferences(getApplicationContext());

        //Determining whether to set dark or light mode based on shared preferences
        SharedPreferencesHelper.toggleDarkLightModeUI(this);

        String hashedPassword = encryptedSharedPreferences.getString("password", "");
        Boolean isPasswordEmpty = hashedPassword.isEmpty();

        if (!isPasswordEmpty) {
            textViewRegisterOrUnlock.setText(getString(R.string.unlock_newpass_button_text));
            welcomeTextView.setText(getString(R.string.welcome_back_newpass_text));

            // Show "Forgot password?" only when a password already exists
            forgotPasswordTv.setVisibility(View.VISIBLE);
            forgotPasswordTv.setOnClickListener(v -> showForgotPasswordDialog());

            if (loginViewModel.isMasterPasswordLocked(encryptedSharedPreferences)) {
                applyMasterLockoutUi();
            } else if (SharedPreferencesHelper.isBiometricEnabled(this)) {
                biometricButton.setVisibility(View.VISIBLE);
                
                biometricButton.setImageResource(R.drawable.ic_fingerprint);
                
                biometricButton.setOnClickListener(v -> {
                    VibrationHelper.vibrate(binding.getRoot(), VibrationHelper.VibrationType.Weak);
                    loginViewModel.loginUserWithBiometricAuth(this, encryptedSharedPreferences);
                });
                
                // Automatically trigger it on start, but wait for Window Focus
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                     loginViewModel.loginUserWithBiometricAuth(this, encryptedSharedPreferences);
                });
            } else {
                biometricButton.setVisibility(View.GONE);
            }
        } else {
            biometricButton.setVisibility(View.GONE);
        }

        buttonPasswordVisibility.setOnClickListener(v -> {

            if (isPasswordVisible) {
                buttonPasswordVisibility.setImageDrawable(ContextCompat.getDrawable(LoginActivity.this, R.drawable.icon_visibility_on));
                passwordEntry.setTransformationMethod(PasswordTransformationMethod.getInstance());
            } else {
                buttonPasswordVisibility.setImageDrawable(ContextCompat.getDrawable(LoginActivity.this, R.drawable.icon_visibility_off));
                passwordEntry.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }

            isPasswordVisible = !isPasswordVisible;
        });


        buttonRegisterOrUnlockListener(buttonRegisterOrUnlock, isPasswordEmpty);
    }



    public void buttonRegisterOrUnlockListener(View view, Boolean isPasswordEmpty) {

        if (!isPasswordEmpty) {
            loginUser(view);

        } else {
            registerUser();
        }
    }

    private void loginUser(View view) {
        if (BuildConfig.DEBUG) Log.d("LOGIN_VM", "Already launched before");
        loginWithPassword(view);
    }

    private void registerUser() {
        if (BuildConfig.DEBUG) Log.d("LOGIN_VM", "First launch");

        buttonRegisterOrUnlock.setOnClickListener(v -> {
            String passwordInput = passwordEntry.getText().toString();
            try {
                loginViewModel.createUser(passwordInput, encryptedSharedPreferences);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException(e);
            }
            VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
        });
    }

    private void showLoading() {
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(0f);
        loadingOverlay.animate()
                .alpha(1f)
                .setDuration(250)
                .withEndAction(null)
                .start();
        
        // Disable interaction with the form
        buttonRegisterOrUnlock.setEnabled(false);
        passwordEntry.setEnabled(false);
        buttonPasswordVisibility.setEnabled(false);
    }

    private void hideLoading() {
        loadingOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    loadingOverlay.setVisibility(View.GONE);
                })
                .start();
        
        // Failsafe: ensure it goes away even if animations are disabled
        loadingOverlay.postDelayed(() -> {
            loadingOverlay.setVisibility(View.GONE);
            loadingOverlay.setAlpha(1f);
        }, 250);
        
        // Re-enable interaction
        buttonRegisterOrUnlock.setEnabled(true);
        passwordEntry.setEnabled(true);
        buttonPasswordVisibility.setEnabled(true);
    }

    private void hideUI(boolean bool) {
        if (bool) {
            buttonRegisterOrUnlock.setVisibility(View.GONE);
            passwordEntry.setVisibility(View.GONE);
            passwordBox.setVisibility(View.GONE);
            welcomeTextView.setVisibility(View.GONE);
            bgImage.setVisibility(View.GONE);
            buttonPasswordVisibility.setVisibility(View.GONE);
        } else {
            buttonRegisterOrUnlock.setVisibility(View.VISIBLE);
            passwordEntry.setVisibility(View.VISIBLE);
            passwordBox.setVisibility(View.VISIBLE);
            welcomeTextView.setVisibility(View.VISIBLE);
            bgImage.setVisibility(View.VISIBLE);
            buttonPasswordVisibility.setVisibility(View.VISIBLE);
        }
    }

    private void loginWithPassword(View view) {
        view.setOnTouchListener((v, event) -> {

            String passwordInput = passwordEntry.getText().toString();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    try {
                        loginViewModel.loginUserWithPassword(passwordInput, encryptedSharedPreferences);
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        throw new RuntimeException(e);
                    }
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
                    return true;
            }
            return false;
        });
    }


    private void initViews(ActivityLoginBinding binding) {
        passwordEntry = binding.loginTwPassword;
        welcomeTextView = binding.welcomeLoginTw;
        buttonRegisterOrUnlock = binding.registerOrUnlockButton;
        textViewRegisterOrUnlock = binding.registerOrUnlockTextView;
        passwordBox = binding.backgroundInputbox2;
        bgImage = binding.logoLogin;
        buttonPasswordVisibility = binding.passwordVisibilityButton;
        biometricButton = binding.biometricButton;
        loadingOverlay = binding.loadingOverlay;
        forgotPasswordTv = binding.forgotPasswordTv;
    }

    private void applyMasterLockoutUi() {
        passwordEntry.setText("");
        passwordEntry.setEnabled(false);
        buttonPasswordVisibility.setEnabled(false);
        biometricButton.setVisibility(View.GONE);
        textViewRegisterOrUnlock.setText(getString(R.string.recovery_key_button));
        welcomeTextView.setText(getString(R.string.master_password_locked));
        forgotPasswordTv.setVisibility(View.VISIBLE);
        forgotPasswordTv.setOnClickListener(v -> showForgotPasswordDialog());
        buttonRegisterOrUnlock.setOnTouchListener(null);
        buttonRegisterOrUnlock.setOnClickListener(v -> {
            VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
            showForgotPasswordDialog();
        });
    }

    private void navigateToMain() {
        loginViewModel.clearAuthFailureState(encryptedSharedPreferences);
        String databaseKey = loginViewModel.getDatabaseKeyLiveData().getValue();
        if (databaseKey == null || databaseKey.isEmpty()) {
            databaseKey = encryptedSharedPreferences.getString("password", "");
        }
        StringHelper.setSharedString(databaseKey);
        startActivity(new Intent(LoginActivity.this, MainViewActivity.class));
        finish();
    }

    /**
     * Shows the one-time recovery code dialog.
     * Called after first password creation and after a successful recovery-based password reset.
     */
    private void showRecoveryCodeDialog(String formattedCode) {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recovery_code, null);
        TextView tvCode = dialogView.findViewById(R.id.tv_recovery_code);
        Button btnSaved = dialogView.findViewById(R.id.btn_saved);
        ImageButton btnCopy = dialogView.findViewById(R.id.btn_copy_code);

        tvCode.setText(formattedCode);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        btnCopy.setOnClickListener(v -> {
            com.gero.newpass.utilities.ClipboardHelper.copyToClipboardWithTimeout(this, "RecoveryKey", formattedCode, 30_000);
            com.gero.newpass.utilities.ToastHelper.showToast(this, R.string.recovery_key_copied, Toast.LENGTH_SHORT);
        });

        btnSaved.setOnClickListener(v -> {
            dialog.dismiss();
            navigateToMain();
        });

        dialog.show();
    }

    /**
     * Shows the "Forgot Password?" dialog: enter recovery key + new password.
     */
    private void showForgotPasswordDialog() {
        if (isFinishing() || isDestroyed()) return;

        String storedHash = encryptedSharedPreferences.getString("recovery_code_hash", "");
        if (storedHash.isEmpty()) {
            // No recovery key set up yet — guide the user
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(getString(R.string.no_recovery_key_found))
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_enter_recovery_code, null);
        EditText inputKey = dialogView.findViewById(R.id.input_recovery_key);
        EditText inputNew = dialogView.findViewById(R.id.input_new_password);
        EditText inputConfirm = dialogView.findViewById(R.id.input_confirm_password);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm_recovery);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_recovery);
        Button btnWipe = dialogView.findViewById(R.id.btn_wipe_vault);
        LinearLayout recoveryLoadingRow = dialogView.findViewById(R.id.recovery_loading_row);
        final boolean[] recoveryRequestInFlight = {false};

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog_rounded);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        Runnable updateRecoveryLockoutUi = () -> {
            long remainingMs = loginViewModel.getRecoveryLockoutRemainingMs(encryptedSharedPreferences);
            boolean locked = remainingMs > 0;
            boolean enabled = !locked && !recoveryRequestInFlight[0];
            setRecoveryDialogEnabled(inputKey, inputNew, inputConfirm, btnConfirm, btnCancel, btnWipe, enabled);
            recoveryLoadingRow.setVisibility(recoveryRequestInFlight[0] ? View.VISIBLE : View.GONE);
            if (locked) {
                com.gero.newpass.utilities.ToastHelper.showToast(
                        this,
                        getString(R.string.recovery_locked_wait, loginViewModel.getFormattedRecoveryLockout(encryptedSharedPreferences)),
                        Toast.LENGTH_LONG
                );
            }
            btnWipe.setVisibility(loginViewModel.shouldShowManualWipe(encryptedSharedPreferences) ? View.VISIBLE : View.GONE);
        };

        btnWipe.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.wipe_vault_title)
                .setMessage(R.string.wipe_vault_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.wipe_vault_confirm, (confirmDialog, which) -> {
                    loginViewModel.wipeVault(this, encryptedSharedPreferences);
                    com.gero.newpass.utilities.ToastHelper.showToast(this, R.string.vault_wiped, Toast.LENGTH_SHORT);
                    dialog.dismiss();
                    recreate();
                })
                .show());

        updateRecoveryLockoutUi.run();

        btnConfirm.setOnClickListener(v -> {
            if (recoveryRequestInFlight[0]) {
                return;
            }

            if (loginViewModel.isRecoveryTemporarilyLocked(encryptedSharedPreferences)) {
                updateRecoveryLockoutUi.run();
                return;
            }

            String key = inputKey.getText().toString().trim();
            String newPwd = inputNew.getText().toString();
            String confirmPwd = inputConfirm.getText().toString();

            if (key.isEmpty() || newPwd.isEmpty()) {
                com.gero.newpass.utilities.ToastHelper.showToast(this, R.string.password_cannot_be_empty, Toast.LENGTH_SHORT);
                return;
            }
            if (!newPwd.equals(confirmPwd)) {
                com.gero.newpass.utilities.ToastHelper.showToast(this, R.string.passwords_do_not_match, Toast.LENGTH_SHORT);
                return;
            }
            if (newPwd.length() < 6) {
                com.gero.newpass.utilities.ToastHelper.showToast(this, R.string.password_must_be_at_least_4_characters_long, Toast.LENGTH_SHORT);
                return;
            }

            recoveryRequestInFlight[0] = true;
            dialog.setCancelable(false);
            setRecoveryDialogEnabled(inputKey, inputNew, inputConfirm, btnConfirm, btnCancel, btnWipe, false);
            recoveryLoadingRow.setVisibility(View.VISIBLE);

            loginViewModel.verifyAndResetWithRecoveryCode(this, key, newPwd, encryptedSharedPreferences, (success, newCode) -> {
                recoveryRequestInFlight[0] = false;
                dialog.setCancelable(true);
                if (!dialog.isShowing() || isFinishing() || isDestroyed()) {
                    return;
                }
                recoveryLoadingRow.setVisibility(View.GONE);
                if (success) {
                    dialog.dismiss();
                    com.gero.newpass.utilities.ToastHelper.showToast(this, R.string.recovery_success, Toast.LENGTH_SHORT);
                    // Show the brand-new recovery code that was generated
                    showRecoveryCodeDialog(newCode);
                } else {
                    long remainingMs = loginViewModel.getRecoveryLockoutRemainingMs(encryptedSharedPreferences);
                    if (remainingMs > 0) {
                        updateRecoveryLockoutUi.run();
                    } else {
                        setRecoveryDialogEnabled(inputKey, inputNew, inputConfirm, btnConfirm, btnCancel, btnWipe, true);
                        int attemptsLeft = Math.max(0, 6 - loginViewModel.getRecoveryFailedAttempts(encryptedSharedPreferences));
                        com.gero.newpass.utilities.ToastHelper.showToast(this, getString(R.string.recovery_attempts_left, attemptsLeft), Toast.LENGTH_SHORT);
                    }
                }
            });
        });

        dialog.show();
    }

    private void setRecoveryDialogEnabled(EditText inputKey, EditText inputNew, EditText inputConfirm,
                                          Button btnConfirm, Button btnCancel, Button btnWipe,
                                          boolean enabled) {
        inputKey.setEnabled(enabled);
        inputNew.setEnabled(enabled);
        inputConfirm.setEnabled(enabled);
        btnConfirm.setEnabled(enabled);
        btnCancel.setEnabled(enabled);
        btnWipe.setEnabled(enabled);
    }


}
