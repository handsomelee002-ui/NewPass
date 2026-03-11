package com.gero.newpass.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.gero.newpass.R;
import com.gero.newpass.encryption.EncryptionHelper;
import com.gero.newpass.utilities.StringHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "Password.db";
    private static final int DATABASE_VERSION = 2; // Incremented for folders feature
    private static final String TABLE_NAME = "my_password_record";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "record_name";
    private static final String COLUMN_EMAIL = "record_email";
    private static final String COLUMN_PASSWORD = "record_password";
    private static final String COLUMN_FOLDER_ID = "folder_id"; // Nullable foreign key to folders table
    private static final String COLUMN_SORT_ORDER = "sort_order"; // For manual re-ordering

    private static final String TABLE_FOLDERS = "folders";
    private static final String COLUMN_FOLDER_NAME = "folder_name";
    
    private static final String KEY_ENCRYPTION = StringHelper.getSharedString();

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        assert context != null;
        SQLiteDatabase.loadLibs(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query =
                "CREATE TABLE " + TABLE_NAME +
                        " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_NAME + " TEXT, " +
                        COLUMN_EMAIL + " TEXT, " +
                        COLUMN_PASSWORD + " TEXT, " +
                        COLUMN_FOLDER_ID + " INTEGER, " +
                        COLUMN_SORT_ORDER + " INTEGER);";

        String queryFolders =
                "CREATE TABLE " + TABLE_FOLDERS +
                        " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_FOLDER_NAME + " TEXT, " +
                        COLUMN_SORT_ORDER + " INTEGER);";

        db.execSQL(query);
        db.execSQL(queryFolders);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_FOLDER_ID + " INTEGER;");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_SORT_ORDER + " INTEGER;");
            
            String queryFolders =
                "CREATE TABLE " + TABLE_FOLDERS +
                        " (" + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_FOLDER_NAME + " TEXT, " +
                        COLUMN_SORT_ORDER + " INTEGER);";
            db.execSQL(queryFolders);
        }
    }



    /**
     * Encrypts the password and adds a new entry with the given name, email, and (encrypted) password to the database.
     *
     * @param context  The context to get the database of the application
     * @param name     The name of the entry.
     * @param email    The email of the entry.
     * @param password The password of the entry (it will be encrypted before being inserted into the database)
     */
    public static void addEntry(Context context, String name, String email, String password, Integer folderId) {
        //SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE);

        ContentValues cv = new ContentValues();

        String encryptedPassword = EncryptionHelper.encrypt(password);

        cv.put(COLUMN_NAME, name);
        cv.put(COLUMN_EMAIL, email);
        cv.put(COLUMN_PASSWORD, encryptedPassword);
        
        if (folderId != null) {
            cv.put(COLUMN_FOLDER_ID, folderId);
        }
        
        // Get max sort order
        Cursor c = db.rawQuery("SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_NAME + " WHERE " + (folderId == null ? COLUMN_FOLDER_ID + " IS NULL" : COLUMN_FOLDER_ID + " = " + folderId), null);
        int sortOrder = 0;
        if (c != null && c.moveToFirst() && !c.isNull(0)) {
            sortOrder = c.getInt(0) + 1;
        }
        if (c != null) c.close();
        cv.put(COLUMN_SORT_ORDER, sortOrder);

        db.insert(TABLE_NAME, null, cv);
    }



    /**
     * Reads all data from the database table.
     *
     * @return A Cursor object containing all the data from the database table.
     * @throws SQLiteException If there's an error accessing the database.
     */
    public Cursor readAllData() {
        SQLiteDatabase db = this.getReadableDatabase(KEY_ENCRYPTION);
        String query = "SELECT * FROM " + TABLE_NAME;

        return db.rawQuery(query, null);
    }



    /**
     * Searches for items in the database based on the provided search query.
     *
     * @param itemToSearch The search query used to find matching items in the database.
     * @return A Cursor object containing the results of the search.
     * @throws SQLiteException If there's an error accessing the database.
     */
    public Cursor searchItem(String itemToSearch) {
        SQLiteDatabase db = this.getReadableDatabase(KEY_ENCRYPTION);

        String query = "SELECT * " +
                "FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME + " LIKE '%" + itemToSearch.toLowerCase() + "%'";

        return db.rawQuery(query, null);
    }


    /**
     * Updates an existing row in the database table with the specified row ID.
     *
     * @param row_id   The ID of the row to be updated.
     * @param name     The new value for the name column.
     * @param email    The new value for the email column.
     * @param password The new value for the password column.
     */
    public void updateData(String row_id, String name, String email, String password, Integer folderId) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        ContentValues cv = new ContentValues();

        cv.put(COLUMN_NAME, name);
        cv.put(COLUMN_EMAIL, email);
        cv.put(COLUMN_PASSWORD, password);
        if (folderId != null) {
            cv.put(COLUMN_FOLDER_ID, folderId);
        } else {
            cv.putNull(COLUMN_FOLDER_ID);
        }

        db.update(TABLE_NAME, cv, "id=?", new String[]{row_id});
    }



    /**
     * Deletes a row from the database with the specified row ID.
     *
     * @param rowId The ID of the row to be deleted.
     * @throws SQLiteException If there's an error accessing or updating the database.
     */
    public void deleteOneRow(String rowId) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        db.delete(TABLE_NAME, "id=?", new String[]{rowId});
    }

    /**
     * Adds a new folder.
     */
    public void addFolder(String folderName) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_FOLDER_NAME, folderName);
        
        Cursor c = db.rawQuery("SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS, null);
        int sortOrder = 0;
        if (c != null && c.moveToFirst() && !c.isNull(0)) {
            sortOrder = c.getInt(0) + 1;
        }
        if (c != null) c.close();
        cv.put(COLUMN_SORT_ORDER, sortOrder);
        
        db.insert(TABLE_FOLDERS, null, cv);
    }

    /**
     * Reads all folders.
     */
    public Cursor readAllFolders() {
        SQLiteDatabase db = this.getReadableDatabase(KEY_ENCRYPTION);
        return db.rawQuery("SELECT * FROM " + TABLE_FOLDERS + " ORDER BY " + COLUMN_SORT_ORDER + " ASC", null);
    }

    /**
     * Reads entries for a specific folder, or root if folderId is null.
     */
    public Cursor readEntriesByFolder(Integer folderId) {
        SQLiteDatabase db = this.getReadableDatabase(KEY_ENCRYPTION);
        String query;
        if (folderId == null) {
            query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " IS NULL ORDER BY " + COLUMN_SORT_ORDER + " ASC";
            return db.rawQuery(query, null);
        } else {
            query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " = ? ORDER BY " + COLUMN_SORT_ORDER + " ASC";
            return db.rawQuery(query, new String[]{String.valueOf(folderId)});
        }
    }

    /**
     * Deletes a folder and handles cascading logic.
     * @param cascade If true, delete all passwords in this folder. If false, move them to root (folder_id = NULL).
     */
    public void deleteFolder(String folderId, boolean cascade) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        if (cascade) {
            db.delete(TABLE_NAME, COLUMN_FOLDER_ID + "=?", new String[]{folderId});
        } else {
            ContentValues cv = new ContentValues();
            cv.putNull(COLUMN_FOLDER_ID);
            db.update(TABLE_NAME, cv, COLUMN_FOLDER_ID + "=?", new String[]{folderId});
        }
        db.delete(TABLE_FOLDERS, "id=?", new String[]{folderId});
    }

    /**
     * Duplicates a folder and all the passwords inside it.
     */
    public void duplicateFolder(String folderId, String originalFolderName) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        
        // 1. Create new folder
        String newFolderName = originalFolderName + " (Copy)";
        ContentValues cvFolder = new ContentValues();
        cvFolder.put(COLUMN_FOLDER_NAME, newFolderName);
        
        Cursor cSort = db.rawQuery("SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS, null);
        int sortOrder = 0;
        if (cSort != null && cSort.moveToFirst() && !cSort.isNull(0)) {
            sortOrder = cSort.getInt(0) + 1;
        }
        if (cSort != null) cSort.close();
        cvFolder.put(COLUMN_SORT_ORDER, sortOrder);
        
        long newFolderId = db.insert(TABLE_FOLDERS, null, cvFolder);
        
        if (newFolderId == -1) return; // Insertion failed

        // 2. Duplicate all passwords inside the original folder
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " = ?", new String[]{folderId});
        if (cursor != null && cursor.moveToFirst()) {
            db.beginTransaction();
            try {
                do {
                    @SuppressLint("Range") String passName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                    @SuppressLint("Range") String passEmail = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL));
                    @SuppressLint("Range") String passEncrypted = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD));
                    
                    ContentValues cvPass = new ContentValues();
                    cvPass.put(COLUMN_NAME, passName); // Keep exact same name for recursive items, or add Copy, let's keep exact
                    cvPass.put(COLUMN_EMAIL, passEmail);
                    cvPass.put(COLUMN_PASSWORD, passEncrypted); // already encrypted
                    cvPass.put(COLUMN_FOLDER_ID, newFolderId);
                    
                    Cursor cPassSort = db.rawQuery("SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " = " + newFolderId, null);
                    int passSortOrder = 0;
                    if (cPassSort != null && cPassSort.moveToFirst() && !cPassSort.isNull(0)) {
                        passSortOrder = cPassSort.getInt(0) + 1;
                    }
                    if (cPassSort != null) cPassSort.close();
                    cvPass.put(COLUMN_SORT_ORDER, passSortOrder);
                    
                    db.insert(TABLE_NAME, null, cvPass);
                } while (cursor.moveToNext());
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        if (cursor != null) cursor.close();
    }

    /**
     * Renames a folder.
     */
    public void updateFolderName(String folderId, String newName) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_FOLDER_NAME, newName);
        db.update(TABLE_FOLDERS, cv, "id=?", new String[]{folderId});
    }
    
    /**
     * Duplicates a password entry.
     */
    public void duplicateEntry(String rowId, Integer targetFolderId) {
        SQLiteDatabase db = this.getReadableDatabase(KEY_ENCRYPTION);
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE id=?", new String[]{rowId});
        
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME)) + " (Copy)";
            @SuppressLint("Range") String email = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL));
            @SuppressLint("Range") String pass = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD));
            cursor.close();
            
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_NAME, name);
            cv.put(COLUMN_EMAIL, email);
            cv.put(COLUMN_PASSWORD, pass); // Store raw from db, which is encrypted
            if (targetFolderId != null) {
                cv.put(COLUMN_FOLDER_ID, targetFolderId);
            }
            
            Cursor c = db.rawQuery("SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_NAME + " WHERE " + (targetFolderId == null ? COLUMN_FOLDER_ID + " IS NULL" : COLUMN_FOLDER_ID + " = " + targetFolderId), null);
            int sortOrder = 0;
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                sortOrder = c.getInt(0) + 1;
            }
            if (c != null) c.close();
            cv.put(COLUMN_SORT_ORDER, sortOrder);

            SQLiteDatabase writeDb = this.getWritableDatabase(KEY_ENCRYPTION);
            writeDb.insert(TABLE_NAME, null, cv);
        }
    }

    /**
     * Updates the sort order for a folder.
     */
    public void updateFolderSortOrder(String folderId, int newSortOrder) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_SORT_ORDER, newSortOrder);
        db.update(TABLE_FOLDERS, cv, "id=?", new String[]{folderId});
    }

    /**
     * Updates the sort order for a password entry.
     */
    public void updateEntrySortOrder(String entryId, int newSortOrder) {
        SQLiteDatabase db = this.getWritableDatabase(KEY_ENCRYPTION);
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_SORT_ORDER, newSortOrder);
        db.update(TABLE_NAME, cv, "id=?", new String[]{entryId});
    }



    /**
     * Checks if an account with the given name and email already exists in the database.
     *
     * @param name  The name of the account.
     * @param email The email of the account.
     * @return True if an account with the given name and email exists; otherwise, false.
     * @throws SQLiteException If there's an error accessing the database.
     */
    public static boolean checkIfAccountAlreadyExist(Context context, String name, String email) {
        //SQLiteDatabase db = context.getReadableDatabase(KEY_ENCRYPTION);
        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE);

        String selection = COLUMN_NAME + " = ? AND " + COLUMN_EMAIL + " = ?";
        String[] selectionArgs = {name, email};

        Cursor cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, null);

        boolean result = cursor != null && cursor.moveToFirst();

        if (cursor != null) {
            cursor.close();
        }

        return result;
    }



    /**
     * Changes the password used to encrypt the database.
     *
     * @param newPassword The new password for the database.
     * @param context     The application context.
     * @throws SQLiteException If there's an error accessing or updating the database.
     */
    public static void changeDBPassword(String newPassword, Context context) {
        SQLiteDatabase.loadLibs(context);
        String databasePath = context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(databasePath, KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE);
        db.rawExecSQL("PRAGMA rekey = '" + newPassword + "'");
        db.close();
        Toast.makeText(context, R.string.database_password_changed_successfully, Toast.LENGTH_SHORT).show();
    }



    @SuppressLint("Range")
    public static void exportDatabaseToJson(Context context, String passwordGotFromUser) {

        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE);

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        JSONArray jsonArray = new JSONArray();

        if (cursor != null && cursor.moveToFirst()) {
            do {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put(COLUMN_ID, cursor.getInt(cursor.getColumnIndex(COLUMN_ID)));
                    jsonObject.put(COLUMN_NAME, cursor.getString(cursor.getColumnIndex(COLUMN_NAME)));
                    jsonObject.put(COLUMN_EMAIL, cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL)));
                    jsonObject.put(COLUMN_PASSWORD, EncryptionHelper.decrypt(cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD))));

                    jsonArray.put(jsonObject);
                } catch (JSONException e) {
                    Log.e("8953467", "Error converting database row to JSON", e);
                }
            } while (cursor.moveToNext());

            cursor.close();
        }

        try {
            File exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            Calendar calendar = Calendar.getInstance();

            File file = new File(exportDir, "Encrypted_NewPass_DB_" +
                    calendar.get(Calendar.YEAR) +
                    "_" + (calendar.get(Calendar.MONTH) + 1) +
                    "_" + calendar.get(Calendar.DAY_OF_MONTH) + ".json"
            );

            if (file.exists()) {
                Log.d("8953467", "file already exists");
            } else {
                Log.d("8953467", "file not exists");
            }

            String jsonString = jsonArray.toString();
            String jsonEncryptedString = EncryptionHelper.encryptDatabase(jsonString, passwordGotFromUser);

            FileWriter fileWriter = new FileWriter(file);

            fileWriter.write(jsonEncryptedString);
            fileWriter.flush();
            fileWriter.close();

            Log.d("8953467", "Database exported to JSON successfully");
            Toast.makeText(context, context.getString(R.string.database_successfully_exported_to) + " " + Environment.DIRECTORY_DOWNLOADS, Toast.LENGTH_LONG).show();


        } catch (IOException e) {
            Log.e("8953467", "Error: ", e);
            Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show();

        } finally {
            db.close();
        }
    }


    public static void importJsonToDatabase(Context context, Uri fileUri, String passwordGotFromUser) throws NoSuchAlgorithmException, InvalidKeySpecException {

        String jsonEncryptedString = readJsonFromFile(context, fileUri);
        String jsonDecryptedString = EncryptionHelper.decryptDatabase(context, jsonEncryptedString, passwordGotFromUser);

        if (jsonDecryptedString == null) {
            Log.e("8953467", "Error reading JSON file");
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonDecryptedString);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                String name = jsonObject.getString(COLUMN_NAME);
                String email = jsonObject.getString(COLUMN_EMAIL);
                String password = jsonObject.getString(COLUMN_PASSWORD);

                if (!checkIfAccountAlreadyExist(context, name, email)) {
                    addEntry(context, name, email, password, null);
                } else {
                    Log.w("8953467", "entry: " + name + " " + email + " already exists");
                }
            }

            Log.d("8953467", "Data imported from JSON to database successfully");
            Toast.makeText(context, R.string.database_imported_successfully, Toast.LENGTH_LONG).show();

        } catch (JSONException e) {
            Toast.makeText(context, R.string.error_importing_database, Toast.LENGTH_LONG).show();
            Log.e("8953467", "Error parsing JSON", e);
        }
    }

    private static String readJsonFromFile(Context context, Uri fileUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream != null) {
                StringBuilder stringBuilder = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line);
                }
                bufferedReader.close();
                inputStream.close();
                return stringBuilder.toString();
            }
        } catch (IOException e) {
            Toast.makeText(context, R.string.error_importing_database, Toast.LENGTH_LONG).show();
            Log.e("8953467", "Error reading JSON file", e);
        }
        return null;
    }

}
