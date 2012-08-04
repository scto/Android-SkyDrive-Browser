package com.killerud.skydrive;

/**
 * User: William
 * Date: 07.05.12
 * Time: 21:11
 */

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveDownloadOperation;
import com.microsoft.live.LiveDownloadOperationListener;
import com.microsoft.live.LiveOperationException;

import java.io.File;

/**
 * The photo dialog. Downloads and displays an image, but does not save the
 * image unless the user presses the save button (i.e. acts as a cache)
 */
public class ImageGalleryActivity extends SherlockActivity
{
    private boolean savePhoto;
    private File image;
    private XLoader xLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_gallery);

        savePhoto = false;

        Intent photoDetails = getIntent();
        String photoId = photoDetails.getStringExtra("killerud.skydrive.PHOTO_ID");
        String photoName = photoDetails.getStringExtra("killerud.skydrive.PHOTO_NAME");
        setTitle(photoName);

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        LiveConnectClient client = app.getConnectClient();
        xLoader = new XLoader(app.getCurrentBrowser());
        final LinearLayout layout = (LinearLayout) findViewById(R.id.photo_dialog);
        final TextView textView = (TextView) layout.findViewById(R.id.imageText);
        final ImageView imageView = (ImageView) layout.findViewById(R.id.imageDialogImage);


        image = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/", photoName);

        if (image.exists())
        {
            BitmapFactory.Options options = determineBitmapDecodeOptions();
            imageView.setImageBitmap(BitmapFactory.decodeFile(image.getPath(), options));
            try
            {
                layout.removeView(textView);
            }catch (NullPointerException e)
            {
                //View does not exist
            }
        }
        else if(client != null)
        {

            final LiveDownloadOperation operation =
                    client.downloadAsync(photoId + "/content",
                            image,
                            new LiveDownloadOperationListener()
                            {
                                @Override
                                public void onDownloadProgress(int totalBytes,
                                                               int bytesRemaining,
                                                               LiveDownloadOperation operation)
                                {

                                }

                                @Override
                                public void onDownloadFailed(LiveOperationException exception,
                                                             LiveDownloadOperation operation)
                                {
                                    Toast.makeText(getApplicationContext(), getString(R.string.downloadError), Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onDownloadCompleted(LiveDownloadOperation operation)
                                {
                                    BitmapFactory.Options options = determineBitmapDecodeOptions();
                                    imageView.setImageBitmap(BitmapFactory.decodeFile(image.getPath(), options));
                                    layout.removeView(textView);
                                }
                            });
        }
    }

    private BitmapFactory.Options determineBitmapDecodeOptions() {
        BitmapFactory.Options scoutOptions = new BitmapFactory.Options();
        scoutOptions.inJustDecodeBounds = true;

        Bitmap bitmapBounds = BitmapFactory.decodeFile(image.getPath(), scoutOptions);

        int bitmapHeight = scoutOptions.outHeight;
        int bitmapWidth = scoutOptions.outWidth;

        int dividend  = bitmapWidth;

        if(bitmapHeight > bitmapWidth)
        {
            dividend = bitmapHeight;
        }

        int sampleSize = bitmapHeight / 800;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPurgeable = true;
        options.inScaled = false;

        return options;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.image_gallery_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                savePhoto = false;
                finish();
                return true;
            case R.id.galleryCancel:
                savePhoto = false;
                finish();
                return true;
            case R.id.gallerySave:
                savePhoto = true;
                finish();
                return true;
            default:
                return false;
        }
    }

    /* Known "feature": pressing the Home-button (or rather, not pressing
    * Cancel or the Back-button, triggering Dismiss) causes the file to
    * stay saved even if the user didn't explicitly ask for it. The user
    * is not informed of this save/failure to delete, and so may be
    * surprised to see the file saved later on.
    */
    @Override
    protected void onStop()
    {
        super.onStop();
        if (savePhoto)
        {
            xLoader.showFileXloadedNotification(image, true);
        }
        else
        {
            if (image != null) image.delete();
        }
    }
}