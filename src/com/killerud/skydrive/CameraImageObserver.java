package com.killerud.skydrive;

import android.content.ContentUris;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import com.killerud.skydrive.util.Stopwatch;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: William
 * Date: 29.07.12
 * Time: 18:24
 * To change this template use File | Settings | File Templates.
 */
public class CameraImageObserver extends ContentObserver
{
    private CameraObserverService context;
    private int latestMediaId;
    XLoader loader;
    private boolean isWiFiOnly;
    private boolean isCameraWiFiOnly;
    private ConnectivityManager connectivityManager;

    public CameraImageObserver(Handler handler, CameraObserverService context)
    {
        super(handler);
        this.context = context;
        loader = new XLoader(null);
        connectivityManager = (ConnectivityManager) context.getSystemService(context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void onChange(boolean selfChange)
    {
        int id = getIdOfLatestImageAddedToMediaStore();
        if (id == -1)
        {
            return;
        }

        String imagePath = getLatestCameraImagePathFromMediaStore();
        if(imagePath == null)
        {
            return;
        }

        ArrayList<String> path = new ArrayList<String>();
        path.add(imagePath);
        if(!connectionIsUnavailable())
        {
            loader.uploadFile(((BrowserForSkyDriveApplication) context.getApplication())
                    .getConnectClient(), path, "me/skydrive/camera_roll");
        }
    }


    private boolean connectionIsUnavailable()
    {
        getPreferences();
        boolean unavailable;
        try{
            unavailable = (isWiFiOnly &&
                    (connectivityManager.getActiveNetworkInfo().getType()
                            != ConnectivityManager.TYPE_WIFI))
                    || (isCameraWiFiOnly
                    && (connectivityManager.getActiveNetworkInfo().getType()
                    != ConnectivityManager.TYPE_WIFI));
        }catch (NullPointerException e)
        {
            unavailable = true;
        }
        return unavailable;
    }

    private void getPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplication());
        isWiFiOnly = preferences.getBoolean("limit_all_to_wifi", false);
        isCameraWiFiOnly = preferences.getBoolean("camera_upload_wifi_only", false);
    }

    private int getIdOfLatestImageAddedToMediaStore()
    {
        String[] columns = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN};
        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
                MediaStore.Images.Media._ID + " DESC");

        if (!cursor.moveToFirst())
        {
            cursor.close();
            return -1;
        }

        int topMediaIdFromDatabase = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media._ID));
        if (changeOrDeletionInMediaStoreSinceLastInvocation(topMediaIdFromDatabase))
        {
            latestMediaId = topMediaIdFromDatabase;
            cursor.close();
            return -1;
        }

        if (!isCameraImage(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN))))
        {
            latestMediaId = topMediaIdFromDatabase;
            cursor.close();
            return -1;
        }

        latestMediaId = topMediaIdFromDatabase;
        cursor.close();
        return topMediaIdFromDatabase;
    }

    private boolean isCameraImage(String imageOrientation)
    {
        return (imageOrientation != null);
    }

    private boolean changeOrDeletionInMediaStoreSinceLastInvocation(int topMediaIdFromDatabase)
    {
        return (topMediaIdFromDatabase <= latestMediaId);
    }

    public String getLatestCameraImagePathFromMediaStore()
    {
        String path = null;
        String[] columns = new String[]{MediaStore.Images.Media.DATA, MediaStore.Images.Media.MINI_THUMB_MAGIC};


        Uri image = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, latestMediaId);
        Cursor cursor = context.getContentResolver().query(image, columns, null, null, null);
        if(cursor == null)
        {
            cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
                    MediaStore.Images.Media._ID + " DESC");
        }
        if (cursor != null && cursor.moveToFirst())
        {
            Stopwatch stopwatch = new Stopwatch();
            while (true)
            {

                if(stopwatch.elapsedTimeInSeconds()>10)
                {
                    path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    break;
                }

                String thumb = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.MINI_THUMB_MAGIC));
                if (thumb != null)
                {
                    path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    break;
                }
            }
        }
        cursor.close();
        return path;
    }
}
