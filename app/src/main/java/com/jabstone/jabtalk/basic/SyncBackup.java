package com.jabstone.jabtalk.basic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class SyncBackup {

    public static final String PREFS_NAME = "jabtalk";
    public static final String PREF_SYNC_FOLDER_URI = "sync_folder_uri";
    private static final String TAG = SyncBackup.class.getSimpleName();

    private SyncBackup() {}

    public static Uri getFolderUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String s = prefs.getString(PREF_SYNC_FOLDER_URI, null);
        if (s == null) return null;
        Uri uri = Uri.parse(s);
        for (UriPermission p : context.getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isWritePermission()) {
                return uri;
            }
        }
        return null;
    }

    public static void saveFolderUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(PREF_SYNC_FOLDER_URI, uri.toString()).apply();
    }

    public static void clearFolder(Context context) {
        Uri current = getFolderUri(context);
        if (current != null) {
            try {
                context.getContentResolver().releasePersistableUriPermission(current,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(PREF_SYNC_FOLDER_URI).apply();
    }

    // Writes a jabtalk_YYYY-MM-DD.bak to the configured sync folder, overwriting
    // the same day's file. Returns null on success, a short error string on failure,
    // or "no_folder" if no folder is configured.
    public static String writeBackupIfConfigured(Context context) {
        Uri folderUri = getFolderUri(context);
        if (folderUri == null) {
            return "no_folder";
        }
        try {
            DocumentFile tree = DocumentFile.fromTreeUri(context, folderUri);
            if (tree == null || !tree.canWrite()) {
                clearFolder(context);
                return "folder_lost";
            }
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date());
            String filename = "autoJabtalk-" + date + ".bak";
            DocumentFile existing = tree.findFile(filename);
            if (existing != null) {
                existing.delete();
            }
            DocumentFile out = tree.createFile("application/octet-stream", filename);
            if (out == null) {
                return "create_failed";
            }
            JTApp.getDataStore().backupDataStore(out.getUri(),
                    JTApp.getDataStore().getRootCategory());
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_INFO,
                    "Sync backup written: " + filename);
            return null;
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                    "Sync backup failed: " + e.getMessage());
            return e.getMessage() == null ? "unknown" : e.getMessage();
        }
    }
}
