package com.killerud.skydrive;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import com.killerud.skydrive.constants.ContextItems;
import com.killerud.skydrive.constants.SortCriteria;
import com.killerud.skydrive.dialogs.NewFolderDialog;
import com.killerud.skydrive.dialogs.PlayAudioDialog;
import com.killerud.skydrive.dialogs.RenameDialog;
import com.killerud.skydrive.dialogs.ViewPhotoDialog;
import com.killerud.skydrive.objects.*;
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
    private SkyDriveListAdapter mPhotoAdapter;
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

        mXloader = new XLoader(this);
        mPhotoAdapter = new SkyDriveListAdapter(this);
        setListAdapter(mPhotoAdapter);

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
        mFolderHierarchy.push("Home");
        updateFolderHierarchy(null);

        app.setCurrentBrowser(this);

        mActionBar = getSupportActionBar();

        if (savedInstanceState != null && savedInstanceState.containsKey(Constants.STATE_CURRENT_FOLDER))
        {
            mCurrentFolderId = savedInstanceState.getString(Constants.STATE_CURRENT_FOLDER);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(Constants.STATE_CURRENT_HIERARCHY))
        {
            mFolderHierarchy = new Stack<String>();
            String[] hierarchy = savedInstanceState.getStringArray(Constants.STATE_CURRENT_HIERARCHY);
            for (int i = 0; i < hierarchy.length; i++)
            {
                mFolderHierarchy.push(hierarchy[i]);
            }
            updateFolderHierarchy(null);

        }
        if (savedInstanceState != null && savedInstanceState.containsKey(Constants.STATE_PREVIOUS_FOLDERS))
        {
            mPreviousFolderIds = new Stack<String>();
            String[] folderIds = savedInstanceState.getStringArray(Constants.STATE_PREVIOUS_FOLDERS);
            for (int i = 0; i < folderIds.length; i++)
            {
                mPreviousFolderIds.push(folderIds[i]);
            }
        }

        startService(new Intent(this, CameraImageAutoUploadService.class));

        loadFolder(mCurrentFolderId);
    }

    /**
     * Sets up the List View click- and selection listeners
     *
     * @param lv The lisst view to set up
     */
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
                    boolean rowIsChecked = mPhotoAdapter.isSelected(position);
                    if (rowIsChecked)
                    {
                        /* It's checked. Time to make it not! */
                        mCurrentlySelectedFiles.remove(
                                ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                    }
                    else
                    {
                        mCurrentlySelectedFiles.add(
                                ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                    }
                    mPhotoAdapter.setChecked(position, !rowIsChecked);
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
                    mPhotoAdapter.setChecked(position, true);
                    mCurrentlySelectedFiles.add(
                            ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                }
                return true;
            }
        });
    }

    /**
     * Determines whether or not the activity was started from the sharing activity.
     * If yes, load the uploading layout and set the state to uploading. This makes
     * the activity display only folders.
     *
     * @param startIntent
     */
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
                    mXloader.uploadFile(mClient,
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
            /* If the previous folder stack is empty, exit the application */
            if (navigateBack())
            {
                return true;
            }
            else
            {
                return super.onKeyDown(keyCode, event);
            }
        }
        else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    private boolean navigateBack()
    {
        if (mPreviousFolderIds.isEmpty())
        {
            if (mActionBar != null)
            {
                mActionBar.setDisplayHomeAsUpEnabled(false);
            }

            return false;
        }

        loadFolder(mPreviousFolderIds.pop());

        if (!mFolderHierarchy.isEmpty())
        {
            mFolderHierarchy.pop();
            updateFolderHierarchy(null);
        }

        return true;
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

    /**
     * Sets up the listadapter for the browser, making sure the correct dialogs are opened for different file types
     */
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
                startActivity(startPhotoDialog);
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
                mXloader.downloadFiles(mClient, toDownload);
            }

            @Override
            public void visit(SkyDriveVideo video)
            {
                if (mUploadDialog) return;
                ArrayList<SkyDriveObject> toDownload = new ArrayList<SkyDriveObject>();
                toDownload.add(video);
                toDownload.trimToSize();
                mXloader.downloadFiles(mClient, toDownload);
            }

            @Override
            public void visit(SkyDriveAudio audio)
            {
                if (mUploadDialog) return;
                ((BrowserForSkyDriveApplication) getApplication()).setCurrentMusic(audio);
                Intent startAudioDialog = new Intent(getApplicationContext(), PlayAudioDialog.class);
                startActivity(startAudioDialog);
            }
        });
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        /* Checks to see if the Sharing activity started the browser. If yes, some changes are made. */
        Intent intentThatStartedMe = getIntent();
        if (intentThatStartedMe.getAction() != null &&
                intentThatStartedMe.getAction().equalsIgnoreCase("killerud.skydrive.SHARE_UPLOAD"))
        {
            if (intentThatStartedMe.getExtras().getString(UploadFileActivity.EXTRA_FILES_LIST) != null)
            {
                mXloader.uploadFile(mClient,
                        intentThatStartedMe.getStringArrayListExtra(UploadFileActivity.EXTRA_FILES_LIST),
                        mCurrentFolderId);
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        /* Checks to see if the progress notification was clicked and started the activity */
        if(mXloader != null){
            //No XLoader means no operations
            Intent intentThatStartedMe = getIntent();
            assert intentThatStartedMe.getAction() != null;
            ((NotificationManager) getSystemService(Service.NOTIFICATION_SERVICE))
                        .cancel(XLoader.NOTIFICATION_PROGRESS_ID);
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

    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
//---retrieve the information persisted earlier---
        String ID = savedInstanceState.getString("ID");
    }

    private void updateFolderHierarchy(SkyDriveObject folder)
    {
        String currentText = mFolderHierarchyView.getText().toString();
        String newText = "";

        if (folder == null)
        {
            if (!mFolderHierarchy.isEmpty())
            {
                setTitle(mFolderHierarchy.peek());
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < mFolderHierarchy.size(); i++)
            {
                builder.append(mFolderHierarchy.get(i));
                builder.append(">");
            }
            newText = builder.toString();
        }
        else
        {
            if (!mFolderHierarchy.peek().equals(folder.getName()))
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

    public void reloadFolder()
    {
        setSupportProgressBarIndeterminateVisibility(false);
        supportInvalidateOptionsMenu();
        loadFolder(mCurrentFolderId);
    }


    /**
     * Gets the folder content for displaying
     */
    private void loadFolder(String folderId)
    {
        if (folderId == null) return;

        setSupportProgressBarIndeterminateVisibility(true);

        if (mActionBar != null && !folderId.equalsIgnoreCase(HOME_FOLDER))
        {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }

        mCurrentFolderId = folderId;

        if (mCurrentlySelectedFiles != null) mCurrentlySelectedFiles.clear();
        ((SkyDriveListAdapter) getListAdapter()).clearChecked();

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

                ArrayList<SkyDriveObject> skyDriveObjs = mPhotoAdapter.getSkyDriveObjects();
                skyDriveObjs.clear();

                JSONArray data = result.optJSONArray(JsonKeys.DATA);
                for (int i = 0; i < data.length(); i++)
                {
                    SkyDriveObject skyDriveObj = SkyDriveObject.create(data.optJSONObject(i));
                    skyDriveObjs.add(skyDriveObj);
                }

                mPhotoAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(LiveOperationException exception, LiveOperation operation)
            {
                setSupportProgressBarIndeterminateVisibility(false);
                Log.e("ASE", exception.getMessage());
            }
        });
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
                navigateBack();
                return true;

            case R.id.newFolder:
                Intent startNewFolderDialog = new Intent(getApplicationContext(), NewFolderDialog.class);
                startNewFolderDialog.putExtra("killerud.skydrive.CURRENT_FOLDER", mCurrentFolderId);
                startActivity(startNewFolderDialog);
                supportInvalidateOptionsMenu();
                return true;
            case R.id.uploadFile:
                Intent intent = new Intent(getApplicationContext(), UploadFileActivity.class);
                startActivityForResult(intent, UploadFileActivity.PICK_FILE_REQUEST);
                supportInvalidateOptionsMenu();
                return true;
            case R.id.reload:
                loadFolder(mCurrentFolderId);
                supportInvalidateOptionsMenu();
                return true;
            case R.id.paste:
                setSupportProgressBarIndeterminateVisibility(true);
                mXloader.pasteFiles(mClient, mCopyCutFiles, mCurrentFolderId, mCutNotPaste);
                return true;
            case R.id.savedFiles:
                startActivity(new Intent(getApplicationContext(), FileBrowserActivity.class));
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


    /**
     * The SkyDrive list adapter. Determines the list item layout and display behaviour.
     */
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

        public void clearChecked()
        {
            mCheckedPositions = new SparseBooleanArray();
            notifyDataSetChanged();
        }

        public void checkAll()
        {
            for (int i = 0; i < mSkyDriveObjs.size(); i++)
            {
                mCheckedPositions.put(i, true);
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

                                }else{
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

                        /* Try to do the most popular types first to hopefully
                           limit the number of comparisons */
                        if (extension.equalsIgnoreCase("doc") ||
                                extension.equalsIgnoreCase("odt") ||
                                extension.equalsIgnoreCase("fodt") ||
                                extension.equalsIgnoreCase("docx") ||
                                extension.equalsIgnoreCase("odf"))
                        {
                            return R.drawable.office_document;
                        }
                        else if (extension.equalsIgnoreCase("ppt") ||
                                extension.equalsIgnoreCase("pps") ||
                                extension.equalsIgnoreCase("pptx") ||
                                extension.equalsIgnoreCase("ppsx") ||
                                extension.equalsIgnoreCase("odp") ||
                                extension.equalsIgnoreCase("fodp"))
                        {
                            return R.drawable.office_presentation;
                        }
                        else if (extension.equalsIgnoreCase("ods") ||
                                extension.equalsIgnoreCase("xls") ||
                                extension.equalsIgnoreCase("xlr") ||
                                extension.equalsIgnoreCase("xlsx") ||
                                extension.equalsIgnoreCase("ots"))
                        {
                            return R.drawable.office_spreadsheet;
                        }
                        else if (extension.equalsIgnoreCase("pdf"))
                        {
                            return R.drawable.document_pdf;
                        }
                        else if (extension.equalsIgnoreCase("zip") ||
                                extension.equalsIgnoreCase("rar") ||
                                extension.equalsIgnoreCase("gz") ||
                                extension.equalsIgnoreCase("bz2") ||
                                extension.equalsIgnoreCase("tar") ||
                                extension.equalsIgnoreCase("jar"))
                        {
                            return R.drawable.archive_generic;
                        }
                        else if (extension.equalsIgnoreCase("7z"))
                        {
                            return R.drawable.archive_sevenzip;
                        }
                        else if (extension.equalsIgnoreCase("torrent"))
                        {
                            return R.drawable.document_torrent;
                        }
                        else if (extension.equalsIgnoreCase("exe") ||
                                extension.equalsIgnoreCase("msi"))
                        {
                            return R.drawable.executable_generic;
                        }
                        else if (extension.equalsIgnoreCase("iso") ||
                                extension.equalsIgnoreCase("nrg") ||
                                extension.equalsIgnoreCase("img") ||
                                extension.equalsIgnoreCase("bin"))
                        {
                            return R.drawable.archive_disc_image;
                        }
                        else if (extension.equalsIgnoreCase("apk"))
                        {
                            return R.drawable.executable_apk;
                        }
                        else if (extension.equalsIgnoreCase("html") ||
                                extension.equalsIgnoreCase("htm"))
                        {
                            return R.drawable.text_html;
                        }
                        else if (extension.equalsIgnoreCase("css"))
                        {
                            return R.drawable.text_css;
                        }
                        else if (extension.equalsIgnoreCase("deb"))
                        {
                            return R.drawable.executable_deb;
                        }
                        else if (extension.equalsIgnoreCase("rpm"))
                        {
                            return R.drawable.executable_rpm;
                        }
                        else if (extension.equalsIgnoreCase("java") ||
                                extension.equalsIgnoreCase("class"))
                        {
                            return R.drawable.document_java;
                        }
                        else if (extension.equalsIgnoreCase("pl") ||
                                extension.equalsIgnoreCase("plc"))
                        {
                            return R.drawable.document_perl;
                        }
                        else if (extension.equalsIgnoreCase("php"))
                        {
                            return R.drawable.document_php;
                        }
                        else if (extension.equalsIgnoreCase("py"))
                        {
                            return R.drawable.document_python;
                        }
                        else if (extension.equalsIgnoreCase("rb"))
                        {
                            return R.drawable.document_ruby;
                        }
                    }
                    /* If all else fails */
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
            /* Lock the orientation while the action mode is active.

             * The actionmode as well as the selected items should be
             * saved in onSaveInstanceState, this is a pretty ugly hack.
             * */
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

            menu.add(ContextItems.MENU_TITLE_DOWNLOAD)
                    .setIcon(android.R.drawable.ic_menu_save)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(ContextItems.MENU_TITLE_COPY)
                    .setIcon(R.drawable.ic_menu_copy_holo_light)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(ContextItems.MENU_TITLE_CUT)
                    .setIcon(R.drawable.ic_menu_cut_holo_light)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(ContextItems.MENU_TITLE_RENAME)
                    .setIcon(android.R.drawable.ic_menu_edit)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(ContextItems.MENU_TITLE_DELETE)
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(ContextItems.MENU_TITLE_SELECT_ALL)
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
            if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_DOWNLOAD))
            {
                /* Downloads are done by calling recursively on a trimmed version of the same arraylist onComplete
                *  Create a clone so selected aren't cleared logically.
                */
                mXloader.downloadFiles(mClient, (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone());
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_COPY))
            {
                mCopyCutFiles = (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone();
                mCutNotPaste = false;

                Toast.makeText(getApplicationContext(), R.string.copyCutSelectedFiles, Toast.LENGTH_SHORT).show();
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_CUT))
            {
                mCopyCutFiles = (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone();
                mCutNotPaste = true;

                Toast.makeText(getApplicationContext(), R.string.copyCutSelectedFiles, Toast.LENGTH_SHORT).show();
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_DELETE))
            {
                final AlertDialog dialog = new AlertDialog.Builder(getSupportActionBar().getThemedContext()).create();
                dialog.setTitle("Delete files?");
                dialog.setIcon(R.drawable.warning_triangle);
                StringBuilder deleteMessage = new StringBuilder();
                deleteMessage.append("The following files will be deleted: \n\n");
                for (int i = 0; i < mCurrentlySelectedFiles.size(); i++)
                {
                    deleteMessage.append(mCurrentlySelectedFiles.get(i).getName());
                    deleteMessage.append("\n");
                }
                deleteMessage.append("Are you sure you want to do this?");

                dialog.setMessage(deleteMessage.toString());
                dialog.setButton("Yes", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        setSupportProgressBarIndeterminateVisibility(true);
                        mXloader.deleteFiles(mClient, (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone());
                        ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                        mCurrentlySelectedFiles.clear();
                        mode.finish();

                    }
                });
                dialog.setButton2("No!", new DialogInterface.OnClickListener()
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
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_RENAME))
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
                startActivity(startRenameDialog);

                ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                mode.finish();
                return true;
            }
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_SELECT_ALL))
            {
                mPhotoAdapter.checkAll();

                item.setTitle(ContextItems.MENU_TITLE_DESELECT_ALL);
                return true;
            }
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_DESELECT_ALL))
            {
                mPhotoAdapter.clearChecked();
                item.setTitle(ContextItems.MENU_TITLE_SELECT_ALL);
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
            /* Releases the orientation.

             * The actionmode as well as the selected items should be
             * saved in onSaveInstanceState, this is a pretty ugly hack.
             * */
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            mPhotoAdapter.clearChecked();
            mActionMode = null;
            mCurrentlySelectedFiles.clear();
            supportInvalidateOptionsMenu();
        }
    }
}

