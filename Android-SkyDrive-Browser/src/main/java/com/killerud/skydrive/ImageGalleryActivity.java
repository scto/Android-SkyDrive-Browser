package com.killerud.skydrive;

/**
 * User: William
 * Date: 07.05.12
 * Time: 21:11
 */

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveDownloadOperation;
import com.microsoft.live.LiveDownloadOperationListener;
import com.microsoft.live.LiveOperationException;

import java.io.File;
import java.net.URI;

/**
 * The photo dialog. Downloads and displays an image, but does not save the
 * image unless the user presses the save button (i.e. acts as a cache)
 */
public class ImageGalleryActivity extends ActionBarActivity
{
    private boolean savePhoto;
    private boolean sharePhoto;
    private File image;
    private XLoader xLoader;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_gallery);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        savePhoto = false;
        sharePhoto = false;

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
            } catch (NullPointerException e)
            {
                //View does not exist
            }
        } else if (client != null)
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

    private BitmapFactory.Options determineBitmapDecodeOptions()
    {
        BitmapFactory.Options scoutOptions = new BitmapFactory.Options();
        scoutOptions.inJustDecodeBounds = true;

        Bitmap bitmapBounds = BitmapFactory.decodeFile(image.getPath(), scoutOptions);

        int bitmapHeight = scoutOptions.outHeight;
        int bitmapWidth = scoutOptions.outWidth;

        int dividend = bitmapWidth;

        if (bitmapHeight > bitmapWidth)
        {
            dividend = bitmapHeight;
        }

        int sampleSize = bitmapHeight / dividend;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPurgeable = true;
        options.inScaled = false;

        return options;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.image_gallery_menu, menu);
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
            case R.id.galleryShare:
                sharePhoto = true;
                sharePhoto();
                return true;
            case R.id.gallerySave:
                savePhoto = true;
                finish();
                return true;
            default:
                return false;
        }
    }

    private void sharePhoto()
    {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        try{
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(image.getAbsolutePath()));
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        }catch (Exception e)
        {
            Toast.makeText(getApplicationContext(), R.string.errorFileNotFound, Toast.LENGTH_SHORT).show();
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
        if (savePhoto && !sharePhoto)
        {
            xLoader.showFileXloadedNotification(image, true);
        } else if(!sharePhoto)
        {
            if (image != null)
            {
                image.delete();
            }
        }
    }
}