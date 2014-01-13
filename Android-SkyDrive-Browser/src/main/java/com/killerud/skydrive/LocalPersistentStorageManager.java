package com.killerud.skydrive;

import android.os.Environment;
import android.util.Log;
import com.killerud.skydrive.constants.Constants;
import java.io.File;


public class LocalPersistentStorageManager {
    public static final long THUMBS_MAX_SIZE = 10485760; //10MB
    public static final long FILES_MAX_SIZE = 104857600; //100MB

    public void createLocalSkyDriveFolderIfNotExists()
    {
        File sdcard = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/");
        if (!sdcard.exists())
        {
            sdcard.mkdir();
        }
    }

    public void pruneCache(final File cacheFolder, final long maxSize)
    {
        /* No cache for us to prune */
        if (!cacheFolder.exists())
        {
            return;
        }

        /* This block could potentially be a while, so run it in a new thread */
        new Thread(new Runnable()
        {
            public void run()
            {
                File[] cacheContents = cacheFolder.listFiles();
                long cacheSize = 0l;

                for (int i = 0; i < cacheContents.length; i++)
                {
                    cacheSize += cacheContents[i].length();
                }

                if (cacheSize > maxSize)
                {

                    boolean cachePruned = false;
                    int fileIndex = 0;

                    while (!cachePruned)
                    {
                        try
                        {
                            cacheSize -= cacheContents[fileIndex].length();
                            cacheContents[fileIndex].delete();
                            Log.i(Constants.LOGTAG, "File cache pruned");
                        } catch (IndexOutOfBoundsException e)
                        {
                            cachePruned = true;
                            Log.e(Constants.LOGTAG, "Error on file cache prune. " + e.getMessage());
                        } finally
                        {
                            if (cacheSize < cacheSize - 50)
                            {
                                cachePruned = true;
                            }

                            fileIndex++;
                        }
                    }
                }
            }
        }).start();
    }
}
