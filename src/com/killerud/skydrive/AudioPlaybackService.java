package com.killerud.skydrive;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import com.killerud.skydrive.objects.SkyDriveAudio;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

/**
 * Created with IntelliJ IDEA.
 * User: William
 * Date: 04.07.12
 * Time: 22:39
 * To change this template use File | Settings | File Templates.
 */
public class AudioPlaybackService extends Service {

    private final IBinder mBinder = new AudioPlaybackServiceBinder();
    private final static int FOREGROUND_NOTIFICATION_ID = 837;
    private MediaPlayer mediaPlayer;


    @Override
    public void onCreate()
    {
        Intent foregroundNotificationIntent = new Intent(this, AudioControlActivity.class);
        foregroundNotificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent foregroundNotificationPendingIntent = PendingIntent
                .getActivity(this, 1, foregroundNotificationIntent, 0);

        Notification foregroundNotification = new Notification(R.drawable.ic_media_play,
                getString(R.string.app_name), System.currentTimeMillis());

        foregroundNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        mediaPlayer = new MediaPlayer();
    }




    public final Queue<SkyDriveAudio> NOW_PLAYING_QUEUE = new Queue<SkyDriveAudio>() {
        @Override
        public boolean add(SkyDriveAudio skyDriveAudio) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean offer(SkyDriveAudio skyDriveAudio) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public SkyDriveAudio remove() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public SkyDriveAudio poll() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public SkyDriveAudio element() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public SkyDriveAudio peek() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean addAll(Collection<? extends SkyDriveAudio> skyDriveAudios) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void clear() {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean contains(Object o) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean containsAll(Collection<?> objects) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean isEmpty() {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public Iterator<SkyDriveAudio> iterator() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean remove(Object o) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean removeAll(Collection<?> objects) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public boolean retainAll(Collection<?> objects) {
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public int size() {
            return 0;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public Object[] toArray() {
            return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public <T> T[] toArray(T[] ts) {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    };;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class AudioPlaybackServiceBinder extends Binder
    {
        public AudioPlaybackService getService()
        {
            return AudioPlaybackService.this;
        }
    }
}
