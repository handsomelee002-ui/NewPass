package com.gero.newpass.viewmodel;

import android.database.Cursor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.gero.newpass.model.ListItem;
import com.gero.newpass.model.UserData;
import com.gero.newpass.database.DatabaseHelper;
import com.gero.newpass.database.DatabaseServiceLocator;
import com.gero.newpass.utilities.PasswordStrengthHelper;

import java.util.ArrayList;

public class SecurityDashboardViewModel extends ViewModel {

    private final MutableLiveData<ArrayList<ListItem>> flaggedDataList;
    private final MutableLiveData<Integer> weakCount;
    private final MutableLiveData<Integer> oldCount;
    private final DatabaseHelper myDB;

    public SecurityDashboardViewModel() {
        flaggedDataList = new MutableLiveData<>();
        weakCount = new MutableLiveData<>(0);
        oldCount = new MutableLiveData<>(0);
        myDB = DatabaseServiceLocator.getDatabaseHelper();
    }

    public void analyzeVault() {
        ArrayList<ListItem> localList = new ArrayList<>();
        int wCount = 0;
        int oCount = 0;

        Cursor cursor = myDB.readAllPasswords();
        long sixMonthsAgo = System.currentTimeMillis() - (180L * 24L * 60L * 60L * 1000L); // Approx 6 months

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                String pass = cursor.getString(3);
                long lastUpdated = cursor.isNull(6) ? System.currentTimeMillis() : cursor.getLong(6);

                try {
                    String decryptedPass = com.gero.newpass.encryption.EncryptionHelper.decrypt(pass);
                    PasswordStrengthHelper.Strength strength = PasswordStrengthHelper.calculateStrength(decryptedPass);
                    
                    boolean isWeak = (strength == PasswordStrengthHelper.Strength.WEAK);
                    boolean isOld = (lastUpdated < sixMonthsAgo);

                    if (isWeak || isOld) {
                        if (isWeak) wCount++;
                        if (isOld) oCount++;

                        UserData userData = new UserData(
                                cursor.getString(0),
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getString(3), 
                                cursor.isNull(4) ? null : cursor.getInt(4),
                                cursor.getInt(5),
                                lastUpdated
                        );
                        localList.add(userData);
                    }
                } catch (Exception e) {
                    // Ignore decryption failures smoothly here
                }
            }
            cursor.close();
        }

        weakCount.postValue(wCount);
        oldCount.postValue(oCount);
        flaggedDataList.postValue(localList);
    }

    public LiveData<ArrayList<ListItem>> getFlaggedDataList() {
        return flaggedDataList;
    }
    public LiveData<Integer> getWeakCount() {
        return weakCount;
    }
    public LiveData<Integer> getOldCount() {
        return oldCount;
    }
}
