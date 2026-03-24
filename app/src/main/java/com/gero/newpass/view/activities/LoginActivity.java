package com.gero.newpass.view.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gero.newpass.BuildConfig;
import android.widget.FrameLayout;
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
    private ImageButton buttonRegisterOrUnlock, buttonPasswordVisibility;
    private ImageView passwordBox, bgImage;
    private TextView welcomeTextView, textViewRegisterOrUnlock;
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
             com.gero.newpass.utilities.ToastHelper.showToast(this, message, Toast.LENGTH_SHORT);
        });

        loginViewModel.getLoginSuccessLiveData().observe(this, success -> {
            String hashedPassword = encryptedSharedPreferences.getString("password", "");

            if (success) {
                Intent intent = new Intent(LoginActivity.this, MainViewActivity.class);
                StringHelper.setSharedString(hashedPassword);
                startActivity(intent);
                finish();
            } else {
                AnimationsUtility.errorAnimation(buttonRegisterOrUnlock, textViewRegisterOrUnlock);
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

            if (SharedPreferencesHelper.isBiometricEnabled(this)) {
                binding.biometricButton.setVisibility(View.VISIBLE);
                
                binding.biometricButton.setImageResource(R.drawable.ic_fingerprint);
                
                binding.biometricButton.setOnClickListener(v -> {
                    VibrationHelper.vibrate(binding.getRoot(), VibrationHelper.VibrationType.Weak);
                    loginViewModel.loginUserWithBiometricAuth(this);
                });
                
                // Automatically trigger it on start, but wait for Window Focus
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                     loginViewModel.loginUserWithBiometricAuth(this);
                });
            } else {
                binding.biometricButton.setVisibility(View.GONE);
            }
        } else {
            binding.biometricButton.setVisibility(View.GONE);
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
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(250);
        fadeIn.setFillAfter(true);
        loadingOverlay.startAnimation(fadeIn);
        
        // Disable interaction with the form
        buttonRegisterOrUnlock.setEnabled(false);
        passwordEntry.setEnabled(false);
        buttonPasswordVisibility.setEnabled(false);
    }

    private void hideLoading() {
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(200);
        fadeOut.setFillAfter(true);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                loadingOverlay.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        loadingOverlay.startAnimation(fadeOut);
        
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
        loadingOverlay = binding.loadingOverlay;
    }


}