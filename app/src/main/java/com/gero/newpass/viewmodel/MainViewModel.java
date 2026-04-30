package com.gero.newpass.viewmodel;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.gero.newpass.model.UserData;
import com.gero.newpass.model.FolderData;
import com.gero.newpass.model.ListItem;
import com.gero.newpass.database.DatabaseHelper;
import com.gero.newpass.database.DatabaseServiceLocator;

import java.util.ArrayList;

public class MainViewModel extends ViewModel {

    private final MutableLiveData<ArrayList<ListItem>> dataList = new MutableLiveData<>();
    private final MutableLiveData<ArrayList<ListItem>> searchedDataList = new MutableLiveData<>();

    // Always look up the current singleton so a post-rekey DatabaseHelper is used.
    private DatabaseHelper getDB() {
        return DatabaseServiceLocator.getDatabaseHelper();
    }

    public void storeDataInArrays(Integer folderId) {
        DatabaseHelper myDB = getDB();
        ArrayList<ListItem> localList = new ArrayList<>();
        
        // Always fetch sub-folders for the current folder (root or any sub-folder)
        Cursor folderCursor = myDB.readFoldersByParent(folderId);
        if (folderCursor != null) {
            if (folderCursor.getCount() > 0) {
                while (folderCursor.moveToNext()) {
                    Integer parentFolderId = folderCursor.isNull(2) ? null : folderCursor.getInt(2);
                    FolderData folderData = new FolderData(
                            folderCursor.getString(0),
                            folderCursor.getString(1),
                            parentFolderId,
                            folderCursor.getInt(3)
                    );
                    localList.add(folderData);
                }
            }
            folderCursor.close();
        }

        // Fetch passwords for the current folder (or root)
        Cursor cursor = myDB.readEntriesByFolder(folderId);

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    UserData userData = new UserData(
                            cursor.getString(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.isNull(4) ? null : cursor.getString(4),
                            cursor.isNull(5) ? null : cursor.getInt(5),
                            cursor.getInt(6),
                            cursor.isNull(7) ? System.currentTimeMillis() : cursor.getLong(7)
                    );
                    localList.add(userData);
                }
            }
            cursor.close();
        }
        
        java.util.Collections.sort(localList, java.util.Comparator.comparingInt(ListItem::getSortOrder));
        dataList.postValue(localList);
    }

    public void storeSearchedDataInArrays(String searchedData) {
        DatabaseHelper myDB = getDB();
        ArrayList<ListItem> localList = new ArrayList<>();
        Cursor cursor = myDB.searchItem(searchedData);

        if (cursor != null) {
            if (cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    UserData userData = new UserData(
                            cursor.getString(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.isNull(4) ? null : cursor.getString(4),
                            cursor.isNull(5) ? null : cursor.getInt(5),
                            cursor.getInt(6),
                            cursor.isNull(7) ? System.currentTimeMillis() : cursor.getLong(7)
                    );

                    localList.add(userData);
                }
            }
            cursor.close();
        }
        
        java.util.Collections.sort(localList, java.util.Comparator.comparingInt(ListItem::getSortOrder));
        searchedDataList.postValue(localList);
    }

    public LiveData<ArrayList<ListItem>> getSearchedDataList() {
        return searchedDataList;
    }

    public LiveData<ArrayList<ListItem>> getDataList() {
        return dataList;
    }
}