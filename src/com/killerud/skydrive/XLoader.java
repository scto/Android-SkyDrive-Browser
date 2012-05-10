package com.killerud.skydrive;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.killerud.skydrive.objects.SkyDriveObject;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.*;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/**
 * User: William
 * Date: 09.05.12
 * Time: 17:26
 */
public class XLoader {

    private NotificationManager mNotificationManager;
    private Notification mNotificationProgress;
    private RemoteViews mNotificationView;
    private int mNotificationProgressId = 2;
    private int mNotificationXLoadId = 1;
    private Context mContext;

    private IOUtil mIOUtil;


    public XLoader(Context context){
        mContext = context;
        mNotificationManager = (NotificationManager) mContext.getSystemService(Service.NOTIFICATION_SERVICE);
        mIOUtil = new IOUtil();
    }

    /**
     * Handles the uploading of a file. Manages a notification with a progressbar.
     *
     * @param client The LiveConnectClient for communicating with SkyDrive
     * @param localFilePath The path to the local file to be uploaded
     * @param currentFolderId The current SkyDrive folder, the one we upload to
     */
    public void uploadFile(LiveConnectClient client, String localFilePath, String currentFolderId) {
        //String filePath = data.getStringExtra(UploadFileDialog.EXTRA_FILE_PATH);
        if (TextUtils.isEmpty(localFilePath)) {
            return;
        }

        final File file = new File(localFilePath);
        createProgressNotification(file.getName(),false);

        final LiveOperation uploadOperation =
                client.uploadAsync(currentFolderId,
                        file.getName(),
                        file,
                        new LiveUploadOperationListener() {
                            @Override
                            public void onUploadProgress(int totalBytes,
                                                         int bytesRemaining,
                                                         LiveOperation operation) {
                                mNotificationView.setProgressBar(R.id.progressBar,100,
                                        computePercentCompleted(totalBytes,bytesRemaining),false);
                            }

                            @Override
                            public void onUploadFailed(LiveOperationException exception,
                                                       LiveOperation operation) {
                                mNotificationManager.cancel(mNotificationProgressId);
                                Toast.makeText(mContext, R.string.uploadError, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onUploadCompleted(LiveOperation operation) {
                                mNotificationManager.cancel(mNotificationProgressId);
                                JSONObject result = operation.getResult();
                                if (result.has(JsonKeys.ERROR)) {
                                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                    String message = error.optString(JsonKeys.MESSAGE);
                                    String code = error.optString(JsonKeys.CODE);
                                    Toast.makeText(mContext,
                                            mContext.getString(R.string.uploadError), Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                showFileXloadedNotification(file, false);
                            }
                        });
    }

    /**
     * Handles the downloading of a file from SkyDrive. Manages a notification with a progressbar.
     *
     * @param client The LiveConnectClient for communicating with SkyDrive
     * @param fileId The ID of the file we wish to download
     * @param localFile The local File object to write to (path here)
     */
    public void downloadFile(LiveConnectClient client, String fileId, File localFile) {
        createProgressNotification(localFile.getName(), true);
        final File fileToCreateLocally = checkForFileDuplicateAndCreateCopy(localFile);
        //TODO   ERROR/ASE(4525): An error occured on the client during the operation.
        final LiveDownloadOperation operation =
                client.downloadAsync(fileId + "/content",
                        fileToCreateLocally,
                        new LiveDownloadOperationListener() {
                            @Override
                            public void onDownloadProgress(int totalBytes,
                                                           int bytesRemaining,
                                                           LiveDownloadOperation operation) {
                                mNotificationView.setProgressBar(R.id.progressBar,100,
                                        computePercentCompleted(totalBytes,bytesRemaining),false);
                            }

                            @Override
                            public void onDownloadFailed(LiveOperationException exception,
                                                         LiveDownloadOperation operation) {
                                mNotificationManager.cancel(mNotificationProgressId);
                                Toast.makeText(mContext, mContext.getString(R.string.downloadError),
                                        Toast.LENGTH_SHORT).show();
                                Log.e("ASE",exception.getMessage());
                            }

                            @Override
                            public void onDownloadCompleted(LiveDownloadOperation operation) {
                                mNotificationManager.cancel(mNotificationProgressId);
                                showFileXloadedNotification(fileToCreateLocally, true);
                            }
                        });
    }

    /**
     * Creates a XLoad notification for the given file name
     *
     * @param fileName
     * @param downloading Whether or not we are downloading. Determines text output.
     */
    private void createProgressNotification(String fileName, boolean downloading){
        mNotificationProgress = new Notification();
        mNotificationView = new RemoteViews(mContext.getPackageName(), R.layout.notification_xload);
        mNotificationView.setImageViewResource(R.id.image, R.drawable.logo);
        mNotificationView.setTextViewText(R.id.title, (downloading ? "Downloading " : "Uploading ") + fileName);
        mNotificationView.setProgressBar(R.id.progressBar, 100, 0, false);
        mNotificationProgress.contentView = mNotificationView;
        mNotificationManager.notify(mNotificationProgressId, mNotificationProgress);
    }


    private int computePercentCompleted(int totalBytes, int bytesRemaining) {
        return (int) (((float) (totalBytes - bytesRemaining)) / totalBytes * 100);
    }


    /**
     * Checks the local storage for duplicates. If they exist, finds the next available name recursively.
     *
     * @param file The file to check for duplicates
     * @return The reference to the file with its available file name
     */
    private File checkForFileDuplicateAndCreateCopy(File file){
        if(!file.exists()){
            return file;
        }
        int index = file.getName().lastIndexOf(".");
        int copyNr = 1;
        File result = new File(file.getName().substring(0,index) +
                " Copy " + copyNr + file.getName().substring(index,file.getName().length()));
        boolean availableFileNameFound = false;

        while(availableFileNameFound){
            if(result.exists()){
                copyNr++;
                result = new File(file.getName().substring(0,index) +
                        " Copy " + copyNr + file.getName().substring(index,file.getName().length()));
            }else{
                availableFileNameFound = true;
            }
        }
        return result;
    }


    /** Pings the user with a notification that a file was either downloaded or
     * uploaded, depending on the given boolean. True = download, false = up.
     *
     * @param file The file to be displayed
     * @param download Whether or not this is a download notification
     */
    private void showFileXloadedNotification(File file, boolean download) {
        int icon = R.drawable.notification_icon;
        CharSequence tickerText = file.getName() + " saved " + (download ? "from" : "to") + "SkyDrive!";
        long when = System.currentTimeMillis();

        Notification notification = new Notification(icon, tickerText, when);

        Context context = mContext;
        CharSequence contentTitle = mContext.getString(R.string.appName);
        CharSequence contentText = file.getName() + " was saved to your " + (download ? "phone" : "SkyDrive") + "!";

        Intent notificationIntent;

        if(download){
            Uri path = Uri.fromFile(file);
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setDataAndType(path, mIOUtil.findMimeTypeOfFile(file));
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Notification.FLAG_AUTO_CANCEL);
        }else{
            notificationIntent = new Intent(context, XLoader.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        mNotificationManager.notify(mNotificationXLoadId, notification);
    }


    /**
     * Deletes the files who's SkyDrive IDs are in the given parameter.
     * @param client
     * @param fileIds
     */
    public void deleteFiles(LiveConnectClient client, final ArrayList<SkyDriveObject> fileIds) {
        for(int i=0;i<fileIds.size();i++){
            final String fileId = fileIds.get(i).getId();
            client.deleteAsync(fileId, new LiveOperationListener() {
                public void onError(LiveOperationException exception, LiveOperation operation) {
                    Toast.makeText(mContext,
                            "Error deleting file"  + (fileIds.size()>1?"s":""), Toast.LENGTH_SHORT).show();
                    Log.e("ASE", exception.getMessage());
                }
                public void onComplete(LiveOperation operation) {
                    Toast.makeText(mContext,
                            "Deleted file"  + (fileIds.size()>1?"s":""), Toast.LENGTH_SHORT).show();
                }
            });
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
    public void pasteFiles(LiveConnectClient client, final ArrayList<SkyDriveObject> fileIds, String currentFolder, boolean cutNotCopy){
        for(int i=0;i<fileIds.size();i++){
            final String fileId = fileIds.get(i).getId();
            if(cutNotCopy){
                client.moveAsync(fileId, currentFolder, new LiveOperationListener() {
                    public void onError(LiveOperationException exception, LiveOperation operation) {
                        Toast.makeText(mContext,
                                "Error moving file"  + (fileIds.size()>1?"s":""), Toast.LENGTH_SHORT).show();
                        Log.e("ASE", exception.getMessage());
                    }
                    public void onComplete(LiveOperation operation) {
                        Toast.makeText(mContext,
                                "Moved file"  + (fileIds.size()>1?"s":"") + " to current folder",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }else{
                client.copyAsync(fileId, currentFolder, new LiveOperationListener() {
                    public void onError(LiveOperationException exception, LiveOperation operation) {
                        Toast.makeText(mContext,
                                "Error copying file" + (fileIds.size()>1?"s":""), Toast.LENGTH_SHORT).show();
                        Log.e("ASE", exception.getMessage());
                    }
                    public void onComplete(LiveOperation operation) {
                        operation.getResult();

                        Toast.makeText(mContext,
                                "Copied file"  + (fileIds.size()>1?"s":"") + " to current folder",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

        }

    }

}
