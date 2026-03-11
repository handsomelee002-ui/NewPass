package com.gero.newpass.view.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.gero.newpass.R;
import com.gero.newpass.databinding.FragmentAddPasswordBinding;
import com.gero.newpass.factory.ViewMoldelsFactory;
import com.gero.newpass.repository.ResourceRepository;
import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.view.activities.MainViewActivity;
import com.gero.newpass.viewmodel.AddViewModel;
import com.gero.newpass.database.DatabaseServiceLocator;
import com.gero.newpass.model.FolderData;

import java.util.ArrayList;
import java.util.List;

public class AddPasswordFragment extends Fragment {

    private EditText nameInput, emailInput, passwordInput;
    private Spinner folderSpinner;
    private ImageButton buttonBack, buttonPasswordVisibility;
    private TextView buttonAdd;
    private FragmentAddPasswordBinding binding;
    private Boolean isPasswordVisible = false;
    private List<FolderData> folderDataList;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentAddPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        ResourceRepository resourceRepository = new ResourceRepository(requireContext());
        ViewMoldelsFactory factory = new ViewMoldelsFactory(resourceRepository);
        AddViewModel addViewModel = new ViewModelProvider(this, factory).get(AddViewModel.class);

        initViews(binding);
        setupFolderSpinner();

        Activity activity = this.getActivity();

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

        buttonAdd.setOnTouchListener((v, event) -> {

            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    Integer folderId = null;
                    int selectedPosition = folderSpinner.getSelectedItemPosition();
                    if (selectedPosition > 0) { // 0 is "No Folder"
                        folderId = Integer.parseInt(folderDataList.get(selectedPosition - 1).getId());
                    }
                    addViewModel.addEntry(requireContext(), name, email, password, folderId);
                    VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
                    return true;
            }
            return false;
        });

        addViewModel.getSuccessLiveData().observe(getViewLifecycleOwner(), success -> {
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

        buttonBack.setOnClickListener(v -> {
            if (activity instanceof MainViewActivity) {
                ((MainViewActivity) activity).onBackPressed();
            }
        });


        // Observe any feedback messages from the ViewModel
        addViewModel.getMessageLiveData().observe(getViewLifecycleOwner(), message ->
                Toast.makeText(this.getContext(), message, Toast.LENGTH_SHORT).show());
    }

    private void initViews(FragmentAddPasswordBinding binding) {
        nameInput = binding.nameInput;
        emailInput = binding.emailInput;
        passwordInput = binding.passwordInput;
        folderSpinner = binding.folderSpinner;
        buttonAdd = binding.addButton;
        buttonBack = binding.backButton;
        buttonPasswordVisibility = binding.passwordVisibilityButton;
    }

    private void setupFolderSpinner() {
        Cursor cursor = DatabaseServiceLocator.getDatabaseHelper().readAllFolders();
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
        Integer defaultFolderId = null;
        if (getArguments() != null) {
            defaultFolderId = getArguments().getInt("defaultFolderId", -1);
            if (defaultFolderId == -1) defaultFolderId = null;
        }

        for (int i = 0; i < folderDataList.size(); i++) {
            FolderData folder = folderDataList.get(i);
            folderNames.add(folder.getName());
            if (defaultFolderId != null && folder.getId().equals(String.valueOf(defaultFolderId))) {
                defaultSelectionIndex = i + 1;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_selected_item, folderNames);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        folderSpinner.setAdapter(adapter);
        folderSpinner.setSelection(defaultSelectionIndex);
    }
}