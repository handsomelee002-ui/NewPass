package com.gero.newpass.view.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.gero.newpass.R;
import com.gero.newpass.databinding.FragmentMainViewBinding;

import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.view.activities.MainViewActivity;
import com.gero.newpass.view.adapters.CustomAdapter;
import com.gero.newpass.viewmodel.MainViewModel;
import com.gero.newpass.model.ListItem;
import com.gero.newpass.model.FolderData;
import com.gero.newpass.model.UserData;

import java.util.Objects;


public class MainViewFragment extends Fragment {

    private FragmentMainViewBinding binding;
    private TextView noData, count;
    private ImageView empty_imageview;
    private RecyclerView recyclerView;
    private ImageButton buttonSettings, buttonSearch, buttonCancel;
    private TextView buttonGenerate, buttonAdd, buttonAddFolder;
    private MainViewModel mainViewModel;
    private Integer currentFolderId = null; // null represents root
    private String currentFolderName = null;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentMainViewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        initViews();

        populateUI();

        // Handle back press to go to root level if we are inside a folder
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentFolderId != null) {
                    currentFolderId = null;
                    currentFolderName = null;
                    populateUI();
                } else {
                    setEnabled(false);
                    requireActivity().onBackPressed();
                }
            }
        });


        //Navigating to generate/ add password and settings fragments using the method inherited from the base activity
        Activity activity = this.getActivity();
        if (activity instanceof MainViewActivity) {


            buttonSettings.setOnClickListener(v -> {
                VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                ((MainViewActivity) activity).openFragment(new SettingsFragment());
            });


            buttonAddFolder.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
                        showAddFolderDialog();
                        return true;
                }
                return false;
            });

            buttonAdd.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
                        
                        AddPasswordFragment addFragment = new AddPasswordFragment();
                        if (currentFolderId != null) {
                            Bundle b = new Bundle();
                            b.putInt("defaultFolderId", currentFolderId);
                            addFragment.setArguments(b);
                        }
                        
                        ((MainViewActivity) activity).openFragment(addFragment);
                        return true;
                }
                return false;
            });


            buttonGenerate.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Weak);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        VibrationHelper.vibrate(v, VibrationHelper.VibrationType.Strong);
                        ((MainViewActivity) activity).openFragment(new GeneratePasswordFragment());
                        return true;
                }
                return false;
            });

            buttonSearch.setOnClickListener(v -> showInputDialog());

            buttonCancel.setOnClickListener(v -> populateUI());

        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Nullify the binding object to avoid memory leaks
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        getParentFragmentManager().setFragmentResultListener("requestKey", this, (requestKey, bundle) -> {
            String result = bundle.getString("resultKey");
            // Updating UI after update
            if (Objects.equals(result, "1")) {
                populateUI();
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void populateUI() {
        buttonCancel.setVisibility(View.GONE);
        mainViewModel.storeDataInArrays(currentFolderId);

        if (currentFolderId != null) {
            binding.textViewCreate.setText(currentFolderName);
            buttonAddFolder.setVisibility(View.GONE);
        } else {
            binding.textViewCreate.setText(R.string.main_saved_password);
            buttonAddFolder.setVisibility(View.VISIBLE);
        }

        mainViewModel.getDataList().observe(getViewLifecycleOwner(), dataList -> {
            CustomAdapter customAdapter = new CustomAdapter(this.getActivity(), this.getContext(), dataList, new CustomAdapter.OnFolderClickListener() {
                @Override
                public void onFolderClick(FolderData folder) {
                    currentFolderId = Integer.parseInt(folder.getId());
                    currentFolderName = folder.getName();
                    populateUI();
                }

                @Override
                public void onFolderLongClick(FolderData folder) {
                    showFolderContextMenu(folder);
                }
            });
            recyclerView.setAdapter(customAdapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

            ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    int fromPosition = viewHolder.getAdapterPosition();
                    int toPosition = target.getAdapterPosition();
                    customAdapter.moveItem(fromPosition, toPosition);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }

                @Override
                public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    super.clearView(recyclerView, viewHolder);
                    // Save new order after drag ends
                    com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                    for (int i = 0; i < dataList.size(); i++) {
                        ListItem item = dataList.get(i);
                        if (item.getType() == ListItem.TYPE_FOLDER) {
                            db.updateFolderSortOrder(((FolderData)item).getId(), i);
                        } else {
                            db.updateEntrySortOrder(((UserData)item).getId(), i);
                        }
                    }
                }
            };
            ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
            touchHelper.attachToRecyclerView(recyclerView);

            Log.i("235903425", "sus");

            count.setText("[" + customAdapter.getItemCount() + "]");

            if (customAdapter.getItemCount() == 0) {
                empty_imageview.setVisibility(View.VISIBLE);
                noData.setVisibility(View.VISIBLE);
            } else {
                empty_imageview.setVisibility(View.GONE);
                noData.setVisibility(View.GONE);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void showInputDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_search, null);
        builder.setView(dialogView);


        EditText input = dialogView.findViewById(R.id.input);

        builder.setTitle(R.string.search_password)
                .setPositiveButton(R.string.ok, (dialog, which) -> {

                    String searchTerm = input.getText().toString().toLowerCase().trim();

                    if (searchTerm.isEmpty()) {
                        buttonCancel.setVisibility(View.GONE);
                    } else {
                        buttonCancel.setVisibility(View.VISIBLE);
                    }

                    mainViewModel.storeSearchedDataInArrays(searchTerm);

                    mainViewModel.getSearchedDataList().observe(getViewLifecycleOwner(), searchedDataList -> {
                        CustomAdapter customAdapter = new CustomAdapter(this.getActivity(), this.getContext(), searchedDataList, new CustomAdapter.OnFolderClickListener() {
                            @Override
                            public void onFolderClick(FolderData folder) {
                                currentFolderId = Integer.parseInt(folder.getId());
                                currentFolderName = folder.getName();
                                populateUI();
                            }
            
                            @Override
                            public void onFolderLongClick(FolderData folder) {
                                showFolderContextMenu(folder);
                            }
                        });
                        recyclerView.setAdapter(customAdapter);
                        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

                        count.setText("[" + customAdapter.getItemCount() + "]");

                        if (customAdapter.getItemCount() == 0) {
                            empty_imageview.setVisibility(View.VISIBLE);
                            noData.setVisibility(View.VISIBLE);
                        } else {
                            empty_imageview.setVisibility(View.GONE);
                            noData.setVisibility(View.GONE);
                        }
                    });
                });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showAddFolderDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_folder, null);
        builder.setView(dialogView);

        EditText input = dialogView.findViewById(R.id.folder_name_input);

        builder.setTitle("Create Folder")
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                        db.addFolder(folderName);
                        populateUI();
                    }
                });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showFolderContextMenu(FolderData folder) {
        String[] options = {"Rename", "Duplicate", "Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(folder.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameFolderDialog(folder);
                    } else if (which == 1) {
                        com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                        db.duplicateFolder(folder.getId(), folder.getName());
                        populateUI();
                    } else if (which == 2) {
                        showDeleteFolderDialog(folder);
                    }
                });
        builder.show();
    }

    private void showRenameFolderDialog(FolderData folder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_folder, null);
        builder.setView(dialogView);

        EditText input = dialogView.findViewById(R.id.folder_name_input);
        input.setText(folder.getName());

        builder.setTitle("Rename Folder")
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                        db.updateFolderName(folder.getId(), folderName);
                        populateUI();
                    }
                });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteFolderDialog(FolderData folder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Delete Folder")
                .setMessage("Delete all passwords inside the folder?")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                    db.deleteFolder(folder.getId(), true);
                    populateUI();
                })
                .setNeutralButton("Move to Root", (dialog, which) -> {
                    com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                    db.deleteFolder(folder.getId(), false);
                    populateUI();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void initViews() {
        recyclerView = binding.recyclerView;
        buttonGenerate = binding.buttonGenerate;
        buttonAdd = binding.buttonAdd;
        buttonAddFolder = binding.buttonAddFolder;
        buttonSettings = binding.buttonSettings;
        count = binding.textViewCount;
        empty_imageview = binding.emptyImageview;
        noData = binding.noData;
        buttonSearch = binding.buttonSearch;
        buttonCancel = binding.buttonCancel;
    }

}