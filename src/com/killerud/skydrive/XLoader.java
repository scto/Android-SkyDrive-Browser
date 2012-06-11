package com.killerud.skydrive;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveObject;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/**
 * User: William
 * Date: 09.05.12
 * Time: 17:26
 */
public class XLoader
{

    private NotificationManager mNotificationManager;
    private Notification mNotificationProgress;
    private RemoteViews mNotificationView;
    public static int NOTIFICATION_PROGRESS_ID = 2;
    public static int NOTIFICATION_XLOADED_ID = 1;
    private BrowserActivity mContext;

    private IOUtil mIOUtil;

    private String[] mSupportedFileTypes = new String[]{
            "3g2", "3gp", "ai", "bmp", "chm", "doc", "docm", "docx", "dot", "dotx", "epub", "gif",
            "jpeg", "jpg", "mp4", "one", "pdf", "png", "pot", "potm", "potx", "pps", "ppsm", "ppsx",
            "ppt", "pptm", "pptx", "psd", "tif", "tiff", "txt", "xls", "xlsb", "xlsm", "xlsx",
            "wav", "webp", "wmv"};


    public XLoader(BrowserActivity browserActivity)
    {
        mContext = browserActivity;
        mNotificationManager = (NotificationManager) mContext.getSystemService(Service.NOTIFICATION_SERVICE);
        mIOUtil = new IOUtil();
    }

    /**
     * Handles the uploading of a file. Manages a notification with a progressbar.
     * Works recursively so large upload batches doesn't slow the phone to a crawl.
     *
     * @param client          The LiveConnectClient for communicating with SkyDrive
     * @param localFilePaths  The paths to the local file to be uploaded
     * @param currentFolderId The current SkyDrive folder, the one we upload to
     */
    public void uploadFile(final LiveConnectClient client, final ArrayList<String> localFilePaths, final String currentFolderId)
    {
        if (localFilePaths.size() <= 0)
        {
            mContext.reloadFolder();
            return;
        }

        String localFilePath = localFilePaths.get(localFilePaths.size() - 1);
        final File file = new File(localFilePath);

        String fileExtension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
        boolean supported = false;
        for (int i = 0; i < mSupportedFileTypes.length; i++)
        {
            if (fileExtension.equals(mSupportedFileTypes[i]))
            {
                supported = true;
            }
        }

        if (!supported)
        {
            fileNotSupportedBySkyDriveNotification(file);
            localFilePaths.remove(localFilePaths.size() - 1);
            localFilePaths.trimToSize();
            uploadFile(client, localFilePaths, currentFolderId);
            return;
        }

        if (!file.exists())
        {
            localFilePaths.remove(localFilePaths.size() - 1);
            localFilePaths.trimToSize();
            uploadFile(client, localFilePaths, currentFolderId);
            return;
        }

        createProgressNotification(file.getName(), false);

        final LiveOperation operation =
                client.uploadAsync(currentFolderId,
                        file.getName(),
                        file,
                        new LiveUploadOperationListener()
                        {
                            int lastPercent = 0;

                            @Override
                            public void onUploadProgress(int totalBytes,
                                                         int bytesRemaining,
                                                         LiveOperation operation)
                            {
                                int newPercent = computePercentCompleted(totalBytes, bytesRemaining);
                                /* This is done to limit the amount of updates to the notification
                                 * Restrictionles updating makes the system crash, so beware!
                                 */
                                if (newPercent > lastPercent + 5)
                                {
                                    lastPercent = newPercent;
                                    mNotificationProgress.contentView.setProgressBar(R.id.progressBar, 100,
                                            lastPercent, false);
                                    mNotificationManager.notify(NOTIFICATION_PROGRESS_ID, mNotificationProgress);
                                }
                            }

                            @Override
                            public void onUploadFailed(LiveOperationException exception,
                                                       LiveOperation operation)
                            {
                                mNotificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                Toast.makeText(mContext, mContext.getString(R.string.uploadError), Toast.LENGTH_SHORT).show();

                                localFilePaths.remove(localFilePaths.size() - 1);
                                localFilePaths.trimToSize();
                                uploadFile(client, localFilePaths, currentFolderId);
                            }

                            @Override
                            public void onUploadCompleted(LiveOperation operation)
                            {
                                mNotificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                JSONObject result = operation.getResult();
                                if (result.has(JsonKeys.ERROR))
                                {
                                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                    String message = error.optString(JsonKeys.MESSAGE);
                                    String code = error.optString(JsonKeys.CODE);
                                    Toast.makeText(mContext,
                                            mContext.getString(R.string.uploadError), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                showFileXloadedNotification(file, false);
                                localFilePaths.remove(localFilePaths.size() - 1);
                                localFilePaths.trimToSize();
                                uploadFile(client, localFilePaths, currentFolderId);
                            }
                        });
    }


    /**
     * A user might not want to download and save a file when clicking it, so cache it locally instead.
     * Actual downloading is performed either in-dialog or with select -> download.
     *
     * @param client
     * @param fileId
     */
    public void cacheFile(final LiveConnectClient client, SkyDriveObject fileId)
    {
        createProgressNotification(fileId.getName(), true);

        final File fileToCreateLocally = checkForFileDuplicateAndCreateCopy(
                new File(Environment.getExternalStorageDirectory() + "/Android/data/com.killerud.skydrive/cache/" + fileId.getName()));
        final LiveDownloadOperation operation =
                client.downloadAsync(fileId.getId() + "/content",
                        fileToCreateLocally,
                        new LiveDownloadOperationListener()
                        {
                            int lastPercent = 0;

                            @Override
                            public void onDownloadProgress(int totalBytes,
                                                           int bytesRemaining,
                                                           LiveDownloadOperation operation)
                            {
                                int newPercent = computePercentCompleted(totalBytes, bytesRemaining);
                                if (newPercent > lastPercent + 5)
                                {
                                    lastPercent = newPercent;
                                    mNotificationProgress.contentView.setProgressBar(R.id.progressBar, 100,
                                            lastPercent, false);
                                    mNotificationManager.notify(NOTIFICATION_PROGRESS_ID, mNotificationProgress);
                                }
                            }

                            @Override
                            public void onDownloadFailed(LiveOperationException exception,
                                                         LiveDownloadOperation operation)
                            {
                                mNotificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                Log.e("ASE", exception.getMessage());
                                Toast.makeText(mContext, mContext.getString(R.string.downloadError),
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onDownloadCompleted(LiveDownloadOperation operation)
                            {
                                mNotificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                            }
                        });
    }


    /**
     * Handles the downloading of a file from SkyDrive. Manages a notification with a progressbar.
     * Works recursively so large download batches doesn't slow the phone to a crawl.
     *
     * @param client  The LiveConnectClient for communicating with SkyDrive
     * @param fileIds The ID of the file we wish to download
     */
    public void downloadFiles(final LiveConnectClient client, final ArrayList<SkyDriveObject> fileIds)
    {
        if (fileIds.size() <= 0)
        {
            mContext.reloadFolder();
            return;
        }

        SkyDriveObject skyDriveFile = fileIds.get(fileIds.size() - 1);
        createProgressNotification(skyDriveFile.getName(), true);

        final File fileToCreateLocally = checkForFileDuplicateAndCreateCopy(
                new File(Environment.getExternalStorageDirectory() + "/SkyDrive/" + skyDriveFile.getName()));

        final LiveDownloadOperation operation =
                client.downloadAsync(skyDriveFile.getId() + "/content",
                        fileToCreateLocally,
                        new LiveDownloadOperationListener()
                        {
                            int lastPercent = 0;

                            @Override
                            public void onDownloadProgress(int totalBytes,
                                                           int bytesRemaining,
                                                           LiveDownloadOperation operation)
                            {
                                int newPercent = computePercentCompleted(totalBytes, bytesRemaining);
                                if (newPercent > lastPercent + 5)
                                {
                                    lastPercent = newPercent;
                                    mNotificationProgress.contentView.setProgressBar(R.id.progressBar, 100,
                                            lastPercent, false);
                                    mNotificationManager.notify(NOTIFICATION_PROGRESS_ID, mNotificationProgress);
                                }
                            }

                            @Override
                            public void onDownloadFailed(LiveOperationException exception,
                                                         LiveDownloadOperation operation)
                            {
                                mNotificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                Log.e("ASE", exception.getMessage());

                                Toast.makeText(mContext, mContext.getString(R.string.downloadError),
                                        Toast.LENGTH_SHORT).show();

                                fileIds.remove(fileIds.size() - 1);
                                fileIds.trimToSize();
                                downloadFiles(client, fileIds);
                            }

                            @Override
                            public void onDownloadCompleted(LiveDownloadOperation operation)
                            {
                                mNotificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                showFileXloadedNotification(fileToCreateLocally, true);

                                fileIds.remove(fileIds.size() - 1);
                                fileIds.trimToSize();
                                downloadFiles(client, fileIds);
                            }
                        });
    }

    /**
     * Deletes the files who's SkyDrive IDs are in the given parameter.
     *
     * @param client
     * @param fileIds
     */
    public void deleteFiles(final LiveConnectClient client, final ArrayList<SkyDriveObject> fileIds)
    {
        if (fileIds.size() <= 0)
        {
            mContext.reloadFolder();
            Toast.makeText(mContext,
                    "Deleted file(s)", Toast.LENGTH_SHORT).show();
            return;
        }

        final String fileId = fileIds.get(fileIds.size() - 1).getId();
        client.deleteAsync(fileId, new LiveOperationListener()
        {
            public void onError(LiveOperationException exception, LiveOperation operation)
            {
                Toast.makeText(mContext,
                        "Error deleting file" + (fileIds.size() > 1 ? "s" : ""), Toast.LENGTH_SHORT).show();
                Log.e("ASE", exception.getMessage());
                fileIds.remove(fileIds.size() - 1);
                fileIds.trimToSize();
                deleteFiles(client, fileIds);
            }

            public void onComplete(LiveOperation operation)
            {
                fileIds.remove(fileIds.size() - 1);
                fileIds.trimToSize();
                deleteFiles(client, fileIds);
            }
        });
    }

    /**
     * Pastes the given files to the given folder and cuts/copies depending on the boolean value.
     *
     * @param client
     * @param fileIds
     * @param currentFolder
     * @param cutNotCopy
     */
    public void pasteFiles(final LiveConnectClient client,
                           final ArrayList<SkyDriveObject> fileIds,
                           final String currentFolder, final boolean cutNotCopy)
    {

        if (fileIds.size() <= 0)
        {
            Toast.makeText(mContext,
                    "Moved file(s)",
                    Toast.LENGTH_SHORT).show();
            mContext.reloadFolder();
            return;
        }

        final String fileId = fileIds.get(fileIds.size() - 1).getId();
        if (cutNotCopy)
        {
            client.moveAsync(fileId, currentFolder, new LiveOperationListener()
            {
                public void onError(LiveOperationException exception, LiveOperation operation)
                {
                    Toast.makeText(mContext,
                            "Error moving file" + (fileIds.size() > 1 ? "s" : ""), Toast.LENGTH_SHORT).show();
                    Log.e("ASE", exception.getMessage());
                    fileIds.remove(fileIds.size() - 1);
                    fileIds.trimToSize();
                    pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                }

                public void onComplete(LiveOperation operation)
                {
                    fileIds.remove(fileIds.size() - 1);
                    fileIds.trimToSize();
                    pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                }
            });
        }
        else
        {
            client.copyAsync(fileId, currentFolder, new LiveOperationListener()
            {
                public void onError(LiveOperationException exception, LiveOperation operation)
                {
                    Toast.makeText(mContext,
                            "Error copying file" + (fileIds.size() > 1 ? "s" : ""), Toast.LENGTH_SHORT).show();
                    Log.e("ASE", exception.getMessage());
                    fileIds.remove(fileIds.size() - 1);
                    fileIds.trimToSize();
                    pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                }

                public void onComplete(LiveOperation operation)
                {
                    try{
                        fileIds.remove(fileIds.size() - 1);
                        fileIds.trimToSize();
                        pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                    }catch (IndexOutOfBoundsException e){
                        Toast.makeText(mContext,
                                "Something happened while moving. Some files may not have been moved.",
                                Toast.LENGTH_SHORT).show();
                        mContext.reloadFolder();
                        return;
                    }

                }
            });
        }
    }


    /**
     * Renames the given files using the base name and (for anything above 0) the loop iteration for naming
     * and likewise for description. Batch renaming, yay! :D
     *
     * @param client
     * @param fileIds
     * @param baseName
     * @param baseDescription
     */
    public void renameFiles(final LiveConnectClient client, final ArrayList<String> fileIds,
                            final ArrayList<String> fileNames,
                            final String baseName, final String baseDescription)
    {
        if (fileIds.size() <= 0)
        {
            mContext.setSupportProgressBarIndeterminateVisibility(false);
            Toast.makeText(mContext, "File renamed", Toast.LENGTH_SHORT).show();
            mContext.reloadFolder();
            return;
        }


        final LiveOperationListener operationListener = new LiveOperationListener()
        {
            @Override
            public void onComplete(LiveOperation operation)
            {
                fileIds.remove(fileIds.size() - 1);
                fileIds.trimToSize();
                fileNames.remove(fileNames.size() - 1);
                fileNames.trimToSize();
                renameFiles(client, fileIds, fileNames, baseName, baseDescription);
                Log.i("ASE", "File updated " + operation.getRawResult());
            }

            @Override
            public void onError(LiveOperationException exception, LiveOperation operation)
            {
                Log.e("ASE", exception.getMessage());
                fileIds.remove(fileIds.size() - 1);
                fileIds.trimToSize();
                fileNames.remove(fileNames.size() - 1);
                fileNames.trimToSize();
                renameFiles(client, fileIds, fileNames, baseName, baseDescription);
                Toast.makeText(mContext, "Could not rename file", Toast.LENGTH_SHORT).show();
            }
        };


        String fileId = fileIds.get(fileIds.size() - 1);
        try
        {
            JSONObject body = new JSONObject();
            String extension = "";
            String fileName = fileNames.get(fileNames.size() - 1);
            int index = fileName.lastIndexOf(".");
            /* If a file extension exists (not a folder) */
            if (index != -1)
            {
                extension = fileName.substring(index, fileName.length());
            }
            /* Give name with added file extension, if any */
            body.put("name", baseName + (fileIds.size() - 1 > 0 ? " " + (fileIds.size() - 1) : "") + extension);
            body.put("description", baseDescription + (fileIds.size() - 1 > 0 ? " " + (fileIds.size() - 1) : ""));
            client.putAsync(fileId, body, operationListener);
        } catch (JSONException e)
        {
            Toast.makeText(mContext, "Could not rename file", Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Creates a XLoad notification for the given file name
     *
     * @param fileName
     * @param downloading Whether or not we are downloading. Determines text output.
     */
    private void createProgressNotification(String fileName, boolean downloading)
    {
        mNotificationProgress = new Notification(R.drawable.notification_icon,
                (downloading ? "Downloading " : "Uploading ") + fileName,
                System.currentTimeMillis());


        mNotificationProgress.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotificationProgress.flags |= Notification.FLAG_ONGOING_EVENT;

        mNotificationView = new RemoteViews(mContext.getPackageName(), R.layout.notification_xload);
        mNotificationView.setImageViewResource(R.id.image, R.drawable.notification_icon);
        mNotificationView.setTextViewText(R.id.title, (downloading ? "Downloading " : "Uploading ") + fileName);
        mNotificationView.setProgressBar(R.id.progressBar, 100, 0, false);

        Intent cancelOperation = new Intent(mContext, BrowserActivity.class);
        if (downloading)
        {
            cancelOperation.setAction(Constants.ACTION_CANCEL_DOWN);
        }
        else
        {
            cancelOperation.setAction(Constants.ACTION_CANCEL_UP);
        }

        mNotificationProgress.contentIntent = PendingIntent.getActivity(
                mContext, 0, cancelOperation, 0);
        mNotificationProgress.contentView = mNotificationView;

        mNotificationManager.notify(NOTIFICATION_PROGRESS_ID, mNotificationProgress);
    }


    private int computePercentCompleted(int totalBytes, int bytesRemaining)
    {
        return (int) (((float) (totalBytes - bytesRemaining)) / totalBytes * 100);
    }


    /**
     * Checks the local storage for duplicates. If they exist, finds the next available name recursively.
     *
     * @param file The file to check for duplicates
     * @return The reference to the file with its available file name
     */
    private File checkForFileDuplicateAndCreateCopy(File file)
    {
        if (!file.exists())
        {
            return file;
        }
        int index = file.getName().lastIndexOf(".");
        String extension = "";
        String fileName = file.getName();
        if (index != -1)
        {
            extension = file.getName().substring(index, fileName.length());
            fileName = fileName.substring(0, index);
        }
        int copyNr = 1;
        File result = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/" +
                fileName + " Copy " + copyNr + extension);
        boolean availableFileNameFound = false;

        while (availableFileNameFound)
        {
            if (result.exists())
            {
                copyNr++;
                result = new File(file.getName().substring(0, index) +
                        " Copy " + copyNr + file.getName().substring(index, file.getName().length()));
            }
            else
            {
                availableFileNameFound = true;
            }
        }
        return result;
    }


    /**
     * Pings the user with a notification that a file was either downloaded or
     * uploaded, depending on the given boolean. True = download, false = up.
     *
     * @param file     The file to be displayed
     * @param download Whether or not this is a download notification
     */
    public void showFileXloadedNotification(File file, boolean download)
    {
        int icon = R.drawable.notification_icon;
        CharSequence tickerText = file.getName() + " saved " + (download ? "from" : "to") + "SkyDrive!";
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        Context context = mContext;
        CharSequence contentTitle = mContext.getString(R.string.appName);
        CharSequence contentText = file.getName() + " was saved to your " + (download ? "phone" : "SkyDrive") + "!";

        Intent notificationIntent;

        if (download)
        {
            Uri path = Uri.fromFile(file);
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setDataAndType(path, mIOUtil.findMimeTypeOfFile(file));
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        else
        {
            notificationIntent = new Intent(context, XLoader.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(NOTIFICATION_XLOADED_ID, notification);
    }


    /**
     * Notifies the user that the given file is unsupported by SkyDrive and has promptly been skipped for upload
     *
     * @param file
     */
    private void fileNotSupportedBySkyDriveNotification(File file)
    {
        int icon = R.drawable.notification_icon;
        CharSequence tickerText = file.getName() + " cannot be uploaded to SkyDrive by third-party apps and has been skipped...";
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);

        Context context = mContext;
        CharSequence contentTitle = mContext.getString(R.string.appName);
        CharSequence contentText = file.getName() + " is of a file type that cannot be uploaded to SkyDrive by third-party apps and has been skipped...";

        Intent notificationIntent = new Intent(context, XLoader.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(NOTIFICATION_XLOADED_ID, notification);
    }

}
