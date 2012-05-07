package com.killerud.skydrive;

import android.app.Application;
import com.microsoft.live.LiveAuthClient;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveConnectSession;

/**
 * User: William
 * Date: 25.04.12
 * Time: 14:29
 */
public class BrowserForSkyDriveApplication extends Application {
    private LiveAuthClient mAuthClient;
    private LiveConnectClient mConnectClient;
    private LiveConnectSession mSession;
    private final String LOGCAT_TAG = "ASE";

    private String mFirstName;

    public LiveAuthClient getAuthClient() {
        return mAuthClient;
    }

    public LiveConnectClient getConnectClient() {
        return mConnectClient;
    }

    public LiveConnectSession getSession() {
        return mSession;
    }

    public void setAuthClient(LiveAuthClient authClient) {
        mAuthClient = authClient;
    }

    public void setConnectClient(LiveConnectClient connectClient) {
        mConnectClient = connectClient;
    }

    public void setSession(LiveConnectSession session) {
        mSession = session;
    }

    public void setUserFirstName(String name){
        mFirstName = name;
    }

    public String getUserFirstName(){
        return mFirstName;
    }

    public String getDebugTag(){
        return LOGCAT_TAG;
    }

}
