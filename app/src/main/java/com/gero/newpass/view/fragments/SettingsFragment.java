package com.gero.newpass.view.fragments;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.gero.newpass.R;
import com.gero.newpass.databinding.FragmentSettingsBinding;
import com.gero.newpass.encryption.EncryptionHelper;
import com.gero.newpass.model.SettingData;
import com.gero.newpass.utilities.DialogHelper;
import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.view.activities.MainViewActivity;
import com.gero.newpass.view.adapters.SettingsAdapter;

import java.util.ArrayList;

public class SettingsFragment extends Fragment {
    private static final int REQUEST_CODE_IMPORT_DOCUMENT = 2;
    private ImageButton buttonBack;
    private FragmentSettingsBinding binding;
    private ListView listView;
    private String url;
    private Intent intent;
    private EncryptedSharedPreferences encryptedSharedPreferences;
    static final int DARK_THEME = 0;
    static final int BIOMETRIC_LOGIN = 1;
    static final int GENERATE_PASSWORD = 2;
    static final int CHANGE_PASSWORD = 3;
    static final int EXPORT = 4;
    static final int IMPORT = 5;
    static final int APP_VERSION = 6;
    private ActivityResultLauncher<Intent> importDocumentLauncher;
    private ActivityResultLauncher<Intent> exportDocumentLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        importDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri fileURL = result.getData().getData();
                        DialogHelper.showImportingDialog(requireContext(), fileURL);
                    }
                }
        );
        exportDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri targetUri = result.getData().getData();
                        DialogHelper.showExportingDialog(requireContext(), targetUri);
                    }
                }
        );
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(binding);
        Activity activity = this.getActivity();

        ArrayList<SettingData> arrayList = new ArrayList<>();
        encryptedSharedPreferences = EncryptionHelper.getEncryptedSharedPreferences(requireContext());

        buttonBack.setOnClickListener(v -> {
            if (activity instanceof MainViewActivity) {
                ((MainViewActivity) activity).onBackPressed();
            }
        });

        createSettingsList(arrayList);

        SettingsAdapter settingsAdapter = new SettingsAdapter(requireContext(), R.layout.list_row, arrayList, getActivity());

        listView.setAdapter(settingsAdapter);

        listView.setOnItemClickListener((parent, view1, position, id) -> {

            switch (position) {
                case GENERATE_PASSWORD:
                    VibrationHelper.vibrate(binding.getRoot(), VibrationHelper.VibrationType.Weak);
                    if (getActivity() instanceof MainViewActivity) {
                        ((MainViewActivity) getActivity()).openFragment(new GeneratePasswordFragment());
                    }
                    break;

                case CHANGE_PASSWORD:
                    VibrationHelper.vibrate(binding.getRoot(), VibrationHelper.VibrationType.Weak);
                    DialogHelper.showChangePasswordDialog(requireContext(), encryptedSharedPreferences);
                    break;

                case EXPORT:
                    VibrationHelper.vibrate(binding.getRoot(), VibrationHelper.VibrationType.Weak);
                    Intent intentExport = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intentExport.addCategory(Intent.CATEGORY_OPENABLE);
                    intentExport.setType("application/json");
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.US);
                    String timestamp = sdf.format(new java.util.Date());
                    intentExport.putExtra(Intent.EXTRA_TITLE, "Encrypted_NewPass_DB_" + timestamp + ".json");
                    exportDocumentLauncher.launch(intentExport);
                    break;

                case IMPORT:
                    VibrationHelper.vibrate(binding.getRoot(), VibrationHelper.VibrationType.Weak);

                    Intent intentImport = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intentImport.addCategory(Intent.CATEGORY_OPENABLE);
                    intentImport.setType("*/*");
                    importDocumentLauncher.launch(intentImport);
                    break;

                case APP_VERSION:
                    com.gero.newpass.utilities.ToastHelper.showToast(requireContext(), "\uD83D\uDE80⚡", Toast.LENGTH_SHORT);
                    break;
            }
        });
    }


    private void createSettingsList(ArrayList<SettingData> arrayList) {
        arrayList.add(new SettingData(DARK_THEME, R.drawable.settings_icon_dark_theme, getString(R.string.settings_dark_theme), false, true, 1));
        arrayList.add(new SettingData(BIOMETRIC_LOGIN, R.drawable.ic_fingerprint, "Enable Biometric Login", false, true, 2));
        arrayList.add(new SettingData(GENERATE_PASSWORD, R.drawable.btn_regenerate, "Random Password Generator"));
        arrayList.add(new SettingData(CHANGE_PASSWORD, R.drawable.settings_icon_lock, getString(R.string.settings_change_password)));
        arrayList.add(new SettingData(EXPORT, R.drawable.icon_export, getString(R.string.settings_export_db)));
        arrayList.add(new SettingData(IMPORT, R.drawable.icon_import, getString(R.string.settings_import_db)));
        arrayList.add(new SettingData(APP_VERSION, R.drawable.settings_icon_version, getString(R.string.app_version) + getAppVersion()));
    }

    private String getAppVersion() {
        String versionName = "";

        try {
            PackageManager packageManager = requireActivity().getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(requireActivity().getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("AppVersion", "Error getting app version", e);
        }
        return versionName;
    }

    private void initViews(FragmentSettingsBinding binding) {
        buttonBack = binding.backButton;
        listView = binding.listView;
    }
}
