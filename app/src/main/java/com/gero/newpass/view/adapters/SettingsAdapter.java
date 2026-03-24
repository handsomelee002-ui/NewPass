package com.gero.newpass.view.adapters;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gero.newpass.R;
import com.gero.newpass.SharedPreferences.SharedPreferencesHelper;
import com.gero.newpass.model.SettingData;
import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.view.activities.MainViewActivity;


import java.util.ArrayList;

public class SettingsAdapter extends ArrayAdapter<SettingData> {

    private final Context mContext;
    private final int mResource;
    private Boolean isDarkModeSet;
    private final Activity mActivity;
    private final int DARK_THEME_SWITCH = 1;
    private final int BIOMETRIC_LOGIN_SWITCH = 2;

    // Constructor
    public SettingsAdapter(@NonNull Context context, int resource, @NonNull ArrayList<SettingData> objects, Activity activity) {
        super(context, resource, objects);
        this.mContext = context;
        this.mResource = resource;
        this.mActivity = activity;
    }

    // View holder class
    private static class ViewHolder {
        ImageView imageView;
        TextView txtName;
        ImageButton switchView;
        ImageView arrowImage;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(mResource, parent, false);
            holder = new ViewHolder();
            holder.imageView = convertView.findViewById(R.id.image);
            holder.txtName = convertView.findViewById(R.id.txtName);
            holder.switchView = convertView.findViewById(R.id.switch1);
            holder.arrowImage = convertView.findViewById(R.id.arrow);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        SettingData setting = getItem(position);
        if (setting != null) {
            holder.imageView.setImageResource(setting.getImage());
            holder.txtName.setText(setting.getName());



            if (setting.getSwitchPresence()) {
                holder.switchView.setVisibility(View.VISIBLE);

                //int imageResource = (SharedPreferencesHelper.isDarkModeSet(mContext)) ? R.drawable.btn_yes : R.drawable.btn_no;
                //holder.switchView.setImageDrawable(ContextCompat.getDrawable(mContext, imageResource));

                int switchID = setting.getSwitchID();
                int imageResource;

                if (switchID == DARK_THEME_SWITCH) {
                    imageResource = (SharedPreferencesHelper.isDarkModeSet(mContext)) ? R.drawable.btn_yes : R.drawable.btn_no;
                } else if (switchID == BIOMETRIC_LOGIN_SWITCH) {
                    imageResource = (SharedPreferencesHelper.isBiometricEnabled(mContext)) ? R.drawable.btn_yes : R.drawable.btn_no;
                } else {
                    imageResource = R.drawable.btn_yes; // Imposta un valore predefinito nel caso in cui l'ID dello switch non sia valido
                }

                holder.switchView.setImageDrawable(ContextCompat.getDrawable(mContext, imageResource));

                holder.switchView.setOnClickListener(v -> {
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);

                    switch (switchID) {

                        case DARK_THEME_SWITCH:
                            toggleDarkMode();
                            break;
                            
                        case BIOMETRIC_LOGIN_SWITCH:
                            toggleBiometric();
                            holder.switchView.setImageDrawable(ContextCompat.getDrawable(mContext, SharedPreferencesHelper.isBiometricEnabled(mContext) ? R.drawable.btn_yes : R.drawable.btn_no));
                            break;
                    }
                });
            } else {
                holder.switchView.setVisibility(View.GONE);
            }

            if (setting.getImagePresence()) {
                holder.arrowImage.setVisibility(View.VISIBLE);
            } else {
                holder.arrowImage.setVisibility(View.GONE);
            }
        }
        return convertView;
    }

    private void toggleDarkMode() {
        boolean isDarkModeSet = SharedPreferencesHelper.isDarkModeSet(mContext);
        if (isDarkModeSet) {
            SharedPreferencesHelper.setAndEditSharedPrefForLightMode(mContext);
        } else {
            SharedPreferencesHelper.setAndEditSharedPrefForDarkMode(mContext);
        }
        if (mActivity instanceof MainViewActivity) {
            SharedPreferencesHelper.updateNavigationBarColor(isDarkModeSet, mActivity);
        }
    }
    
    private void toggleBiometric() {
        boolean isEnabled = SharedPreferencesHelper.isBiometricEnabled(mContext);
        
        if (!isEnabled) {
            androidx.biometric.BiometricManager biometricManager = androidx.biometric.BiometricManager.from(mContext);
            int canAuthenticate = biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK);
            if (canAuthenticate != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                com.gero.newpass.utilities.ToastHelper.showToast(mContext, "Your device OS doesn't have compatible biometrics enrolled for third-party apps.", android.widget.Toast.LENGTH_LONG);
                return;
            } else {
                com.gero.newpass.utilities.ToastHelper.showToast(mContext, "Biometrics successfully enabled for NewPass!", android.widget.Toast.LENGTH_SHORT);
            }
        } else {
            com.gero.newpass.utilities.ToastHelper.showToast(mContext, "Biometrics disabled.", android.widget.Toast.LENGTH_SHORT);
        }
        
        SharedPreferencesHelper.setBiometricEnabled(mContext, !isEnabled);
    }
}
