package com.killerud.skydrive;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Toast;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.microsoft.live.LiveConnectClient;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

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
    private LiveConnectClient connectClient;

    private boolean notPlaying;
    private Notification foregroundNotification;
    private PendingIntent foregroundNotificationPendingIntent;


    @Override
    public void onCreate()
    {
        super.onCreate();
        notPlaying = true;
        foregroundNotification = createForegroundNotification();
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification);
        mediaPlayer = createMediaPlayer();
        connectClient = ((BrowserForSkyDriveApplication) getApplication()).getConnectClient();
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
    }

    private void onPlayerCompletion()
    {
        mediaPlayer.reset();
        NOW_PLAYING_QUEUE.poll();
        updateUI(NOW_PLAYING_QUEUE.peek());
        startPlayback(NOW_PLAYING_QUEUE.peek());
    }

    public void updateUI()
    {
        updateUI(NOW_PLAYING_QUEUE.peek());
    }

    public void updateUI(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;
        updateNotificationWithNewAudio(skyDriveAudio);
        broadcastNewSongForActivityUpdate(skyDriveAudio);
    }

    private void broadcastNewSongForActivityUpdate(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;
        Intent broadcast = new Intent();
        broadcast.setAction(Constants.ACTION_SONG_CHANGE);
        broadcast.putExtra(Constants.EXTRA_SONG_TITLE, skyDriveAudio.getName());
        sendBroadcast(broadcast);
    }

    private void updateNotificationWithNewAudio(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null) return;
        foregroundNotification.setLatestEventInfo(getApplicationContext(),
                  getString(R.string.audioPlaying) + " " +
                          audioTitle(skyDriveAudio),
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
        SkyDriveAudio skyDriveAudio = NOW_PLAYING_QUEUE.peek();
        foregroundNotification.setLatestEventInfo(getApplicationContext(),
                songState + " " + audioTitle(skyDriveAudio),
                getString(R.string.audioReturnToControls),
                foregroundNotificationPendingIntent);
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification);
    }

    public String audioTitle(SkyDriveAudio skyDriveAudio)
    {
        return (skyDriveAudio.getTitle()!=null?skyDriveAudio.getTitle():
            (skyDriveAudio.getName()!=null?skyDriveAudio.getName():"audio"));
    }

    private void updateNotificationNotPlaying()
    {
        foregroundNotification.setLatestEventInfo(getApplicationContext(),
                getString(R.string.audioNotPlayingSummary),
                getString(R.string.audioReturnToControls),
                foregroundNotificationPendingIntent);
        startForeground(FOREGROUND_NOTIFICATION_ID, foregroundNotification);
    }

    private void startPlayback(SkyDriveAudio skyDriveAudio)
    {
        if(skyDriveAudio == null)
        {
            updateNotificationNotPlaying();
            notPlaying = true;
            return;
        }

        notPlaying = false;

        try
        {
            if (hasLocalCache(skyDriveAudio))
            {
                mediaPlayer.setDataSource(getLocalCache(skyDriveAudio).getPath());
            } else
            {
                mediaPlayer.setDataSource(skyDriveAudio.getSource());
            }

            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepareAsync();
        } catch (IllegalArgumentException e)
        {
            showToast(e.getMessage());
        } catch (IllegalStateException e)
        {
            showToast(e.getMessage());
        } catch (IOException e)
        {
            showToast(e.getMessage());
        }
    }

    private void startFirstSongPlayback(SkyDriveAudio skyDriveAudio)
    {
        updateUI(skyDriveAudio);
        startPlayback(skyDriveAudio);
    }

    public void stopSong()
    {
        try
        {
            mediaPlayer.stop();
            mediaPlayer.reset();
            updateNotificationPlayState(R.string.audioStopped);
        }catch (IllegalStateException e)
        {
        }
    }

    public void playSong()
    {
        try{
            startPlayback(NOW_PLAYING_QUEUE.peek());
            updateNotificationPlayState(R.string.audioPlaying);
        }catch (IllegalStateException e)
        {
        }
    }

    public boolean isPlaying()
    {
        assert mediaPlayer != null;
        return mediaPlayer.isPlaying();
    }

    public void pauseSong()
    {
        try{
            mediaPlayer.pause();
            updateNotificationPlayState(R.string.audioPaused);
        }catch (IllegalStateException e)
        {
        }
    }

    public void nextSong()
    {
        mediaPlayer.reset();
        NOW_PLAYING_QUEUE.poll();
        startPlayback(NOW_PLAYING_QUEUE.peek());
    }

    public final Queue<SkyDriveAudio> NOW_PLAYING_QUEUE = new Queue<SkyDriveAudio>()
    {
        private LinkedList<SkyDriveAudio> nowPlayingList = new LinkedList<SkyDriveAudio>();

        @Override
        public boolean add(SkyDriveAudio skyDriveAudio)
        {
            boolean success = nowPlayingList.add(skyDriveAudio);
            if (success && firstAudioAdded())
            {
                startFirstSongPlayback(nowPlayingList.peek());
            }
            return success;
        }

        private boolean firstAudioAdded()
        {
            return nowPlayingList.size() == 1;
        }

        @Override
        public boolean offer(SkyDriveAudio skyDriveAudio)
        {
            boolean success;
            try
            {
                success = nowPlayingList.add(skyDriveAudio);
            } catch (Exception e)
            {
                success = false;
            }
            return success;
        }

        @Override
        public SkyDriveAudio remove()
        {
            return nowPlayingList.removeFirst();
        }

        @Override
        public SkyDriveAudio poll()
        {
            return nowPlayingList.pollFirst();
        }

        @Override
        public SkyDriveAudio element()
        {
            return nowPlayingList.element();
        }

        @Override
        public SkyDriveAudio peek()
        {
            return nowPlayingList.peekFirst();
        }

        @Override
        public boolean addAll(Collection<? extends SkyDriveAudio> skyDriveAudios)
        {
            return nowPlayingList.addAll(skyDriveAudios);
        }

        @Override
        public void clear()
        {
            nowPlayingList.clear();
        }

        @Override
        public boolean contains(Object o)
        {
            return nowPlayingList.contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> objects)
        {
            return nowPlayingList.containsAll(objects);
        }

        @Override
        public boolean isEmpty()
        {
            return nowPlayingList.isEmpty();
        }

        @Override
        public Iterator<SkyDriveAudio> iterator()
        {
            return nowPlayingList.iterator();
        }

        @Override
        public boolean remove(Object o)
        {
            return nowPlayingList.remove(o);
        }

        @Override
        public boolean removeAll(Collection<?> objects)
        {
            return nowPlayingList.removeAll(objects);
        }

        @Override
        public boolean retainAll(Collection<?> objects)
        {
            return nowPlayingList.retainAll(objects);
        }

        @Override
        public int size()
        {
            return nowPlayingList.size();
        }

        @Override
        public Object[] toArray()
        {
            return nowPlayingList.toArray();
        }

        @Override
        public <T> T[] toArray(T[] ts)
        {
            return nowPlayingList.toArray(ts);
        }
    };


    private boolean hasLocalCache(SkyDriveAudio skyDriveAudio)
    {
        final File file = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", skyDriveAudio.getName());
        return file.exists();
    }

    private File getLocalCache(SkyDriveAudio skyDriveAudio)
    {
        return new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", skyDriveAudio.getName());
    }

    private void showToast(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

        if (mediaPlayer == null)
        {
            return;
        }
        try
        {
            mediaPlayer.stop();
        } catch (IllegalStateException e)
        {
        } finally
        {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public boolean notPlaying()
    {
        return this.notPlaying;
    }
}
