package com.killerud.skydrive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Camera;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import com.killerud.skydrive.constants.Constants;
import com.microsoft.live.LiveAuthClient;
import com.microsoft.live.LiveAuthException;
import com.microsoft.live.LiveAuthListener;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveConnectSession;
import com.microsoft.live.LiveStatus;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;


public class CameraObserverService extends Service
{

    private static final String QUEUE_FILE_NAME = "image_paths";
    private boolean isWiFiOnly;
    private boolean isCameraWiFiOnly;
    private boolean serviceIsWanted;
    private XLoader loader;
    private ConnectivityManager connectivityManager;

    @Override
    public void onCreate()
    {
        super.onCreate();
        loader = new XLoader(getApplicationContext());
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
        if(preferences.getBoolean("automatic_camera_upload",false))
        {
            CameraImageObserver cameraImageObserver = new CameraImageObserver(new Handler(), this);
            getApplicationContext().getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true, cameraImageObserver);
            scheduleNextRun();
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if(intent != null)
        {
            if(intent.getAction() != null
                    && intent.getAction().equals(Constants.ACTION_UPLOAD_QUEUE))
            {
                uploadImages(getImageQueue());
            }
        }
        return START_STICKY;
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

    public void uploadImage(String imagePath)
    {
        ArrayList<String> image = new ArrayList<String>();
        image.add(imagePath);
        uploadImages(image);
    }

    public void uploadImages(final ArrayList<String> path){
        if (connectionIsUnavailable())
        {
            pushImagesToQueue(path);
        }else{
            final LiveConnectClient client = ((BrowserForSkyDriveApplication) getApplication()).getConnectClient();
            if (client == null)
            {
                final LiveAuthClient authClient = new LiveAuthClient(getApplicationContext(), Constants.APP_CLIENT_ID);
                ((BrowserForSkyDriveApplication) getApplication()).setAuthClient(authClient);
                authClient.initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
                {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                    {
                        if (status == LiveStatus.CONNECTED)
                        {
                            ((BrowserForSkyDriveApplication) getApplication()).setAuthClient(authClient);
                            ((BrowserForSkyDriveApplication) getApplication()).setSession(session);
                            ((BrowserForSkyDriveApplication) getApplication())
                                    .setConnectClient(new LiveConnectClient(session));

                            loader.uploadFile(((BrowserForSkyDriveApplication) getApplication()).getConnectClient()
                                    , path, "me/skydrive/camera_roll");
                        } else
                        {
                            Log.e(Constants.LOGTAG, "Initialize did not connect. Status is " + status + ".");
                        }
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
                        Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                    }
                });
            } else
            {
                loader.uploadFile(client, path, "me/skydrive/camera_roll");
            }

        }
    }

    private boolean connectionIsUnavailable()
    {
        getPreferences();
        boolean unavailable;
        try
        {
            unavailable = (isWiFiOnly &&
                    (connectivityManager.getActiveNetworkInfo().getType()
                            != ConnectivityManager.TYPE_WIFI))
                    || (isCameraWiFiOnly
                    && (connectivityManager.getActiveNetworkInfo().getType()
                    != ConnectivityManager.TYPE_WIFI)
                    || !serviceIsWanted);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (!preferences.getBoolean("automatic_camera_upload", false))
            {
                unavailable = true;
            }
        } catch (NullPointerException e)
        {
            unavailable = true;
        }
        return unavailable;
    }

    private void getPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
        isWiFiOnly = preferences.getBoolean("limit_all_to_wifi", false);
        isCameraWiFiOnly = preferences.getBoolean("camera_upload_wifi_only", false);
        serviceIsWanted = preferences.getBoolean("automatic_camera_upload",false);

    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }


    public void pushImagesToQueue(ArrayList<String> imagePaths)
    {
        FileOutputStream writer = null;
        try {
            writer = openFileOutput(QUEUE_FILE_NAME, MODE_APPEND);
        } catch (FileNotFoundException e) {
            try {
                writer = openFileOutput(QUEUE_FILE_NAME, MODE_PRIVATE);
            } catch (FileNotFoundException e1) {
                return;
            }
        }
        for(String path : imagePaths)
        {
            final String line = path + System.getProperty("line.separator");
            try {
                writer.write(line.getBytes());
            } catch (IOException e) {
            }
        }
        try {
            writer.close();
        } catch (IOException e) {
        }
    }

    public ArrayList<String> getImageQueue() {
        try {
            final FileInputStream inputStream = openFileInput(QUEUE_FILE_NAME);
            final ArrayList<String> filesInQueue = new ArrayList<String>();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = reader.readLine();
            while(line != null)
            {
                filesInQueue.add(line);
                line = reader.readLine();
            }
            inputStream.close();
            return filesInQueue;
        } catch (FileNotFoundException e) {
            return new ArrayList<String>();
        } catch (IOException e) {
            return new ArrayList<String>();
        }
    }
}
