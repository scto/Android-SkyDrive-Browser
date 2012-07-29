package com.killerud.skydrive;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.killerud.skydrive.util.Utility;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: William
 * Date: 04.07.12
 * Time: 22:39
 * To change this template use File | Settings | File Templates.
 */
public class AudioPlaybackService extends Service
{

    private final IBinder mBinder = new AudioPlaybackServiceBinder();
    private final static int FOREGROUND_NOTIFICATION_ID = 837;
    private MediaPlayer mediaPlayer;
    private Notification foregroundNotification;
    private PendingIntent foregroundNotificationPendingIntent;
    private boolean repeatPlaylist;

    private LinkedList<SkyDriveAudio> unshuffledPlaylist;
    private boolean shuffled;
    private boolean isLimitedToWiFi;


    @Override
    public void onCreate()
    {
        super.onCreate();
        repeatPlaylist = false;
        foregroundNotification = createForegroundNotification();
        mediaPlayer = createMediaPlayer();
    }

    private Notification createForegroundNotification()
    {
        Intent foregroundNotificationIntent = new Intent(this, AudioControlActivity.class);
        foregroundNotificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        foregroundNotificationPendingIntent = PendingIntent
                .getActivity(this, 1, foregroundNotificationIntent, 0);

        Notification notification = new Notification(R.drawable.ic_media_play_notification,
                getString(R.string.audioServiceName), System.currentTimeMillis());
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        notification.setLatestEventInfo(getApplicationContext(),
                getString(R.string.audioTapToAddToQueue),
                getString(R.string.audioReturnToControls),
                foregroundNotificationPendingIntent);
        return notification;
    }

    private MediaPlayer createMediaPlayer()
    {
        MediaPlayer player = new MediaPlayer();
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener()
        {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer)
            {
                onPlayerPrepared(mediaPlayer);
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener()
        {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int i, int i1)
            {
                mediaPlayer.reset();
                if (NOW_PLAYING.size() > 0)
                {
                    NOW_PLAYING.poll();
                    startPlayback(NOW_PLAYING.peek());
                }
                return true;
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer)
            {
                onPlayerCompletion();
            }
        });
        return player;
    }

    private void onPlayerPrepared(MediaPlayer mediaPlayer)
    {
        mediaPlayer.start();
        updateUI();
    }

    private void onPlayerCompletion()
    {
        mediaPlayer.reset();
        PLAYED.push(NOW_PLAYING.poll());
        updateUI(NOW_PLAYING.peek());
        startPlayback(NOW_PLAYING.peek());
    }

    public void updateUI()
    {
        updateUI(NOW_PLAYING.peek());
    }

    public void updateUI(SkyDriveAudio skyDriveAudio)
    {
        if (skyDriveAudio == null)
        {
            stopForeground(true);
        }
        updateNotificationWithNewAudio(skyDriveAudio);
        broadcastNewSongForActivityUpdate(skyDriveAudio);
    }

    private void broadcastNewSongForActivityUpdate(SkyDriveAudio skyDriveAudio)
    {
        Intent broadcast = new Intent();
        broadcast.setAction(Constants.ACTION_SONG_CHANGE);
        sendBroadcast(broadcast);
    }

    private void updateNotificationWithNewAudio(SkyDriveAudio skyDriveAudio)
    {
        if (skyDriveAudio == null)
        {
            return;
        }

        foregroundNotification.setLatestEventInfo(getApplicationContext(),
                getString(R.string.audioPlaying) + " " +
                        getAudioTitle(skyDriveAudio),
                getString(R.string.audioReturnToControls),
                foregroundNotificationPendingIntent);
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification);
    }


    private void updateNotificationPlayState(int resourceId)
    {
        updateNotificationPlayState(getString(resourceId));
    }

    private void updateNotificationPlayState(String songState)
    {
        SkyDriveAudio skyDriveAudio = NOW_PLAYING.peek();
        foregroundNotification.setLatestEventInfo(getApplicationContext(),
                songState + " " + getAudioTitle(skyDriveAudio),
                getString(R.string.audioReturnToControls),
                foregroundNotificationPendingIntent);
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification);
    }

    public String getAudioTitle(SkyDriveAudio skyDriveAudio)
    {
        return (skyDriveAudio.getTitle() != null ? skyDriveAudio.getTitle() :
                (skyDriveAudio.getName() != null ? skyDriveAudio.getName() : "audio"));
    }

    private void startPlayback(SkyDriveAudio skyDriveAudio)
    {
        if(mediaPlayer == null)
        {
            mediaPlayer = createMediaPlayer();
        }

        if (skyDriveAudio == null && repeatPlaylist)
        {
            if(NOW_PLAYING.size()>0){
                populateNowPlayingWithPlayed();
                startPlayback(NOW_PLAYING.peek());
            }
            return;
        } else if (skyDriveAudio == null)
        {
            stopForeground(true);
            updateUI();
            clearPlaylist();
            return;
        }

        try
        {
            if (hasLocalCache(skyDriveAudio))
            {
                mediaPlayer.setDataSource(getLocalCache(skyDriveAudio).getPath());
            } else
            {

                if(connectionIsUnavailable()){
                    stopSong();
                    return;
                }
                mediaPlayer.setDataSource(skyDriveAudio.getSource());
            }

            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
        } catch (IllegalArgumentException e)
        {
            Log.e(Constants.LOGTAG, "Illegal argument exception for media player in audio playback service");
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "Media player in audio playback service in illegal state");
        } catch (IOException e)
        {
            Log.e(Constants.LOGTAG, "IOException for media player in audio playback service");
        }

        updateNotificationPlayState(R.string.audioPlaying);
    }

    private void clearPlaylist()
    {
        NOW_PLAYING.clear();
        PLAYED.clear();
    }

    private void populateNowPlayingWithPlayed()
    {
        SkyDriveAudio audio;
        for (int i = 0; i < PLAYED.size(); i++)
        {
            audio = PLAYED.pop();
            if(audio != null){
                NOW_PLAYING.add(PLAYED.pop());
            }
        }
    }

    public void stopSong()
    {
        try
        {
            mediaPlayer.stop();
            mediaPlayer.reset();
            stopForeground(true);
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "" + e.getMessage());
        }
    }

    public boolean isPlaying()
    {
        if (mediaPlayer == null)
        {
            return false;
        }
        try
        {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e)
        {
            return false;
        }
    }

    public void resumeSong()
    {
        if (NOW_PLAYING.peek() == null)
        {
            return;
        }

        try
        {
            mediaPlayer.start();
            updateNotificationPlayState(R.string.audioPlaying);
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "" + e.getMessage());
        }
    }

    public void pauseSong()
    {
        if (NOW_PLAYING.peek() == null)
        {
            return;
        }

        try
        {
            mediaPlayer.pause();
            updateNotificationPlayState(R.string.audioPaused);
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "" + e.getMessage());
        }
    }

    public void nextSong()
    {
        if (mediaPlayer == null)
        {
            stopForeground(true);
            return;
        }

        if (NOW_PLAYING.peek() == null)
        {
            Toast.makeText(getApplicationContext(), R.string.audioNothingToSkipTo, Toast.LENGTH_SHORT).show();
            return;
        }

        try
        {
            mediaPlayer.stop();
            mediaPlayer.reset();
            PLAYED.push(NOW_PLAYING.poll());
            startPlayback(NOW_PLAYING.peek());
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "" + e.getMessage());
        }
    }

    public void previousSong()
    {
        if (mediaPlayer == null || PLAYED.isEmpty())
        {
            Toast.makeText(getApplicationContext(), R.string.audioNothingToSkipTo, Toast.LENGTH_SHORT).show();
            return;
        }

        try
        {
            mediaPlayer.stop();
            mediaPlayer.reset();
            NOW_PLAYING.addFirst(PLAYED.pop());
            startPlayback(NOW_PLAYING.peek());
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "" + e.getMessage());
        }
    }


    public void toggleShuffle()
    {
        if (this.shuffled)
        {
            restoreUnshuffledPlaylist();
            this.shuffled = false;
        } else
        {
            shufflePlaylist();
            this.shuffled = true;
        }
    }

    private void shufflePlaylist()
    {
        unshuffledPlaylist = (LinkedList<SkyDriveAudio>) NOW_PLAYING.clone();
        for (int i = NOW_PLAYING.size() - 1; i > 0; i--)
        {
            int j = Utility.getRandomNuberFromRange(0, i);
            exchangeNowPlayingQueueItemsAt(j, i);
        }
    }

    private void restoreUnshuffledPlaylist()
    {
        NOW_PLAYING.clear();
        for (SkyDriveAudio skyDriveAudio : unshuffledPlaylist)
        {
            if (skyDriveAudio == null)
            {
                continue;
            }

            NOW_PLAYING.add(skyDriveAudio);
        }
    }

    private void exchangeNowPlayingQueueItemsAt(int j, int i)
    {
        SkyDriveAudio temp = NOW_PLAYING.get(i);
        NOW_PLAYING.set(i, NOW_PLAYING.get(j));
        NOW_PLAYING.set(j, temp);
    }

    public void toggleRepeat()
    {
        repeatPlaylist = !repeatPlaylist;
    }

    public boolean isShuffled()
    {
        return shuffled;
    }

    public boolean isOnRepeat()
    {
        return repeatPlaylist;
    }

    public void populateNowPlayingWithUpdatedQueue(List<SkyDriveAudio> newQueue)
    {
        NOW_PLAYING.clear();
        for(SkyDriveAudio skyDriveAudio : newQueue)
        {
            NOW_PLAYING.add(skyDriveAudio);
        }
    }

    public final LinkedList<SkyDriveAudio> NOW_PLAYING = new LinkedList<SkyDriveAudio>()
    {
        @Override
        public boolean add(SkyDriveAudio skyDriveAudio)
        {
            if (skyDriveAudio == null)
            {
                return false;
            }

            boolean success = super.add(skyDriveAudio);
            return success;
        }
    };

    private void getPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
        isLimitedToWiFi = preferences.getBoolean("limit_all_to_wifi", false);
    }

    private boolean connectionIsUnavailable()
    {
        getPreferences();
        boolean unavailable = (isLimitedToWiFi &&
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo().getType()
                        != ConnectivityManager.TYPE_WIFI);
        return unavailable;
    }

    public void startFirstSong()
    {
        if(NOW_PLAYING.size() != 1)
        {
            return;
        }
        startPlayback(NOW_PLAYING.peek());
    }

    private final Stack<SkyDriveAudio> PLAYED = new Stack<SkyDriveAudio>();


    private boolean hasLocalCache(SkyDriveAudio skyDriveAudio)
    {
        String skyDriveAudioName = "";
        try
        {
            skyDriveAudioName = skyDriveAudio.getName();
        } catch (NullPointerException e)
        {
            return false;
        }

        final File file = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", skyDriveAudioName);
        return file.exists();
    }

    private File getLocalCache(SkyDriveAudio skyDriveAudio)
    {
        return new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", skyDriveAudio.getName());
    }


    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    public class AudioPlaybackServiceBinder extends Binder
    {
        public AudioPlaybackService getService()
        {
            return AudioPlaybackService.this;
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        NOW_PLAYING.clear();
        PLAYED.clear();

        if (mediaPlayer == null)
        {
            return;
        }
        try
        {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        } catch (IllegalStateException e)
        {
            Log.e(Constants.LOGTAG, "" + e.getMessage());
        }
    }
}
