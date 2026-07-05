package com.jabstone.jabtalk.basic.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jabstone.jabtalk.basic.ClipBoard;
import com.jabstone.jabtalk.basic.ClipBoard.Operation;
import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.adapters.ManageRecyclerAdapter;
import com.jabstone.jabtalk.basic.exceptions.JabException;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;


public class ManageActivity extends Activity {

    private static final String TAG = ManageActivity.class.getSimpleName();
    private final int DIALOG_DELETE_IDEOGRAM_CONFIRMATION = 4001;
    private final int DIALOG_ERROR = 4002;
    private final int DIALOG_GENERIC = 4004;
    private final int DIALOG_EXIT_MANAGE_SCREEN = 4005;
    private final int DIALOG_ACTION_ADD = 4006;
    private final int DIALOG_BACKUP_DATASTORE_FULL = 4007;
    private final int DIALOG_RESTORE_DATASTORE_FULL = 4008;
    private final int DIALOG_BACKUP_DATASTORE_PARTIAL = 4010;
    private final int DIALOG_RESTORE_DATASTORE_PARTIAL = 4011;

    private final String STATE_IDEOGRAM = "ideogram";
    private final int ADD_CATEGORY = 0;
    private final int ADD_WORD = 1;
    private final int ACTIVITY_RESULT_PREFERENCE = 5000;
    private final int ACTIVITY_EDIT_IDEOGRAM = 5001;
    private final int ACTIVITY_EXPAND_CATEGORY = 5002;
    private final int ACTIVITY_SELECT_BACKUP_PATH = 5003;
    private final int ACTIVITY_SELECT_RESTORE_FILE = 5004;
    private final int ACTIVITY_SELECT_SYNC_FOLDER = 5005;
    private ManageRecyclerAdapter m_adapter = null;
    private RestoreTask restoreTask = null;
    private BackupTask backupTask = null;
    private SaveDataStoreTask saveTask = null;
    private ProgressDialog progressDialog = null;
    private Ideogram m_ideogram = null;
    private Ideogram m_selectedGram = null;
    private boolean madeChanges = false;
    private RecyclerView m_listView = null;
    private ItemTouchHelper m_touchHelper = null;
    private boolean m_sortMode = false;
    private boolean isBackupRestoreClicked = false;
    private boolean isPartialRestoreClicked = false;
    private Uri restorePath = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_activity);

        boolean fromMain = getIntent().hasExtra(JTApp.INTENT_EXTRA_CALLED_FROM_MAIN);

        String gramId = getIntent().getStringExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID);
        m_ideogram = JTApp.getDataStore().getIdeogram(gramId);
        m_selectedGram = m_ideogram;

        // See if activity was launched from main screen due to empty category
        if (!m_ideogram.isRoot() && m_ideogram.getType() == Type.Category && fromMain) {
            m_ideogram = JTApp.getDataStore().getRootCategory();
            m_selectedGram = JTApp.getDataStore().getIdeogram(gramId);
            expandCategory(m_selectedGram);
        }

        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        m_adapter = new ManageRecyclerAdapter(this, m_ideogram);
        m_adapter.setOnItemClickListener(new ManageRecyclerAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Ideogram gram) {
                if (gram.getType() == Type.Category) {
                    expandCategory(gram);
                } else {
                    editIdeogram(gram);
                }
            }
        });
        m_adapter.setOnItemMoveListener(new ManageRecyclerAdapter.OnItemMoveListener() {
            @Override
            public void onItemMoved() {
                madeChanges = true;
                persistChanges(false);
            }
        });

        getListView().setLayoutManager(new LinearLayoutManager(this));
        getListView().setAdapter(m_adapter);

        m_touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(RecyclerView recyclerView,
                                  RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                return m_adapter.onItemMove(viewHolder.getAdapterPosition(),
                        target.getAdapterPosition());
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                // swipe not used
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                m_adapter.onItemMoveFinished();
            }
        });
        m_touchHelper.attachToRecyclerView(getListView());
        m_adapter.setTouchHelper(m_touchHelper);

        JTApp.addDataStoreListener(m_adapter);
        restoreProgressDialog();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(STATE_IDEOGRAM)) {
            m_ideogram = (Ideogram) savedInstanceState.getSerializable(STATE_IDEOGRAM);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_IDEOGRAM, m_ideogram);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(m_ideogram == null) {
            m_ideogram = JTApp.getDataStore().getRootCategory();
        }
        if (!m_ideogram.isRoot()) {
            this.setTitle(m_ideogram.getLabel());
        } else {
            this.setTitle(getString(R.string.manage_activity_title));
        }
        restoreProgressDialog();
        invalidateOptionsMenu();

        // Display add item dialog if category is blank
        if (m_ideogram != null && m_ideogram.getChildren(true).size() < 1 && !isBackupRestoreClicked) {
            showDialog(DIALOG_ACTION_ADD);
        }
        isBackupRestoreClicked = false;
    }

    @Override
    protected void onPause() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        super.onPause();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        m_selectedGram = (Ideogram) v.getTag();
        int position = m_ideogram.getChildren(true).indexOf(m_selectedGram);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manage_context_menu, menu);
        ClipBoard clip = JTApp.getClipBoard();
        MenuItem convert = menu.findItem(R.id.context_menu_item_convert);
        MenuItem paste = menu.findItem(R.id.context_menu_item_paste);
        paste.setEnabled(false);

        // Move menu items
        MenuItem moveUp = menu.findItem(R.id.context_menu_item_move_up);
        MenuItem moveFirst = menu.findItem(R.id.context_menu_item_move_beginning);
        MenuItem moveDown = menu.findItem(R.id.context_menu_item_move_down);
        MenuItem moveLast = menu.findItem(R.id.context_menu_item_move_end);
        moveUp.setEnabled(true);
        moveDown.setEnabled(true);
        moveFirst.setEnabled(true);
        moveLast.setEnabled(true);

        // Should we show the hide or unhide menu item
        MenuItem hide = menu.findItem(R.id.context_menu_item_hide);
        hide.setTitle(R.string.menu_hide);
        if (m_selectedGram.isHidden()) {
            hide.setTitle(R.string.menu_unhide);
        }

        // Should we show past menu item
        if (!clip.isEmpty() && m_selectedGram.getType() == Type.Category
                && JTApp.getDataStore().getIdeogramMap().containsKey(clip.getId())) {
            Ideogram source = JTApp.getDataStore().getIdeogram(clip.getId());
            if (source != null) {
                switch (clip.getOperation()) {
                    case CUT:
                        if (JTApp.getDataStore().isSafeToMove(source, m_selectedGram)) {
                            paste.setEnabled(true);
                        }
                        break;
                    case COPY:
                        paste.setEnabled(true);
                        break;
                }
            }
        }

        // Should we show backup category menu item
        MenuItem backupCategory = menu.findItem(R.id.context_menu_backup_category);
        MenuItem restoreCategory = menu.findItem(R.id.context_menu_restore_category);
        boolean isCategory = m_selectedGram.getType() == Type.Category;
        backupCategory.setEnabled(isCategory);
        restoreCategory.setEnabled(isCategory);

        // Determine state of move menu items
        if (position < 1 || m_ideogram.getChildren(true).size() < 2) {
            moveUp.setEnabled(false);
            moveFirst.setEnabled(false);
        }
        if (position == m_ideogram.getChildren(true).size() - 1
                || m_ideogram.getChildren(true).size() < 2) {
            moveDown.setEnabled(false);
            moveLast.setEnabled(false);
        }
        if (m_selectedGram.getType() == Type.Category) {
            convert.setEnabled(false);
        }
    }

    @Override
    public void onBackPressed() {
        if (m_ideogram.isRoot()) {
            showDialog(DIALOG_EXIT_MANAGE_SCREEN);
        } else {
            finish();
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ClipBoard clipboard = JTApp.getClipBoard();
        int position = m_ideogram.getChildren(true).indexOf(m_selectedGram);
        LinkedList<Ideogram> categoryList = m_ideogram.getChildren(true);

        switch (item.getItemId()) {
            case R.id.context_menu_item_edit:
                editIdeogram(m_selectedGram);
                break;
            case R.id.context_menu_item_delete:
                m_ideogram = m_selectedGram;
                if (m_ideogram != null) {
                    showDialog(DIALOG_DELETE_IDEOGRAM_CONFIRMATION);
                }
                break;
            case R.id.context_menu_item_copy:
                clipboard.setId(m_selectedGram.getId());
                clipboard.setOperation(Operation.COPY);
                invalidateOptionsMenu();
                break;
            case R.id.context_menu_item_cut:
                clipboard.setId(m_selectedGram.getId());
                clipboard.setOperation(Operation.CUT);
                invalidateOptionsMenu();
                break;
            case R.id.context_menu_item_paste:
                try {
                    JTApp.getClipBoard().paste(m_selectedGram.getId());
                    persistChanges(false);
                    invalidateOptionsMenu();
                } catch (JabException e) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_save_results));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case R.id.context_menu_item_move_up:
                categoryList.add(position - 1, m_selectedGram);
                categoryList.remove(position + 1);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_move_down:
                categoryList.remove(position);
                categoryList.add(position + 1, m_selectedGram);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_move_beginning:
                categoryList.addFirst(m_selectedGram);
                categoryList.remove(position + 1);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_move_end:
                categoryList.addLast(m_selectedGram);
                categoryList.remove(position);
                JTApp.fireDataStoreUpdated();
                madeChanges = true;
                break;
            case R.id.context_menu_item_hide:
                m_selectedGram.setHidden(!m_selectedGram.isHidden());
                persistChanges(false);
                break;
            case R.id.context_menu_item_convert:
                Ideogram sub = new Ideogram(m_selectedGram);
                sub.setType(Type.Category);

                Ideogram parent = JTApp.getDataStore()
                        .getIdeogram(m_selectedGram.getParentId());
                parent.getChildren(true).add(sub);
                parent.getChildren(true).remove(m_selectedGram);
                JTApp.getDataStore().getIdeogramMap().remove(m_selectedGram);
                JTApp.getDataStore().getIdeogramMap().put(sub.getId(), sub);
                m_selectedGram = sub;
                persistChanges(false);
                break;
            case R.id.context_menu_backup_category:
                isBackupRestoreClicked = true;
                Date currentDate = new Date(System.currentTimeMillis());
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String formattedDate = dateFormat.format(currentDate);
                formattedDate = formattedDate.replaceAll("[^a-zA-Z0-9.-]", "_");

                m_ideogram = m_selectedGram;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "jabtalk_" + formattedDate + ".bak");
                startActivityForResult(intent, ACTIVITY_SELECT_BACKUP_PATH);
                break;
            case R.id.context_menu_restore_category:
                isBackupRestoreClicked = true;
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                this.isPartialRestoreClicked = true;
                intent.setType("*/*");
                startActivityForResult(intent, ACTIVITY_SELECT_RESTORE_FILE);
                break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        JTApp.removeDataStoreListener(m_adapter);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.manage_menu, menu);

        // Only show edit action item if current item is a category
        MenuItem edit = menu.findItem(R.id.menu_item_edit_ideogram);
        edit.setVisible(false);
        if (!m_ideogram.isRoot()) {
            edit.setVisible(true);
        }

        MenuItem backup = menu.findItem(R.id.menu_item_backup);
        backup.setVisible(true);
        if(!m_ideogram.isRoot() || (m_ideogram.isRoot() && m_ideogram.getChildren(true).size() == 0)) {
            backup.setVisible(false);
        }

        MenuItem restore = menu.findItem(R.id.menu_item_restore);
        restore.setVisible(m_ideogram.isRoot());

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        if (searchItem != null) {
            android.widget.SearchView searchView =
                    (android.widget.SearchView) searchItem.getActionView();
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    m_adapter.setSearchQuery(query);
                    return true;
                }
                @Override
                public boolean onQueryTextChange(String newText) {
                    m_adapter.setSearchQuery(newText);
                    return true;
                }
            });
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) { return true; }
                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    m_adapter.setSearchQuery(null);
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu != null) {
            MenuItem paste = menu.findItem(R.id.menu_item_paste);
            ClipBoard clip = JTApp.getClipBoard();
            paste.setVisible(false);

            if (!clip.isEmpty()) {
                Ideogram source = JTApp.getDataStore().getIdeogram(clip.getId());
                if (source != null
                        && (clip.getOperation() == Operation.COPY || JTApp.getDataStore()
                        .isSafeToMove(source, m_selectedGram))) {
                    paste.setVisible(true);
                }
            }

        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_item_history:
                intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_item_log:
                intent = new Intent(this, LogActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_item_backup:
                isBackupRestoreClicked = true;
                Date currentDate = new Date(System.currentTimeMillis());
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String formattedDate = dateFormat.format(currentDate);
                formattedDate = formattedDate.replaceAll("[^a-zA-Z0-9.-]", "_");

                m_selectedGram = JTApp.getDataStore().getRootCategory();
                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "jabtalk_" + formattedDate + ".bak");
                startActivityForResult(intent, ACTIVITY_SELECT_BACKUP_PATH);
                break;
            case R.id.menu_item_paste:
                try {
                    JTApp.getClipBoard().paste(m_ideogram.getId());
                    persistChanges(false);
                    invalidateOptionsMenu();
                } catch (JabException e) {
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                            getString(R.string.dialog_title_save_results));
                    getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
                    showDialog(DIALOG_GENERIC);
                }
                break;
            case R.id.menu_item_restore:
                isBackupRestoreClicked = true;
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                this.isPartialRestoreClicked = false;
                intent.setType("*/*");
                startActivityForResult(intent, ACTIVITY_SELECT_RESTORE_FILE);
                break;
            case R.id.menu_item_help:
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(JTApp.URL_SUPPORT));
                startActivity(i);
                break;
            case R.id.menu_item_preferences:
                intent = new Intent(this, PreferenceActivity.class);
                startActivityForResult(intent, ACTIVITY_RESULT_PREFERENCE);
                break;
            case R.id.menu_item_add_ideogram:
                showDialog(DIALOG_ACTION_ADD);
                break;
            case R.id.menu_item_edit_ideogram:
                editIdeogram(m_ideogram);
                break;
            case R.id.menu_item_sort:
                toggleSortMode();
                break;
            case R.id.menu_item_sync:
                showSyncDialog();
                break;
            case R.id.menu_item_stats:
                startActivity(new Intent(this, StatsActivity.class));
                break;
            case R.id.menu_item_share:
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_message));
                startActivity(Intent.createChooser(share,
                        getString(R.string.share_app_chooser_title)));
                break;
        }

        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case ACTIVITY_RESULT_PREFERENCE:
                    getIntent().putExtra(JTApp.INTENT_EXTRA_REFRESH, true);
                    setResult(RESULT_OK, getIntent());
                    break;
                case ACTIVITY_EDIT_IDEOGRAM:
                    boolean isDataDirty = data.getBooleanExtra(JTApp.INTENT_EXTRA_DIRTY_DATA,
                            false);
                    if (isDataDirty) {
                        madeChanges = true;
                        JTApp.fireDataStoreUpdated();
                        String id = getIntent().getStringExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID);
                        if (JTApp.getDataStore().getIdeogram(id) == null) {
                            exitActivity();
                        }
                    }
                    break;
                case ACTIVITY_SELECT_SYNC_FOLDER:
                    if (data != null && data.getData() != null) {
                        Uri treeUri = data.getData();
                        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        try {
                            getContentResolver().takePersistableUriPermission(treeUri, flags);
                            saveSyncFolderUri(treeUri);
                            performSyncBackup(true);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(this,
                                    getString(R.string.sync_toast_backup_failed, e.getMessage()),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                    break;
                case ACTIVITY_SELECT_BACKUP_PATH:
                    Uri uri = null;
                    if (data != null) {
                        uri = data.getData();
                        String backupName = getFileNameFromUri(uri);
                        if(!backupName.endsWith(".bak")) {
                            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Backup files must end with a .bak file extension.");
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                                    getString(R.string.dialog_title_backup_results));
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, getString(R.string.dialog_message_backup_invalid_filename));
                            showDialog(DIALOG_GENERIC);
                        } else {
                            backupData(uri);
                        }
                    }
                    break;
                case ACTIVITY_SELECT_RESTORE_FILE:
                    Uri restoreUri = null;
                    if (data != null) {
                        restoreUri = data.getData();
                        String backupName = getFileNameFromUri(restoreUri);
                        if(!backupName.endsWith(".bak")) {
                            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "Please choose a backup file with a .bak file extension.");
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                                    getString(R.string.dialog_title_restore_results));
                            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, getString(R.string.dialog_message_backup_invalid_filename));
                            showDialog(DIALOG_GENERIC);
                        } else {
                            this.restorePath = restoreUri;
                            showDialog(DIALOG_RESTORE_DATASTORE_PARTIAL);
                        }
                        
                    }
                    break;
            }
            
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        AlertDialog alert = null;
        AlertDialog.Builder builder;
        switch (id) {
            case DIALOG_ACTION_ADD:
                final CharSequence[] items = new CharSequence[2];
                items[ADD_CATEGORY] = getString(R.string.dialog_item_add_category);
                items[ADD_WORD] = getString(R.string.dialog_item_add_word);

                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.manage_activity_category_actions));
                builder.setItems(items, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int item) {
                        addIdeogram(item);
                    }
                });
                alert = builder.create();
                break;
            case DIALOG_DELETE_IDEOGRAM_CONFIRMATION:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_delete_confirmation));

                if (m_selectedGram.getType().equals(Type.Word)) {
                    builder.setMessage(R.string.dialog_message_delete_word);
                } else {
                    builder.setMessage(R.string.dialog_message_delete_category);
                }

                builder.setPositiveButton(R.string.button_yes,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                JTApp.getDataStore().deleteIdeogram(m_selectedGram.getId());
                                persistChanges(false);
                                invalidateOptionsMenu();
                                m_ideogram = JTApp.getDataStore().getRootCategory();
                                m_selectedGram = m_ideogram;
                            }
                        });
                builder.setNegativeButton(R.string.button_no,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_DELETE_IDEOGRAM_CONFIRMATION);
                            }
                        });
                alert = builder.create();
                break;

            case DIALOG_RESTORE_DATASTORE_FULL:
            case DIALOG_RESTORE_DATASTORE_PARTIAL:
                builder = new AlertDialog.Builder(this);
                LayoutInflater restoreInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                final View restoreLayout = restoreInflater.inflate(R.layout.backup_restore_dialog,
                        (ViewGroup) findViewById(R.id.restore_linear_layout));
                final CheckBox restoreOver = (CheckBox) restoreLayout.findViewById(R.id.chkRestoreOverData);
                final CheckBox keepStats = (CheckBox) restoreLayout.findViewById(R.id.chkKeepStats);

                final Button btnCancelRestore = (Button) restoreLayout.findViewById(R.id.btn_cancelRestore);
                btnCancelRestore.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dismissDialog(id);
                        finish();
                    }
                });

                final Uri restoreUri = this.restorePath;
                final Button btnRestore = (Button) restoreLayout.findViewById(R.id.btn_saveRestore);
                final boolean isPartialRestore = this.isPartialRestoreClicked;
                btnRestore.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dismissDialog(id);
                        if (restoreTask == null
                                || restoreTask.getStatus() == Status.FINISHED) {
                            restoreTask = new RestoreTask(restoreOver.isChecked(), isPartialRestore, keepStats.isChecked());
                            restoreTask.execute(restoreUri);
                        } else {
                            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR,
                                    "RestoreTask in invalid state");
                        }
                    }
                });

                builder.setTitle(R.string.dialog_title_restore_dataset);
                builder.setIcon(R.drawable.ic_action_restore);
                builder.setView(restoreLayout);
                builder.setCancelable(true);

                alert = builder.create();
                break;

            case DIALOG_EXIT_MANAGE_SCREEN:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.dialog_title_exit_warning));
                builder.setMessage(R.string.dialog_message_leave_screen);

                builder.setPositiveButton(R.string.button_yes,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                exitActivity();
                            }
                        });
                builder.setNegativeButton(R.string.button_no,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_EXIT_MANAGE_SCREEN);
                            }
                        });
                alert = builder.create();
                break;
            case DIALOG_ERROR:
                final Activity thisActity = this;
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getIntent().getStringExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE));
                builder.setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));

                builder.setPositiveButton(R.string.button_view_log,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_ERROR);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE);
                                Intent logIntent = new Intent(thisActity, LogActivity.class);
                                startActivity(logIntent);
                            }
                        });
                alert = builder.create();
                break;
            case DIALOG_GENERIC:
                builder = new AlertDialog.Builder(this);
                builder.setTitle(getIntent().getStringExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE));
                builder.setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));

                builder.setPositiveButton(R.string.button_ok,
                        new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {
                                dismissDialog(DIALOG_GENERIC);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE);
                                getIntent().removeExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE);
                            }
                        });
                alert = builder.create();
                break;

        }
        return alert;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (id) {
            case DIALOG_DELETE_IDEOGRAM_CONFIRMATION:
                if (m_selectedGram.getType().equals(Type.Word)) {
                    ((AlertDialog) dialog)
                            .setMessage(getString(R.string.dialog_message_delete_word));
                } else {
                    ((AlertDialog) dialog)
                            .setMessage(getString(R.string.dialog_message_delete_category));
                }
                break;
            case DIALOG_ERROR:
            case DIALOG_GENERIC:
                ((AlertDialog) dialog).setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_TITLE));
                ((AlertDialog) dialog).setMessage(getIntent().getStringExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE));
                break;
        }
    }

    private void showSyncDialog() {
        final Uri current = getSyncFolderUri();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.sync_dialog_title);

        if (current == null) {
            builder.setMessage(R.string.sync_status_no_folder);
            builder.setPositiveButton(R.string.sync_choose_folder,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            launchSyncFolderPicker();
                        }
                    });
            builder.setNegativeButton(R.string.button_cancel, null);
        } else {
            String display = current.getLastPathSegment ();
            if (display == null) display = current.toString ();
            builder.setMessage(getString(R.string.sync_status_set, display));
            builder.setPositiveButton(R.string.sync_backup_now,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            performSyncBackup(true);
                        }
                    });
            builder.setNeutralButton(R.string.sync_change_folder,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            launchSyncFolderPicker();
                        }
                    });
            builder.setNegativeButton(R.string.sync_disable,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            clearSyncFolder();
                        }
                    });
        }
        builder.show();
    }

    private void launchSyncFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, ACTIVITY_SELECT_SYNC_FOLDER);
    }

    private Uri getSyncFolderUri() {
        return com.jabstone.jabtalk.basic.SyncBackup.getFolderUri(this);
    }

    private void saveSyncFolderUri(Uri uri) {
        com.jabstone.jabtalk.basic.SyncBackup.saveFolderUri(this, uri);
    }

    private void clearSyncFolder() {
        com.jabstone.jabtalk.basic.SyncBackup.clearFolder(this);
    }

    private void performSyncBackup(boolean showToast) {
        if (madeChanges) {
            try {
                JTApp.getDataStore().saveDataStore();
                madeChanges = false;
            } catch (Exception ignored) {}
        }
        String err = com.jabstone.jabtalk.basic.SyncBackup.writeBackupIfConfigured(this);
        if (err == null) {
            if (showToast) {
                android.widget.Toast.makeText(this, R.string.sync_toast_backup_done,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } else if ("no_folder".equals(err) || "folder_lost".equals(err)) {
            if (showToast) {
                android.widget.Toast.makeText(this, R.string.sync_toast_folder_lost,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } else {
            android.widget.Toast.makeText(this,
                    getString(R.string.sync_toast_backup_failed, err),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void toggleSortMode() {
        m_sortMode = !m_sortMode;
        m_adapter.setSortMode(m_sortMode);
        int msg = m_sortMode
                ? R.string.manage_activity_sort_mode_on
                : R.string.manage_activity_sort_mode_off;
        android.widget.Toast.makeText(this, getString(msg), android.widget.Toast.LENGTH_SHORT).show();
        if (m_sortMode) {
            setTitle(getString(R.string.manage_activity_sort_mode_on));
        } else if (!m_ideogram.isRoot()) {
            setTitle(m_ideogram.getLabel());
        } else {
            setTitle(getString(R.string.manage_activity_title));
        }
    }

    protected RecyclerView getListView() {
        if (m_listView == null) {
            m_listView = (RecyclerView) findViewById(R.id.manage_list);
        }
        return m_listView;
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (uri.getScheme() != null && uri.getScheme().equals("file")) {
            fileName = new File(uri.getPath()).getName();
        }
        return fileName;
    }

    private void restoreProgressDialog() {
        if (restoreTask != null && restoreTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_restoring));
            progressDialog.show();
        }

        if (saveTask != null && saveTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_saving));
            progressDialog.show();
        }

        if (backupTask != null && backupTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressDialog.setMessage(getString(R.string.dialog_message_backup));
            progressDialog.show();
        }
    }

    private void expandCategory(Ideogram gram) {
        Intent intent = new Intent();
        intent.setClass(this, ManageActivity.class);
        intent.setAction(Intent.ACTION_EDIT);
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, gram.getId());
        intent.putExtra(JTApp.INTENT_EXTRA_TYPE, Type.Category);
        startActivityForResult(intent, ACTIVITY_EXPAND_CATEGORY);
    }



    private void backupData(Uri fileName) {
        if (backupTask == null || backupTask.getStatus() == Status.FINISHED) {
            backupTask = new BackupTask();
            backupTask.execute(fileName);
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "BackupTask in invalid state");
        }
    }

    private void editIdeogram(Ideogram gram) {
        Intent intent;
        intent = new Intent();
        intent.setClass(this, EditIdeogramActivity.class);
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, gram.getId());
        intent.putExtra(JTApp.INTENT_EXTRA_TYPE, gram.getType());
        intent.setAction(Intent.ACTION_EDIT);
        startActivityForResult(intent, ACTIVITY_EDIT_IDEOGRAM);
    }

    private void addIdeogram(int item) {
        Intent intent;
        intent = new Intent();
        intent.setClass(this, EditIdeogramActivity.class);
        intent.setAction(Intent.ACTION_INSERT);
        intent.putExtra(JTApp.INTENT_EXTRA_IDEOGRAM_ID, m_ideogram.getId());

        // Are we adding a word or category
        switch (item) {
            case ADD_CATEGORY:
                intent.putExtra(JTApp.INTENT_EXTRA_TYPE, Type.Category);
                break;
            case ADD_WORD:
                intent.putExtra(JTApp.INTENT_EXTRA_TYPE, Type.Word);
                break;
        }

        startActivityForResult(intent, ACTIVITY_EDIT_IDEOGRAM);
    }

    private void exitActivity() {
        setResult(RESULT_OK, getIntent());
        if (madeChanges) {
            persistChanges(true);
        } else {
            finish();
        }
    }

    private void persistChanges(boolean exitAfterSave) {
        if (saveTask == null || saveTask.getStatus() == Status.FINISHED) {
            saveTask = new SaveDataStoreTask();
            saveTask.execute(exitAfterSave);
        } else {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, "SaveDataStore Task in invalid state");
        }
    }

    private void lockScreenOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private void unlockScreenOrientation() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }


    private class RestoreTask extends AsyncTask<Uri, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        private boolean errorFlag = false;
        private boolean isOverWriteData = false;
        private boolean isPartialRestore = false;
        private boolean keepStats = false;
        private java.util.Map<String, java.util.HashMap<String, Integer>> capturedStats = null;

        public RestoreTask(boolean overWriteData, boolean isPartialRestore, boolean keepStats) {
            this.isOverWriteData = overWriteData;
            this.isPartialRestore = isPartialRestore;
            this.keepStats = keepStats;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            progressDialog.setMessage(getString(R.string.dialog_message_restoring));
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Restore:jabtalkPWL");
        }

        @Override
        protected Void doInBackground(Uri... params) {
            Uri fileName = params[0];
            try {
                java.util.HashMap<String, Integer> capturedSentencePhrases = null;
                java.util.HashMap<String, Integer> capturedSentenceBigrams = null;
                java.util.HashMap<String, Integer> capturedFreehandBigrams = null;
                if (keepStats) {
                    capturedStats = new java.util.HashMap<>();
                    for (java.util.Map.Entry<String, Ideogram> entry
                            : JTApp.getDataStore().getIdeogramMap().entrySet()) {
                        java.util.Map<String, Integer> plays = entry.getValue().getPlaysByMonth();
                        if (plays != null && !plays.isEmpty()) {
                            capturedStats.put(entry.getKey(), new java.util.HashMap<>(plays));
                        }
                    }
                    capturedSentencePhrases = new java.util.HashMap<>(JTApp.getDataStore().getSentencePhrases());
                    capturedSentenceBigrams = new java.util.HashMap<>(JTApp.getDataStore().getSentenceBigrams());
                    capturedFreehandBigrams = new java.util.HashMap<>(JTApp.getDataStore().getFreehandBigrams());
                }
                if (this.isPartialRestore | !isOverWriteData) {
                    JTApp.getDataStore().restorePartialDataStore(fileName, m_selectedGram, isOverWriteData);
                } else {
                    JTApp.getDataStore().restoreFullDataStore(fileName);
                }
                if (keepStats && capturedStats != null) {
                    for (java.util.Map.Entry<String, java.util.HashMap<String, Integer>> e
                            : capturedStats.entrySet()) {
                        Ideogram g = JTApp.getDataStore().getIdeogram(e.getKey());
                        if (g != null) {
                            g.setPlaysByMonth(e.getValue());
                        }
                    }
                    JTApp.getDataStore().getSentencePhrases().clear();
                    JTApp.getDataStore().getSentencePhrases().putAll(capturedSentencePhrases);
                    JTApp.getDataStore().getSentenceBigrams().clear();
                    JTApp.getDataStore().getSentenceBigrams().putAll(capturedSentenceBigrams);
                    JTApp.getDataStore().getFreehandBigrams().clear();
                    JTApp.getDataStore().getFreehandBigrams().putAll(capturedFreehandBigrams);
                    JTApp.getDataStore().saveDataStore();
                }
            } catch (Exception e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                        getString(R.string.dialog_title_restore_results));
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);

            unlockScreenOrientation();
            JTApp.getClipBoard().clear();
            progressDialog.dismiss();

            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }
            JTApp.fireDataStoreUpdated();

            if (errorFlag) {
                showDialog(DIALOG_ERROR);
            } else {
                getIntent().putExtra(JTApp.INTENT_EXTRA_CLEAR_MANAGE_STACK, true);
                setResult(RESULT_OK, getIntent());
                finish();
            }
        }
    }

    private class BackupTask extends AsyncTask<Uri, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        private boolean errorFlag = false;
        private Uri fileName = null;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            if (madeChanges) {
                progressDialog.setMessage(getString(R.string.dialog_message_saving));
            } else {
                progressDialog.setMessage(getString(R.string.dialog_message_backup));
            }
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Backup:jabtalkPWL");
        }

        @Override
        protected Void doInBackground(Uri... params) {
            fileName = params[0];
            try {
                if (madeChanges) {
                    JTApp.getDataStore().saveDataStore();
                }

                JTApp.getDataStore().backupDataStore(fileName, m_selectedGram);

            } catch (Exception e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);
            unlockScreenOrientation();
            progressDialog.dismiss();

            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }
            getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                    getString(R.string.dialog_title_backup_results));
            if (!errorFlag) {
                getIntent().putExtra(
                        JTApp.INTENT_EXTRA_DIALOG_MESSAGE,
                        getString(R.string.dialog_message_backup_success));
                showDialog(DIALOG_GENERIC);
            } else {
                showDialog(DIALOG_ERROR);
            }
        }

    }

    private class SaveDataStoreTask extends AsyncTask<Boolean, Void, Void> {

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock m_wakeLock;
        boolean exitAfterSave = false;
        private boolean errorFlag = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            lockScreenOrientation();
            progressDialog.setMessage(getString(R.string.dialog_message_saving));
            progressDialog.show();
            m_wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Save:jabtalkPWL");
        }

        @Override
        protected Void doInBackground(Boolean... params) {
            exitAfterSave = params[0];
            try {
                JTApp.getDataStore().saveDataStore();
            } catch (JabException e) {
                errorFlag = true;
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_TITLE,
                        getString(R.string.dialog_title_save_results));
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_MESSAGE, e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            super.onPostExecute(param);
            unlockScreenOrientation();
            progressDialog.dismiss();

            if (m_wakeLock.isHeld()) {
                m_wakeLock.release();
            }

            if (errorFlag) {
                getIntent().putExtra(JTApp.INTENT_EXTRA_DIALOG_FINISH_ON_DISMISS, true);
                showDialog(DIALOG_ERROR);
            } else {
                JTApp.fireDataStoreUpdated();
                if (getSyncFolderUri() != null) {
                    performSyncBackup(false);
                }
            }
            if (exitAfterSave) {
                finish();
            }
        }
    }

}
