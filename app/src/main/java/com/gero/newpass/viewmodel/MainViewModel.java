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

    private MutableLiveData<ArrayList<ListItem>> dataList, searchedDataList;
    private final DatabaseHelper myDB;

    public MainViewModel() {
        myDB = DatabaseServiceLocator.getDatabaseHelper();
    }

    public void storeDataInArrays(Integer folderId) {
        dataList = new MutableLiveData<>();
        ArrayList<ListItem> localList = new ArrayList<>();
        
        // If we are at the root, fetch folders first
        if (folderId == null) {
            Cursor folderCursor = myDB.readAllFolders();
            if (folderCursor != null && folderCursor.getCount() > 0) {
                while (folderCursor.moveToNext()) {
                    FolderData folderData = new FolderData(
                            folderCursor.getString(0),
                            folderCursor.getString(1),
                            folderCursor.getInt(2)
                    );
                    localList.add(folderData);
                }
                folderCursor.close();
            }
        }

        // Fetch passwords for the current folder (or root)
        Cursor cursor = myDB.readEntriesByFolder(folderId);

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                UserData userData = new UserData(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.isNull(4) ? null : cursor.getInt(4),
                        cursor.getInt(5)
                );
                localList.add(userData);
            }
            cursor.close();
        }
        
        java.util.Collections.sort(localList, java.util.Comparator.comparingInt(ListItem::getSortOrder));
        dataList.postValue(localList);
    }

    public void storeSearchedDataInArrays(String searchedData) {
        searchedDataList = new MutableLiveData<>();
        ArrayList<ListItem> localList = new ArrayList<>();
        Cursor cursor = myDB.searchItem(searchedData);

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                UserData userData = new UserData(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.isNull(4) ? null : cursor.getInt(4),
                        cursor.getInt(5)
                );

                localList.add(userData);
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