package com.killerud.skydrive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.text.format.Time;


public class CameraObserverService extends Service
{

    private CameraImageObserver cameraImageObserver;

    @Override
    public void onCreate()
    {
        super.onCreate();
        cameraImageObserver = new CameraImageObserver(new Handler(), this);
        getApplicationContext().getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, cameraImageObserver);
        scheduleNextRun();
    }


    private void scheduleNextRun()
    {
        Intent i = new Intent(this, this.getClass());
        PendingIntent pi = PendingIntent.getService(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        long currentTimeMillis = System.currentTimeMillis();
        long  nextUpdateTimeMillis = currentTimeMillis + 15 * DateUtils.MINUTE_IN_MILLIS;
        Time nextUpdateTime = new Time();
        nextUpdateTime.set(nextUpdateTimeMillis);

        /* Not many pictures will be taken between 01:00 and 07:00 (probably)
        , so don't restart until morning to reduce resource consumption somewhat */
        if (nextUpdateTime.hour < 7 || nextUpdateTime.hour >= 1)
        {
            nextUpdateTime.hour = 6;
            nextUpdateTime.minute = 0;
            nextUpdateTime.second = 0;
            nextUpdateTimeMillis = nextUpdateTime.toMillis(false) + DateUtils.DAY_IN_MILLIS;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, nextUpdateTimeMillis, pi);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}
