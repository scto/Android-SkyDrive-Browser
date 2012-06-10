package com.killerud.skydrive;

import android.app.Application;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.killerud.skydrive.objects.SkyDriveVideo;
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
    private SkyDriveAudio mCurrentMusic;
    private SkyDriveVideo mCurrentVideo;
    private String mFirstName;
    private BrowserActivity mCurrentBrowser;


    public void setCurrentBrowser(BrowserActivity browser){
        this.mCurrentBrowser = browser;
    }

    public BrowserActivity getCurrentBrowser(){
        if(mCurrentBrowser==null){
            return new BrowserActivity();
        }
        return this.mCurrentBrowser;
    }

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

    public void setCurrentMusic(SkyDriveAudio audio){
        this.mCurrentMusic = audio;
    }

    public SkyDriveAudio getCurrentMusic(){
        return this.mCurrentMusic;
    }

    public void setCurrentVideo(SkyDriveVideo video){
        this.mCurrentVideo = video;
    }

    public SkyDriveVideo getCurrentVideo(){
        return this.mCurrentVideo;
    }



}
