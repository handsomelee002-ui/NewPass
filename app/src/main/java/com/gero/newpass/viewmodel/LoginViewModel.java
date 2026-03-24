package com.gero.newpass.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginViewModel extends ViewModel {

    private final MutableLiveData<String> loginMessageLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loginSuccessLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final ResourceRepository resourceRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Brute-force protection
    private int failedAttempts = 0;
    private static final int MAX_ATTEMPTS_BEFORE_DELAY = 3;
    private static final long MAX_DELAY_MS = 30_000; // 30 seconds max


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


    public void createUser(String password, EncryptedSharedPreferences sharedPreferences) throws NoSuchAlgorithmException, InvalidKeySpecException {

        if (password.length() >= 6) {
            loadingLiveData.setValue(true);
            
            executor.execute(() -> {
                try {
                    String hashedPassword = HashUtils.hashPassword(password);
                    
                    mainHandler.post(() -> {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("password", hashedPassword);
                        editor.apply();
                        
                        loadingLiveData.setValue(false);
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

        String hashedPassword = sharedPreferences.getString("password", "");

        // Brute-force protection: enforce delay after too many failed attempts
        if (failedAttempts >= MAX_ATTEMPTS_BEFORE_DELAY) {
            long delayMs = Math.min((long) Math.pow(2, failedAttempts - MAX_ATTEMPTS_BEFORE_DELAY) * 1000, MAX_DELAY_MS);
            loadingLiveData.setValue(true);
            mainHandler.postDelayed(() -> {
                performLogin(password, hashedPassword);
            }, delayMs);
            return;
        }

        performLogin(password, hashedPassword);
    }

    private void performLogin(String password, String hashedPassword) {
        loadingLiveData.setValue(true);
        
        executor.execute(() -> {
            try {
                boolean verified = HashUtils.verifyPassword(password, hashedPassword);
                
                mainHandler.post(() -> {
                    loadingLiveData.setValue(false);
                    if (verified) {
                        failedAttempts = 0; // Reset on success
                        loginSuccessLiveData.setValue(true);
                        loginMessageLiveData.setValue(resourceRepository.getString(R.string.login_done));
                    } else {
                        failedAttempts++;
                        loginSuccessLiveData.setValue(false);
                        if (failedAttempts >= MAX_ATTEMPTS_BEFORE_DELAY) {
                            long nextDelaySeconds = Math.min((long) Math.pow(2, failedAttempts - MAX_ATTEMPTS_BEFORE_DELAY), MAX_DELAY_MS / 1000);
                            loginMessageLiveData.setValue(resourceRepository.getString(R.string.access_denied) + " (" + nextDelaySeconds + "s wait)");
                        } else {
                            loginMessageLiveData.setValue(resourceRepository.getString(R.string.access_denied));
                        }
                    }
                });
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                mainHandler.post(() -> {
                    loadingLiveData.setValue(false);
                    loginSuccessLiveData.setValue(false);
                    loginMessageLiveData.setValue("Verification error");
                });
            }
        });
    }

    public void loginUserWithBiometricAuth(Context context) {
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
                loginSuccessLiveData.postValue(true);
                loginMessageLiveData.postValue(resourceRepository.getString(R.string.login_done));
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                loginSuccessLiveData.postValue(false);
                loginMessageLiveData.postValue(resourceRepository.getString(R.string.access_denied));
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.login))
                .setSubtitle(context.getString(R.string.use_your_biometric_or_device_credentials))
                .setNegativeButtonText(context.getString(R.string.cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
