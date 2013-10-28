package com.killerud.skydrive;

import android.content.ContentUris;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import com.killerud.skydrive.util.Stopwatch;


public class CameraImageObserver extends ContentObserver
{
    private final CameraObserverService observerService;
    private int latestMediaId;


    public CameraImageObserver(Handler handler, CameraObserverService observerService)
    {
        super(handler);
        this.observerService = observerService;
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
        if (imagePath == null)
        {
            return;
        }

        observerService.uploadImage(imagePath);
    }

    private int getIdOfLatestImageAddedToMediaStore()
    {
        String[] columns = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN};
        Cursor cursor = observerService.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
                MediaStore.Images.Media._ID + " DESC");

        if (cursor == null)
        {
            return -1;
        }
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

        if (isNewCameraImage(cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN))))
        {
            latestMediaId = topMediaIdFromDatabase;
            cursor.close();
            return topMediaIdFromDatabase;
        } else
        {
            latestMediaId = topMediaIdFromDatabase;
            cursor.close();
            return -1;
        }
    }

    private boolean isNewCameraImage(String dateTaken)
    {
        try{
            long dateTakenUnixTime = Long.parseLong(dateTaken);
            //if the image is less than thirty seconds old we consider the image "new"
            long currentTime = System.currentTimeMillis();
            if(dateTakenUnixTime+30000>currentTime)
            {
                return true;
            }
            else
            {
                return false;
            }
        }catch (NumberFormatException e)
        {
            Log.e("AEfS", e.getMessage());
        }
        return false;
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
        Cursor cursor = observerService.getContentResolver().query(image, columns, null, null, null);
        if (cursor == null)
        {
            cursor = observerService.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
                    MediaStore.Images.Media._ID + " DESC");
        }
        if (cursor != null && cursor.moveToFirst())
        {
            Stopwatch stopwatch = new Stopwatch();
            while (true)
            {

                if (stopwatch.elapsedTimeInSeconds() > 10)
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
