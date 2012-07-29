package com.killerud.skydrive;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import com.killerud.skydrive.CameraImageObserver;


public class CameraObserverService extends Service
{

    private CameraImageObserver cameraImageObserver;

    @Override
    public void onCreate()
    {
        super.onCreate();
        cameraImageObserver = new CameraImageObserver(new Handler(), this);
        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, cameraImageObserver);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}
