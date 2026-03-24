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


import android.os.Handler;
import android.os.Looper;
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
import java.util.Stack;


public class MainViewFragment extends Fragment {

    private FragmentMainViewBinding binding;
    private TextView noData, count;
    private ImageView empty_imageview;
    private RecyclerView recyclerView;
    private ImageButton buttonSettings, buttonSearch, buttonCancel;
    private TextView buttonAdd, buttonAddFolder;
    private MainViewModel mainViewModel;
    private Integer currentFolderId = null; // null represents root
    private String currentFolderName = null;
    
    // Navigation stack for nested folder traversal
    private final Stack<Integer> folderIdStack = new Stack<>();
    private final Stack<String> folderNameStack = new Stack<>();


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

        // Handle back press to navigate up through folder hierarchy
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentFolderId != null) {
                    // Navigate to parent folder
                    if (!folderIdStack.isEmpty()) {
                        currentFolderId = folderIdStack.pop();
                        currentFolderName = folderNameStack.pop();
                    } else {
                        currentFolderId = null;
                        currentFolderName = null;
                    }
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
        } else {
            binding.textViewCreate.setText(R.string.main_saved_password);
        }
        
        // Always show "Add Folder" button so sub-folders can be created at any level
        buttonAddFolder.setVisibility(View.VISIBLE);

        mainViewModel.getDataList().observe(getViewLifecycleOwner(), dataList -> {
            ItemTouchHelper[] touchHelper = new ItemTouchHelper[1];

            CustomAdapter customAdapter = new CustomAdapter(this.getActivity(), this.getContext(), dataList, new CustomAdapter.OnItemInteractionListener() {
                @Override
                public void onFolderClick(FolderData folder) {
                    // Push current folder onto the stack before navigating into child
                    folderIdStack.push(currentFolderId);
                    folderNameStack.push(currentFolderName);
                    
                    currentFolderId = Integer.parseInt(folder.getId());
                    currentFolderName = folder.getName();
                    populateUI();
                }

                @Override
                public void onFolderLongClick(FolderData folder) {
                    showFolderContextMenu(folder);
                }
                
                @Override
                public void onPasswordLongClick(UserData userData) {
                    showPasswordContextMenu(userData);
                }

                @Override
                public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
                    if (touchHelper[0] != null) {
                        touchHelper[0].startDrag(viewHolder);
                    }
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
            ItemTouchHelper touchHelperInstance = new ItemTouchHelper(callback);
            touchHelperInstance.attachToRecyclerView(recyclerView);
            touchHelper[0] = touchHelperInstance;



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

                    String searchTerm = input.getText().toString().toLowerCase(java.util.Locale.ROOT).trim();

                    if (searchTerm.isEmpty()) {
                        buttonCancel.setVisibility(View.GONE);
                    } else {
                        buttonCancel.setVisibility(View.VISIBLE);
                    }

                    mainViewModel.storeSearchedDataInArrays(searchTerm);

                    mainViewModel.getSearchedDataList().observe(getViewLifecycleOwner(), searchedDataList -> {
                        ItemTouchHelper[] touchHelper = new ItemTouchHelper[1];
                        
                        CustomAdapter customAdapter = new CustomAdapter(this.getActivity(), this.getContext(), searchedDataList, new CustomAdapter.OnItemInteractionListener() {
                            @Override
                            public void onFolderClick(FolderData folder) {
                                folderIdStack.push(currentFolderId);
                                folderNameStack.push(currentFolderName);
                                
                                currentFolderId = Integer.parseInt(folder.getId());
                                currentFolderName = folder.getName();
                                populateUI();
                            }
            
                            @Override
                            public void onFolderLongClick(FolderData folder) {
                                showFolderContextMenu(folder);
                            }
                            
                            @Override
                            public void onPasswordLongClick(UserData userData) {
                                showPasswordContextMenu(userData);
                            }

                            @Override
                            public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
                                if (touchHelper[0] != null) {
                                    touchHelper[0].startDrag(viewHolder);
                                }
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
                            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
            
                            @Override
                            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                                super.clearView(recyclerView, viewHolder);
                                com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                                for (int i = 0; i < searchedDataList.size(); i++) {
                                    ListItem item = searchedDataList.get(i);
                                    if (item.getType() == ListItem.TYPE_FOLDER) {
                                        db.updateFolderSortOrder(((FolderData)item).getId(), i);
                                    } else {
                                        db.updateEntrySortOrder(((UserData)item).getId(), i);
                                    }
                                }
                            }
                        };
                        ItemTouchHelper touchHelperInstance = new ItemTouchHelper(callback);
                        touchHelperInstance.attachToRecyclerView(recyclerView);
                        touchHelper[0] = touchHelperInstance;

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

        String title = currentFolderId != null ? "Create Sub-Folder" : "Create Folder";
        builder.setTitle(title)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                        db.addFolder(folderName, currentFolderId);
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
                .setMessage("Delete all passwords and sub-folders inside this folder?")
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

    private void showPasswordContextMenu(UserData password) {
        String[] options = {"Copy to Clipboard", "Move to Folder", "Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(password.getName())
               .setItems(options, (dialog, which) -> {
                   if (which == 0) {
                         try {
                             android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireActivity().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                             android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Password", com.gero.newpass.encryption.EncryptionHelper.decrypt(password.getPassword()));
                             // Mark as sensitive on API 33+
                             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                 clip.getDescription().setExtras(new android.os.PersistableBundle());
                                 clip.getDescription().getExtras().putBoolean("android.content.extra.IS_SENSITIVE", true);
                             }
                             clipboard.setPrimaryClip(clip);
                             // Auto-clear clipboard after 30 seconds
                             new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                 try {
                                     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                         clipboard.clearPrimaryClip();
                                     } else {
                                         clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""));
                                     }
                                 } catch (Exception ignored) {}
                             }, 30_000);
                             com.gero.newpass.utilities.ToastHelper.showToast(requireContext(), "Password Copied (auto-clears in 30s)", android.widget.Toast.LENGTH_SHORT);
                         } catch (Exception ignored) {}
                   } else if (which == 1) {
                        showMoveToFolderDialog(password);
                   } else if (which == 2) {
                        com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
                        db.deleteOneRow(password.getId());
                        populateUI();
                   }
               });
        builder.show();
    }

    private void showMoveToFolderDialog(UserData password) {
        com.gero.newpass.database.DatabaseHelper db = com.gero.newpass.database.DatabaseServiceLocator.getDatabaseHelper();
        
        // Build folder tree with indentation for nested display
        java.util.List<String[]> folderTree = new java.util.ArrayList<>();
        db.buildFolderTree(folderTree, null, 0);
        
        java.util.List<String> folderNames = new java.util.ArrayList<>();
        java.util.List<Integer> folderIds = new java.util.ArrayList<>();
        
        // Root option
        folderNames.add("Root (No Folder)");
        folderIds.add(-1);
        
        for (String[] entry : folderTree) {
            folderNames.add(entry[0]); // display name with indentation
            folderIds.add(Integer.parseInt(entry[1]));
        }

        String[] options = folderNames.toArray(new String[0]);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Move to Folder")
               .setItems(options, (dialog, which) -> {
                   Integer targetFolderId = folderIds.get(which);
                   if (targetFolderId == -1) targetFolderId = null;
                   
                   db.updateData(password.getId(), password.getName(), password.getEmail(), password.getPassword(), targetFolderId);
                   populateUI();
               })
               .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void initViews() {
        recyclerView = binding.recyclerView;
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