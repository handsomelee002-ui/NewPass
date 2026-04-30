package com.gero.newpass.view.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.activity.OnBackPressedCallback;

import com.gero.newpass.SharedPreferences.SharedPreferencesHelper;
import com.gero.newpass.database.DatabaseServiceLocator;
import com.gero.newpass.databinding.ActivityMainViewBinding;

import com.gero.newpass.R;
import com.gero.newpass.utilities.SystemBarColorHelper;
import com.gero.newpass.view.fragments.MainViewFragment;
import com.gero.newpass.view.activities.LoginActivity;

public class MainViewActivity extends AppCompatActivity {

    private long backgroundTime = 0;
    private final android.os.Handler inactivityHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable inactivityRunnable = this::lockApp;

    public void startInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable);
        android.content.SharedPreferences encryptedSharedPreferences = com.gero.newpass.encryption.EncryptionHelper.getEncryptedSharedPreferences(this);
        int timeoutSeconds = encryptedSharedPreferences.getInt("AUTO_LOCK_TIMEOUT", 15);
        inactivityHandler.postDelayed(inactivityRunnable, timeoutSeconds * 1000L);
    }

    public void stopInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable);
    }

    private void lockApp() {
        com.gero.newpass.utilities.StringHelper.setSharedString(""); // Clear master password
        android.content.Intent intent = new android.content.Intent(this, LoginActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            startInactivityTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        ActivityMainViewBinding binding = ActivityMainViewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        DatabaseServiceLocator.init(getApplicationContext());

        SystemBarColorHelper.changeBarsColor(this, R.color.background_primary);

        // Initial fragment setup, showing the 'AddFragment' by default
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MainViewFragment())
                    .commit();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 1) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        backgroundTime = System.currentTimeMillis();
        stopInactivityTimer();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferencesHelper.toggleDarkLightModeUI(this);
        
        if (backgroundTime > 0) {
            long delta = System.currentTimeMillis() - backgroundTime;
            android.content.SharedPreferences encryptedSharedPreferences = com.gero.newpass.encryption.EncryptionHelper.getEncryptedSharedPreferences(this);
            int timeoutSeconds = encryptedSharedPreferences.getInt("AUTO_LOCK_TIMEOUT", 15);
            
            if (delta > (timeoutSeconds * 1000L)) {
                // Timeout exceeded! Lock the app securely.
                lockApp();
                return;
            }
        }
        backgroundTime = 0; // reset
        startInactivityTimer();
    }

    public void openFragment(Fragment fragment) {

        // Perform the fragment transaction and add it to the back stack
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left,
                        R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }




}
