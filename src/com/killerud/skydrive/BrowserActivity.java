package com.killerud.skydrive;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.killerud.skydrive.objects.*;
import com.killerud.skydrive.util.JsonKeys;
import com.killerud.skydrive.util.UploadFileDialog;
import com.microsoft.live.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    /* The ActionMode callback. This callback enables the Contextual Action Bar. */
    /*private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

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
                case R.id.menu_share:
                    shareCurrentItem();
                    mode.finish(); // Action picked, so close the CAB
                    return true;
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
    };
    */

    /**
     *  The Create a new folder dialog. Always creates in the current directory.
     */
    private class NewFolderDialog extends Dialog {
        public NewFolderDialog(Context context) {
            super(context);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.create_folder);
            setTitle(getString(R.string.newFolderTitle));

            final EditText name = (EditText) findViewById(R.id.nameEditText);
            final EditText description = (EditText) findViewById(R.id.descriptionEditText);

            findViewById(R.id.saveButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    /* Uses a hashmap for creating a JSON object later on.
                     * Communication with the SkyDrive API is in JSON.
                     */
                    Map<String, String> folder = new HashMap<String, String>();
                    folder.put(JsonKeys.NAME, name.getText().toString());
                    folder.put(JsonKeys.DESCRIPTION, description.getText().toString());

                    final ProgressDialog progressDialog =
                            showProgressDialog("", getString(R.string.navigateWait), true);
                    progressDialog.show();

                    /* Attempts to create the folder */
                    mClient.postAsync(mCurrentFolderId,
                            new JSONObject(folder),
                            new LiveOperationListener() {
                                @Override
                                public void onError(LiveOperationException exception, LiveOperation operation) {
                                    progressDialog.dismiss();
                                    showToast(exception.getMessage());
                                }

                                /* Gets the result of the operation and shows the user in a toast
                                 * on error, reloads on success
                                 */
                                @Override
                                public void onComplete(LiveOperation operation) {
                                    progressDialog.dismiss();

                                    JSONObject result = operation.getResult();
                                    if (result.has(JsonKeys.ERROR)) {
                                        JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                        String message = error.optString(JsonKeys.MESSAGE);
                                        String code = error.optString(JsonKeys.CODE);
                                        showToast(code + ":" + message);
                                    } else {
                                        dismiss();
                                        loadFolder(mCurrentFolderId);
                                    }
                                }
                            });
                }
            });

            findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });
        }
    }

    /** The Audio dialog. Automatically starts buffering and playing a song,
     * and allows the user to pause, play, stop, and save the song, or
     * dismiss the dialog
     */
    private class PlayAudioDialog extends Dialog {
        private final SkyDriveAudio mAudio;
        private MediaPlayer mPlayer;
        private TextView mPlayerStatus;
        private LinearLayout mLayout;
        private LinearLayout mButtonLayout;
        private ImageButton mPlayPauseButton;
        private ImageButton mStopButton;
        private File mFile;

        private boolean isPlaying;

        public PlayAudioDialog(Context context, SkyDriveAudio audio) {
            super(context);
            assert audio != null;
            mAudio = audio;
            mPlayer = new MediaPlayer();
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setTitle(mAudio.getName());

            /* Creates the layout.
             * A vertical linearlayout contains a textview used for status updates
             * and a horizontal linearlayout for holding the buttons.
             */
            mLayout = new LinearLayout(getContext());
            mLayout.setOrientation(LinearLayout.VERTICAL);
            mButtonLayout = new LinearLayout(mLayout.getContext());
            mButtonLayout.setOrientation(LinearLayout.HORIZONTAL);

            mPlayerStatus = new TextView(mLayout.getContext());
            mPlayerStatus.setText(getString(R.string.buffering) + " " + mAudio.getName());

            mPlayPauseButton = new ImageButton(mButtonLayout.getContext());
            mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_play);
            mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(isPlaying){
                        mPlayer.pause();
                        mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_play);
                        mPlayerStatus.setText(getString(R.string.paused) + " " + mAudio.getName());
                        isPlaying = false;
                    }else if(!isPlaying){
                        mPlayer.start();
                        mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                        mPlayerStatus.setText(getString(R.string.playing) + " " + mAudio.getName());
                        isPlaying = true;
                    }
                }
            });

            mStopButton = new ImageButton(mButtonLayout.getContext());
            mStopButton.setBackgroundResource(R.drawable.ic_media_stop);
            mStopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mPlayer.stop();
                    mPlayerStatus.setText(getString(R.string.stopped) + " " + mAudio.getName());
                }
            });

            mFile = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", mAudio.getName());

            ImageButton saveButton = new ImageButton(mButtonLayout.getContext());
            saveButton.setBackgroundResource(android.R.drawable.ic_menu_save);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final ProgressDialog progressDialog =
                            new ProgressDialog(BrowserActivity.this);
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setMessage(getString(R.string.downloading));
                    progressDialog.setCancelable(true);




                    if(mFile.exists()) {
                        AlertDialog existsAlert = new AlertDialog.Builder(getApplicationContext()).create();
                        existsAlert.setTitle(R.string.fileAlreadySaved);
                        existsAlert.setMessage(getString(R.string.fileAlreadySavedMessage));
                        existsAlert.setButton("Download", new OnClickListener(){
                            public void onClick(DialogInterface dialog, int which){
                                  downloadFile(progressDialog);
                            }
                        });
                        existsAlert.setButton2("Cancel", new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dismiss();
                            }
                        });
                        existsAlert.show();

                    }else{
                        downloadFile(progressDialog);
                    }
                }
            });

            ImageButton cancel = new ImageButton(mButtonLayout.getContext());
            cancel.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mPlayer.stop();
                    dismiss();
                }
            });


            mLayout.addView(mPlayerStatus, new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.FILL_PARENT));

            mButtonLayout.addView(mPlayPauseButton, new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.FILL_PARENT));
            mButtonLayout.addView(mStopButton,new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.FILL_PARENT));
            mButtonLayout.addView(saveButton,new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.FILL_PARENT));
            mButtonLayout.addView(cancel,new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.FILL_PARENT));

            mLayout.addView(mButtonLayout, new AbsListView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT));



            addContentView(mLayout,
                    new LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT));

            mPlayer.setOnPreparedListener(new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mPlayerStatus.setText(getString(R.string.playing) + " " + mAudio.getName());
                    mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                    mPlayer.start();
                    isPlaying = true;
                }
            });

            mPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mPlayerStatus.setText(getString(R.string.stopped) + " " + mAudio.getName());
                }
            });



            try {
                if(mFile.exists()){
                    mPlayer.setDataSource(mFile.getPath());
                }else{
                    mPlayer.setDataSource(mAudio.getSource());
                }
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.prepareAsync();
            } catch (IllegalArgumentException e) {
                showToast(e.getMessage());
                return;
            } catch (IllegalStateException e) {
                showToast(e.getMessage());
                return;
            } catch (IOException e) {
                showToast(e.getMessage());
                return;
            }


        }

        private void downloadFile(final ProgressDialog progressDialog) {
            progressDialog.show();

            final LiveDownloadOperation operation =
                    mClient.downloadAsync(mAudio.getId() + "/content",
                            mFile,
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
                                    showFileXloadedNotification(mFile.getName(), true);
                                }
                            });

            progressDialog.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    operation.cancel();
                }
            });
        }

        /* Known "feature": pressing the Home-button (or rather, not pressing
         * Cancel or the Back-button, triggering Dismiss) causes the music to
         * keep playing. There is no way to turn off that mediaplayer other
         * than force-quitting. Background music playback, yay! :D Fortunately
         * there's no playlist!
         */
        @Override
        protected void onStop() {
            super.onStop();
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }


    /** The photo dialog. Downloads and displays an image, but does not save the
     * image unless the user presses the save button (i.e. acts as a cache)
     */
    private class ViewPhotoDialog extends Dialog {
        private final SkyDrivePhoto mPhoto;
        private boolean mSavePhoto;
        private String mFileName;
        private File mFile;

        public ViewPhotoDialog(Context context, SkyDrivePhoto photo) {
            super(context);
            assert photo != null;
            mPhoto = photo;
            mSavePhoto = false;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setTitle(mPhoto.getName());

            /* Creates the layout.
             * The layout consists of a textview to let the user know we're loading,
             * an imageview to display the image and a linearlayout containing the
             * buttons. This is wrapped in a linearlayout, then a scrollview and displayed.
             */
            final ScrollView wrapper = new ScrollView(getContext());

            final LinearLayout layout = new LinearLayout(wrapper.getContext());
            layout.setOrientation(LinearLayout.VERTICAL);

            final ImageView imageView = new ImageView(layout.getContext());
            final TextView loadingText = new TextView(layout.getContext());
            loadingText.setText(R.string.navigateWait);

            final LinearLayout buttonLayout = new LinearLayout(layout.getContext());
            buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

            final ImageButton saveButton = new ImageButton(buttonLayout.getContext());
            saveButton.setBackgroundResource(android.R.drawable.ic_menu_save);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mSavePhoto = true;
                    showFileXloadedNotification(mFile.getName(), true);
                    dismiss();
                }
            });

            final ImageButton cancel = new ImageButton(buttonLayout.getContext());
            cancel.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mSavePhoto = false;
                    dismiss();
                }
            });

            layout.addView(loadingText,new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            layout.addView(imageView, new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            buttonLayout.addView(saveButton,new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            buttonLayout.addView(cancel,new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));
            layout.addView(buttonLayout,new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.FILL_PARENT));
            wrapper.addView(layout,new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT));

            addContentView(wrapper,
                    new LayoutParams(LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT));







            mFile = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", mPhoto.getName());
            mFileName = mFile.getName();

            if(mFile.exists()){
                imageView.setImageBitmap(BitmapFactory.decodeFile(mFile.getPath()));
                layout.removeView(loadingText);
            }else{
                final ProgressDialog progressDialog =
                        new ProgressDialog(BrowserActivity.this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMessage(getString(R.string.downloading));
                progressDialog.setCancelable(true);
                progressDialog.show();

                final LiveDownloadOperation operation =
                        mClient.downloadAsync(mPhoto.getId() + "/content",
                                mFile,
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
                                        imageView.setImageBitmap(BitmapFactory.decodeFile(mFile.getPath()));
                                        layout.removeView(loadingText);
                                    }
                                });

                progressDialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        operation.cancel();
                        if(mFile != null) mFile.delete();
                    }
                });
            }
        }


        /* Known "feature": pressing the Home-button (or rather, not pressing
         * Cancel or the Back-button, triggering Dismiss) causes the file to
         * stay saved even if the user didn't explicitly ask for it. The user
         * is not informed of this save/failure to delete, and so may be
         * surprised to see the file saved later on.
         */
        @Override
        protected void onStop(){
            if(mSavePhoto){
                showFileXloadedNotification(mFileName, true);
            }else{
                if(mFile != null) mFile.delete();
            }
        }

    }

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

                                                        showFileXloadedNotification(file.getName(), true);
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
     * @param fileName The file name to be displayed
     * @param download Whether or not this is a download notification
     */
    private void showFileXloadedNotification(String fileName, boolean download) {
        int icon = R.drawable.notification_icon;
        CharSequence tickerText = fileName + " saved " + (download ? "from" : "to") + "SkyDrive!";
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.appName);
        CharSequence contentText = fileName + " was saved to your " + (download ? "phone" : "SkyDrive") + "!";
        Intent notificationIntent = new Intent(context, BrowserActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(1, notification);
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
                                showFileXloadedNotification(file.getName(), false);
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
        /*lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> av, View v, int
                    pos, long id) {
                if (mActionMode != null) {
                    return false;
                }
                // Start the CAB using the ActionMode.Callback defined above
                mActionMode = startActionMode(mActionModeCallback);
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
                    case R.id.menu_delete:
                        deleteSelectedItems();
                        mode.finish(); // Action picked, so close the CAB
                        return true;
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

        */
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
                ViewPhotoDialog dialog =
                        new ViewPhotoDialog(BrowserActivity.this, photo);
                dialog.setOwnerActivity(BrowserActivity.this);
                dialog.show();
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
                PlayAudioDialog audioDialog =
                        new PlayAudioDialog(BrowserActivity.this, audio);
                audioDialog.show();
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
                NewFolderDialog dialog = new NewFolderDialog(BrowserActivity.this);
                dialog.setOwnerActivity(BrowserActivity.this);
                dialog.show();
                return true;
            case R.id.uploadFile:
                Intent intent = new Intent(getApplicationContext(), UploadFileDialog.class);
                startActivityForResult(intent, UploadFileDialog.PICK_FILE_REQUEST);
                return true;
            case R.id.reload:
                loadFolder(mCurrentFolderId);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

        public SkyDriveListAdapter(Context context) {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSkyDriveObjs = new ArrayList<SkyDriveObject>();
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

            skyDriveObj.accept(new SkyDriveObject.Visitor() {
                @Override
                public void visit(SkyDriveVideo video) {

                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.video_x_generic);
                    setName(video);
                }

                @Override
                public void visit(SkyDriveFile file) {

                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.text_x_preview);
                    setName(file);
                }

                @Override
                public void visit(SkyDriveFolder folder) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.folder);
                    setName(folder);
                }

                @Override
                public void visit(SkyDrivePhoto photo) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.image_x_generic);
                    setName(photo);

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
                }

                @Override
                public void visit(SkyDriveAudio audio) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.audio_x_generic);
                    setName(audio);
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
            });


            return mView;
        }
    }
}

