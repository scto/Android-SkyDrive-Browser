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
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.BrowserActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.XLoader;
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
public class ViewPhotoDialog extends SherlockActivity {
    private boolean mSavePhoto;
    private File mFile;
    private XLoader mXLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photo_dialog);

        mSavePhoto = false;

        Intent photoDetails = getIntent();
        String photoId = photoDetails.getStringExtra("killerud.skydrive.PHOTO_ID");
        String photoName = photoDetails.getStringExtra("killerud.skydrive.PHOTO_NAME");
        setTitle(photoName);

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        LiveConnectClient client = app.getConnectClient();
        mXLoader = new XLoader(app.getCurrentBrowser());
        final LinearLayout layout = (LinearLayout) findViewById(R.id.photo_dialog);
        final TextView textView = (TextView) layout.findViewById(R.id.imageText);
        final ImageView imageView = (ImageView) layout.findViewById(R.id.imageDialogImage);

        final ImageButton saveButton = (ImageButton) layout.findViewById(R.id.imageSave);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSavePhoto = true;
                mXLoader.showFileXloadedNotification(mFile, true);
                finish();
            }
        });

        final ImageButton cancel = (ImageButton) layout.findViewById(R.id.imageCancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSavePhoto = false;
                finish();
            }
        });


        mFile = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", photoName);

        if(mFile.exists()){
            imageView.setImageBitmap(BitmapFactory.decodeFile(mFile.getPath()));
            layout.removeView(textView);
        }else{
            final LiveDownloadOperation operation =
                    client.downloadAsync(photoId + "/content",
                            mFile,
                            new LiveDownloadOperationListener() {
                                @Override
                                public void onDownloadProgress(int totalBytes,
                                                               int bytesRemaining,
                                                               LiveDownloadOperation operation) {

                                }

                                @Override
                                public void onDownloadFailed(LiveOperationException exception,
                                                             LiveDownloadOperation operation) {
                                    Toast.makeText(getApplicationContext(),getString(R.string.downloadError), Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onDownloadCompleted(LiveDownloadOperation operation) {
                                    /* JPG was not supported right off the bat. Options fix that. */
                                    BitmapFactory.Options options = new BitmapFactory.Options();
                                    options.inSampleSize = 2;
                                    imageView.setImageBitmap(BitmapFactory.decodeFile(mFile.getPath(), options));
                                    layout.removeView(textView);
                                }
                            });
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
            mXLoader.showFileXloadedNotification(mFile, true);
        }else{
            if(mFile != null) mFile.delete();
        }
    }
}