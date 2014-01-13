package com.killerud.skydrive;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;
import com.killerud.skydrive.constants.Constants;
import com.microsoft.live.*;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;

public class SignInAndShareHandlerActivity extends SherlockActivity
{
    BrowserForSkyDriveApplication application;
    LiveAuthClient liveAuthClient;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.sign_in);
        pruneCacheFolders();

        application = (BrowserForSkyDriveApplication) getApplication();
        liveAuthClient = new LiveAuthClient(this, Constants.APP_CLIENT_ID);
        application.setAuthClient(liveAuthClient);

        Button signInButton = (Button) findViewById(R.id.signInButton);
        createTouchableHyperlinkToRegisterForAccountIfNeeded();
        signInButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                loginToSkyDriveUsingInputFromUser();

            }
        });
    }

    private void loginToSkyDriveUsingInputFromUser()
    {
        setSupportProgressBarIndeterminateVisibility(true);
        try
        {
            liveAuthClient.login(this, Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
            {
                @Override
                public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                {
                    setSupportProgressBarIndeterminateVisibility(false);

                    if (status == LiveStatus.CONNECTED)
                    {
                        onLiveConnect(session);
                    } else
                    {
                        Log.e(Constants.LOGTAG, "Login did not connect. Status is " + status + ".");
                    }
                }

                @Override
                public void onAuthError(LiveAuthException exception, Object userState)
                {
                    setSupportProgressBarIndeterminateVisibility(false);
                    Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                }
            });
        } catch (IllegalStateException e)
        {
            setSupportProgressBarIndeterminateVisibility(false);
            /* Already logged in , or login in progress*/
            Log.e("ASE", e.getMessage());
        }
    }

    private void createTouchableHyperlinkToRegisterForAccountIfNeeded()
    {
        final TextView noAccountText = (TextView) findViewById(R.id.noAccountText);
        noAccountText.setText(getString(R.string.noAccountQuestion));
        Linkify.addLinks(noAccountText, Linkify.ALL);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        loginToSkyDriveAutomaticallyUsingPermissionGrantedByUser();
    }

    private void loginToSkyDriveAutomaticallyUsingPermissionGrantedByUser()
    {
        setSupportProgressBarIndeterminateVisibility(true);
        try
        {
            liveAuthClient.initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
            {
                @Override
                public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                {
                    setSupportProgressBarIndeterminateVisibility(false);
                    if (status == LiveStatus.CONNECTED)
                    {
                        onLiveConnect(session);
                    } else
                    {
                        Log.e(Constants.LOGTAG, "Initialize did not connect. Status is " + status + ".");
                    }
                }

                @Override
                public void onAuthError(LiveAuthException exception, Object userState)
                {
                    setSupportProgressBarIndeterminateVisibility(false);
                    Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                }
            });
        } catch (IllegalStateException e)
        {
            /* Already logged in, or login in progress */
            Log.e("ASE", e.getMessage());
            startActivity(new Intent(getApplicationContext(), BrowserActivity.class));
            finish();
        }
    }

    private void pruneCacheFolders()
    {
        LocalPersistentStorageManager cacheManager = new LocalPersistentStorageManager();
        pruneFileCache(cacheManager);
        pruneThumbCache(cacheManager);
    }

    private void pruneFileCache(LocalPersistentStorageManager cacheManager)
    {
        final File fileCacheFolder = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/cache/");
        cacheManager.pruneCache(fileCacheFolder, LocalPersistentStorageManager.FILES_MAX_SIZE);
    }


    private void pruneThumbCache(LocalPersistentStorageManager cacheManager)
    {
        final File thumbCacheFolder = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/thumbs/");
        cacheManager.pruneCache(thumbCacheFolder, LocalPersistentStorageManager.THUMBS_MAX_SIZE);
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            setSupportProgressBarIndeterminateVisibility(false);
            return super.onKeyDown(keyCode, event);
        } else
        {
            return super.onKeyDown(keyCode, event);
        }
    }


    private void onLiveConnect(LiveConnectSession session)
    {
         setUpApplicationWithSessionDetails(session);
         if(activityStartedThroughShareIntent())
         {
             handleSharedFilesAndStartBrowserActivity();
         }else
         {
             startBrowserActivity();
         }
    }

    private void startBrowserActivity()
    {
        startActivity(new Intent(getApplicationContext(), BrowserActivity.class));
        finish();
    }

    private void setUpApplicationWithSessionDetails(LiveConnectSession session)
    {
        assert session != null;
        application.setSession(session);
        application.setConnectClient(new LiveConnectClient(session));
    }


    private boolean activityStartedThroughShareIntent()
    {
        Intent shareIntent = getIntent();
        String action = shareIntent.getAction();
        if (action != null
                && action.equals(Intent.ACTION_SEND))
        {
            return true;
        }
        else
        {
            return false;
        }
    }


    private void handleSharedFilesAndStartBrowserActivity()
    {
        Intent shareIntent = getIntent();
        String action = shareIntent.getAction();
        String fileType = shareIntent.getType();

        Intent startIntent = new Intent();

        if (action != null
                && action.equals(Intent.ACTION_SEND))
        {
            if (fileType.startsWith("image/"))
            {
                startIntent = handleSentImage(shareIntent);
            } else if (fileType.startsWith("audio/"))
            {
                startIntent = handleSentAudio(shareIntent);
            } else if (fileType.startsWith("video/"))
            {
                startIntent = handleSentVideo(shareIntent);
            } else if (fileType.equals("application/pdf") ||
                    fileType.equals("application/msword") ||
                    fileType.equals("application/vnd.ms-powerpoint") ||
                    fileType.equals("application/vnd.ms-excel") ||
                    fileType.startsWith("application/vnd.openxmlformats-officedocument."))
            {
                startIntent = handleSentDocument(shareIntent);
            }


        } else if (action != null
                && action.equals(Intent.ACTION_SEND_MULTIPLE))
        {
            if (fileType.startsWith("image/"))
            {
                startIntent = handleSentMultipleImages(shareIntent);
            } else if (fileType.startsWith("audio/"))
            {
                startIntent = handleSentMultipleAudio(shareIntent);
            } else if (fileType.startsWith("video/"))
            {
                startIntent = handleSentMultipleVideos(shareIntent);
            } else if (fileType.equals("application/pdf") ||
                    fileType.equals("application/msword") ||
                    fileType.equals("application/vnd.ms-powerpoint") ||
                    fileType.equals("application/vnd.ms-excel") ||
                    fileType.startsWith("application/vnd.openxmlformats-officedocument."))
            {
                startIntent = handleSentMultipleDocuments(shareIntent);
            }

        }

        if (startIntent == null)
        {
            showErrorToast();
            finish();
            return;
        } else
        {
            startIntent.setClass(getApplicationContext(), BrowserActivity.class);
            startActivity(startIntent);
            finish();
        }
    }

    private Intent handleSentDocument(Intent shareIntent)
    {
        Intent startIntent = new Intent();
        Uri uri = shareIntent.getParcelableExtra(Intent.EXTRA_STREAM);

        ArrayList<String> filePath = new ArrayList<String>();
        try
        {
            String decodedPath = URLDecoder.decode(uri.toString().substring("file://".length()), "UTF-8");
            filePath.add(decodedPath);
        } catch (UnsupportedEncodingException e)
        {
            filePath.add(uri.toString().substring("file://".length()));
        }

        startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
        startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePath);

        return startIntent;
    }

    private Intent handleSentMultipleDocuments(Intent shareIntent)
    {
        Intent startIntent = new Intent();
        ArrayList<String> paths = new ArrayList<String>();
        ArrayList<Parcelable> sharedContent = shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);

        if (sharedContent != null)
        {
            for (Parcelable item : sharedContent)
            {
                Uri uri = (Uri) item;
                if (uri != null)
                {
                    try
                    {
                        String decodedPath = URLDecoder.decode(uri.toString().substring("file://".length()), "UTF-8");
                        paths.add(decodedPath);
                    } catch (UnsupportedEncodingException e)
                    {
                        paths.add(uri.toString().substring("file://".length()));
                    }
                }
            }
        }

        startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
        startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, paths);
        return startIntent;
    }

    private Intent handleSentMultipleImages(Intent shareIntent)
    {
        Intent startIntent = new Intent();

        Bundle extras = shareIntent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM))
        {
            ArrayList<Uri> fileList = shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            ArrayList<String> filePaths = new ArrayList<String>();
            try
            {
                for (int i = 0; i < fileList.size(); i++)
                {
                    Uri uri = fileList.get(i);
                    if (uri != null)
                    {
                        filePaths.add(parseUriFromMediaStoreToFilePath(uri, MediaStore.Images.Media.DATA));
                    }
                }
            } catch (UnsupportedOperationException e)
            {
                Toast.makeText(this, R.string.errorCouldNotFetchFileForSharing, Toast.LENGTH_SHORT).show();
                finish();
                return null;
            }

            startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
            startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePaths);
        }

        return startIntent;
    }

    private Intent handleSentImage(Intent shareIntent)
    {
        Intent startIntent = new Intent();

        Bundle extras = shareIntent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM))
        {
            Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
            ArrayList<String> filePath = new ArrayList<String>();
            try
            {
                if (uri != null)
                {
                    filePath.add(parseUriFromMediaStoreToFilePath(uri, MediaStore.Images.Media.DATA));
                }
            } catch (UnsupportedOperationException e)
            {
                return null;
            }
            startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
            startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePath);
        }

        return startIntent;
    }

    private Intent handleSentMultipleVideos(Intent shareIntent)
    {
        Intent startIntent = new Intent();

        Bundle extras = shareIntent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM))
        {
            ArrayList<Uri> fileList = shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            ArrayList<String> filePaths = new ArrayList<String>();
            try
            {
                for (int i = 0; i < fileList.size(); i++)
                {
                    Uri uri = fileList.get(i);
                    if (uri != null)
                    {
                        filePaths.add(parseUriFromMediaStoreToFilePath(uri, MediaStore.Video.Media.DATA));
                    }
                }
            } catch (UnsupportedOperationException e)
            {
                Toast.makeText(this, R.string.errorCouldNotFetchFileForSharing, Toast.LENGTH_SHORT).show();
                finish();
                return null;
            }

            startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
            startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePaths);
        }

        return startIntent;
    }

    private Intent handleSentVideo(Intent shareIntent)
    {
        Intent startIntent = new Intent();

        Bundle extras = shareIntent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM))
        {
            Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
            ArrayList<String> filePath = new ArrayList<String>();
            try
            {
                if (uri != null)
                {
                    filePath.add(parseUriFromMediaStoreToFilePath(uri, MediaStore.Video.Media.DATA));
                }
            } catch (UnsupportedOperationException e)
            {
                return null;
            }
            startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
            startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePath);
        }

        return startIntent;
    }

    private Intent handleSentMultipleAudio(Intent shareIntent)
    {
        Intent startIntent = new Intent();

        Bundle extras = shareIntent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM))
        {
            ArrayList<Uri> fileList = shareIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            ArrayList<String> filePaths = new ArrayList<String>();
            try
            {
                for (int i = 0; i < fileList.size(); i++)
                {
                    Uri uri = fileList.get(i);
                    if (uri != null)
                    {
                        filePaths.add(parseUriFromMediaStoreToFilePath(uri, MediaStore.Audio.Media.DATA));
                    }
                }
            } catch (UnsupportedOperationException e)
            {
                Toast.makeText(this, R.string.errorCouldNotFetchFileForSharing, Toast.LENGTH_SHORT).show();
                finish();
                return null;
            }

            startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
            startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePaths);
        }

        return startIntent;
    }

    private Intent handleSentAudio(Intent shareIntent)
    {
        Intent startIntent = new Intent();

        Bundle extras = shareIntent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM))
        {
            Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
            ArrayList<String> filePath = new ArrayList<String>();
            try
            {
                if (uri != null)
                {
                    filePath.add(parseUriFromMediaStoreToFilePath(uri, MediaStore.Audio.Media.DATA));
                }
            } catch (UnsupportedOperationException e)
            {
                return null;
            }
            startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
            startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePath);
        }

        return startIntent;
    }

    public String parseUriFromMediaStoreToFilePath(Uri uri, String mediaStore)
    {
        assert uri != null;

        String selectedImagePath = null;
        String filemanagerPath = uri.getPath();

        String[] projection = {};
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null)
        {
            // Here you will get a null pointer if cursor is null
            // This can be if you used OI file manager for picking the media
            int column_index = cursor.getColumnIndexOrThrow(mediaStore);
            cursor.moveToFirst();
            selectedImagePath = cursor.getString(column_index);
            cursor.close();
        }

        if (selectedImagePath != null)
        {
            return selectedImagePath;
        } else if (filemanagerPath != null)
        {
            return filemanagerPath;
        }
        return null;
    }

    private void showErrorToast()
    {
        Toast.makeText(this, R.string.errorCouldNotFetchFileForSharing, Toast.LENGTH_SHORT).show();
    }

}
