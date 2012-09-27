package com.killerud.skydrive;

import android.app.Application;
import android.os.FileObserver;
import android.util.SparseBooleanArray;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.killerud.skydrive.objects.SkyDriveObject;
import com.killerud.skydrive.objects.SkyDriveVideo;
import com.microsoft.live.LiveAuthClient;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveConnectSession;

import java.util.ArrayList;
import java.util.Stack;

/**
 * User: William
 * Date: 25.04.12
 * Time: 14:29
 */
public class BrowserForSkyDriveApplication extends Application
{
    private LiveAuthClient authClient;
    private LiveConnectClient connectClient;
    private LiveConnectSession session;
    private SkyDriveAudio currentMusic;
    private SkyDriveVideo currentVideo;
    private String firstName;
    private BrowserActivity browserActivity;
    private SparseBooleanArray checkedPositions;
    private FileObserver cameraFileObserver;
    private Stack<ArrayList<SkyDriveObject>> navigationHistory;


    public void setCurrentBrowser(BrowserActivity browser)
    {
        this.browserActivity = browser;
    }

    public BrowserActivity getCurrentBrowser()
    {
        if (browserActivity == null)
        {
            return new BrowserActivity();
        }
        return this.browserActivity;
    }

    public LiveAuthClient getAuthClient()
    {
        if (authClient == null)
        {
            return new LiveAuthClient(getApplicationContext(), Constants.APP_CLIENT_ID);
        }
        return authClient;
    }

    public LiveConnectClient getConnectClient()
    {
        return connectClient;
    }

    public LiveConnectSession getSession()
    {
        return session;
    }

    public void setAuthClient(LiveAuthClient authClient)
    {
        this.authClient = authClient;
    }

    public void setConnectClient(LiveConnectClient connectClient)
    {
        this.connectClient = connectClient;
    }

    public void setSession(LiveConnectSession session)
    {
        this.session = session;
    }

    public void setUserFirstName(String name)
    {
        firstName = name;
    }

    public String getUserFirstName()
    {
        return firstName;
    }

    public void setCurrentMusic(SkyDriveAudio audio)
    {
        this.currentMusic = audio;
    }

    public SkyDriveAudio getAudioClicked()
    {
        return this.currentMusic;
    }

    public void setCurrentVideo(SkyDriveVideo video)
    {
        this.currentVideo = video;
    }

    public SkyDriveVideo getCurrentVideo()
    {
        return this.currentVideo;
    }

    public SparseBooleanArray getCurrentlyCheckedPositions()
    {
        if (checkedPositions != null)
        {
            return checkedPositions;
        }

        return new SparseBooleanArray();
    }

    public void setCurrentlyCheckedPositions(SparseBooleanArray checkedPositions)
    {
        this.checkedPositions = checkedPositions;
    }

    public void setCameraFileObserver(FileObserver cameraFileObserver)
    {
        this.cameraFileObserver = cameraFileObserver;
    }

    public FileObserver getCameraFileObserver()
    {
        return this.cameraFileObserver;
    }

    public Stack<ArrayList<SkyDriveObject>> getNavigationHistory() {
        return navigationHistory;
    }

    public void setNavigationHistory(Stack<ArrayList<SkyDriveObject>> navigationHistory) {
        this.navigationHistory = navigationHistory;
    }
}
