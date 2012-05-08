package com.killerud.skydrive;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.killerud.skydrive.dialogs.NewFolderDialog;
import com.killerud.skydrive.dialogs.PlayAudioDialog;
import com.killerud.skydrive.dialogs.ViewPhotoDialog;
import com.killerud.skydrive.objects.*;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.killerud.skydrive.dialogs.UploadFileDialog;
import com.microsoft.live.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Stack;


/**
 * User: William
 * Date: 25.04.12
 * Time: 15:07
 */
public class BrowserActivity extends ListActivity {
    private LiveConnectClient mClient;

    /* Dialogs and notification */
    private NotificationManager mNotificationManager;
    private static final int DIALOG_DOWNLOAD_ID = 0;
    private SkyDriveListAdapter mPhotoAdapter;

    /* Directory navigation */
    private static final String HOME_FOLDER = "me/skydrive";
    private String mCurrentFolderId;
    private Stack<String> mPreviousFolderIds;

    /* File manipulation */
    private ArrayList<View> mSelectedView;
    private boolean mCutNotPaste;
    private IOUtil mIOUtil;

    private ArrayList<View> mViewsToBeCopiedOrMoved;
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




    /* Creates a download progress dialog while downloading the given file (fetched from the bundle) */
    @Override
    protected Dialog onCreateDialog(final int id, final Bundle bundle) {
        Dialog dialog = null;
        switch (id) {
            case DIALOG_DOWNLOAD_ID: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.download))
                        .setMessage(getString(R.string.fileWillBeDownloaded))
                        .setPositiveButton(getString(R.string.ok), new Dialog.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {


                                final String fileId = bundle.getString(JsonKeys.ID);
                                final String name = bundle.getString(JsonKeys.NAME);
                                final ProgressDialog progressDialog =
                                        new ProgressDialog(BrowserActivity.this);
                                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                progressDialog.setMessage(getString(R.string.downloading));
                                progressDialog.setCancelable(true);


                                final File file = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", name);
                                if (file.exists()) {
                                builder.setTitle(R.string.fileAlreadySaved)
                                        .setMessage(getString(R.string.fileAlreadySavedMessage))
                                        .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {

                                            downloadFile(progressDialog, fileId, file);

                                        }
                                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            finish();
                                        }
                                    }).show();
                                }else{
                                     downloadFile(progressDialog, fileId, file);
                                }
                            }

                            private void downloadFile(final ProgressDialog progressDialog, String fileId, final File file) {
                                progressDialog.show();

                                final LiveDownloadOperation operation =
                                        mClient.downloadAsync(fileId + "/content",
                                                file,
                                                new LiveDownloadOperationListener() {
                                                    @Override
                                                    public void onDownloadProgress(int totalBytes,
                                                                                   int bytesRemaining,
                                                                                   LiveDownloadOperation operation) {
                                                        int percentCompleted =
                                                                computePercentCompleted(totalBytes, bytesRemaining);

                                                        progressDialog.setProgress(percentCompleted);
                                                    }

                                                    @Override
                                                    public void onDownloadFailed(LiveOperationException exception,
                                                                                 LiveDownloadOperation operation) {
                                                        progressDialog.dismiss();
                                                        showToast(getString(R.string.downloadError));
                                                    }

                                                    @Override
                                                    public void onDownloadCompleted(LiveDownloadOperation operation) {
                                                        progressDialog.dismiss();

                                                        showFileXloadedNotification(file, true);
                                                    }
                                                });

                                progressDialog.setOnCancelListener(new OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        operation.cancel();
                                    }
                                });
                            }
                        }).setNegativeButton(getString(R.string.cancel), new Dialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                dialog = builder.create();
                break;
            }
        }

        if (dialog != null) {
            dialog.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    removeDialog(id);
                }
            });
        }

        return dialog;
    }

    /** Pings the user with a notification that a file was either downloaded or
     * uploaded, depending on the given boolean. True = download, false = up.
     *
     * @param file The file to be displayed
     * @param download Whether or not this is a download notification
     */
    private void showFileXloadedNotification(File file, boolean download) {
        int icon = R.drawable.notification_icon;
        CharSequence tickerText = file.getName() + " saved " + (download ? "from" : "to") + "SkyDrive!";
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.appName);
        CharSequence contentText = file.getName() + " was saved to your " + (download ? "phone" : "SkyDrive") + "!";

        Intent notificationIntent;

        if(download){
            Uri path = Uri.fromFile(file);
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setDataAndType(path, mIOUtil.findMimeTypeOfFile(file));
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Notification.FLAG_AUTO_CANCEL);
        }else{
            notificationIntent = new Intent(context, BrowserActivity.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, notification);
    }

    /**
     *  Handles the chosen file from the UploadFile dialog
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UploadFileDialog.PICK_FILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                uploadFile(data);
            }
        }
    }

    /**
     * Handles the actual uploading of a file. Gets the filename from the passed intent.
     */
    private void uploadFile(Intent data) {
        String filePath = data.getStringExtra(UploadFileDialog.EXTRA_FILE_PATH);
        if (TextUtils.isEmpty(filePath)) {
            return;
        }

        final File file = new File(filePath);

        final ProgressDialog uploadProgressDialog =
                new ProgressDialog(BrowserActivity.this);
        uploadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        uploadProgressDialog.setMessage(getString(R.string.uploading));
        uploadProgressDialog.setCancelable(true);
        uploadProgressDialog.show();

        final LiveOperation uploadOperation =
                mClient.uploadAsync(mCurrentFolderId,
                        file.getName(),
                        file,
                        new LiveUploadOperationListener() {
                            @Override
                            public void onUploadProgress(int totalBytes,
                                                         int bytesRemaining,
                                                         LiveOperation operation) {
                                int percentCompleted = computePercentCompleted(totalBytes, bytesRemaining);

                                uploadProgressDialog.setProgress(percentCompleted);
                            }

                            @Override
                            public void onUploadFailed(LiveOperationException exception,
                                                       LiveOperation operation) {
                                uploadProgressDialog.dismiss();
                                Toast.makeText(getApplicationContext(), R.string.uploadError, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onUploadCompleted(LiveOperation operation) {
                                uploadProgressDialog.dismiss();

                                JSONObject result = operation.getResult();
                                if (result.has(JsonKeys.ERROR)) {
                                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                    String message = error.optString(JsonKeys.MESSAGE);
                                    String code = error.optString(JsonKeys.CODE);
                                    showToast(getString(R.string.uploadError));
                                    return;
                                }
                                showFileXloadedNotification(file, false);
                                if(mUploadDialog){
                                    finish();
                                }else{
                                    loadFolder(mCurrentFolderId);
                                }
                            }
                        });

        uploadProgressDialog.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                uploadOperation.cancel();
            }
        });
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent startIntent = getIntent();
        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();

        mSelectedView = new ArrayList<View>();
        mIOUtil = new IOUtil();
        mClient = app.getConnectClient();


        determineBrowserStateAndLayout(startIntent);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mPreviousFolderIds = new Stack<String>();

        /* Makes sure that a local SkyDrive folder exists */
        File sdcard = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/");
        if (!sdcard.exists()) {
            sdcard.mkdir();
        }

        ListView lv = getListView();
        setupListView(lv);

        mPhotoAdapter = new SkyDriveListAdapter(this);
        setListAdapter(mPhotoAdapter);


    }

    /**
     *  Sets up the List View click- and selection listeners
     *
     * @param lv The lisst view to set up
     */
    private void setupListView(ListView lv) {
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setupBrowserAdapterOnClick(parent, position);
            }


        });
        if(Build.VERSION.SDK_INT < 10){
            lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> av, View v, int
                        pos, long id) {
                    if (mActionMode != null) {
                        return false;
                    }
                    // Start the CAB using the ActionMode.Callback defined above
                    mActionMode = startActionMode(new ActionMode.Callback() {

                        // Called when the action mode is created; startActionMode() was called
                        @Override
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            // Inflate a menu resource providing context menu items
                            MenuInflater inflater = mode.getMenuInflater();
                            inflater.inflate(R.menu.context_menu, menu);
                            return true;
                        }

                        // Called each time the action mode is shown. Always called after onCreateActionMode, but
                        // may be called multiple times if the mode is invalidated.
                        @Override
                        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                            return false; // Return false if nothing is done
                        }

                        // Called when the user selects a contextual menu item
                        @Override
                        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                            switch (item.getItemId()) {
                                //case R.id.menu_share:
                                  //  shareCurrentItem();
                                    //mode.finish(); // Action picked, so close the CAB
                                   // return true;
                                //TODO
                                default:
                                    return false;
                            }
                        }

                        // Called when the user exits the action mode. Changes have been made, so reload folder.
                        @Override
                        public void onDestroyActionMode(ActionMode mode) {
                            mActionMode = null;
                            loadFolder(mCurrentFolderId);
                        }
                    });
                    v.setSelected(true);
                    return true;
                }
            });
            lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            lv.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

                @Override
                public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                      long id, boolean checked) {
                    //TODO
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    // Respond to clicks on the actions in the CAB
                    // Make sure to do operations on ALL SELECTED ITEMS
                    switch (item.getItemId()) {
                        //TODO
                        //case R.id.menu_delete:
                        //deleteSelectedItems();
                        //mode.finish(); // Action picked, so close the CAB
                        //return true;
                        default:
                            return false;
                    }
                }

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    // Inflate the menu for the CAB
                    MenuInflater inflater = mode.getMenuInflater();
                    inflater.inflate(R.menu.context_menu_multi, menu);
                    return true;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    // Here you can make any necessary updates to the activity when
                    // the CAB is removed. By default, selected items are deselected/unchecked.
                    loadFolder(mCurrentFolderId);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    // Here you can perform updates to the CAB due to
                    // an invalidate() request
                    return false;
                }
            });

        }else{
            //TODO context menu
        }
    }

    /**
     * Determines whether or not the activity was started from the sharing activity.
     * If yes, load the uploading layout and set the state to uploading. This makes
     * the activity display only folders.
     *
     * @param startIntent
     */
    private void determineBrowserStateAndLayout(Intent startIntent) {
        if (startIntent.getAction() != null && startIntent.getAction().equals("killerud.skydrive.UPLOAD_PICK_FOLDER")) {
            setContentView(R.layout.skydrive_upload_picker);
            Button uploadButton = (Button) findViewById(R.id.uploadToThisFolder);
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    uploadFile(new Intent().putExtra(UploadFileDialog.EXTRA_FILE_PATH,
                            getIntent().getExtras().getString(UploadFileDialog.EXTRA_FILE_PATH)));
                }
            });
            mUploadDialog = true;
        } else {
            setContentView(R.layout.skydrive);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !mPreviousFolderIds.isEmpty()) {
            /* If the previous folder stack is empty, exit the application */
            loadFolder(mPreviousFolderIds.pop());
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void onCheckboxClicked(View v) {
        // Perform action on clicks, depending on whether it's now checked
        if (((CheckBox) v).isChecked()) {
            ((SkyDriveListAdapter) getListAdapter()).setChecked(getListView().getPositionForView(v),true);
            mSelectedView.add(v);
        } else {
            ((SkyDriveListAdapter) getListAdapter()).setChecked(getListView().getPositionForView(v),false);
            mSelectedView.remove(v);
        }
    }

    /**
     *  Sets up the listadapter for the browser, making sure the correct dialogs are opened for different file types
     */
    private void setupBrowserAdapterOnClick(AdapterView<?> parent, int position) {
        SkyDriveObject skyDriveObj = (SkyDriveObject) parent.getItemAtPosition(position);


        skyDriveObj.accept(new SkyDriveObject.Visitor() {
            @Override
            public void visit(SkyDriveAlbum album) {
                mPreviousFolderIds.push(mCurrentFolderId);
                loadFolder(album.getId());
            }

            @Override
            public void visit(SkyDrivePhoto photo) {
                if (mUploadDialog) return;
                Intent startPhotoDialog = new Intent(getApplicationContext(), ViewPhotoDialog.class);
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_ID", photo.getId());
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_NAME", photo.getName());
                startActivity(startPhotoDialog);
            }

            @Override
            public void visit(SkyDriveFolder folder) {
                mPreviousFolderIds.push(mCurrentFolderId);
                loadFolder(folder.getId());
            }

            @Override
            public void visit(SkyDriveFile file) {
                if (mUploadDialog) return;
                Bundle b = new Bundle();
                b.putString(JsonKeys.NAME, file.getName());
                b.putString(JsonKeys.ID, file.getId());
                b.putString(JsonKeys.LOCATION, file.getUploadLocation());
                showDialog(DIALOG_DOWNLOAD_ID, b);
            }

            @Override
            public void visit(SkyDriveVideo video) {
                if (mUploadDialog) return;
                if (mUploadDialog) return;
                Bundle b = new Bundle();
                b.putString(JsonKeys.NAME, video.getName());
                b.putString(JsonKeys.ID, video.getId());
                b.putString(JsonKeys.LOCATION, video.getUploadLocation());
                showDialog(DIALOG_DOWNLOAD_ID, b);
            }

            @Override
            public void visit(SkyDriveAudio audio) {
                if (mUploadDialog) return;
                Intent startAudioDialog = new Intent(getApplicationContext(), PlayAudioDialog.class);
                startAudioDialog.putExtra("killerud.skydrive.AUDIO_ID", audio.getId());
                startAudioDialog.putExtra("killerud.skydrive.AUDIO_NAME", audio.getName());
                startAudioDialog.putExtra("killerud.skydrive.AUDIO_SOURCE", audio.getSource());
                startActivity(startAudioDialog);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadFolder(HOME_FOLDER);

        /* Checks to see if the Sharing activity started the browser. If yes, some changes are made. */
        Intent intentThatStartedMe = getIntent();
        if (intentThatStartedMe.getAction() != null && intentThatStartedMe.getAction().equals("killerud.skydrive.SHARE_UPLOAD")) {
            if (intentThatStartedMe.getExtras().getString(UploadFileDialog.EXTRA_FILE_PATH) != null) {
                uploadFile(new Intent().putExtra(UploadFileDialog.EXTRA_FILE_PATH,
                        intentThatStartedMe.getExtras().getString(UploadFileDialog.EXTRA_FILE_PATH)));
            }
        }
    }

    /**
     * Gets the folder content for displaying
     */
    private void loadFolder(String folderId) {
        assert folderId != null;
        mCurrentFolderId = folderId;

        final ProgressDialog progressDialog =
                ProgressDialog.show(this, "", getString(R.string.navigateWait), true);

        mClient.getAsync(folderId + "/files", new LiveOperationListener() {
            @Override
            public void onComplete(LiveOperation operation) {
                progressDialog.dismiss();

                JSONObject result = operation.getResult();
                if (result.has(JsonKeys.ERROR)) {
                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                    String message = error.optString(JsonKeys.MESSAGE);
                    String code = error.optString(JsonKeys.CODE);
                    showToast(code + ": " + message);
                    return;
                }

                ArrayList<SkyDriveObject> skyDriveObjs = mPhotoAdapter.getSkyDriveObjs();
                skyDriveObjs.clear();

                JSONArray data = result.optJSONArray(JsonKeys.DATA);
                for (int i = 0; i < data.length(); i++) {
                    SkyDriveObject skyDriveObj = SkyDriveObject.create(data.optJSONObject(i));
                    if (mUploadDialog && (skyDriveObj.getType().equals(SkyDriveFolder.TYPE) || skyDriveObj.getType().equals(SkyDriveAlbum.TYPE))) {
                        skyDriveObjs.add(skyDriveObj);
                    } else if (!mUploadDialog) {
                        skyDriveObjs.add(skyDriveObj);
                    }
                }

                mPhotoAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(LiveOperationException exception, LiveOperation operation) {
                progressDialog.dismiss();

                showToast(exception.getMessage());
            }
        });
    }

    /* Menus and AB */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.browser_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.newFolder:
                Intent startNewFolderDialog = new Intent(getApplicationContext(), NewFolderDialog.class);
                startNewFolderDialog.putExtra("killerud.skydrive.CURRENT_FOLDER", mCurrentFolderId);
                startActivity(startNewFolderDialog);
                return true;
            case R.id.uploadFile:
                Intent intent = new Intent(getApplicationContext(), UploadFileDialog.class);
                startActivityForResult(intent, UploadFileDialog.PICK_FILE_REQUEST);
                return true;
            case R.id.reload:
                loadFolder(mCurrentFolderId);
                return true;
            case R.id.copy:
                mViewsToBeCopiedOrMoved = (ArrayList<View>) mSelectedView.clone();
                mCutNotPaste = false;
                Toast.makeText(getApplicationContext(), "Selected files ready to paste", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.cut:
                mViewsToBeCopiedOrMoved = (ArrayList<View>) mSelectedView.clone();
                mCutNotPaste = true;
                Toast.makeText(getApplicationContext(), "Selected files ready to paste", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.paste:
                pasteFiles(mViewsToBeCopiedOrMoved, mCutNotPaste);
                return true;
            case R.id.delete:
                deleteFilesFromView(mSelectedView);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void deleteFilesFromView(ArrayList<View> selectedView) {
        for(int i=0;i<selectedView.size();i++){
            View view = selectedView.get(i);
            final SkyDriveObject skyDriveObject = (SkyDriveObject) getListAdapter().getItem(getListView().getPositionForView(view));
            final String fileId = skyDriveObject.getId();
            mClient.deleteAsync(fileId, new LiveOperationListener() {
                public void onError(LiveOperationException exception, LiveOperation operation) {
                    Toast.makeText(getApplicationContext(), "Error deleting " + skyDriveObject.getName(), Toast.LENGTH_SHORT).show();
                    Log.e("ASE", exception.getMessage());
                }
                public void onComplete(LiveOperation operation) {
                    Toast.makeText(getApplicationContext(), "Deleted " + skyDriveObject.getName(), Toast.LENGTH_SHORT).show();
                }
            });
        }
        loadFolder(mCurrentFolderId);
    }

    private void pasteFiles(ArrayList<View> selectedView, boolean cutNotCopy){
        for(int i=0;i<selectedView.size();i++){
            View view = selectedView.get(i);
            final SkyDriveObject skyDriveObject = (SkyDriveObject) getListAdapter().getItem(getListView().getPositionForView(view));
            final String fileId = skyDriveObject.getId();
            if(cutNotCopy){
                mClient.moveAsync(fileId, mCurrentFolderId, new LiveOperationListener() {
                    public void onError(LiveOperationException exception, LiveOperation operation) {
                        Toast.makeText(getApplicationContext(), "Error moving " + skyDriveObject.getName(), Toast.LENGTH_SHORT).show();
                        Log.e("ASE", exception.getMessage());
                    }
                    public void onComplete(LiveOperation operation) {
                        Toast.makeText(getApplicationContext(), "Moved " + skyDriveObject.getName() + " to current folder", Toast.LENGTH_SHORT).show();
                    }
                });
            }else{
                mClient.copyAsync(fileId, mCurrentFolderId, new LiveOperationListener() {
                    public void onError(LiveOperationException exception, LiveOperation operation) {
                        Toast.makeText(getApplicationContext(), "Error copying " + skyDriveObject.getName(), Toast.LENGTH_SHORT).show();
                        Log.e("ASE", exception.getMessage());
                    }
                    public void onComplete(LiveOperation operation) {
                        Toast.makeText(getApplicationContext(), "Copied " + skyDriveObject.getName() + " to current folder", Toast.LENGTH_SHORT).show();
                    }
                });
            }

        }
        loadFolder(mCurrentFolderId);
    }

    /* Util */

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int computePercentCompleted(int totalBytes, int bytesRemaining) {
        return (int) (((float) (totalBytes - bytesRemaining)) / totalBytes * 100);
    }

    private ProgressDialog showProgressDialog(String title, String message, boolean indeterminate) {
        return ProgressDialog.show(this, title, message, indeterminate);
    }

    /**
     * The SkyDrive list adapter. Determines the list item layout and display behaviour.
     */
    private class SkyDriveListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final ArrayList<SkyDriveObject> mSkyDriveObjs;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mPosition;

        public SkyDriveListAdapter(Context context) {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSkyDriveObjs = new ArrayList<SkyDriveObject>();
            mCheckedPositions = new SparseBooleanArray();
        }

        /**
         * @return The underlying array of the class. If changes are made to this object and you
         *         want them to be seen, call {@link #notifyDataSetChanged()}.
         */
        public ArrayList<SkyDriveObject> getSkyDriveObjs() {
            return mSkyDriveObjs;
        }

        @Override
        public int getCount() {
            return mSkyDriveObjs.size();
        }

        public boolean isChecked(int pos){
            return mCheckedPositions.get(pos, false);
        }

        public void setChecked(int pos, boolean checked){
            mCheckedPositions.put(pos,checked);
            notifyDataSetChanged();
        }

        @Override
        public SkyDriveObject getItem(int position) {
            return mSkyDriveObjs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            SkyDriveObject skyDriveObj = getItem(position);
            mView = convertView != null ? convertView : null;
            mPosition = position;

            skyDriveObj.accept(new SkyDriveObject.Visitor() {
                @Override
                public void visit(SkyDriveVideo video) {

                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.video_x_generic);
                    setName(video);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveFile file) {

                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.text_x_preview);
                    setName(file);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveFolder folder) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.folder);
                    setName(folder);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDrivePhoto photo) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.image_x_generic);
                    setName(photo);
                    setChecked(isChecked(mPosition));

                    // Try to find a smaller/thumbnail and use that source
                    String thumbnailSource = null;
                    String smallSource = null;
                    for (SkyDrivePhoto.Image image : photo.getImages()) {
                        if (image.getType().equals("small")) {
                            smallSource = image.getSource();
                        } else if (image.getType().equals("thumbnail")) {
                            thumbnailSource = image.getSource();
                        }
                    }

                    String source = thumbnailSource != null ? thumbnailSource :
                            smallSource != null ? smallSource : null;

                    // if we do not have a thumbnail or small image, just leave.
                    if (source == null) {
                        return;
                    }

                    // Since we are doing async calls and mView is constantly changing,
                    // we need to hold on to this reference.
                    final View v = mView;
                    mClient.downloadAsync(source, new LiveDownloadOperationListener() {
                        @Override
                        public void onDownloadProgress(int totalBytes,
                                                       int bytesRemaining,
                                                       LiveDownloadOperation operation) {
                        }

                        @Override
                        public void onDownloadFailed(LiveOperationException exception,
                                                     LiveDownloadOperation operation) {
                            showToast(exception.getMessage());
                        }

                        @Override
                        public void onDownloadCompleted(LiveDownloadOperation operation) {
                            Bitmap bm = BitmapFactory.decodeStream(operation.getStream());
                            ImageView imgView = (ImageView) v.findViewById(R.id.skyDriveItemIcon);
                            imgView.setImageBitmap(bm);
                        }
                    });
                }

                @Override
                public void visit(SkyDriveAlbum album) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }
                    setIcon(R.drawable.folder_image);
                    setName(album);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveAudio audio) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.audio_x_generic);
                    setName(audio);
                    setChecked(isChecked(mPosition));
                }

                private void setName(SkyDriveObject skyDriveObj) {
                    TextView tv = (TextView) mView.findViewById(R.id.nameTextView);
                    tv.setText(skyDriveObj.getName());
                }

                private View inflateNewSkyDriveListItem() {
                    return mInflater.inflate(R.layout.skydrive_list_item, parent, false);
                }

                private void setIcon(int iconResId) {
                    ImageView img = (ImageView) mView.findViewById(R.id.skyDriveItemIcon);
                    img.setImageResource(iconResId);
                }

                private void setChecked(boolean checked){
                    CheckBox checkBox = (CheckBox) mView.findViewById(R.id.selectedSkyDrive);
                    checkBox.setChecked(checked);
                }
            });


            return mView;
        }
    }
}

