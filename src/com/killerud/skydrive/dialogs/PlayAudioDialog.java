package com.killerud.skydrive.dialogs;

/**
 * User: William
 * Date: 07.05.12
 * Time: 16:59
 */

import android.app.*;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.XLoader;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.killerud.skydrive.objects.SkyDriveObject;
import com.microsoft.live.LiveConnectClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** The Audio dialog. Automatically starts buffering and playing a song,
 * and allows the user to pause, play, stop, and save the song, or
 * dismiss the dialog
 */
public class PlayAudioDialog extends SherlockActivity {
    private MediaPlayer mPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPlayer = new MediaPlayer();

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        final SkyDriveAudio audio = app.getCurrentMusic();
        final XLoader loader = new XLoader(app.getCurrentBrowser());
        final LiveConnectClient client = app.getConnectClient();

        setContentView(R.layout.music_dialog);
        setTitle(audio.getName());

        final LinearLayout layout = (LinearLayout) findViewById(R.id.audioLayout);
        final LinearLayout buttonLayout = (LinearLayout) layout.findViewById(R.id.audioButtonLayout);
        final TextView playerStatus = (TextView) layout.findViewById(R.id.audioText);
        playerStatus.setText(getString(R.string.buffering) + " " + audio.getName());

        final File file = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", audio.getName());



        final ImageButton playPauseButton = (ImageButton) buttonLayout.findViewById(R.id.audioPlayPause);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPlayer.isPlaying()) {
                    mPlayer.pause();
                    playPauseButton.setImageResource(R.drawable.ic_media_play);
                    playerStatus.setText(getString(R.string.paused) + " " + audio.getName());
                } else if (!mPlayer.isPlaying()) {
                    mPlayer.start();
                    playPauseButton.setImageResource(R.drawable.ic_media_pause);
                    playerStatus.setText(getString(R.string.playing) + " " + audio.getName());
                }else{
                    try {
                        if(file.exists()){
                            mPlayer.setDataSource(file.getPath());
                        }else{
                            mPlayer.setDataSource(audio.getSource());
                        }
                        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mPlayer.prepareAsync();
                    } catch (IllegalArgumentException e) {
                        showToast(e.getMessage());
                    } catch (IllegalStateException e) {
                        showToast(e.getMessage());
                    } catch (IOException e) {
                        showToast(e.getMessage());
                    }
                }
            }
        });

        final ImageButton stopButton = (ImageButton) buttonLayout.findViewById(R.id.audioStop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.stop();
                playerStatus.setText(getString(R.string.stopped) + " " + audio.getName());
            }
        });


        final ImageButton saveButton = (ImageButton) buttonLayout.findViewById(R.id.audioSave);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ArrayList<SkyDriveObject> file = new ArrayList<SkyDriveObject>();
                file.add(audio);
                loader.downloadFiles(client, file);
            }
        });

        ImageButton cancel = (ImageButton) buttonLayout.findViewById(R.id.audioCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlayer.stop();
                finish();
            }
        });

        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                playerStatus.setText(getString(R.string.playing) + " " + audio.getName());
                playPauseButton.setImageResource(R.drawable.ic_media_pause);
                mPlayer.start();
            }
        });

        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playerStatus.setText(getString(R.string.stopped) + " " + audio.getName());
            }
        });



        try {
            if(file.exists()){
                mPlayer.setDataSource(file.getPath());
            }else{
                mPlayer.setDataSource(audio.getSource());
            }
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.prepareAsync();
        } catch (IllegalArgumentException e) {
            showToast(e.getMessage());
        } catch (IllegalStateException e) {
            showToast(e.getMessage());
        } catch (IOException e) {
            showToast(e.getMessage());
        }


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
        if(mPlayer !=null){
            try{
                mPlayer.stop();
            }catch (IllegalStateException e){
            }finally {
                mPlayer.release();
                mPlayer = null;
            }
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

}
