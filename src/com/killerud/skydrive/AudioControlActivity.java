package com.killerud.skydrive;

/**
 * User: William
 * Date: 07.05.12
 * Time: 16:59
 */

import android.content.*;
import android.drm.DrmStore;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveAudio;

/**
 * The Audio dialog. Automatically starts buffering and playing a song,
 * and allows the user to pause, play, stop, and save the song, or
 * dismiss the dialog
 */
public class AudioControlActivity extends SherlockActivity  implements View.OnClickListener
{
    private MediaPlayer mPlayer;
    private AudioPlaybackService audioPlaybackService;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
    }

    @Override
    protected void onResume()
    {
        registerBroadcastReceiver();
    }

    @Override
    protected void onStop()
    {
        super.onStop();
    }

    private void showToast(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void registerBroadcastReceiver()
    {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Constants.ACTION_SONG_CHANGE);
        registerReceiver(broadcastReceiver, new IntentFilter(ifilter));
    }


    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            default:
                return;
        }
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            if(intent.getAction().equals(Constants.ACTION_SONG_CHANGE))
            {

            }
        }
    };




    private ServiceConnection audioPlaybackServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder)
        {
            audioPlaybackService = ((AudioPlaybackService.AudioPlaybackServiceBinder) iBinder).getService();
            audioPlaybackService.updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            audioPlaybackService = null;
        }
    };
}
