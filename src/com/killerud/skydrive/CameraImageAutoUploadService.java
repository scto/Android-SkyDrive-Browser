package com.killerud.skydrive;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import com.microsoft.live.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: William
 * Date: 10.06.12
 * Time: 19:46
 */
public class CameraImageAutoUploadService extends Service
{
    private final IBinder mBinder = new AutoUploadServiceBinder();

    public class AutoUploadServiceBinder extends Binder
    {
        public CameraImageAutoUploadService getService()
        {
            return CameraImageAutoUploadService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    /* Binder stuff ends here */
    boolean mConnectedToSkyDrive;
    FileObserver mNewCameraImagesObserver;

    @Override
    public void onCreate()
    {
        final BrowserForSkyDriveApplication browserForSkyDriveApplication = (BrowserForSkyDriveApplication) getApplication();
        final LiveAuthClient liveAuthClient = new LiveAuthClient(this, com.killerud.skydrive.constants.Constants.APP_CLIENT_ID);
        browserForSkyDriveApplication.setAuthClient(liveAuthClient);

        liveAuthClient.initialize(Arrays.asList(com.killerud.skydrive.constants.Constants.APP_SCOPES), new LiveAuthListener()
        {
            @Override
            public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
            {
                if (status == LiveStatus.CONNECTED)
                {
                    mConnectedToSkyDrive = true;
                }
                else
                {
                    onDestroy();
                }
            }

            @Override
            public void onAuthError(LiveAuthException exception, Object userState)
            {
                onDestroy();
            }
        });

        final String cameraFilePath = Environment.getExternalStorageDirectory() + "/DCIM/Camera/";

        final XLoader skyDriveFileLoader = new XLoader(
                ((BrowserForSkyDriveApplication) getApplication()).getCurrentBrowser());

        mNewCameraImagesObserver = new FileObserver(cameraFilePath)
        {
            @Override
            public void onEvent(int eventCode, String fileName)
            {
                /* The file .probe is created every time the camera is launched */
                if (eventCode == FileObserver.CREATE && !fileName.equals(".probe"))
                {
                    if (mConnectedToSkyDrive)
                    {
                        ArrayList fileNameContainer = new ArrayList<String>();
                        fileNameContainer.add(cameraFilePath + fileName);
                        skyDriveFileLoader.uploadFile(browserForSkyDriveApplication.getConnectClient(),
                                fileNameContainer
                                , "me/skydrive/camera_roll");
                    }
                }
            }
        };

        mNewCameraImagesObserver.startWatching();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        /*
            "If you need your service to keep running,
            but you can live with interruptions from time to time,
            return START_STICKY from onStartCommand()"
            - http://stackoverflow.com/questions/6951868/service-getting-stopped-even-after-using-alarm-manager
        */
        return Service.START_STICKY;
    }


    public void stopWatchingForNewImages()
    {
        assert mNewCameraImagesObserver != null;
        mNewCameraImagesObserver.stopWatching();
    }

    public void startWatchingForNewImages()
    {
        assert mNewCameraImagesObserver != null;
        mNewCameraImagesObserver.startWatching();
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        assert mNewCameraImagesObserver != null;
        mNewCameraImagesObserver.stopWatching();
    }
}
