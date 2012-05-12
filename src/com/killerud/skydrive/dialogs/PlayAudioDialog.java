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
public class PlayAudioDialog extends Activity {
    private MediaPlayer mPlayer;
    private boolean isPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final XLoader loader = new XLoader(getApplicationContext());
        final SkyDriveAudio audio = ((BrowserForSkyDriveApplication)getApplication()).getCurrentMusic();
        mPlayer = new MediaPlayer();
        final LiveConnectClient client = ((BrowserForSkyDriveApplication) getApplication()).getConnectClient();

        setContentView(R.layout.music_dialog);
        setTitle(audio.getName());

        final LinearLayout layout = (LinearLayout) findViewById(R.id.audioLayout);
        final LinearLayout buttonLayout = (LinearLayout) layout.findViewById(R.id.audioButtonLayout);
        final TextView playerStatus = (TextView) layout.findViewById(R.id.audioText);
        playerStatus.setText(getString(R.string.buffering) + " " + audio.getName());

        final ImageButton playPauseButton = (ImageButton) buttonLayout.findViewById(R.id.audioPlayPause);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPlaying) {
                    mPlayer.pause();
                    playPauseButton.setImageResource(R.drawable.ic_media_play);
                    playerStatus.setText(getString(R.string.paused) + " " + audio.getName());
                    isPlaying = false;
                } else if (!isPlaying) {
                    mPlayer.start();
                    playPauseButton.setImageResource(R.drawable.ic_media_pause);
                    playerStatus.setText(getString(R.string.playing) + " " + audio.getName());
                    isPlaying = true;
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

        final File file = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", audio.getName());

        final ImageButton saveButton = (ImageButton) buttonLayout.findViewById(R.id.audioSave);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (file.exists()) {
                    AlertDialog existsAlert = new AlertDialog.Builder(getApplicationContext()).create();
                    existsAlert.setTitle(R.string.fileAlreadySaved);
                    existsAlert.setMessage(getString(R.string.fileAlreadySavedMessage));
                    existsAlert.setButton("Download", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ArrayList<SkyDriveObject> file = new ArrayList<SkyDriveObject>();
                            file.add(audio);
                            loader.downloadFiles(client, file);
                        }
                    });
                    existsAlert.setButton2("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    });
                    existsAlert.show();

                } else {
                    ArrayList<SkyDriveObject> file = new ArrayList<SkyDriveObject>();
                    file.add(audio);
                    loader.downloadFiles(client, file);
                }
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
                isPlaying = true;
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
            return;
        } catch (IllegalStateException e) {
            showToast(e.getMessage());
            return;
        } catch (IOException e) {
            showToast(e.getMessage());
            return;
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
        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

}
