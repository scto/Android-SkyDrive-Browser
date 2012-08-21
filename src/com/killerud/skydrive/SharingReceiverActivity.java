package com.killerud.skydrive;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;
import com.killerud.skydrive.constants.Constants;
import com.microsoft.live.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: William
 * Date: 03.05.12
 * Time: 21:27
 */
public class SharingReceiverActivity extends SherlockActivity
{
    BrowserForSkyDriveApplication application;
    TextView resultTextView;
    LiveAuthClient authClient;
    Button signInButton;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.sign_in);

        application = (BrowserForSkyDriveApplication) getApplication();
        resultTextView = (TextView) findViewById(R.id.introTextView);
        authClient = new LiveAuthClient(this, Constants.APP_CLIENT_ID);
        application.setAuthClient(authClient);

        signInButton = (Button) findViewById(R.id.signInButton);
        signInButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                setSupportProgressBarIndeterminateVisibility(true);
                authClient.login(SharingReceiverActivity.this, Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
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
                            Toast.makeText(getApplicationContext(), R.string.manualSignInError, Toast.LENGTH_SHORT);
                            Log.e(Constants.LOGTAG, "Login did not connect. Status is " + status + ".");
                            finish();
                        }
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);

                        Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                        finish();
                    }
                });
            }
        });
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        setSupportProgressBarIndeterminateVisibility(true);

        authClient.initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
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
                    Toast.makeText(getApplicationContext(), R.string.automaticSignInError, Toast.LENGTH_SHORT);
                    Log.e(Constants.LOGTAG, "Initialize did not connect. Status is " + status + ".");
                    finish();
                }
            }

            @Override
            public void onAuthError(LiveAuthException exception, Object userState)
            {
                setSupportProgressBarIndeterminateVisibility(false);
                Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                finish();
            }
        });
    }

    private void onLiveConnect(LiveConnectSession session)
    {
        setUpApplicationWithSessionDetails(session);
        handleSharedFilesAndStartBrowserActivity();
    }

    private void setUpApplicationWithSessionDetails(LiveConnectSession session)
    {
        assert session != null;
        application.setSession(session);
        application.setConnectClient(new LiveConnectClient(session));
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
        Uri uri = (Uri) shareIntent.getParcelableExtra(Intent.EXTRA_STREAM);

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
