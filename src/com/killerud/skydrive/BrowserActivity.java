package com.killerud.skydrive;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.*;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.constants.SortCriteria;
import com.killerud.skydrive.dialogs.NewFolderDialog;
import com.killerud.skydrive.dialogs.PlayAudioDialog;
import com.killerud.skydrive.dialogs.RenameDialog;
import com.killerud.skydrive.dialogs.ViewPhotoDialog;
import com.killerud.skydrive.objects.*;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Stack;


/**
 * User: William
 * Date: 25.04.12
 * Time: 15:07
 */
public class BrowserActivity extends SherlockListActivity
{
    /* Live Client and download/upload class */
    private LiveConnectClient mClient;
    private XLoader mXloader;

    /* Directory navigation */
    private SkyDriveListAdapter mSkyDriveListAdapter;
    private static final String HOME_FOLDER = "me/skydrive";
    private String mCurrentFolderId;
    private Stack<String> mPreviousFolderIds;
    private Stack<String> mFolderHierarchy;
    private TextView mFolderHierarchyView;
    private ActionBar mActionBar;

    /* File manipulation */
    private boolean mCutNotPaste;
    private ArrayList<SkyDriveObject> mCopyCutFiles;
    private ArrayList<SkyDriveObject> mCurrentlySelectedFiles;

    /*
     * Holder for the ActionMode, part of the contectual action bar
     * for selecting and manipulating items
     */
    private ActionMode mActionMode;

    /* Browser state. If this is set to true only folders will be shown
     * and a button starting an upload of a given file (passed through
     * an intent) to the current folder is added to the layout.
     *
     * Used by the share receiver activity.
     */
    private boolean mUploadDialog = false;
    private boolean mAllWifiOnly;
    private ConnectivityManager mConnectivityManager;

    /**
     * Handles the chosen file from the UploadFile dialog
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == UploadFileActivity.PICK_FILE_REQUEST)
        {
            if (resultCode == RESULT_OK)
            {
                XLoader loader = new XLoader(this);
                loader.uploadFile(mClient,
                        data.getStringArrayListExtra(UploadFileActivity.EXTRA_FILES_LIST),
                        mCurrentFolderId);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        mXloader = new XLoader(this);
        mSkyDriveListAdapter = new SkyDriveListAdapter(this);
        setListAdapter(mSkyDriveListAdapter);

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();

        mClient = app.getConnectClient();

        mCurrentlySelectedFiles = new ArrayList<SkyDriveObject>();
        mCopyCutFiles = new ArrayList<SkyDriveObject>();
        mPreviousFolderIds = new Stack<String>();
        mCurrentFolderId = HOME_FOLDER;

        determineBrowserStateAndLayout(getIntent());


        /* Makes sure that a local SkyDrive folder exists */
        File sdcard = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/");
        if (!sdcard.exists())
        {
            sdcard.mkdir();
        }

        ListView lv = getListView();
        setupListView(lv);

        mFolderHierarchyView = (TextView) findViewById(R.id.folder_hierarchy);
        mFolderHierarchy = new Stack<String>();
        mFolderHierarchy.push(getString(R.string.rootFolderTitle));
        updateFolderHierarchy(null);

        app.setCurrentBrowser(this);

        mActionBar = getSupportActionBar();

        if (savedInstanceState != null)
        {
            restoreSavedInstanceState(savedInstanceState);
        }

        loadFolder(mCurrentFolderId);
    }

    private void restoreSavedInstanceState(Bundle savedInstanceState)
    {
        assert savedInstanceState != null;

        if (savedInstanceState.containsKey(Constants.STATE_CURRENT_FOLDER))
        {
            mCurrentFolderId = savedInstanceState.getString(Constants.STATE_CURRENT_FOLDER);
        }

        if (savedInstanceState.containsKey(Constants.STATE_CURRENT_HIERARCHY))
        {
            mFolderHierarchy = new Stack<String>();
            String[] hierarchy = savedInstanceState.getStringArray(Constants.STATE_CURRENT_HIERARCHY);
            for (int i = 0; i < hierarchy.length; i++)
            {
                mFolderHierarchy.push(hierarchy[i]);
            }
            updateFolderHierarchy(null);
        }

        if (savedInstanceState.containsKey(Constants.STATE_PREVIOUS_FOLDERS))
        {
            mPreviousFolderIds = new Stack<String>();
            String[] folderIds = savedInstanceState.getStringArray(Constants.STATE_PREVIOUS_FOLDERS);
            for (int i = 0; i < folderIds.length; i++)
            {
                mPreviousFolderIds.push(folderIds[i]);
            }
        }

        if (savedInstanceState.containsKey(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
        {
            if (savedInstanceState.getBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
            {
                mActionMode = startActionMode(new SkyDriveActionMode());
            }
        }


        ((SkyDriveListAdapter) getListAdapter()).setCheckedPositions(((BrowserForSkyDriveApplication) getApplication())
                .getCurrentlyCheckedPositions());

    }


    private void setupListView(ListView lv)
    {
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (mActionMode != null)
                {
                    boolean rowIsChecked = mSkyDriveListAdapter.isSelected(position);
                    if (rowIsChecked)
                    {
                        mCurrentlySelectedFiles.remove(
                                ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                    }
                    else
                    {
                        mCurrentlySelectedFiles.add(
                                ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                    }
                    mSkyDriveListAdapter.setChecked(position, !rowIsChecked);
                }
                else
                {
                    handleListItemClick(parent, position);
                }
            }


        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l)
            {
                if (mActionMode == null)
                {
                    mActionMode = startActionMode(new SkyDriveActionMode());
                    mSkyDriveListAdapter.setChecked(position, true);
                    mCurrentlySelectedFiles.add(
                            ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                }
                return true;
            }
        });
    }


    private void determineBrowserStateAndLayout(Intent startIntent)
    {
        if (startIntent.getAction() != null && startIntent.getAction().equalsIgnoreCase("killerud.skydrive.UPLOAD_PICK_FOLDER"))
        {
            setContentView(R.layout.skydrive_upload_picker);
            Button uploadButton = (Button) findViewById(R.id.uploadToThisFolder);
            uploadButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    if (!connectionIsUnavailable()) mXloader.uploadFile(mClient,
                            getIntent().getStringArrayListExtra(UploadFileActivity.EXTRA_FILES_LIST),
                            mCurrentFolderId);
                    finish();
                }
            });

            mUploadDialog = true;
        }
        else
        {
            setContentView(R.layout.skydrive);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (canNavigateBack())
            {
                navigateBack();
                return true;
            }
            else
            {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                if (preferences.getBoolean(Constants.CONFIRM_EXIT, false))
                {
                    AlertDialog.Builder  builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.appExitConfirmationHeader)
                        .setCancelable(false)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                BrowserActivity.this.finish();
                            }
                        })
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();

                    return true;
                }else{
                    return super.onKeyDown(keyCode, event);
                }
            }
        }
        else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    private boolean canNavigateBack()
    {
        if (connectionIsUnavailable()) return false;

        if (mPreviousFolderIds.isEmpty())
        {
            if (mActionBar != null)
            {
                mActionBar.setDisplayHomeAsUpEnabled(false);
            }

            return false;
        }
        return true;
    }

    private void navigateBack() {
        loadFolder(mPreviousFolderIds.pop());

        if (!mFolderHierarchy.isEmpty())
        {
            mFolderHierarchy.pop();
            updateFolderHierarchy(null);
        }
    }

    private void pushPreviousFolderId(String folderId)
    {

        if (!mPreviousFolderIds.isEmpty()
                && mPreviousFolderIds.peek().equals(folderId))
        {
            return;
        }
        else
        {
            mPreviousFolderIds.push(folderId);
        }
    }

    private void handleListItemClick(AdapterView<?> parent, int position)
    {
        SkyDriveObject skyDriveObj = (SkyDriveObject) parent.getItemAtPosition(position);


        skyDriveObj.accept(new SkyDriveObject.Visitor()
        {
            @Override
            public void visit(SkyDriveAlbum album)
            {
                pushPreviousFolderId(mCurrentFolderId);
                updateFolderHierarchy(album);
                loadFolder(album.getId());
            }

            @Override
            public void visit(SkyDrivePhoto photo)
            {
                if (mUploadDialog) return;
                Intent startPhotoDialog = new Intent(getApplicationContext(), ViewPhotoDialog.class);
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_ID", photo.getId());
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_NAME", photo.getName());
                if (!connectionIsUnavailable()) startActivity(startPhotoDialog);
            }

            @Override
            public void visit(SkyDriveFolder folder)
            {
                pushPreviousFolderId(mCurrentFolderId);
                updateFolderHierarchy(folder);
                loadFolder(folder.getId());
            }

            @Override
            public void visit(SkyDriveFile file)
            {
                if (mUploadDialog) return;
                ArrayList<SkyDriveObject> toDownload = new ArrayList<SkyDriveObject>();
                toDownload.add(file);
                toDownload.trimToSize();
                if (!connectionIsUnavailable()) mXloader.downloadFiles(mClient, toDownload);
            }

            @Override
            public void visit(SkyDriveVideo video)
            {
                if (mUploadDialog) return;
                ((BrowserForSkyDriveApplication) getApplication()).setCurrentVideo(video);
                Intent startVideoDialog = new Intent(getApplicationContext(), PlayVideoActivity.class);
                if (!connectionIsUnavailable()) startActivity(startVideoDialog);
            }

            @Override
            public void visit(SkyDriveAudio audio)
            {
                if (mUploadDialog) return;
                ((BrowserForSkyDriveApplication) getApplication()).setCurrentMusic(audio);
                Intent startAudioDialog = new Intent(getApplicationContext(), PlayAudioDialog.class);
                if (!connectionIsUnavailable()) startActivity(startAudioDialog);
            }
        });
    }

    @Override
    protected void onStart()
    {
        super.onStart();

        Intent intentThatStartedMe = getIntent();
        if (intentThatStartedMe.getAction() != null &&
                intentThatStartedMe.getAction().equalsIgnoreCase("killerud.skydrive.SHARE_UPLOAD"))
        {
            uploadFilesSentThroughShareButton(intentThatStartedMe);
        }
    }

    private void uploadFilesSentThroughShareButton(Intent intentThatStartedMe)
    {
        if (intentThatStartedMe.getExtras().getString(UploadFileActivity.EXTRA_FILES_LIST) != null)
        {
            if (!connectionIsUnavailable()) mXloader.uploadFile(mClient,
                    intentThatStartedMe.getStringArrayListExtra(UploadFileActivity.EXTRA_FILES_LIST),
                    mCurrentFolderId);
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean("automatic_camera_upload", false))
        {
            startService(new Intent(this, CameraImageAutoUploadService.class));
        }
        else
        {
            stopService(new Intent(this, CameraImageAutoUploadService.class));
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putString(Constants.STATE_CURRENT_FOLDER, mCurrentFolderId);

        String[] hierarcy = new String[mFolderHierarchy.size()];
        for (int i = 0; i < hierarcy.length; i++)
        {
            hierarcy[i] = mFolderHierarchy.get(i);
        }

        String[] previous = new String[mPreviousFolderIds.size()];
        for (int i = 0; i < previous.length; i++)
        {
            previous[i] = mPreviousFolderIds.get(i);
        }

        savedInstanceState.putStringArray(Constants.STATE_CURRENT_HIERARCHY, hierarcy);
        savedInstanceState.putStringArray(Constants.STATE_PREVIOUS_FOLDERS, previous);

        if (mActionMode != null)
        {
            savedInstanceState.putBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON, true);
        }

        ((BrowserForSkyDriveApplication) getApplication())
                .setCurrentlyCheckedPositions(
                        ((SkyDriveListAdapter) getListAdapter())
                                .getCheckedPositions());
    }

    private void updateFolderHierarchy(SkyDriveObject folder)
    {
        String currentText = mFolderHierarchyView.getText().toString();
        String newText = "";

        if (folder == null)
        {
            newText = updateFolderHierarchyWhenNavigatingUp();
        }
        else
        {
            if (!mFolderHierarchy.empty() &&
                    !mFolderHierarchy.peek().equals(folder.getName()))
            {
                mFolderHierarchy.push(folder.getName());
                newText = currentText + ">" + mFolderHierarchy.peek();
                setTitle(folder.getName());
            }
            else
            {
                newText = currentText;
            }
        }
        mFolderHierarchyView.setText(newText);
    }

    private String updateFolderHierarchyWhenNavigatingUp()
    {
        if (!mFolderHierarchy.isEmpty())
        {
            setTitle(mFolderHierarchy.peek());
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < mFolderHierarchy.size(); i++)
        {
            if (i > 0) //If not root
            {
                builder.append(">");
            }
            builder.append(mFolderHierarchy.get(i));
        }
        return builder.toString();
    }

    public void reloadFolder()
    {
        try{
            setSupportProgressBarIndeterminateVisibility(false);
            supportInvalidateOptionsMenu();
            loadFolder(mCurrentFolderId);
        }catch (NullPointerException e)
        {
            /* At this point an XLoader object has attempted a reload of a BrowserActivity that no longer exists.
            * We do nothing in this case, as it is pointless to update a UI that doesn't exist.
            * */
        }
    }

    private void loadSharedFiles()
    {
        loadFolder("me/skydrive/shared");
    }

    private void loadFolder(String folderId)
    {
        if (folderId == null) return;
        if (connectionIsUnavailable())
        {
            return;
        }

        setSupportProgressBarIndeterminateVisibility(true);

        if (mActionBar != null && !mPreviousFolderIds.empty())
        {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }
        else
        {
            mActionBar.setDisplayHomeAsUpEnabled(false);
        }

        mCurrentFolderId = folderId;

        if (mCurrentlySelectedFiles != null) mCurrentlySelectedFiles.clear();

        if (mActionMode == null)
        {
            /* If there is an action mode, we are currently selecting files and the state has just changed.
             * No actual navigation has taken place, so we don't want to clear selected. */
            ((SkyDriveListAdapter) getListAdapter()).clearChecked();
        }

        if (mClient != null) mClient.getAsync(folderId + "/files?sort_by=" +
                SortCriteria.NAME + "&sort_order=" + SortCriteria.ASCENDING, new LiveOperationListener()
        {
            @Override
            public void onComplete(LiveOperation operation)
            {
                setSupportProgressBarIndeterminateVisibility(false);

                JSONObject result = operation.getResult();
                if (result.has(JsonKeys.ERROR))
                {
                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                    String message = error.optString(JsonKeys.MESSAGE);
                    String code = error.optString(JsonKeys.CODE);
                    Log.e("ASE", code + ": " + message);
                    return;
                }

                ArrayList<SkyDriveObject> skyDriveObjs = mSkyDriveListAdapter.getSkyDriveObjects();
                skyDriveObjs.clear();

                JSONArray data = result.optJSONArray(JsonKeys.DATA);
                for (int i = 0; i < data.length(); i++)
                {
                    SkyDriveObject skyDriveObj = SkyDriveObject.create(data.optJSONObject(i));
                    skyDriveObjs.add(skyDriveObj);
                }

                mSkyDriveListAdapter.notifyDataSetChanged();

                SparseBooleanArray checkedPositions = mSkyDriveListAdapter.getCheckedPositions();
                for (int i = 0; i < checkedPositions.size(); i++)
                {
                    int adapterPosition = checkedPositions.keyAt(i);
                    SkyDriveObject objectSelected = mSkyDriveListAdapter.getItem(adapterPosition);
                    mCurrentlySelectedFiles.add(objectSelected);
                }
            }


            @Override
            public void onError(LiveOperationException exception, LiveOperation operation)
            {
                setSupportProgressBarIndeterminateVisibility(false);
                Log.e("ASE", exception.getMessage());
            }
        });
    }

    private boolean connectionIsUnavailable()
    {
        getPreferences();
        boolean unavailable = (mAllWifiOnly &&
                (mConnectivityManager.getActiveNetworkInfo().getType()
                        != ConnectivityManager.TYPE_WIFI));
        if (unavailable)
        {
            Toast.makeText(this, R.string.noInternetConnection, Toast.LENGTH_LONG).show();
        }
        return unavailable;
    }

    private void getPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
        mAllWifiOnly = preferences.getBoolean("limit_all_to_wifi", false);
    }

    /* Menus and AB */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if (mCopyCutFiles.size() > 0)
        {
            menu.getItem(3).setVisible(true); //Paste
        }
        else if (mCopyCutFiles.size() < 1)
        {
            menu.getItem(3).setVisible(false); //Paste
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.browser_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                canNavigateBack();
                return true;

            case R.id.newFolder:
                Intent startNewFolderDialog = new Intent(getApplicationContext(), NewFolderDialog.class);
                startNewFolderDialog.putExtra("killerud.skydrive.CURRENT_FOLDER", mCurrentFolderId);
                if (!connectionIsUnavailable()) startActivity(startNewFolderDialog);
                supportInvalidateOptionsMenu();
                return true;
            case R.id.uploadFile:
                Intent intent = new Intent(getApplicationContext(), UploadFileActivity.class);
                if (!connectionIsUnavailable()) startActivityForResult(intent, UploadFileActivity.PICK_FILE_REQUEST);
                supportInvalidateOptionsMenu();
                return true;
            case R.id.reload:
                loadFolder(mCurrentFolderId);
                supportInvalidateOptionsMenu();
                return true;
            case R.id.paste:
                setSupportProgressBarIndeterminateVisibility(true);
                if (!connectionIsUnavailable())
                    mXloader.pasteFiles(mClient, mCopyCutFiles, mCurrentFolderId, mCutNotPaste);
                return true;
            case R.id.sharedFiles:
                loadSharedFiles();
                return true;
            case R.id.savedFiles:
                startActivity(new Intent(getApplicationContext(), FileBrowserActivity.class));
                return true;
            case R.id.settings:
                startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                return true;
            case R.id.signOut:
                setSupportProgressBarIndeterminateVisibility(true);
                ((BrowserForSkyDriveApplication) getApplication()).getAuthClient().logout(new LiveAuthListener()
                {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);
                        Toast.makeText(getApplicationContext(), R.string.loggedOut, Toast.LENGTH_SHORT);
                        startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                        finish();
                        Log.e(Constants.LOGTAG, "Logged out. Status is " + status + ".");
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);
                        startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                        finish();
                        Log.e(Constants.LOGTAG, exception.getMessage());
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }



    private class SkyDriveListAdapter extends BaseAdapter
    {
        private final LayoutInflater mInflater;
        private final ArrayList<SkyDriveObject> mSkyDriveObjs;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mPosition;

        public SkyDriveListAdapter(Context context)
        {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSkyDriveObjs = new ArrayList<SkyDriveObject>();
            mCheckedPositions = new SparseBooleanArray();

        }

        /**
         * @return The underlying array of the class. If changes are made to this object and you
         *         want them to be seen, call {@link #notifyDataSetChanged()}.
         */
        public ArrayList<SkyDriveObject> getSkyDriveObjects()
        {
            return mSkyDriveObjs;
        }

        @Override
        public int getCount()
        {
            return mSkyDriveObjs.size();
        }

        public boolean isSelected(int pos)
        {
            return mCheckedPositions.get(pos, false);
        }

        public void setChecked(int pos, boolean checked)
        {
            mCheckedPositions.put(pos, checked);
            notifyDataSetChanged();
        }


        public void setCheckedPositions(SparseBooleanArray checkedPositions)
        {
            this.mCheckedPositions = checkedPositions;
            notifyDataSetChanged();
        }

        public SparseBooleanArray getCheckedPositions()
        {
            return this.mCheckedPositions;
        }

        public void clearChecked()
        {
            mCheckedPositions = new SparseBooleanArray();
            mCurrentlySelectedFiles = new ArrayList<SkyDriveObject>();
            notifyDataSetChanged();
        }

        public void checkAll()
        {
            for (int i = 0; i < mSkyDriveObjs.size(); i++)
            {
                mCheckedPositions.put(i, true);
                mCurrentlySelectedFiles.add(mSkyDriveObjs.get(i));
            }
            notifyDataSetChanged();
        }

        @Override
        public SkyDriveObject getItem(int position)
        {
            return mSkyDriveObjs.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent)
        {
            SkyDriveObject skyDriveObj = getItem(position);
            mView = convertView != null ? convertView : null;
            mPosition = position;
            skyDriveObj.accept(new SkyDriveObject.Visitor()
            {
                @Override
                public void visit(SkyDriveVideo video)
                {

                    if (mView == null)
                    {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.video_x_generic);
                    setName(video);
                    setSelected(isSelected(mPosition));
                }

                @Override
                public void visit(SkyDriveFile file)
                {

                    if (mView == null)
                    {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(determineFileIcon(file));
                    setName(file);
                    setSelected(isSelected(mPosition));
                }

                @Override
                public void visit(SkyDriveFolder folder)
                {
                    if (mView == null)
                    {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.folder);
                    setName(folder);
                    setSelected(isSelected(mPosition));
                }

                @Override
                public void visit(SkyDriveAlbum album)
                {
                    if (mView == null)
                    {
                        mView = inflateNewSkyDriveListItem();
                    }
                    setIcon(R.drawable.folder_image);
                    setName(album);
                    setSelected(isSelected(mPosition));
                }

                @Override
                public void visit(SkyDriveAudio audio)
                {
                    if (mView == null)
                    {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.audio_x_generic);
                    setName(audio);
                    setSelected(isSelected(mPosition));
                }

                @Override
                public void visit(final SkyDrivePhoto photo)
                {
                    if (mView == null)
                    {
                        mView = inflateNewSkyDriveListItem();
                    }
                    final View view = mView;
                    // Since we are doing async calls and mView is constantly changing,
                    // we need to hold on to this reference.

                    setIcon(R.drawable.image_x_generic);
                    setName(photo);
                    setSelected(isSelected(mPosition));


                    if (!setThumbnailFromCacheIfExists(view, photo))
                    {
                        mClient.downloadAsync(photo.getId() + "/picture?type=thumbnail", new LiveDownloadOperationListener()
                        {
                            @Override
                            public void onDownloadProgress(int totalBytes,
                                                           int bytesRemaining,
                                                           LiveDownloadOperation operation)
                            {
                            }

                            @Override
                            public void onDownloadFailed(LiveOperationException exception,
                                                         LiveDownloadOperation operation)
                            {
                                Log.i(Constants.LOGTAG, "Thumb download failed for " + photo.getName()
                                        + ". " + exception.getMessage());
                                setIcon(R.drawable.image_x_generic);
                            }

                            @Override
                            public void onDownloadCompleted(LiveDownloadOperation operation)
                            {
                                Log.i(Constants.LOGTAG, "Thumb loaded from web for image " + photo.getName());
                                if (Build.VERSION.SDK_INT >= 11)
                                {
                                    try
                                    {
                                        AsyncTask task = new AsyncTask<Object, Void, Bitmap>()
                                        {

                                            @Override
                                            protected Bitmap doInBackground(Object... inputStreams)
                                            {
                                                Bitmap bm = null;

                                                try
                                                {
                                                    bm = BitmapFactory.decodeStream(
                                                            ((LiveDownloadOperation) inputStreams[0]).getStream());
                                                } catch (Exception e)
                                                {
                                                    Log.i(Constants.LOGTAG, "doInBackground failed for "
                                                            + photo.getName() + ". " + e.getMessage());
                                                }

                                                return bm;
                                            }

                                            protected void onPostExecute(Bitmap bm)
                                            {
                                                File cacheFolder = new File(Environment.getExternalStorageDirectory()
                                                        + "/Android/data/com.killerud.skydrive/thumbs/");

                                                if (!cacheFolder.exists())
                                                {
                                                    cacheFolder.mkdirs();
                                                    /*
                                                    VERY important that this is mkdirS, not mkdir,
                                                    or just the last folder will be created, which won't
                                                    work with the other folders absent...
                                                    */
                                                }

                                                File thumb = new File(cacheFolder, photo.getName());
                                                OutputStream out;
                                                try
                                                {
                                                    out = new BufferedOutputStream(new FileOutputStream(thumb));
                                                    bm.compress(Bitmap.CompressFormat.PNG, 85, out);
                                                    out.flush();
                                                    out.close();
                                                    Log.i(Constants.LOGTAG, "Thumb cached for image " + photo.getName());
                                                } catch (Exception e)
                                                {
                                                    /* Couldn't save thumbnail. No biggie.
                                                   * Exception here rather than IOException
                                                   * doe to rare cases of crashes when activity
                                                   * loses focus during load.
                                                   * */
                                                    Log.e(Constants.LOGTAG, "Could not cache thumbnail for " + photo.getName()
                                                            + ". " + e.toString());
                                                }

                                                ImageView imgView = (ImageView) view.findViewById(R.id.skyDriveItemIcon);
                                                imgView.setImageBitmap(bm);
                                            }

                                        };

                                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, operation);
                                    } catch (Exception e)
                                    {
                                        Log.i(Constants.LOGTAG, "OnDownloadCompleted failed for "
                                                + photo.getName() + ". " + e.getMessage());

                                        setIcon(R.drawable.image_x_generic);

                                    }

                                }
                                else
                                {
                                    Bitmap bm = BitmapFactory.decodeStream(operation.getStream());

                                    File cacheFolder = new File(Environment.getExternalStorageDirectory()
                                            + "/Android/data/com.killerud.skydrive/thumbs/");

                                    if (!cacheFolder.exists())
                                    {
                                        cacheFolder.mkdirs();
                                        /*
                                        VERY important that this is mkdirS, not mkdir,
                                        or just the last folder will be created, which won't
                                        work with the other folders absent...
                                        */
                                    }

                                    File thumb = new File(cacheFolder, photo.getName());
                                    try
                                    {
                                        FileOutputStream fileOut = new FileOutputStream(thumb);
                                        bm.compress(Bitmap.CompressFormat.PNG, 85, fileOut);
                                        fileOut.flush();
                                        fileOut.close();
                                        Log.i(Constants.LOGTAG, "Thumb cached for image " + photo.getName());
                                    } catch (Exception e)
                                    {
                                        /* Couldn't save thumbnail. No biggie.
                                       * Exception here rather than IOException
                                       * doe to rare cases of crashes when activity
                                       * loses focus during load.
                                       * */
                                        Log.e(Constants.LOGTAG, "Could not cache thumbnail for " + photo.getName()
                                                + ". " + e.getMessage());
                                    }

                                    ImageView imgView = (ImageView) view.findViewById(R.id.skyDriveItemIcon);
                                    imgView.setImageBitmap(bm);
                                }

                            }

                        });
                    }
                }


                private void setName(SkyDriveObject skyDriveObj)
                {
                    TextView tv = (TextView) mView.findViewById(R.id.nameTextView);
                    tv.setText(skyDriveObj.getName());
                }

                private View inflateNewSkyDriveListItem()
                {
                    return mInflater.inflate(R.layout.skydrive_list_item, parent, false);
                }

                private void setIcon(int iconResId)
                {
                    ImageView img = (ImageView) mView.findViewById(R.id.skyDriveItemIcon);
                    img.setImageResource(iconResId);
                }

                private void setSelected(boolean checked)
                {
                    if (checked)
                    {
                        mView.setBackgroundResource(R.color.HightlightBlue);
                    }
                    else
                    {
                        mView.setBackgroundResource(android.R.color.white);
                    }
                }

                private boolean setThumbnailFromCacheIfExists(View view, SkyDrivePhoto photo)
                {
                    /* Store stuff in app data folder, so it is deleted on uninstall */
                    File cacheFolder = new File(Environment.getExternalStorageDirectory()
                            + "/Android/data/com.killerud.skydrive/thumbs/");

                    if (!cacheFolder.exists())
                    {
                        cacheFolder.mkdir();
                        /* Directory didn't exist, the thumbnail sure as hell doesn't */
                        return false;
                    }

                    File thumb = new File(cacheFolder, photo.getName());
                    if (thumb.exists())
                    {
                        ((ImageView) view.findViewById(R.id.skyDriveItemIcon))
                                .setImageBitmap(BitmapFactory.decodeFile(thumb.getPath()));
                        Log.i(Constants.LOGTAG, "Thumb loaded from cache for image " + photo.getName());
                        return true;
                    }
                    else
                    {
                        return false;
                    }
                }

                private int determineFileIcon(SkyDriveFile file)
                {
                    int index = file.getName().lastIndexOf(".");
                    if (index != -1)
                    {
                        /* Starting from the index includes the dot, so we add one  */
                        String extension = file.getName().substring(index + 1,
                                file.getName().length());

                        return IOUtil.determineFileTypeDrawable(extension);
                    }

                    return R.drawable.text_x_preview;
                }

            });

            return mView;
        }
    }

    private class SkyDriveActionMode implements com.actionbarsherlock.view.ActionMode.Callback
    {

        @Override
        public boolean onCreateActionMode(com.actionbarsherlock.view.ActionMode mode, Menu menu)
        {

            menu.add(getString(R.string.download))
                    .setIcon(R.drawable.ic_menu_save)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.copy))
                    .setIcon(R.drawable.ic_menu_copy_holo_light)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.cut))
                    .setIcon(R.drawable.ic_menu_cut_holo_light)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.rename))
                    .setIcon(R.drawable.ic_menu_edit)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.delete))
                    .setIcon(R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.selectAll))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(com.actionbarsherlock.view.ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onActionItemClicked(final com.actionbarsherlock.view.ActionMode mode, MenuItem item)
        {
            String title = item.getTitle().toString();
            if (title.equalsIgnoreCase(getString(R.string.download)))
            {
                /* Downloads are done by calling recursively on a trimmed version of the same arraylist onComplete
                *  Create a clone so selected aren't cleared logically.
                */
                if (!connectionIsUnavailable())
                    mXloader.downloadFiles(mClient, (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone());

                ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                mCurrentlySelectedFiles.clear();
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.copy)))
            {
                mCopyCutFiles = (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone();
                mCutNotPaste = false;

                Toast.makeText(getApplicationContext(), R.string.copyCutSelectedFiles, Toast.LENGTH_SHORT).show();

                ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                mCurrentlySelectedFiles.clear();
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.cut)))
            {
                mCopyCutFiles = (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone();
                mCutNotPaste = true;

                Toast.makeText(getApplicationContext(), R.string.copyCutSelectedFiles, Toast.LENGTH_SHORT).show();

                ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                mCurrentlySelectedFiles.clear();
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.delete)))
            {
                final AlertDialog dialog = new AlertDialog.Builder(getSupportActionBar().getThemedContext()).create();
                dialog.setTitle(getString(R.string.deleteConfirmationTitle));
                dialog.setIcon(R.drawable.warning_triangle);
                StringBuilder deleteMessage = new StringBuilder();
                deleteMessage.append(getString(R.string.deleteConfirmationBody));
                for (int i = 0; i < mCurrentlySelectedFiles.size(); i++)
                {
                    deleteMessage.append(mCurrentlySelectedFiles.get(i).getName());
                    deleteMessage.append("\n");
                }
                deleteMessage.append(getString(R.string.deleteConfirmationQuestion));

                dialog.setMessage(deleteMessage.toString());
                dialog.setButton(getString(R.string.yes), new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        setSupportProgressBarIndeterminateVisibility(true);
                        if (!connectionIsUnavailable())
                            mXloader.deleteFiles(mClient, (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone());
                        ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                        mCurrentlySelectedFiles.clear();
                        mode.finish();

                    }
                });
                dialog.setButton2(getString(R.string.no), new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialog.dismiss();
                    }
                });
                dialog.show();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.rename)))
            {
                Intent startRenameDialog = new Intent(getSupportActionBar().getThemedContext(), RenameDialog.class);
                ArrayList<String> fileIds = new ArrayList<String>();
                ArrayList<String> fileNames = new ArrayList<String>();
                for (int i = 0; i < mCurrentlySelectedFiles.size(); i++)
                {
                    fileIds.add(mCurrentlySelectedFiles.get(i).getId());
                    fileNames.add(mCurrentlySelectedFiles.get(i).getName());
                }
                startRenameDialog.putExtra(RenameDialog.EXTRAS_FILE_IDS, fileIds);
                startRenameDialog.putExtra(RenameDialog.EXTRAS_FILE_NAMES, fileNames);
                if (!connectionIsUnavailable()) startActivity(startRenameDialog);
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.selectAll)))
            {
                ((SkyDriveListAdapter) getListAdapter()).checkAll();
                item.setTitle(getString(R.string.selectNone));
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.selectNone)))
            {
                ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                mCurrentlySelectedFiles.clear();
                item.setTitle(getString(R.string.selectAll));
                return true;
            }
            else
            {
                return false;
            }
        }


        @Override
        public void onDestroyActionMode(com.actionbarsherlock.view.ActionMode mode)
        {
            ((SkyDriveListAdapter) getListAdapter()).clearChecked();
            mCurrentlySelectedFiles.clear();
            mActionMode = null;
            supportInvalidateOptionsMenu();
        }
    }
}

