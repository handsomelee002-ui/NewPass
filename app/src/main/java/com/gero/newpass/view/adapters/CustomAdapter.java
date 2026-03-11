package com.gero.newpass.view.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MotionEvent;
import androidx.cardview.widget.CardView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

import com.gero.newpass.R;
import com.gero.newpass.model.ListItem;
import com.gero.newpass.model.UserData;
import com.gero.newpass.model.FolderData;
import com.gero.newpass.utilities.VibrationHelper;
import com.gero.newpass.view.fragments.UpdatePasswordFragment;

import java.util.List;

public class CustomAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private final List<ListItem> dataList;
    private final Activity activity;
    private OnItemInteractionListener interactionListener;

    public interface OnItemInteractionListener {
        void onFolderClick(FolderData folderData);
        void onFolderLongClick(FolderData folderData);
        void onPasswordLongClick(UserData userData);
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public CustomAdapter(Activity activity, Context context, List<ListItem> dataList, OnItemInteractionListener interactionListener) {
        this.activity = activity;
        this.context = context;
        this.dataList = dataList;
        this.interactionListener = interactionListener;
    }

    @Override
    public int getItemViewType(int position) {
        return dataList.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == ListItem.TYPE_FOLDER) {
            View view = inflater.inflate(R.layout.folder_row, parent, false);
            return new FolderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.my_row, parent, false);
            return new PasswordViewHolder(view);
        }
    }

    /**
     * Populate every row of the recyclerView in the main activity
     * @param holder The ViewHolder which should be updated to represent the contents of the
     *        item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, @SuppressLint("RecyclerView") final int position) {

        ListItem item = dataList.get(position);

        if (holder.getItemViewType() == ListItem.TYPE_FOLDER) {
            FolderData folderData = (FolderData) item;
            FolderViewHolder folderHolder = (FolderViewHolder) holder;
            folderHolder.folder_name_txt.setText(folderData.getName());

            folderHolder.mainLayoutFolder.setOnClickListener(view -> {
                VibrationHelper.vibrate(view, VibrationHelper.VibrationType.Weak);
                if (interactionListener != null) {
                    interactionListener.onFolderClick(folderData);
                }
            });

            folderHolder.mainLayoutFolder.setOnLongClickListener(view -> {
                VibrationHelper.vibrate(view, VibrationHelper.VibrationType.Strong);
                if (interactionListener != null) {
                    interactionListener.onFolderLongClick(folderData);
                }
                return true;
            });
            
            folderHolder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && interactionListener != null) {
                    interactionListener.onStartDrag(folderHolder);
                }
                return false;
            });

        } else if (holder.getItemViewType() == ListItem.TYPE_PASSWORD) {
            UserData userData = (UserData) item;
            PasswordViewHolder passwordHolder = (PasswordViewHolder) holder;

            String name = userData.getName();
            String email = userData.getEmail();

            String tw;
            if (name != null && name.length() > 2) {
                tw = name.substring(0, 2);
            } else {
                tw = name;
            }

            passwordHolder.row_tw_txt.setText(tw);
            passwordHolder.row_name_txt.setText(name);
            passwordHolder.row_email_txt.setText(email);

            passwordHolder.mainLayout.setOnClickListener(view -> {
                VibrationHelper.vibrate(view, VibrationHelper.VibrationType.Weak);

                UpdatePasswordFragment updatePasswordFragment = new UpdatePasswordFragment();
                Bundle args = new Bundle();
                args.putString("entry", userData.getId());
                args.putString("name", userData.getName());
                args.putString("email", userData.getEmail());
                args.putString("password", userData.getPassword());
                if (userData.getFolderId() != null) {
                    args.putInt("folderId", userData.getFolderId());
                } else {
                    args.putInt("folderId", -1); // Use -1 to denote root since arguments can't easily be null int
                }
                updatePasswordFragment.setArguments(args);

                FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.setCustomAnimations(R.anim.enter_right_to_left, R.anim.exit_right_to_left, R.anim.enter_left_to_right, R.anim.exit_left_to_right)
                        .replace(R.id.fragment_container, updatePasswordFragment)
                        .addToBackStack(null)
                        .commit();
            });
            
            passwordHolder.mainLayout.setOnLongClickListener(view -> {
                VibrationHelper.vibrate(view, VibrationHelper.VibrationType.Strong);
                if (interactionListener != null) {
                    interactionListener.onPasswordLongClick(userData);
                }
                return true;
            });
            
            passwordHolder.dragHandle.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && interactionListener != null) {
                    interactionListener.onStartDrag(passwordHolder);
                }
                return false;
            });
        }
    }


    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public void moveItem(int fromPosition, int toPosition) {
        ListItem item = dataList.remove(fromPosition);
        dataList.add(toPosition, item);
        notifyItemMoved(fromPosition, toPosition);
    }

    public static class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView folder_name_txt;
        CardView mainLayoutFolder;
        ImageView dragHandle;

        public FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            folder_name_txt = itemView.findViewById(R.id.folder_name_txt);
            mainLayoutFolder = itemView.findViewById(R.id.mainLayoutFolder);
            dragHandle = itemView.findViewById(R.id.folder_drag_handle);
        }
    }

    public static class PasswordViewHolder extends RecyclerView.ViewHolder {

        TextView row_name_txt, row_email_txt, row_tw_txt;
        CardView mainLayout;
        ImageView dragHandle;

        public PasswordViewHolder(@NonNull View itemView) {
            super(itemView);

            row_name_txt = itemView.findViewById(R.id.row_name_txt);
            row_tw_txt = itemView.findViewById(R.id.row_tw_txt);
            row_email_txt = itemView.findViewById(R.id.row_email_txt);
            mainLayout = itemView.findViewById(R.id.mainLayout);
            dragHandle = itemView.findViewById(R.id.password_drag_handle);
        }
    }
}
