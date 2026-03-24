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



public class MainViewActivity extends AppCompatActivity {

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
    public void onResume() {
        super.onResume();
        SharedPreferencesHelper.toggleDarkLightModeUI(this);
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
