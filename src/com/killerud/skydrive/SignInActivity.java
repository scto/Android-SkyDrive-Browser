package com.killerud.skydrive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Environment;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.killerud.skydrive.constants.Constants;
import com.microsoft.live.*;

import java.io.File;
import java.util.Arrays;

public class SignInActivity extends Activity
{
    BrowserForSkyDriveApplication mApp;
    TextView mResultTextView;
    LiveAuthClient mAuthClient;
    ProgressDialog mInitializeDialog;
    Button mSignInButton;
    TextView mIntroText;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sign_in);

        handleLocalCache();

        mApp = (BrowserForSkyDriveApplication) getApplication();

        mResultTextView = (TextView) findViewById(R.id.introTextView);
        mAuthClient = new LiveAuthClient(this, Constants.APP_CLIENT_ID);
        mApp.setAuthClient(mAuthClient);

        mSignInButton = (Button) findViewById(R.id.signInButton);
        mIntroText = (TextView) findViewById(R.id.introTextView);

        final TextView noAccountText = (TextView) findViewById(R.id.noAccountText);
        noAccountText.setText(getString(R.string.noAccount));
        Linkify.addLinks(noAccountText, Linkify.ALL);

        mSignInButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                try
                {
                    mAuthClient.login(SignInActivity.this, Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
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
                                Log.e(Constants.LOGTAG, "Login did not connect. Status is " + status + ".");
                            }
                        }

                        @Override
                        public void onAuthError(LiveAuthException exception, Object userState)
                        {
                            Log.e(Constants.LOGTAG, exception.getMessage());
                        }
                    });
                } catch (IllegalStateException e)
                {
                    /* Already logged in , or login in progress*/
                    Log.e("ASE", e.getMessage());
                }

            }
        });


    }

    @Override
    protected void onStart()
    {
        super.onStart();
        mInitializeDialog = new ProgressDialog(this);
        mInitializeDialog.setMessage(getString(R.string.initializing));
        mInitializeDialog.show();

        try
        {

            mAuthClient.initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
            {
                @Override
                public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                {
                    try{
                        mInitializeDialog.dismiss();
                    }catch (NullPointerException e)
                    {
                       /*
                       At this point the view has most likely been recreated, and as such the mInitializeDialog
                       here is no longer available for dismissal.

                       Seeing as the authorization process is restarted on recreation, we just return and let this
                       thread die and be handled by the collector.
                       Same goes for onAuthError.
                        */
                        Log.e(Constants.LOGTAG, "Orientation change during sign-in");
                        return;
                    }
                    if (status == LiveStatus.CONNECTED)
                    {
                        startBrowserActivity(session);
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), R.string.automaticSignInError, Toast.LENGTH_SHORT);
                        Log.e(Constants.LOGTAG, "Initialize did not connect. Status is " + status + ".");
                    }
                }

                @Override
                public void onAuthError(LiveAuthException exception, Object userState)
                {
                    try{
                        mInitializeDialog.dismiss();
                    }catch (NullPointerException e)
                    {
                        Log.e(Constants.LOGTAG, "Orientation change during sign-in");
                        return;
                    }
                    Log.e(Constants.LOGTAG, exception.getMessage());
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

    private void handleLocalCache()
    {
        handleFileCache();
        handleThumbCache();
    }

    private void handleFileCache()
    {
        final File fileCacheFolder = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/cache/");

        /* No cache for us to handle */
        if (!fileCacheFolder.exists())
        {
            return;
        }

        /* This block could potentially be a while, so run it in a new thread */
        new Thread(new Runnable()
        {
            public void run()
            {
                File[] cacheContents = fileCacheFolder.listFiles();
                long cacheSize = 0l;

                for (int i = 0; i < cacheContents.length; i++)
                {
                    cacheSize += cacheContents[i].length();
                }

                if (cacheSize > Constants.CACHE_MAX_SIZE)
                {

                    boolean cachePruned = false;
                    int fileIndex = 0;

                    while (!cachePruned)
                    {
                        try
                        {
                            cacheSize -= cacheContents[fileIndex].length();
                            cacheContents[fileIndex].delete();
                            Log.i(Constants.LOGTAG, "File cache pruned");
                        } catch (IndexOutOfBoundsException e)
                        {
                            cachePruned = true;
                            Log.e(Constants.LOGTAG, "Error on file cache prune. " + e.getMessage());
                        } finally
                        {
                            if (cacheSize < Constants.CACHE_MAX_SIZE - 50)
                            {
                                cachePruned = true;
                            }

                            fileIndex++;
                        }
                    }
                }
            }
        }).start();
    }


    private void handleThumbCache()
    {
        final File thumbCacheFolder = new File(Environment.getExternalStorageDirectory()
                + "/Android/data/com.killerud.skydrive/thumbs/");

        /* No cache for us to handle */
        if (!thumbCacheFolder.exists())
        {
            return;
        }

        /* This block could potentially be a while, so run it in a new thread */
        new Thread(new Runnable()
        {
            public void run()
            {
                File[] cacheContents = thumbCacheFolder.listFiles();
                long cacheSize = 0l;

                for (int i = 0; i < cacheContents.length; i++)
                {
                    cacheSize += cacheContents[i].length();
                }

                if (cacheSize > Constants.THUMBS_MAX_SIZE)
                {

                    boolean cachePruned = false;
                    int fileIndex = 0;

                    while (!cachePruned)
                    {
                        try
                        {
                            cacheSize -= cacheContents[fileIndex].length();
                            cacheContents[fileIndex].delete();
                            Log.i(Constants.LOGTAG, "Thumb cache pruned");
                        } catch (IndexOutOfBoundsException e)
                        {
                            cachePruned = true;
                            Log.e(Constants.LOGTAG, "Error on thumb cache prune. " + e.getMessage());
                        } finally
                        {
                            if (cacheSize < Constants.THUMBS_MAX_SIZE - 50)
                            {
                                cachePruned = true;
                            }

                            fileIndex++;
                        }
                    }
                }
            }
        }).start();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (mAuthClient != null)
            {
                try
                {
                    mAuthClient.logout(new LiveAuthListener()
                    {
                        @Override
                        public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                        {

                        }

                        @Override
                        public void onAuthError(LiveAuthException exception, Object userState)
                        {

                        }
                    });
                } catch (IllegalStateException e)
                {
                    Toast.makeText(getApplicationContext(), "Error during login", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            if (mInitializeDialog != null)
            {
                mInitializeDialog.dismiss();
            }

            return super.onKeyDown(keyCode, event);
        }
        else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void startBrowserActivity(LiveConnectSession session)
    {
        assert session != null;
        mApp.setSession(session);
        mApp.setConnectClient(new LiveConnectClient(session));
        startActivity(new Intent(getApplicationContext(), BrowserActivity.class));
        finish();
    }

}
