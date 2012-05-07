package com.killerud.skydrive.dialogs;

/**
 * User: William
 * Date: 07.05.12
 * Time: 21:11
 */

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.killerud.skydrive.BrowserActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.objects.SkyDrivePhoto;
import com.killerud.skydrive.util.IOUtil;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveDownloadOperation;
import com.microsoft.live.LiveDownloadOperationListener;
import com.microsoft.live.LiveOperationException;

import java.io.File;

/** The photo dialog. Downloads and displays an image, but does not save the
 * image unless the user presses the save button (i.e. acts as a cache)
 */
public class ViewPhotoDialog extends Activity {
    private boolean mSavePhoto;
    private String mFileName;
    private File mFile;
    private IOUtil mIOUtil;
    private LiveConnectClient mClient;
    private String mPhotoName;
    private String mPhotoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSavePhoto = false;
        mIOUtil = new IOUtil();

        Intent photoDetails = getIntent();
        mPhotoId = photoDetails.getStringExtra("killerud.skydrive.PHOTO_ID");
        mPhotoName = photoDetails.getStringExtra("killerud.skydrive.PHOTO_NAME");


        mClient = ((BrowserForSkyDriveApplication) getApplication()).getConnectClient();

        /* Creates the layout.
        * The layout consists of a textview to let the user know we're loading,
        * an imageview to display the image and a linearlayout containing the
        * buttons. This is wrapped in a linearlayout, then a scrollview and displayed.
        */
        final ScrollView wrapper = new ScrollView(getApplicationContext());

        final LinearLayout layout = new LinearLayout(wrapper.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        final ImageView imageView = new ImageView(layout.getContext());
        final TextView loadingText = new TextView(layout.getContext());
        loadingText.setText(R.string.navigateWait);

        final LinearLayout buttonLayout = new LinearLayout(layout.getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);

        final ImageButton saveButton = new ImageButton(buttonLayout.getContext());
        saveButton.setBackgroundResource(android.R.drawable.ic_menu_save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSavePhoto = true;
                showFileXloadedNotification(mFile, true);
                finish();
            }
        });

        final ImageButton cancel = new ImageButton(buttonLayout.getContext());
        cancel.setBackgroundResource(android.R.drawable.ic_menu_close_clear_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSavePhoto = false;
                finish();
            }
        });

        layout.addView(loadingText,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(imageView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        buttonLayout.addView(saveButton,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        buttonLayout.addView(cancel,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        layout.addView(buttonLayout,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        wrapper.addView(layout,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addContentView(wrapper,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));







        mFile = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", mPhotoName);
        mFileName = mFile.getName();

        if(mFile.exists()){
            imageView.setImageBitmap(BitmapFactory.decodeFile(mFile.getPath()));
            layout.removeView(loadingText);
        }else{
//            final ProgressDialog progressDialog =
//                    new ProgressDialog(BrowserActivity.this);
//            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//            progressDialog.setMessage(getString(R.string.downloading));
//            progressDialog.setCancelable(true);
//            progressDialog.show();

            final LiveDownloadOperation operation =
                    mClient.downloadAsync(mPhotoId + "/content",
                            mFile,
                            new LiveDownloadOperationListener() {
                                @Override
                                public void onDownloadProgress(int totalBytes,
                                                               int bytesRemaining,
                                                               LiveDownloadOperation operation) {
                                    int percentCompleted =
                                            computePercentCompleted(totalBytes, bytesRemaining);

                                    //progressDialog.setProgress(percentCompleted);
                                }

                                @Override
                                public void onDownloadFailed(LiveOperationException exception,
                                                             LiveDownloadOperation operation) {
                                    //progressDialog.dismiss();
                                    Toast.makeText(getApplicationContext(),getString(R.string.downloadError), Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onDownloadCompleted(LiveDownloadOperation operation) {
                                    //progressDialog.dismiss();
                                    imageView.setImageBitmap(BitmapFactory.decodeFile(mFile.getPath()));
                                    layout.removeView(loadingText);
                                }
                            });

//            progressDialog.setOnCancelListener(new OnCancelListener() {
//                @Override
//                public void onCancel(DialogInterface dialog) {
//                    operation.cancel();
//                    if(mFile != null) mFile.delete();
//                }
//            });
        }
    }


    /* Known "feature": pressing the Home-button (or rather, not pressing
    * Cancel or the Back-button, triggering Dismiss) causes the file to
    * stay saved even if the user didn't explicitly ask for it. The user
    * is not informed of this save/failure to delete, and so may be
    * surprised to see the file saved later on.
    */
    @Override
    protected void onStop(){
        super.onStop();
        if(mSavePhoto){
            showFileXloadedNotification(mFile, true);
        }else{
            if(mFile != null) mFile.delete();
        }
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

        Context context = getApplicationContext();
        CharSequence contentTitle = getString(R.string.appName);
        CharSequence contentText = file.getName() + " was saved to your " + (download ? "phone" : "SkyDrive") + "!";

        Intent notificationIntent;

        if(download){
            Uri path = Uri.fromFile(file);
            notificationIntent = new Intent(Intent.ACTION_VIEW);
            notificationIntent.setDataAndType(path, mIOUtil.findMimeTypeOfFile(file));
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }else{
            notificationIntent = new Intent(context, BrowserActivity.class);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, notification);

    }

    private int computePercentCompleted(int totalBytes, int bytesRemaining) {
        return (int) (((float) (totalBytes - bytesRemaining)) / totalBytes * 100);
    }

}