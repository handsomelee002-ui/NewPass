package com.gero.newpass.view.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.gero.newpass.R;
import com.gero.newpass.databinding.FragmentUpdatePasswordBinding;
import com.gero.newpass.encryption.EncryptionHelper;

import com.gero.newpass.repository.ResourceRepository;
import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.view.activities.MainViewActivity;
import com.gero.newpass.viewmodel.UpdateViewModel;
import com.gero.newpass.factory.ViewMoldelsFactory;
import com.gero.newpass.database.DatabaseServiceLocator;
import com.gero.newpass.model.FolderData;

import java.util.ArrayList;
import java.util.List;

public class UpdatePasswordFragment extends Fragment {

    private FragmentUpdatePasswordBinding binding;

    private EditText name_input, email_input, passwordInput;
    private Spinner folderSpinner;
    private String entry, name, email, password;
    private Integer folderId;
    private ImageButton copyButtonPassword, copyButtonEmail, backButton, buttonPasswordVisibility;
    private TextView updateButton, deleteButton, duplicateButton;
    private Boolean isPasswordVisible = false;
    private List<FolderData> folderDataList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentUpdatePasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getAndSetIntentData();
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        //updateViewModel = new ViewModelProvider(this).get(UpdateViewModel.class);

        ResourceRepository resourceRepository = new ResourceRepository(requireContext());
        ViewMoldelsFactory factory = new ViewMoldelsFactory(resourceRepository);
        UpdateViewModel updateViewModel = new ViewModelProvider(this, factory).get(UpdateViewModel.class);


        initViews(binding);
        setupFolderSpinner();

        Activity activity = this.getActivity();

        String decryptedPassword = EncryptionHelper.decrypt(password);

        name_input.setText(name);
        email_input.setText(email);
        passwordInput.setText(decryptedPassword);

        // Observe any feedback messages from the ViewModel
        updateViewModel.getMessageLiveData().observe(getViewLifecycleOwner(), message ->
                Toast.makeText(this.getContext(), message, Toast.LENGTH_SHORT).show());


        updateButton.setOnTouchListener((v, event) -> {

            name = name_input.getText().toString().trim();
            email = email_input.getText().toString().trim();
            password = passwordInput.getText().toString().trim();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    Integer selectedFolderId = null;
                    int selectedPosition = folderSpinner.getSelectedItemPosition();
                    if (selectedPosition > 0) { // 0 is "No Folder"
                        selectedFolderId = Integer.parseInt(folderDataList.get(selectedPosition - 1).getId());
                    }
                    updateViewModel.updateEntry(entry, name, email, password, selectedFolderId);
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
            }
            return false;
        });

        updateViewModel.getSuccessUpdateLiveData().observe(getViewLifecycleOwner(), success -> {
                    if (success) {
                        if (activity instanceof MainViewActivity) {
                            Bundle result = new Bundle();
                            //Result key for the main fragment to update the addition
                            result.putString("resultKey", "1");
                            getParentFragmentManager().setFragmentResult("requestKey", result);
                            ((MainViewActivity) activity).onBackPressed();
                        }
                    }
                }
        );


        deleteButton.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();

                    AlertDialog.Builder builder = new AlertDialog.Builder(this.requireContext());
                    builder.setTitle(getString(R.string.update_alertdialog_title) + name + " ?");
                    builder.setMessage(getString(R.string.update_alertdialog_are_you_sure_you_want_to_delete) + name + " ?");
                    builder.setPositiveButton(R.string.update_alertdialog_yes, (dialogInterface, i) -> {
                        updateViewModel.deleteEntry(entry);
                        if (activity instanceof MainViewActivity) {
                            Bundle result = new Bundle();
                            //Result key for the main fragment to update the deletion
                            result.putString("resultKey", "1");
                            getParentFragmentManager().setFragmentResult("requestKey", result);
                            ((MainViewActivity) activity).onBackPressed();
                        }
                    });
                    builder.setNegativeButton(R.string.update_alertdialog_no, (dialogInterface, i) -> {

                    });
                    builder.create().show();

                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
            }
            return false;
        });

        duplicateButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    
                    Integer selectedFolderId = null;
                    int selectedPosition = folderSpinner.getSelectedItemPosition();
                    if (selectedPosition > 0) { // 0 is "No Folder"
                        selectedFolderId = Integer.parseInt(folderDataList.get(selectedPosition - 1).getId());
                    }
                    
                    DatabaseServiceLocator.getDatabaseHelper().duplicateEntry(entry, selectedFolderId);
                    
                    Toast.makeText(this.getContext(), "Entry Duplicated", Toast.LENGTH_SHORT).show();
                    
                    if (activity instanceof MainViewActivity) {
                        Bundle result = new Bundle();
                        result.putString("resultKey", "1");
                        getParentFragmentManager().setFragmentResult("requestKey", result);
                        ((MainViewActivity) activity).onBackPressed();
                    }

                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
            }
            return false;
        });

        updateViewModel.getSuccessUpdateLiveData().observe(getViewLifecycleOwner(), success -> {
                    if (success) {
                        if (activity instanceof MainViewActivity) {
                            Bundle result = new Bundle();
                            //Result key for the main fragment to update the addition
                            result.putString("resultKey", "1");
                            getParentFragmentManager().setFragmentResult("requestKey", result);
                            ((MainViewActivity) activity).onBackPressed();
                        }
                    }
                }
        );

        copyButtonPassword.setOnClickListener(v -> {
            copyToClipboard(passwordInput.getText().toString().trim());
            VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
            Toast.makeText(this.getContext(), R.string.update_password_copied_to_the_clipboard, Toast.LENGTH_SHORT).show();
        });

        copyButtonEmail.setOnClickListener(v -> {
            copyToClipboard(email_input.getText().toString().trim());
            VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
            Toast.makeText(this.getContext(), R.string.update_email_copied_to_the_clipboard, Toast.LENGTH_SHORT).show();
        });

        buttonPasswordVisibility.setOnClickListener(v -> {

            if (isPasswordVisible) {
                buttonPasswordVisibility.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.icon_visibility_on));
                passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            } else {
                buttonPasswordVisibility.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.icon_visibility_off));
                passwordInput.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }

            isPasswordVisible = !isPasswordVisible;
        });

        backButton.setOnClickListener(v -> {
            if (activity instanceof MainViewActivity) {
                Bundle result = new Bundle();
                result.putString("resultKey", "1");
                getParentFragmentManager().setFragmentResult("requestKey", result);
                ((MainViewActivity) activity).onBackPressed();
            }
        });
    }

    private void getAndSetIntentData() {
        Bundle args = getArguments();
        if (args != null && args.containsKey("entry") && args.containsKey("name") &&
                args.containsKey("email") && args.containsKey("password")) {
            entry = args.getString("entry");
            name = args.getString("name");
            email = args.getString("email");
            password = args.getString("password");
            
            if (args.containsKey("folderId")) {
                int fId = args.getInt("folderId");
                folderId = (fId != -1) ? fId : null;
            } else {
                folderId = null;
            }
        } else {
            Toast.makeText(this.getContext(), R.string.update_no_data, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Method for copying text to the clipboard
     *
     * @param text text to copy to the clipboard
     */
    private void copyToClipboard(String text) {

        ClipboardManager clipboardManager = (ClipboardManager) this.requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(getString(R.string.text_copied_to_clipboard), text);
        clipboardManager.setPrimaryClip(clipData);
    }

    private void initViews(FragmentUpdatePasswordBinding binding) {
        name_input = binding.nameInput2;
        email_input = binding.emailInput2;
        passwordInput = binding.passwordInput2;
        folderSpinner = binding.folderSpinner2;
        updateButton = binding.updateButton;
        backButton = binding.backButton;
        deleteButton = binding.deleteButton;
        duplicateButton = binding.duplicateButton;
        copyButtonPassword = binding.copyButtonPassword;
        copyButtonEmail = binding.copyButtonEmail;
        buttonPasswordVisibility = binding.passwordVisibilityButton;
    }

    private void setupFolderSpinner() {
        android.database.Cursor cursor = DatabaseServiceLocator.getDatabaseHelper().readAllFolders();
        folderDataList = new ArrayList<>();
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                FolderData folderData = new FolderData(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2)
                );
                folderDataList.add(folderData);
            }
            cursor.close();
        }
        
        List<String> folderNames = new ArrayList<>();
        folderNames.add("No Folder (Root)");
        
        int defaultSelectionIndex = 0;

        for (int i = 0; i < folderDataList.size(); i++) {
            FolderData folder = folderDataList.get(i);
            folderNames.add(folder.getName());
            if (folderId != null && folder.getId().equals(String.valueOf(folderId))) {
                defaultSelectionIndex = i + 1;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_item, folderNames);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        folderSpinner.setAdapter(adapter);
        folderSpinner.setSelection(defaultSelectionIndex);
    }
}
