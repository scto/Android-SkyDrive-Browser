package com.killerud.skydrive;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.preference.PreferenceManager;
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
    boolean mAllWifiOnly;
    boolean mCameraWifiOnly;
    ConnectivityManager mConnectivityManager;
    BrowserForSkyDriveApplication mApp;

    @Override
    public void onCreate()
    {
        mApp = (BrowserForSkyDriveApplication) getApplication();
        final LiveAuthClient liveAuthClient = new LiveAuthClient(this, com.killerud.skydrive.constants.Constants.APP_CLIENT_ID);
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        mApp.setAuthClient(liveAuthClient);

        if(connectionIsUnavailable()){
            onDestroy();
        }else{
            final String cameraFilePath = Environment.getExternalStorageDirectory() + "/DCIM/Camera/";
            final XLoader skyDriveFileLoader = new XLoader(
                    ((BrowserForSkyDriveApplication) getApplication()).getCurrentBrowser());

            setupFileObserver(mApp, cameraFilePath, skyDriveFileLoader);



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
        }
    }

    private void setupFileObserver(final BrowserForSkyDriveApplication browserForSkyDriveApplication, final String cameraFilePath, final XLoader skyDriveFileLoader)
    {
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
                        if(connectionIsUnavailable()){
                            return;
                        }else{
                            ArrayList fileNameContainer = new ArrayList<String>();
                            fileNameContainer.add(cameraFilePath + fileName);
                            skyDriveFileLoader.uploadFile(browserForSkyDriveApplication.getConnectClient(),
                                    fileNameContainer
                                    , "me/skydrive/camera_roll");

                        }
                    }
                }
            }
        };
        mNewCameraImagesObserver.startWatching();
    }

    private boolean connectionIsUnavailable()
    {
        getPreferences();
        boolean unavailable;
        try{
            unavailable = (mAllWifiOnly &&
                    (mConnectivityManager.getActiveNetworkInfo().getType()
                            != ConnectivityManager.TYPE_WIFI))
                    || (mCameraWifiOnly
                    && (mConnectivityManager.getActiveNetworkInfo().getType()
                    != ConnectivityManager.TYPE_WIFI));
        }catch (NullPointerException e)
        {
            unavailable = true;
        }
        return unavailable;
    }

    private void getPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mApp);
        mAllWifiOnly = preferences.getBoolean("limit_all_to_wifi", false);
        mCameraWifiOnly = preferences.getBoolean("camera_upload_wifi_only", false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return Service.START_STICKY;
    }


    public void stopWatchingForNewImages()
    {
        if(mNewCameraImagesObserver != null) mNewCameraImagesObserver.stopWatching();
    }

    public void startWatchingForNewImages()
    {
        if(mNewCameraImagesObserver != null) mNewCameraImagesObserver.startWatching();
    }


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if(mNewCameraImagesObserver != null) mNewCameraImagesObserver.stopWatching();
    }
}
