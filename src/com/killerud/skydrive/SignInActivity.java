package com.killerud.skydrive;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
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
import java.util.Arrays;

public class SignInActivity extends SherlockActivity
{
    BrowserForSkyDriveApplication application;
    TextView resultTextView;
    LiveAuthClient liveAuthClient;
    Button signInButton;
    TextView introText;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.sign_in);
        handleLocalCache();

        application = (BrowserForSkyDriveApplication) getApplication();

        resultTextView = (TextView) findViewById(R.id.introTextView);
        liveAuthClient = new LiveAuthClient(this, Constants.APP_CLIENT_ID);
        application.setAuthClient(liveAuthClient);

        signInButton = (Button) findViewById(R.id.signInButton);
        introText = (TextView) findViewById(R.id.introTextView);

        final TextView noAccountText = (TextView) findViewById(R.id.noAccountText);
        noAccountText.setText(getString(R.string.noAccountQuestion));
        Linkify.addLinks(noAccountText, Linkify.ALL);

        signInButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                setSupportProgressBarIndeterminateVisibility(true);

                try
                {
                    liveAuthClient.login(SignInActivity.this, Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
                    {
                        @Override
                        public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                        {
                            setSupportProgressBarIndeterminateVisibility(false);

                            if (status == LiveStatus.CONNECTED)
                            {
                                startBrowserActivity(session);
                            } else
                            {
                                Toast.makeText(getApplicationContext(), R.string.manualSignInError, Toast.LENGTH_SHORT);
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
        });


    }

    @Override
    protected void onStart()
    {
        super.onStart();
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
                        startBrowserActivity(session);
                    } else
                    {
                        Toast.makeText(getApplicationContext(), R.string.automaticSignInError, Toast.LENGTH_SHORT);
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
            if (liveAuthClient != null)
            {
                try
                {
                    liveAuthClient.logout(new LiveAuthListener()
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
                    Toast.makeText(getApplicationContext(), getString(R.string.errorDuringLogin), Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            setSupportProgressBarIndeterminateVisibility(false);

            return super.onKeyDown(keyCode, event);
        } else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void startBrowserActivity(LiveConnectSession session)
    {
        assert session != null;
        application.setSession(session);
        application.setConnectClient(new LiveConnectClient(session));
        startActivity(new Intent(getApplicationContext(), BrowserActivity.class));
        finish();
    }

}
