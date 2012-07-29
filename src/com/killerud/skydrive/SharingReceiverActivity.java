package com.killerud.skydrive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.killerud.skydrive.constants.Constants;
import com.microsoft.live.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: William
 * Date: 03.05.12
 * Time: 21:27
 */
public class SharingReceiverActivity extends Activity
{
    BrowserForSkyDriveApplication mApp;
    TextView mResultTextView;
    LiveAuthClient mAuthClient;
    ProgressDialog mInitializeDialog;
    Button mSignInButton;
    TextView mIntroText;
    Intent mIntentThatStartedMe;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sign_in);
        mApp = (BrowserForSkyDriveApplication) getApplication();

        mResultTextView = (TextView) findViewById(R.id.introTextView);
        mAuthClient = new LiveAuthClient(this, Constants.APP_CLIENT_ID);
        mApp.setAuthClient(mAuthClient);

        mInitializeDialog = ProgressDialog.show(this, "", getString(R.string.initializingMessage), true);

        mSignInButton = (Button) findViewById(R.id.signInButton);
        mIntroText = (TextView) findViewById(R.id.introTextView);
        mIntroText.setVisibility(View.INVISIBLE);
        mSignInButton.setVisibility(View.INVISIBLE);

        mSignInButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mAuthClient.login(SharingReceiverActivity.this, Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
                {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                    {
                        if (status == LiveStatus.CONNECTED)
                        {
                            startBrowserActivity(session);
                        }
                        else
                        {
                            Toast.makeText(getApplicationContext(), R.string.manualSignInError, Toast.LENGTH_SHORT);
                            mIntroText.setVisibility(View.VISIBLE);
                            mSignInButton.setVisibility(View.VISIBLE);
                            Log.e(Constants.LOGTAG, "Login did not connect. Status is " + status + ".");
                            finish();
                        }
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
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
        mAuthClient.initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
        {
            @Override
            public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
            {
                mInitializeDialog.dismiss();
                if (status == LiveStatus.CONNECTED)
                {
                    startBrowserActivity(session);
                }
                else
                {
                    Toast.makeText(getApplicationContext(), R.string.automaticSignInError, Toast.LENGTH_SHORT);
                    Log.e(Constants.LOGTAG, "Initialize did not connect. Status is " + status + ".");
                    finish();
                }
            }

            @Override
            public void onAuthError(LiveAuthException exception, Object userState)
            {
                mInitializeDialog.dismiss();
                Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                finish();
            }
        });
    }

    public String parseUriToFilePath(Uri uri)
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
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            selectedImagePath = cursor.getString(column_index);
        }

        cursor.close();

        if (selectedImagePath != null)
        {
            return selectedImagePath;
        }
        else if (filemanagerPath != null)
        {
            return filemanagerPath;
        }
        return null;
    }

    private void startBrowserActivity(LiveConnectSession session)
    {
        assert session != null;
        mApp.setSession(session);
        mApp.setConnectClient(new LiveConnectClient(session));

        /* This is the key that opens up for uploading <|:D~ */
        Intent intentThatStartedMe = getIntent();
        Intent startIntent = new Intent(getApplicationContext(), BrowserActivity.class);
        if (intentThatStartedMe.getAction() != null
                && intentThatStartedMe.getAction().equals(Intent.ACTION_SEND))
        {
            Bundle extras = intentThatStartedMe.getExtras();
            if (extras.containsKey(Intent.EXTRA_STREAM))
            {
                Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);

                ArrayList<String> filePath = new ArrayList<String>();
                try{

                    if(uri!= null) filePath.add(parseUriToFilePath(uri));
                }catch (UnsupportedOperationException e)
                {
                    Toast.makeText(this, R.string.errorCouldNotFetchFileForSharing, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
                startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePath);
            }
        }
        else if (intentThatStartedMe.getAction() != null
                && intentThatStartedMe.getAction().equals(Intent.ACTION_SEND_MULTIPLE))
        {
            Bundle extras = intentThatStartedMe.getExtras();
            if (extras.containsKey(Intent.EXTRA_STREAM))
            {
                ArrayList<Uri> fileList = intentThatStartedMe.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                ArrayList<String> filePaths = new ArrayList<String>();
                try{
                    for (int i = 0; i < fileList.size(); i++)
                    {
                        Uri uri = fileList.get(i);
                        if(uri != null) filePaths.add(parseUriToFilePath(uri));
                    }
                }catch (UnsupportedOperationException e)
                {
                    Toast.makeText(this, R.string.errorCouldNotFetchFileForSharing, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                startIntent.setAction("killerud.skydrive.UPLOAD_PICK_FOLDER");
                startIntent.putExtra(UploadFileActivity.EXTRA_FILES_LIST, filePaths);
            }
        }
        startActivity(startIntent);
        finish();
    }

}
