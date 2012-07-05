package com.killerud.skydrive;

/**
 * User: William
 * Date: 07.05.12
 * Time: 16:59
 */

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.microsoft.live.LiveDownloadOperation;
import com.microsoft.live.LiveDownloadOperationListener;
import com.microsoft.live.LiveOperationException;

import java.io.File;

/**
 * The Audio dialog. Automatically starts buffering and playing a song,
 * and allows the user to pause, play, stop, and save the song, or
 * dismiss the dialog
 */
public class AudioControlActivity extends SherlockActivity  implements View.OnClickListener
{
    private AudioPlaybackService audioPlaybackService;
    private TextView audioStatusText;
    private TextView audioTitleText;
    private TextView audioArtistText;
    private TextView audioAlbumText;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.audioPlayPause).setOnClickListener(this);
        findViewById(R.id.audioCancel).setOnClickListener(this);
        findViewById(R.id.audioStop).setOnClickListener(this);
        findViewById(R.id.audioNext).setOnClickListener(this);

        audioAlbumText = (TextView) findViewById(R.id.audioAlbumText);
        audioArtistText= (TextView) findViewById(R.id.audioArtistText);
        audioTitleText = (TextView) findViewById(R.id.audioTitleText);
        audioStatusText = (TextView) findViewById(R.id.audioStatusText);

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        startService(new Intent(this, AudioPlaybackService.class));
        bindService(new Intent(this, AudioPlaybackService.class),
                audioPlaybackServiceConnection, Context.BIND_ABOVE_CLIENT);
        registerBroadcastReceiver();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
        if(audioPlaybackService.NOW_PLAYING_QUEUE.isEmpty())
        {
            audioPlaybackService.onDestroy();
        }
        unbindService(audioPlaybackServiceConnection);
    }

    private void registerBroadcastReceiver()
    {
        IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Constants.ACTION_SONG_CHANGE);
        registerReceiver(broadcastReceiver, new IntentFilter(ifilter));
    }


    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.audioPlayPause:
                if(audioPlaybackService.isPlaying())
                {
                    audioPlaybackService.pauseSong();
                    setToSongPausedUI();
                }else {
                    audioPlaybackService.playSong();
                    setToSongPlayingUI();
                }
                return;
            case R.id.audioCancel:
                finish();
                return;
            case R.id.audioNext:
                audioPlaybackService.nextSong();
                setToSongPlayingUI();
                return;
            case R.id.audioStop:
                audioPlaybackService.stopSong();
                setToSongStoppedUI();
                return;
            default:
                return;
        }
    }

    private void setToSongStoppedUI()
    {
        audioStatusText.setText(R.string.audioStopped);
        ((ImageButton) findViewById(R.id.audioPlayPause)).setImageResource(R.drawable.ic_media_play);
    }

    private void setToSongPlayingUI()
    {
        audioStatusText.setText(R.string.audioPlaying);
        ((ImageButton) findViewById(R.id.audioPlayPause)).setImageResource(R.drawable.ic_media_pause);
    }

    private void setToSongPausedUI()
    {
        audioStatusText.setText(R.string.audioPaused);
        ((ImageButton) findViewById(R.id.audioPlayPause)).setImageResource(R.drawable.ic_media_play);
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
        if(intent.getAction().equals(Constants.ACTION_SONG_CHANGE))
        {
            updateUI(audioPlaybackService.NOW_PLAYING_QUEUE.peek());
        }
        }
    };



    private void updateUI(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;

        updateCoverPhoto(skyDriveAudio);
        setToSongPlayingUI();
        updateSongDetails(skyDriveAudio);
        setTitle(audioPlaybackService.audioTitle(skyDriveAudio));
    }

    private void updateSongDetails(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;
        audioArtistText.setText(skyDriveAudio.getArtist());
        audioAlbumText.setText(skyDriveAudio.getAlbum());
        audioTitleText.setText(audioPlaybackService.audioTitle(skyDriveAudio));
    }

    private void updateCoverPhoto(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;
        if(albumArtExistsInCache(skyDriveAudio))
        {
            loadAlbumArtFromCache(skyDriveAudio);
        }else{
            loadAlbumArtFromSkyDrive(skyDriveAudio);
        }
    }

    private void loadAlbumArtFromSkyDrive(SkyDriveAudio skyDriveAudio)
    {

        if(skyDriveAudio == null) return;
        final File albumArtFile = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/thumbs/" + skyDriveAudio.getName() + ".jpg");
        final LiveDownloadOperation operation =
                ((BrowserForSkyDriveApplication)getApplication()).getConnectClient().downloadAsync(
                        skyDriveAudio.getId() + "/picture",
                        albumArtFile,
                        new LiveDownloadOperationListener()
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
                                ((ImageView) findViewById(R.id.audioAlbumArt))
                                        .setImageResource(R.drawable.audio_album_generic);
                            }

                            @Override
                            public void onDownloadCompleted(LiveDownloadOperation operation)
                            {
                                BitmapFactory.Options options = determineBitmapDecodeOptions(albumArtFile);
                                ((ImageView) findViewById(R.id.audioAlbumArt)).setImageBitmap(
                                        BitmapFactory.decodeFile(albumArtFile.getPath(), options));
                            }
                        });
    }

    private BitmapFactory.Options determineBitmapDecodeOptions(File file) {
        BitmapFactory.Options scoutOptions = new BitmapFactory.Options();
        scoutOptions.inJustDecodeBounds = true;

        Bitmap bitmapBounds = BitmapFactory.decodeFile(file.getPath(), scoutOptions);

        int bitmapHeight = scoutOptions.outHeight;
        int bitmapWidth = scoutOptions.outWidth;

        int dividend  = bitmapWidth;

        if(bitmapHeight > bitmapWidth)
        {
            dividend = bitmapHeight;
        }

        int sampleSize = bitmapHeight / 800;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPurgeable = true;

        return options;
    }

    private void loadAlbumArtFromCache(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;
        final File albumArtFile = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/thumbs/" + skyDriveAudio.getName() + ".jpg");
        BitmapFactory.Options options = determineBitmapDecodeOptions(albumArtFile);
        ((ImageView) findViewById(R.id.audioAlbumArt)).setImageBitmap(
                BitmapFactory.decodeFile(albumArtFile.getPath(), options));
    }

    private boolean albumArtExistsInCache(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return false;
        final File albumArtFile = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/thumbs/" + skyDriveAudio.getName() + ".jpg");
        return albumArtFile.exists();
    }

    private ServiceConnection audioPlaybackServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder)
        {
            audioPlaybackService = ((AudioPlaybackService.AudioPlaybackServiceBinder) iBinder).getService();
            audioPlaybackService.updateUI();
            updateUI(audioPlaybackService.NOW_PLAYING_QUEUE.peek());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            audioPlaybackService = null;
        }
    };
}
