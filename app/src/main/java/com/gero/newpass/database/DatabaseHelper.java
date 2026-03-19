package com.gero.newpass.database;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
    private static final int DATABASE_VERSION = 3; // Incremented for nested folders feature
    private static final String TABLE_NAME = "my_password_record";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "record_name";
    private static final String COLUMN_EMAIL = "record_email";
    private static final String COLUMN_PASSWORD = "record_password";
    private static final String COLUMN_FOLDER_ID = "folder_id"; // Nullable foreign key to folders table
    private static final String COLUMN_SORT_ORDER = "sort_order"; // For manual re-ordering

    private static final String TABLE_FOLDERS = "folders";
    private static final String COLUMN_FOLDER_NAME = "folder_name";
    private static final String COLUMN_PARENT_FOLDER_ID = "parent_folder_id"; // Nullable, NULL = root
    
    private static final String KEY_ENCRYPTION = StringHelper.getSharedString();

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, KEY_ENCRYPTION, null, DATABASE_VERSION, 1, null, null, false);
        assert context != null;
        System.loadLibrary("sqlcipher");
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
                        COLUMN_PARENT_FOLDER_ID + " INTEGER, " +
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
        if (oldVersion < 3) {
            // Add parent_folder_id column for nested folders support
            db.execSQL("ALTER TABLE " + TABLE_FOLDERS + " ADD COLUMN " + COLUMN_PARENT_FOLDER_ID + " INTEGER;");
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
        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE, (net.zetetic.database.sqlcipher.SQLiteDatabaseHook) null);
        try {
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
        } finally {
            db.close();
        }
    }



    /**
     * Reads all data from the database table.
     *
     * @return A Cursor object containing all the data from the database table.
     * @throws SQLiteException If there's an error accessing the database.
     */
    public Cursor readAllData() {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
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
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();

        String query = "SELECT * " +
                "FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME + " LIKE '%" + itemToSearch.toLowerCase(java.util.Locale.ROOT) + "%'";

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
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
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
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        db.delete(TABLE_NAME, "id=?", new String[]{rowId});
    }

    /**
     * Adds a new folder with an optional parent folder ID for nesting.
     */
    public void addFolder(String folderName, Integer parentFolderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_FOLDER_NAME, folderName);
        
        if (parentFolderId != null) {
            cv.put(COLUMN_PARENT_FOLDER_ID, parentFolderId);
        }
        
        // Scope sort order within the parent
        String sortQuery;
        if (parentFolderId == null) {
            sortQuery = "SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " IS NULL";
        } else {
            sortQuery = "SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = " + parentFolderId;
        }
        
        Cursor c = db.rawQuery(sortQuery, null);
        int sortOrder = 0;
        if (c != null && c.moveToFirst() && !c.isNull(0)) {
            sortOrder = c.getInt(0) + 1;
        }
        if (c != null) c.close();
        cv.put(COLUMN_SORT_ORDER, sortOrder);
        
        db.insert(TABLE_FOLDERS, null, cv);
    }

    /**
     * Reads all folders (used for spinners / folder pickers).
     */
    public Cursor readAllFolders() {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_FOLDERS + " ORDER BY " + COLUMN_SORT_ORDER + " ASC", null);
    }

    /**
     * Reads folders by parent folder ID.
     * If parentFolderId is null, returns root-level folders.
     */
    public Cursor readFoldersByParent(Integer parentFolderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
        String query;
        if (parentFolderId == null) {
            query = "SELECT * FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " IS NULL ORDER BY " + COLUMN_SORT_ORDER + " ASC";
            return db.rawQuery(query, null);
        } else {
            query = "SELECT * FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = ? ORDER BY " + COLUMN_SORT_ORDER + " ASC";
            return db.rawQuery(query, new String[]{String.valueOf(parentFolderId)});
        }
    }

    /**
     * Reads entries for a specific folder, or root if folderId is null.
     */
    public Cursor readEntriesByFolder(Integer folderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
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
     * Recursively deletes all sub-folders first.
     * @param cascade If true, delete all passwords in this folder and sub-folders. If false, move them to root (folder_id = NULL).
     */
    public void deleteFolder(String folderId, boolean cascade) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        
        // First, recursively delete/handle all child sub-folders
        Cursor childFolders = db.rawQuery("SELECT " + COLUMN_ID + " FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = ?", new String[]{folderId});
        if (childFolders != null && childFolders.moveToFirst()) {
            do {
                @SuppressLint("Range") String childId = childFolders.getString(childFolders.getColumnIndex(COLUMN_ID));
                deleteFolder(childId, cascade); // Recursive call
            } while (childFolders.moveToNext());
            childFolders.close();
        }
        
        // Then handle passwords in this folder
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
     * Duplicates a folder, all sub-folders, and all the passwords inside them recursively.
     */
    public void duplicateFolder(String folderId, String originalFolderName) {
        duplicateFolderInternal(folderId, originalFolderName, true);
    }

    /**
     * Internal method for recursive folder duplication.
     * @param appendCopy If true, appends " (Copy)" to the folder name (only for the top-level call).
     */
    @SuppressLint("Range")
    private void duplicateFolderInternal(String folderId, String originalFolderName, boolean appendCopy) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        
        // 1. Get the original folder's parent
        Integer parentFolderId = null;
        Cursor folderCursor = db.rawQuery("SELECT * FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_ID + " = ?", new String[]{folderId});
        if (folderCursor != null && folderCursor.moveToFirst()) {
            if (!folderCursor.isNull(folderCursor.getColumnIndex(COLUMN_PARENT_FOLDER_ID))) {
                parentFolderId = folderCursor.getInt(folderCursor.getColumnIndex(COLUMN_PARENT_FOLDER_ID));
            }
            folderCursor.close();
        }
        
        // 2. Create new folder
        String newFolderName = appendCopy ? originalFolderName + " (Copy)" : originalFolderName;
        ContentValues cvFolder = new ContentValues();
        cvFolder.put(COLUMN_FOLDER_NAME, newFolderName);
        if (parentFolderId != null) {
            cvFolder.put(COLUMN_PARENT_FOLDER_ID, parentFolderId);
        }
        
        String sortQuery;
        if (parentFolderId == null) {
            sortQuery = "SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " IS NULL";
        } else {
            sortQuery = "SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = " + parentFolderId;
        }
        
        Cursor cSort = db.rawQuery(sortQuery, null);
        int sortOrder = 0;
        if (cSort != null && cSort.moveToFirst() && !cSort.isNull(0)) {
            sortOrder = cSort.getInt(0) + 1;
        }
        if (cSort != null) cSort.close();
        cvFolder.put(COLUMN_SORT_ORDER, sortOrder);
        
        long newFolderId = db.insert(TABLE_FOLDERS, null, cvFolder);
        
        if (newFolderId == -1) return; // Insertion failed

        // 3. Duplicate all passwords inside the original folder
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " = ?", new String[]{folderId});
        if (cursor != null && cursor.moveToFirst()) {
            db.beginTransaction();
            try {
                do {
                    String passName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                    String passEmail = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL));
                    String passEncrypted = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD));
                    
                    ContentValues cvPass = new ContentValues();
                    cvPass.put(COLUMN_NAME, passName);
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
        
        // 4. Recursively duplicate all child sub-folders
        Cursor childFolders = db.rawQuery("SELECT " + COLUMN_ID + ", " + COLUMN_FOLDER_NAME + " FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = ?", new String[]{folderId});
        if (childFolders != null && childFolders.moveToFirst()) {
            do {
                String childId = childFolders.getString(childFolders.getColumnIndex(COLUMN_ID));
                String childName = childFolders.getString(childFolders.getColumnIndex(COLUMN_FOLDER_NAME));
                // For child folders during duplication, we need to set their parent to the new folder
                duplicateChildFolder(childId, childName, (int) newFolderId);
            } while (childFolders.moveToNext());
            childFolders.close();
        }
    }

    /**
     * Duplicates a child folder into a new parent, recursively.
     */
    @SuppressLint("Range")
    private void duplicateChildFolder(String originalFolderId, String folderName, int newParentFolderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        
        // Create the folder under the new parent
        ContentValues cvFolder = new ContentValues();
        cvFolder.put(COLUMN_FOLDER_NAME, folderName);
        cvFolder.put(COLUMN_PARENT_FOLDER_ID, newParentFolderId);
        
        Cursor cSort = db.rawQuery("SELECT MAX(" + COLUMN_SORT_ORDER + ") FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = " + newParentFolderId, null);
        int sortOrder = 0;
        if (cSort != null && cSort.moveToFirst() && !cSort.isNull(0)) {
            sortOrder = cSort.getInt(0) + 1;
        }
        if (cSort != null) cSort.close();
        cvFolder.put(COLUMN_SORT_ORDER, sortOrder);
        
        long newFolderId = db.insert(TABLE_FOLDERS, null, cvFolder);
        if (newFolderId == -1) return;
        
        // Duplicate passwords
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " = ?", new String[]{originalFolderId});
        if (cursor != null && cursor.moveToFirst()) {
            db.beginTransaction();
            try {
                do {
                    String passName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));
                    String passEmail = cursor.getString(cursor.getColumnIndex(COLUMN_EMAIL));
                    String passEncrypted = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD));
                    
                    ContentValues cvPass = new ContentValues();
                    cvPass.put(COLUMN_NAME, passName);
                    cvPass.put(COLUMN_EMAIL, passEmail);
                    cvPass.put(COLUMN_PASSWORD, passEncrypted);
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
        
        // Recursively duplicate child folders
        Cursor childFolders = db.rawQuery("SELECT " + COLUMN_ID + ", " + COLUMN_FOLDER_NAME + " FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = ?", new String[]{originalFolderId});
        if (childFolders != null && childFolders.moveToFirst()) {
            do {
                String childId = childFolders.getString(childFolders.getColumnIndex(COLUMN_ID));
                String childName = childFolders.getString(childFolders.getColumnIndex(COLUMN_FOLDER_NAME));
                duplicateChildFolder(childId, childName, (int) newFolderId);
            } while (childFolders.moveToNext());
            childFolders.close();
        }
    }

    /**
     * Renames a folder.
     */
    public void updateFolderName(String folderId, String newName) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_FOLDER_NAME, newName);
        db.update(TABLE_FOLDERS, cv, "id=?", new String[]{folderId});
    }
    
    /**
     * Duplicates a password entry.
     */
    public void duplicateEntry(String rowId, Integer targetFolderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
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

            SQLiteDatabase writeDb = (SQLiteDatabase) this.getWritableDatabase();
            writeDb.insert(TABLE_NAME, null, cv);
        }
    }

    /**
     * Updates the sort order for a folder.
     */
    public void updateFolderSortOrder(String folderId, int newSortOrder) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_SORT_ORDER, newSortOrder);
        db.update(TABLE_FOLDERS, cv, "id=?", new String[]{folderId});
    }

    /**
     * Updates the sort order for a password entry.
     */
    public void updateEntrySortOrder(String entryId, int newSortOrder) {
        SQLiteDatabase db = (SQLiteDatabase) this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_SORT_ORDER, newSortOrder);
        db.update(TABLE_NAME, cv, "id=?", new String[]{entryId});
    }

    /**
     * Returns the parent folder ID for a given folder.
     */
    @SuppressLint("Range")
    public Integer getParentFolderId(String folderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COLUMN_PARENT_FOLDER_ID + " FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_ID + " = ?", new String[]{folderId});
        Integer parentId = null;
        if (cursor != null && cursor.moveToFirst()) {
            if (!cursor.isNull(0)) {
                parentId = cursor.getInt(0);
            }
            cursor.close();
        }
        return parentId;
    }

    /**
     * Returns the folder name for a given folder ID.
     */
    @SuppressLint("Range")
    public String getFolderName(int folderId) {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COLUMN_FOLDER_NAME + " FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_ID + " = ?", new String[]{String.valueOf(folderId)});
        String name = null;
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(0);
            cursor.close();
        }
        return name;
    }

    /**
     * Recursively builds a flat list of all folders with indentation for display in spinners.
     * Each entry is a String[] with {displayName, folderId}.
     */
    @SuppressLint("Range")
    public void buildFolderTree(java.util.List<String[]> result, Integer parentId, int depth) {
        SQLiteDatabase db = (SQLiteDatabase) this.getReadableDatabase();
        String query;
        String[] args;
        if (parentId == null) {
            query = "SELECT * FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " IS NULL ORDER BY " + COLUMN_SORT_ORDER + " ASC";
            args = null;
        } else {
            query = "SELECT * FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_PARENT_FOLDER_ID + " = ? ORDER BY " + COLUMN_SORT_ORDER + " ASC";
            args = new String[]{String.valueOf(parentId)};
        }
        
        Cursor cursor = db.rawQuery(query, args);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String id = cursor.getString(cursor.getColumnIndex(COLUMN_ID));
                String name = cursor.getString(cursor.getColumnIndex(COLUMN_FOLDER_NAME));
                
                StringBuilder indent = new StringBuilder();
                for (int i = 0; i < depth; i++) {
                    indent.append("    ");
                }
                if (depth > 0) {
                    indent.append("└ ");
                }
                
                result.add(new String[]{indent.toString() + name, id});
                
                // Recurse into children
                buildFolderTree(result, Integer.parseInt(id), depth + 1);
            } while (cursor.moveToNext());
            cursor.close();
        }
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
        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE, (net.zetetic.database.sqlcipher.SQLiteDatabaseHook) null);
        try {
            String selection = COLUMN_NAME + " = ? AND " + COLUMN_EMAIL + " = ?";
            String[] selectionArgs = {name, email};

            Cursor cursor = db.query(TABLE_NAME, null, selection, selectionArgs, null, null, null);

            boolean result = cursor != null && cursor.moveToFirst();

            if (cursor != null) {
                cursor.close();
            }

            return result;
        } finally {
            db.close();
        }
    }



    /**
     * Changes the password used to encrypt the database.
     *
     * @param newPassword The new password for the database.
     * @param context     The application context.
     * @throws SQLiteException If there's an error accessing or updating the database.
     */
    public static void changeDBPassword(String newPassword, Context context) {
        System.loadLibrary("sqlcipher");
        String databasePath = context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(databasePath, KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE, (net.zetetic.database.sqlcipher.SQLiteDatabaseHook) null);
        db.rawExecSQL("PRAGMA rekey = '" + newPassword + "'");
        db.close();
        Toast.makeText(context, R.string.database_password_changed_successfully, Toast.LENGTH_SHORT).show();
    }



    @SuppressLint("Range")
    public static void exportDatabaseToJson(Context context, String passwordGotFromUser) {

        SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE, (net.zetetic.database.sqlcipher.SQLiteDatabaseHook) null);

        JSONObject finalExportObject = new JSONObject();
        JSONArray foldersArray = new JSONArray();
        JSONArray passwordsArray = new JSONArray();

        // 1. Export Folders
        Cursor folderCursor = db.rawQuery("SELECT * FROM " + TABLE_FOLDERS, null);
        if (folderCursor != null && folderCursor.moveToFirst()) {
            do {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put(COLUMN_ID, folderCursor.getInt(folderCursor.getColumnIndex(COLUMN_ID)));
                    jsonObject.put(COLUMN_FOLDER_NAME, folderCursor.getString(folderCursor.getColumnIndex(COLUMN_FOLDER_NAME)));
                    if (!folderCursor.isNull(folderCursor.getColumnIndex(COLUMN_PARENT_FOLDER_ID))) {
                        jsonObject.put(COLUMN_PARENT_FOLDER_ID, folderCursor.getInt(folderCursor.getColumnIndex(COLUMN_PARENT_FOLDER_ID)));
                    }
                    jsonObject.put(COLUMN_SORT_ORDER, folderCursor.getInt(folderCursor.getColumnIndex(COLUMN_SORT_ORDER)));
                    foldersArray.put(jsonObject);
                } catch (JSONException e) {
                    Log.e("8953467", "Error converting folder row to JSON", e);
                }
            } while (folderCursor.moveToNext());
            folderCursor.close();
        }

        // 2. Export Passwords
        Cursor passwordCursor = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);
        if (passwordCursor != null && passwordCursor.moveToFirst()) {
            do {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put(COLUMN_ID, passwordCursor.getInt(passwordCursor.getColumnIndex(COLUMN_ID)));
                    jsonObject.put(COLUMN_NAME, passwordCursor.getString(passwordCursor.getColumnIndex(COLUMN_NAME)));
                    jsonObject.put(COLUMN_EMAIL, passwordCursor.getString(passwordCursor.getColumnIndex(COLUMN_EMAIL)));
                    jsonObject.put(COLUMN_PASSWORD, EncryptionHelper.decrypt(passwordCursor.getString(passwordCursor.getColumnIndex(COLUMN_PASSWORD))));
                    
                    if (!passwordCursor.isNull(passwordCursor.getColumnIndex(COLUMN_FOLDER_ID))) {
                        jsonObject.put(COLUMN_FOLDER_ID, passwordCursor.getInt(passwordCursor.getColumnIndex(COLUMN_FOLDER_ID)));
                    }
                    jsonObject.put(COLUMN_SORT_ORDER, passwordCursor.getInt(passwordCursor.getColumnIndex(COLUMN_SORT_ORDER)));

                    passwordsArray.put(jsonObject);
                } catch (JSONException e) {
                    Log.e("8953467", "Error converting password row to JSON", e);
                }
            } while (passwordCursor.moveToNext());
            passwordCursor.close();
        }

        try {
            finalExportObject.put("folders", foldersArray);
            finalExportObject.put("passwords", passwordsArray);
        } catch (JSONException e) {
            Log.e("8953467", "Error assembling final export JSON object", e);
        }

        try {
            File exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.US);
            String timestamp = sdf.format(new java.util.Date());

            File file = new File(exportDir, "Encrypted_NewPass_DB_" + timestamp + ".json");

            if (file.exists()) {
                Log.d("8953467", "file already exists");
            } else {
                Log.d("8953467", "file not exists");
            }

            String jsonString = finalExportObject.toString();
            String jsonEncryptedString = EncryptionHelper.encryptDatabase(jsonString, passwordGotFromUser);

            FileWriter fileWriter = new FileWriter(file);

            fileWriter.write(jsonEncryptedString);
            fileWriter.flush();
            fileWriter.close();

            Log.d("8953467", "Database exported to JSON successfully");
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, context.getString(R.string.database_successfully_exported_to) + " " + Environment.DIRECTORY_DOWNLOADS, Toast.LENGTH_LONG).show()
            );


        } catch (IOException e) {
            Log.e("8953467", "Error: ", e);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_LONG).show()
            );

        } finally {
            db.close();
        }
    }
    public static int[] importJsonToDatabase(Context context, Uri fileUri, String passwordGotFromUser) throws NoSuchAlgorithmException, InvalidKeySpecException {

        String jsonEncryptedString = readJsonFromFile(context, fileUri);
        String jsonDecryptedString = EncryptionHelper.decryptDatabase(context, jsonEncryptedString, passwordGotFromUser);

        if (jsonDecryptedString == null) {
            Log.e("8953467", "Error reading JSON file");
            return null;
        }

        // Import counters: [0] = added, [1] = ignored (exact match), [2] = conflict
        int[] counters = new int[3];

        try {
            JSONObject importData = null;
            JSONArray oldFormatArray = null;

            String trimmedStr = jsonDecryptedString.trim();
            if (trimmedStr.startsWith("{")) {
                importData = new JSONObject(jsonDecryptedString);
            } else if (trimmedStr.startsWith("[")) {
                oldFormatArray = new JSONArray(jsonDecryptedString);
            } else {
                throw new JSONException("Unsupported JSON format");
            }

            SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).getAbsolutePath(), KEY_ENCRYPTION, null, SQLiteDatabase.OPEN_READWRITE, (net.zetetic.database.sqlcipher.SQLiteDatabaseHook) null);

            if (oldFormatArray != null) {
                // LEGACY FORMAT: Flat Array of Passwords
                for (int i = 0; i < oldFormatArray.length(); i++) {
                    JSONObject jsonObject = oldFormatArray.getJSONObject(i);
                    String name = jsonObject.getString(COLUMN_NAME);
                    String email = jsonObject.getString(COLUMN_EMAIL);
                    String password = jsonObject.getString(COLUMN_PASSWORD);

                    int result = importPasswordWithConflictCheck(db, context, name, email, password, null);
                    counters[result]++;
                }
            } else if (importData != null) {
                // NEW FORMAT: Structured Folders and Passwords
                JSONArray foldersArray = importData.optJSONArray("folders");
                java.util.HashMap<Integer, Integer> folderIdMap = new java.util.HashMap<>();

                if (foldersArray != null) {
                    // Build a lookup of imported parent relationships (old IDs)
                    java.util.HashMap<Integer, Integer> importedParentMap = new java.util.HashMap<>();
                    for (int i = 0; i < foldersArray.length(); i++) {
                        JSONObject fObj = foldersArray.getJSONObject(i);
                        int oldId = fObj.getInt(COLUMN_ID);
                        if (fObj.has(COLUMN_PARENT_FOLDER_ID)) {
                            importedParentMap.put(oldId, fObj.getInt(COLUMN_PARENT_FOLDER_ID));
                        }
                    }

                    // Process folders in dependency order (parents before children)
                    java.util.Set<Integer> processed = new java.util.HashSet<>();
                    boolean progress = true;
                    while (progress) {
                        progress = false;
                        for (int i = 0; i < foldersArray.length(); i++) {
                            JSONObject fObj = foldersArray.getJSONObject(i);
                            int oldId = fObj.getInt(COLUMN_ID);
                            if (processed.contains(oldId)) continue;

                            // Check if parent is already processed (or has no parent)
                            Integer oldParentId = importedParentMap.get(oldId);
                            if (oldParentId != null && !processed.contains(oldParentId)) {
                                continue; // Parent not yet processed, skip for now
                            }

                            String fName = fObj.getString(COLUMN_FOLDER_NAME);
                            int sortO = fObj.optInt(COLUMN_SORT_ORDER, 0);

                            // Resolve parent to new ID
                            Integer newParentId = (oldParentId != null) ? folderIdMap.get(oldParentId) : null;

                            // Check if a folder with same name already exists at this parent level
                            Integer existingFolderId = findExistingFolderByNameAndParent(db, fName, newParentId);

                            if (existingFolderId != null) {
                                // Merge: map imported folder to existing folder
                                folderIdMap.put(oldId, existingFolderId);
                                Log.d("8953467", "Folder merged: " + fName + " → existing id " + existingFolderId);
                            } else {
                                // Create new folder
                                ContentValues cv = new ContentValues();
                                cv.put(COLUMN_FOLDER_NAME, fName);
                                cv.put(COLUMN_SORT_ORDER, sortO);
                                if (newParentId != null) {
                                    cv.put(COLUMN_PARENT_FOLDER_ID, newParentId);
                                }
                                long newFolderId = db.insert(TABLE_FOLDERS, null, cv);
                                if (newFolderId != -1) {
                                    folderIdMap.put(oldId, (int) newFolderId);
                                    Log.d("8953467", "Folder created: " + fName + " → new id " + newFolderId);
                                }
                            }

                            processed.add(oldId);
                            progress = true;
                        }
                    }
                }

                JSONArray passwordsArray = importData.optJSONArray("passwords");
                if (passwordsArray != null) {
                    for (int i = 0; i < passwordsArray.length(); i++) {
                        JSONObject pObj = passwordsArray.getJSONObject(i);
                        String name = pObj.getString(COLUMN_NAME);
                        String email = pObj.getString(COLUMN_EMAIL);
                        String password = pObj.getString(COLUMN_PASSWORD);

                        Integer newFolderId = null;
                        if (pObj.has(COLUMN_FOLDER_ID)) {
                            int oldFolderId = pObj.getInt(COLUMN_FOLDER_ID);
                            newFolderId = folderIdMap.get(oldFolderId);
                        }

                        int result = importPasswordWithConflictCheck(db, context, name, email, password, newFolderId);
                        counters[result]++;
                    }
                }

                // Clean up empty folders that were newly created during import
                removeEmptyImportedFolders(db);
            }
            
            db.close();
            Log.d("8953467", "Data imported from JSON to database successfully");
            return counters;

        } catch (JSONException e) {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, R.string.error_importing_database, Toast.LENGTH_LONG).show()
            );
            Log.e("8953467", "Error parsing JSON", e);
            return null;
        }
    }

    /**
     * Import result codes
     */
    private static final int IMPORT_ADDED = 0;
    private static final int IMPORT_IGNORED = 1;
    private static final int IMPORT_CONFLICT = 2;

    /**
     * Imports a password entry with 3-way conflict resolution:
     * 1. Title+Username+Password all match → skip (duplicate) → returns IMPORT_IGNORED
     * 2. Title+Username match but Password differs → import with "(Conflict)" → returns IMPORT_CONFLICT
     * 3. No match → import normally → returns IMPORT_ADDED
     *
     * @param password The PLAINTEXT password from the import file
     * @return IMPORT_ADDED (0), IMPORT_IGNORED (1), or IMPORT_CONFLICT (2)
     */
    @SuppressLint("Range")
    private static int importPasswordWithConflictCheck(SQLiteDatabase db, Context context, String name, String email, String password, Integer folderId) {
        // Look for existing entries with same title + username
        Cursor cursor = db.query(TABLE_NAME, new String[]{COLUMN_PASSWORD},
                COLUMN_NAME + " = ? AND " + COLUMN_EMAIL + " = ?",
                new String[]{name, email}, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            // Found existing entry(ies) with same name+email
            boolean exactMatch = false;
            do {
                String existingEncryptedPassword = cursor.getString(cursor.getColumnIndex(COLUMN_PASSWORD));
                String existingDecryptedPassword = EncryptionHelper.decrypt(existingEncryptedPassword);
                if (password.equals(existingDecryptedPassword)) {
                    exactMatch = true;
                    break;
                }
            } while (cursor.moveToNext());
            cursor.close();

            if (exactMatch) {
                // Case 1: Exact duplicate — skip
                Log.d("8953467", "Import skipped (exact duplicate): " + name + " / " + email);
                return IMPORT_IGNORED;
            } else {
                // Case 2: Same title+username but different password — import with (Conflict)
                Log.d("8953467", "Import conflict: " + name + " / " + email + " — appending (Conflict)");
                addEntry(context, name + " (Conflict)", email, password, folderId);
                return IMPORT_CONFLICT;
            }
        } else {
            // Case 3: No match — import normally
            if (cursor != null) cursor.close();
            addEntry(context, name, email, password, folderId);
            return IMPORT_ADDED;
        }
    }

    /**
     * Finds an existing folder with the same name at the same parent level.
     * Used for folder merging during import.
     *
     * @return The ID of the existing folder if found, null otherwise
     */
    @SuppressLint("Range")
    private static Integer findExistingFolderByNameAndParent(SQLiteDatabase db, String folderName, Integer parentId) {
        String query;
        String[] args;
        
        if (parentId == null) {
            query = "SELECT id FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_FOLDER_NAME + " = ? AND " + COLUMN_PARENT_FOLDER_ID + " IS NULL";
            args = new String[]{folderName};
        } else {
            query = "SELECT id FROM " + TABLE_FOLDERS + " WHERE " + COLUMN_FOLDER_NAME + " = ? AND " + COLUMN_PARENT_FOLDER_ID + " = ?";
            args = new String[]{folderName, String.valueOf(parentId)};
        }
        
        Cursor cursor = db.rawQuery(query, args);
        Integer result = null;
        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getInt(cursor.getColumnIndex("id"));
        }
        if (cursor != null) cursor.close();
        return result;
    }

    /**
     * Removes folders that have no passwords and no sub-folders (empty leaf folders).
     * Runs iteratively until no more empty folders can be removed.
     */
    private static void removeEmptyImportedFolders(SQLiteDatabase db) {
        boolean removed = true;
        while (removed) {
            removed = false;
            // Find folders that have zero passwords AND zero sub-folders
            String query = "SELECT f.id FROM " + TABLE_FOLDERS + " f"
                    + " WHERE NOT EXISTS (SELECT 1 FROM " + TABLE_NAME + " WHERE " + COLUMN_FOLDER_ID + " = f.id)"
                    + " AND NOT EXISTS (SELECT 1 FROM " + TABLE_FOLDERS + " f2 WHERE f2." + COLUMN_PARENT_FOLDER_ID + " = f.id)";
            
            Cursor cursor = db.rawQuery(query, null);
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    @SuppressLint("Range") int folderId = cursor.getInt(cursor.getColumnIndex("id"));
                    db.delete(TABLE_FOLDERS, "id=?", new String[]{String.valueOf(folderId)});
                    removed = true;
                    Log.d("8953467", "Removed empty folder id: " + folderId);
                } while (cursor.moveToNext());
                cursor.close();
            }
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
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, R.string.error_importing_database, Toast.LENGTH_LONG).show()
            );
            Log.e("8953467", "Error reading JSON file", e);
        }
        return null;
    }

}
