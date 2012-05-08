package com.killerud.skydrive.dialogs;

/**
 * User: William
 * Date: 07.05.12
 * Time: 16:59
 */

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.killerud.skydrive.BrowserActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.killerud.skydrive.util.IOUtil;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveDownloadOperation;
import com.microsoft.live.LiveDownloadOperationListener;
import com.microsoft.live.LiveOperationException;

import java.io.File;
import java.io.IOException;

/** The Audio dialog. Automatically starts buffering and playing a song,
 * and allows the user to pause, play, stop, and save the song, or
 * dismiss the dialog
 */
public class PlayAudioDialog extends Activity {
    private MediaPlayer mPlayer;
    private TextView mPlayerStatus;
    private LinearLayout mLayout;
    private LinearLayout mButtonLayout;
    private ImageButton mPlayPauseButton;
    private ImageButton mStopButton;
    private File mFile;
    private LiveConnectClient mClient;
    private IOUtil mIOUtil;

    private String mAudioId;
    private String mAudioName;
    private String mAudioSource;

    private String LOGTAC;

    private boolean isPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LOGTAC = ((BrowserForSkyDriveApplication) getApplication()).getDebugTag();
        mIOUtil = new IOUtil();

        Intent audioDetails = getIntent();
        mAudioId = audioDetails.getStringExtra("killerud.skydrive.AUDIO_ID");
        mAudioName = audioDetails.getStringExtra("killerud.skydrive.AUDIO_NAME");
        mAudioSource = audioDetails.getStringExtra("killerud.skydrive.AUDIO_SOURCE");
        mPlayer = new MediaPlayer();

        mClient = ((BrowserForSkyDriveApplication) getApplication()).getConnectClient();

        setTitle(mAudioName);

        /* Creates the layout.
        * A vertical linearlayout contains a textview used for status updates
        * and a horizontal linearlayout for holding the buttons.
        */
        mLayout = new LinearLayout(getApplicationContext());
        mLayout.setOrientation(LinearLayout.VERTICAL);
        mButtonLayout = new LinearLayout(mLayout.getContext());
        mButtonLayout.setOrientation(LinearLayout.HORIZONTAL);

        mPlayerStatus = new TextView(mLayout.getContext());
        mPlayerStatus.setText(getString(R.string.buffering) + " " + mAudioName);

        mPlayPauseButton = new ImageButton(mButtonLayout.getContext());
        mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_play);
        mPlayPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isPlaying){
                    mPlayer.pause();
                    mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_play);
                    mPlayerStatus.setText(getString(R.string.paused) + " " + mAudioName);
                    isPlaying = false;
                }else if(!isPlaying){
                    mPlayer.start();
                    mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                    mPlayerStatus.setText(getString(R.string.playing) + " " + mAudioName);
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
                mPlayerStatus.setText(getString(R.string.stopped) + " " + mAudioName);
            }
        });

        mFile = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", mAudioName);

        ImageButton saveButton = new ImageButton(mButtonLayout.getContext());
        saveButton.setBackgroundResource(android.R.drawable.ic_menu_save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO download progress notification
                /*final ProgressDialog progressDialog =
                        new ProgressDialog(BrowserActivity.this);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMessage(getString(R.string.downloading));
                progressDialog.setCancelable(true);*/




                if(mFile.exists()) {
                    AlertDialog existsAlert = new AlertDialog.Builder(getApplicationContext()).create();
                    existsAlert.setTitle(R.string.fileAlreadySaved);
                    existsAlert.setMessage(getString(R.string.fileAlreadySavedMessage));
                    existsAlert.setButton("Download", new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int which){
                            //downloadFile(progressDialog);
                        }
                    });
                    existsAlert.setButton2("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    });
                    existsAlert.show();

                }else{
                    //downloadFile(progressDialog);
                }
            }
        });

        ImageButton cancel = new ImageButton(mButtonLayout.getContext());
        cancel.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.stop();
                finish();
            }
        });


        mLayout.addView(mPlayerStatus, new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));

        mButtonLayout.addView(mPlayPauseButton, new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));
        mButtonLayout.addView(mStopButton,new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));
        mButtonLayout.addView(saveButton,new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));
        mButtonLayout.addView(cancel,new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));

        mLayout.addView(mButtonLayout, new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.FILL_PARENT));



        addContentView(mLayout,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mPlayerStatus.setText(getString(R.string.playing) + " " + mAudioName);
                mPlayPauseButton.setBackgroundResource(R.drawable.ic_media_pause);
                mPlayer.start();
                isPlaying = true;
            }
        });

        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPlayerStatus.setText(getString(R.string.stopped) + " " + mAudioName);
            }
        });



        try {
            if(mFile.exists()){
                mPlayer.setDataSource(mFile.getPath());
            }else{
                mPlayer.setDataSource(mAudioSource);
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
                mClient.downloadAsync(mAudioId + "/content",
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
                                showFileXloadedNotification(mFile, true);
                            }
                        });

        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
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

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

    private int computePercentCompleted(int totalBytes, int bytesRemaining) {
        return (int) (((float) (totalBytes - bytesRemaining)) / totalBytes * 100);
    }
}
