package com.killerud.skydrive;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.microsoft.live.*;

import java.util.Arrays;

public class SignInActivity extends Activity {
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sign_in);
        mApp = (BrowserForSkyDriveApplication) getApplication();

        mResultTextView = (TextView) findViewById(R.id.introTextView);
        mAuthClient = new LiveAuthClient(this, Constants.APP_CLIENT_ID);
        mApp.setAuthClient(mAuthClient);

        mInitializeDialog = ProgressDialog.show(this, "", getString(R.string.initializing), true);

        mSignInButton = (Button) findViewById(R.id.signInButton);
        mIntroText = (TextView) findViewById(R.id.introTextView);


        mSignInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                mAuthClient.login(SignInActivity.this, Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener() {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState) {
                        if (status == LiveStatus.CONNECTED) {
                            startBrowserActivity(session);
                        } else {
                            Toast.makeText(getApplicationContext(), R.string.manualSignInError, Toast.LENGTH_SHORT);
                            Log.e(Constants.LOGTAG, "Login did not connect. Status is " + status + ".");
                        }
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState) {
                        Log.e(Constants.LOGTAG, exception.getMessage());
                    }
                });
                }catch (IllegalStateException e){
                    /* Already logged in , or login in progress*/
                    Log.e("ASE", e.getMessage());
                }

            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        try{
            mAuthClient.initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener() {
                @Override
                public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState) {
                    mInitializeDialog.dismiss();
                    if (status == LiveStatus.CONNECTED) {
                        startBrowserActivity(session);
                    } else {
                        Toast.makeText(getApplicationContext(), R.string.automaticSignInError, Toast.LENGTH_SHORT);
                        Log.e(Constants.LOGTAG, "Initialize did not connect. Status is " + status + ".");
                    }
                }

                @Override
                public void onAuthError(LiveAuthException exception, Object userState) {
                    mInitializeDialog.dismiss();
                    Log.e(Constants.LOGTAG, exception.getMessage());
                }
            });
        }catch (IllegalStateException e){
            /* Already logged in, or login in progress */
            Log.e("ASE", e.getMessage());
        }

    }

    private void startBrowserActivity(LiveConnectSession session) {
        assert session != null;
        mApp.setSession(session);
        mApp.setConnectClient(new LiveConnectClient(session));
        startActivity(new Intent(getApplicationContext(), BrowserActivity.class));
        finish();
    }

    private String fetchUserFirstName(){
        return null;
    }
}
