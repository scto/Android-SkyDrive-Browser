package com.killerud.skydrive;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.constants.SortCriteria;
import com.killerud.skydrive.objects.SkyDriveObject;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: William
 * Date: 09.05.12
 * Time: 17:26
 */
public class XLoader
{

    private NotificationManager notificationManager;
    private Notification notificationProgress;
    public static int NOTIFICATION_PROGRESS_ID = 2;
    public static int NOTIFICATION_XLOADED_ID = 1;
    private Context context;
    private boolean contextIsBrowserActivity;
    private boolean notificationIsAvailable;

    private IOUtil ioUtil;

    public XLoader(Context context)
    {
        if(context.getClass().equals(BrowserActivity.class))
        {
            contextIsBrowserActivity = true;
        }
        else
        {
            contextIsBrowserActivity = false;
        }

        this.context = context;
        try
        {
            notificationManager = (NotificationManager) this.context.getSystemService(Service.NOTIFICATION_SERVICE);
            notificationIsAvailable = true;
        } catch (IllegalStateException e)
        {
            notificationIsAvailable = false;
        } catch (NullPointerException e)
        {
            notificationIsAvailable = false;
        }
        ioUtil = new IOUtil();
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
            if (context != null && contextIsBrowserActivity)
            {
                ((BrowserActivity)context).setDefaultBrowserBehaviour();
                ((BrowserActivity)context).reloadFolder();
            }
            return;
        }
        if (localFilePaths.size() > 99)
        {
            for (int i = localFilePaths.size() - 1; i > 99; i--)
            {
                localFilePaths.remove(i);
            }

            if (context != null)
            {
                Toast.makeText(context, R.string.errorTooManyFilesAtOnce, Toast.LENGTH_SHORT).show();
            }
        }

        String localFilePath = localFilePaths.get(localFilePaths.size() - 1);
        final File file = new File(localFilePath);

        InputStream fileStream;
        try
        {
            fileStream = new FileInputStream(file);
        } catch (FileNotFoundException e)
        {
            fileNotFoundNotification(file);
            localFilePaths.remove(localFilePaths.size() - 1);
            localFilePaths.trimToSize();
            uploadFile(client, localFilePaths, currentFolderId);
            return;
        }

        createProgressNotification(file.getName(), false);

        try
        {
            final LiveOperation operation =
                    client.uploadAsync(currentFolderId,
                            file.getName(), file, OverwriteOption.Overwrite,
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
                                    if (newPercent > lastPercent + 5 && notificationIsAvailable)
                                    {
                                        lastPercent = newPercent;
                                        notificationProgress.contentView.setProgressBar(R.id.progressBar, 100,
                                                lastPercent, false);
                                        notificationManager.notify(NOTIFICATION_PROGRESS_ID, notificationProgress);
                                    }
                                }

                                @Override
                                public void onUploadFailed(LiveOperationException exception,
                                                           LiveOperation operation)
                                {
                                    if (notificationIsAvailable)
                                    {
                                        notificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                    }
                                    if (context != null)
                                    {
                                        Toast.makeText(context, context.getString(R.string.uploadError), Toast.LENGTH_SHORT).show();
                                    }

                                    try
                                    {
                                        localFilePaths.remove(localFilePaths.size() - 1);
                                        localFilePaths.trimToSize();
                                        uploadFile(client, localFilePaths, currentFolderId);
                                    } catch (IndexOutOfBoundsException e)
                                    {
                                        return;
                                    }
                                }

                                @Override
                                public void onUploadCompleted(LiveOperation operation)
                                {
                                    if (notificationIsAvailable)
                                    {
                                        notificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                    }
                                    JSONObject result = operation.getResult();
                                    if (result.has(JsonKeys.ERROR))
                                    {
                                        JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                        String message = error.optString(JsonKeys.MESSAGE);
                                        String code = error.optString(JsonKeys.CODE);
                                        if (context != null)
                                        {
                                            Toast.makeText(context,
                                                    context.getString(R.string.uploadError), Toast.LENGTH_SHORT).show();
                                        }
                                        return;
                                    }
                                    showFileXloadedNotification(file, false);
                                    try
                                    {
                                        localFilePaths.remove(localFilePaths.size() - 1);
                                        localFilePaths.trimToSize();
                                        uploadFile(client, localFilePaths, currentFolderId);
                                    } catch (IndexOutOfBoundsException e)
                                    {
                                        return;
                                    }
                                }
                            }, null);
        } catch (IllegalStateException e)
        {
            handleIllegalConnectionState();
        }
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
            if (context != null && contextIsBrowserActivity)
            {
                ((BrowserActivity)context).reloadFolder();
            }
            return;
        }

        if (fileIds.size() > 99)
        {
            for (int i = fileIds.size() - 1; i > 99; i--)
            {
                fileIds.remove(i);
            }

            if (context != null)
            {
                Toast.makeText(context, R.string.errorTooManyFilesAtOnce, Toast.LENGTH_SHORT).show();
            }
        }


        final SkyDriveObject skyDriveFile = fileIds.get(fileIds.size() - 1);
        createProgressNotification(skyDriveFile.getName(), true);

        if (skyDriveFile.getType().equals("folder"))
        {
            addFolderFilesToDownloadList(client, fileIds, skyDriveFile);
            fileIds.remove(skyDriveFile);
            return;
        }

        final File fileToCreateLocally = checkForFileDuplicateAndCreateCopy(skyDriveFile);
        if (!fileToCreateLocally.getParentFile().exists())
        {
            fileToCreateLocally.getParentFile().mkdirs();
        }

        try
        {
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
                                    if (newPercent > lastPercent + 5 && notificationIsAvailable)
                                    {
                                        lastPercent = newPercent;
                                        notificationProgress.contentView.setProgressBar(R.id.progressBar, 100,
                                                lastPercent, false);
                                        notificationManager.notify(NOTIFICATION_PROGRESS_ID, notificationProgress);
                                    }
                                }

                                @Override
                                public void onDownloadFailed(LiveOperationException exception,
                                                             LiveDownloadOperation operation)
                                {
                                    if (notificationIsAvailable)
                                    {
                                        notificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                    }

                                    Log.e("ASE", exception.getMessage());
                                    if (!skyDriveFile.getType().equals("folder"))
                                    {
                                        if (context != null)
                                        {

                                            Toast.makeText(context, context.getString(R.string.downloadError),
                                                    Toast.LENGTH_SHORT).show();

                                        }

                                        try
                                        {
                                            fileIds.remove(fileIds.size() - 1);
                                        } catch (IndexOutOfBoundsException e)
                                        {
                                            return;
                                        }
                                    }

                                    fileIds.trimToSize();
                                    downloadFiles(client, fileIds);
                                }

                                @Override
                                public void onDownloadCompleted(LiveDownloadOperation operation)
                                {
                                    if (notificationIsAvailable)
                                    {
                                        notificationManager.cancel(NOTIFICATION_PROGRESS_ID);
                                    }
                                    showFileXloadedNotification(fileToCreateLocally, true);

                                    try
                                    {
                                        fileIds.remove(fileIds.size() - 1);
                                        fileIds.trimToSize();
                                        downloadFiles(client, fileIds);
                                    } catch (IndexOutOfBoundsException e)
                                    {
                                        return;
                                    }
                                }
                            });
        } catch (IllegalStateException e)
        {
            handleIllegalConnectionState();
        }
    }

    private void addFolderFilesToDownloadList(final LiveConnectClient client, final ArrayList<SkyDriveObject> fileIds,
                                              final SkyDriveObject skyDriveFolder)
    {
        skyDriveFolder.setLocalDownloadLocation(skyDriveFolder.getLocalDownloadLocation() + skyDriveFolder.getName());

        final File folder = new File(skyDriveFolder.getLocalDownloadLocation());
        folder.mkdirs();

        try
        {
            client.getAsync(skyDriveFolder.getId() + "/files?sort_by=" +
                    SortCriteria.NAME + "&sort_order=" + SortCriteria.ASCENDING, new LiveOperationListener()
            {
                @Override
                public void onComplete(LiveOperation operation)
                {
                    JSONObject result = operation.getResult();
                    if (result.has(JsonKeys.ERROR))
                    {
                        JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                        String message = error.optString(JsonKeys.MESSAGE);
                        String code = error.optString(JsonKeys.CODE);
                        Log.e("ASE", code + ": " + message);
                        return;
                    }

                    JSONArray data = result.optJSONArray(JsonKeys.DATA);
                    for (int i = 0; i < data.length(); i++)
                    {
                        SkyDriveObject skyDriveObj = SkyDriveObject.create(data.optJSONObject(i));
                        skyDriveObj.setLocalDownloadLocation(folder.getPath());
                        fileIds.add(skyDriveObj);
                    }

                    downloadFiles(client, fileIds);
                }

                @Override
                public void onError(LiveOperationException exception, LiveOperation operation)
                {
                    Log.e("ASE", exception.getMessage());
                }
            });
        } catch (IllegalStateException e)
        {
            handleIllegalConnectionState();
        }
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
            if (context != null && contextIsBrowserActivity)
            {
                ((BrowserActivity)context).reloadFolder();
                Toast.makeText(context,
                        context.getString(R.string.deletedFiles), Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (fileIds.size() > 99)
        {
            for (int i = fileIds.size() - 1; i > 99; i--)
            {
                fileIds.remove(i);
            }

            if (context != null)
            {

                Toast.makeText(context, R.string.errorTooManyFilesAtOnce, Toast.LENGTH_SHORT).show();
            }
        }

        final String fileId = fileIds.get(fileIds.size() - 1).getId();
        try
        {
            client.deleteAsync(fileId, new LiveOperationListener()
            {
                public void onError(LiveOperationException exception, LiveOperation operation)
                {
                    if (context != null)
                    {
                        Toast.makeText(context, R.string.errorDeletingFile, Toast.LENGTH_SHORT).show();
                    }
                    Log.e("ASE", exception.getMessage());
                    try
                    {
                        fileIds.remove(fileIds.size() - 1);
                        fileIds.trimToSize();
                        deleteFiles(client, fileIds);
                    } catch (IndexOutOfBoundsException e)
                    {
                        return;
                    }
                }

                public void onComplete(LiveOperation operation)
                {
                    fileIds.remove(fileIds.size() - 1);
                    fileIds.trimToSize();
                    deleteFiles(client, fileIds);
                }
            });
        } catch (IllegalStateException e)
        {
            handleIllegalConnectionState();
        }
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
            if (context != null && contextIsBrowserActivity)
            {

                Toast.makeText(context,
                        (cutNotCopy ? context.getString(R.string.movedFiles) : context.getString(R.string.copiedFiles)),
                        Toast.LENGTH_SHORT).show();
                ((BrowserActivity)context).reloadFolder();
            }
            return;
        }

        if (fileIds.size() > 99)
        {
            for (int i = fileIds.size() - 1; i > 99; i--)
            {
                fileIds.remove(i);
            }

            if (context != null)
            {

                Toast.makeText(context, R.string.errorTooManyFilesAtOnce, Toast.LENGTH_SHORT).show();
            }
        }

        final String fileId = fileIds.get(fileIds.size() - 1).getId();
        if (cutNotCopy)
        {
            try
            {
                client.moveAsync(fileId, currentFolder, new LiveOperationListener()
                {
                    public void onError(LiveOperationException exception, LiveOperation operation)
                    {
                        if (context != null)
                        {

                            Toast.makeText(context,
                                    context.getString(R.string.errorMovingFile), Toast.LENGTH_SHORT).show();
                        }
                        Log.e("ASE", exception.getMessage());
                        try
                        {
                            fileIds.remove(fileIds.size() - 1);
                            fileIds.trimToSize();
                            pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                        } catch (IndexOutOfBoundsException e)
                        {
                            return;
                        }
                    }

                    public void onComplete(LiveOperation operation)
                    {
                        try
                        {
                            fileIds.remove(fileIds.size() - 1);
                            fileIds.trimToSize();
                            pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                        } catch (IndexOutOfBoundsException e)
                        {
                            return;
                        }
                    }
                });
            } catch (IllegalStateException e)
            {
                handleIllegalConnectionState();
            }
        } else
        {
            try
            {
                client.copyAsync(fileId, currentFolder, new LiveOperationListener()
                {
                    public void onError(LiveOperationException exception, LiveOperation operation)
                    {
                        if (context != null)
                        {

                            Toast.makeText(context,
                                    context.getString(R.string.errorCopyingFile), Toast.LENGTH_SHORT).show();
                        }
                        Log.e("ASE", exception.getMessage());
                        try
                        {
                            fileIds.remove(fileIds.size() - 1);
                            fileIds.trimToSize();
                            pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                        } catch (IndexOutOfBoundsException e)
                        {
                            return;
                        }
                    }

                    public void onComplete(LiveOperation operation)
                    {
                        try
                        {
                            fileIds.remove(fileIds.size() - 1);
                            fileIds.trimToSize();
                            pasteFiles(client, fileIds, currentFolder, cutNotCopy);
                        } catch (IndexOutOfBoundsException e)
                        {
                            if (context != null && contextIsBrowserActivity)
                            {

                                Toast.makeText(context,
                                        (cutNotCopy ? context.getString(R.string.errorMovingFile) : context.getString(R.string.errorCopyingFile)),
                                        Toast.LENGTH_SHORT).show();
                                ((BrowserActivity)context).reloadFolder();
                            }
                            return;
                        }

                    }
                });
            } catch (IllegalStateException e)
            {
                handleIllegalConnectionState();
            }
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
            if (context != null && contextIsBrowserActivity)
            {
                ((BrowserActivity)context).setSupportProgressBarIndeterminateVisibility(false);
                Toast.makeText(context, context.getString(R.string.renamedFiles), Toast.LENGTH_SHORT).show();
                ((BrowserActivity)context).reloadFolder();
            }
            return;
        }

        if (fileIds.size() > 99)
        {
            for (int i = fileIds.size() - 1; i > 99; i--)
            {
                fileIds.remove(i);
            }

            if (context != null)
            {

                Toast.makeText(context, R.string.errorTooManyFilesAtOnce, Toast.LENGTH_SHORT).show();
            }
        }


        final LiveOperationListener operationListener = new LiveOperationListener()
        {
            @Override
            public void onComplete(LiveOperation operation)
            {
                try
                {
                    fileIds.remove(fileIds.size() - 1);
                    fileIds.trimToSize();
                    fileNames.remove(fileNames.size() - 1);
                    fileNames.trimToSize();
                    renameFiles(client, fileIds, fileNames, baseName, baseDescription);
                    Log.i("ASE", "File updated " + operation.getRawResult());
                } catch (IndexOutOfBoundsException e)
                {
                    return;
                }
            }

            @Override
            public void onError(LiveOperationException exception, LiveOperation operation)
            {
                Log.e("ASE", exception.getMessage());
                try
                {
                    fileIds.remove(fileIds.size() - 1);
                    fileIds.trimToSize();
                    fileNames.remove(fileNames.size() - 1);
                    fileNames.trimToSize();
                    renameFiles(client, fileIds, fileNames, baseName, baseDescription);
                    if (context != null)
                    {

                        Toast.makeText(context, context.getString(R.string.errorRenamingFile), Toast.LENGTH_SHORT).show();
                    }
                } catch (IndexOutOfBoundsException e)
                {
                    return;
                }
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
            try
            {
                client.putAsync(fileId, body, operationListener);
            } catch (IllegalStateException e)
            {
                handleIllegalConnectionState();
            }
        } catch (JSONException e)
        {
            if (context != null)
            {

                Toast.makeText(context, context.getString(R.string.errorRenamingFile), Toast.LENGTH_SHORT).show();
            }
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
        if (!notificationIsAvailable)
        {
            return;
        }
        if (context == null)
        {
            return;
        }

        notificationProgress = new Notification(R.drawable.notification_icon,
                (downloading ? context.getString(R.string.downloading) : context.getString(R.string.uploading)) + " "
                        + fileName, System.currentTimeMillis());


        notificationProgress.flags |= Notification.FLAG_AUTO_CANCEL;
        notificationProgress.flags |= Notification.FLAG_ONGOING_EVENT;

        RemoteViews notificationView = new RemoteViews(context.getPackageName(), R.layout.notification_xload);
        notificationView.setImageViewResource(R.id.image, R.drawable.notification_icon);
        notificationView.setTextViewText(R.id.title,
                (downloading ? context.getString(R.string.downloading) : context.getString(R.string.uploading))
                        + " " + fileName);
        notificationView.setProgressBar(R.id.progressBar, 100, 0, false);

        Intent cancelOperation = new Intent(context, BrowserActivity.class);
        if (downloading)
        {
            cancelOperation.setAction(Constants.ACTION_CANCEL_DOWN);
        } else
        {
            cancelOperation.setAction(Constants.ACTION_CANCEL_UP);
        }

        notificationProgress.contentIntent = PendingIntent.getActivity(
                context, 0, cancelOperation, 0);
        notificationProgress.contentView = notificationView;

        notificationManager.notify(NOTIFICATION_PROGRESS_ID, notificationProgress);
    }


    private int computePercentCompleted(int totalBytes, int bytesRemaining)
    {
        return (int) (((float) (totalBytes - bytesRemaining)) / totalBytes * 100);
    }


    /**
     * Checks the local storage for duplicates. If they exist, finds the next available name recursively.
     *
     * @param skyDriveFile The skyDriveFile to check for duplicates
     * @return The reference to the file with its available file name
     */
    private File checkForFileDuplicateAndCreateCopy(SkyDriveObject skyDriveFile)
    {
        File file = new File(skyDriveFile.getLocalDownloadLocation(), skyDriveFile.getName());
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

        File result;
        if (context != null)
        {
            result = new File(skyDriveFile.getLocalDownloadLocation() +
                    fileName + " " + context.getString(R.string.savedFileCopy) + " " + copyNr + extension);
        } else
        {
            result = new File(skyDriveFile.getLocalDownloadLocation() +
                    fileName + " Copy " + copyNr + extension);
        }
        boolean availableFileNameFound = false;

        while (availableFileNameFound)
        {
            if (result.exists())
            {
                copyNr++;
                try
                {
                    result = new File(file.getName().substring(0, index) + " " +
                            context.getString(R.string.savedFileCopy) +
                            " " + copyNr + file.getName().substring(index, file.getName().length()));
                } catch (NullPointerException e)
                {
                    result = new File(file.getName().substring(0, index) +
                            " Copy " + copyNr + file.getName().substring(index, file.getName().length()));
                }
            } else
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
        if (!notificationIsAvailable)
        {
            return;
        }
        if (context == null)
        {
            return;
        }

        int icon = R.drawable.notification_icon;
        CharSequence tickerText = file.getName() + " " + context.getString(R.string.saved) + " "
                + (download ? context.getString(R.string.from) : context.getString(R.string.to))
                + " " + context.getString(R.string.skyDrive);
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        Context context = this.context;
        CharSequence contentTitle = this.context.getString(R.string.appName);
        CharSequence contentText = tickerText;

        Intent notificationIntent;

        if (download)
        {
            Uri path = Uri.fromFile(file);
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setDataAndType(path, ioUtil.findMimeTypeOfFile(file));
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else
        {
            notificationIntent = new Intent(context, XLoader.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        notificationManager.notify(NOTIFICATION_XLOADED_ID, notification);
    }

    private void fileNotFoundNotification(File file)
    {
        if (!notificationIsAvailable)
        {
            return;
        }
        if (context == null)
        {
            return;
        }

        int icon = R.drawable.notification_icon;
        CharSequence tickerText = file.getName() + context.getString(R.string.errorFileNotFound);
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);

        Context context = this.context;
        CharSequence contentTitle = this.context.getString(R.string.appName);
        CharSequence contentText = tickerText;

        Intent notificationIntent = new Intent(context, XLoader.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        notificationManager.notify(NOTIFICATION_XLOADED_ID, notification);
    }

    private void handleIllegalConnectionState()
    {
        if (context == null || !contextIsBrowserActivity)
        {
            return;
        }

        ((BrowserForSkyDriveApplication) ((BrowserActivity)context).getApplication())
                .getAuthClient()
                .initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
                {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                    {
                        if (status == LiveStatus.CONNECTED)
                        {
                            ((BrowserActivity)context).reloadFolder();
                        } else
                        {
                            informUserOfConnectionProblemAndDismiss();
                        }
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
                        Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                        informUserOfConnectionProblemAndDismiss();
                    }
                });
    }

    private void informUserOfConnectionProblemAndDismiss()
    {
        if (context != null || !contextIsBrowserActivity)
        {
            return;
        }

        Toast.makeText(context, R.string.errorLoggedOut, Toast.LENGTH_LONG).show();
        context.startActivity(new Intent(context, SignInActivity.class));
        ((BrowserActivity)context).finish();
    }

}
