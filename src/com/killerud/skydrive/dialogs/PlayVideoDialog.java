package com.killerud.skydrive.dialogs;

/**
 * User: William
 * Date: 07.05.12
 * Time: 16:59
 */

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.XLoader;
import com.killerud.skydrive.objects.SkyDriveAudio;
import com.killerud.skydrive.objects.SkyDriveObject;
import com.killerud.skydrive.objects.SkyDriveVideo;
import com.microsoft.live.LiveConnectClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/** The Video dialog. Automatically starts buffering and playing a video using VideoView
 */
public class PlayVideoDialog extends SherlockActivity {
    private SkyDriveVideo mVideo;
    private VideoView mVideoHolder;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_activity);

        mVideo = ((BrowserForSkyDriveApplication) getApplication()).getCurrentVideo();
        setTitle(mVideo.getName());

        mVideoHolder = (VideoView) findViewById(R.id.videoView);
        mVideoHolder.setMediaController(new MediaController(mVideoHolder.getContext()));
        mVideoHolder.setVideoURI(Uri.parse(mVideo.getSource()));
        mVideoHolder.start();
    }


}
