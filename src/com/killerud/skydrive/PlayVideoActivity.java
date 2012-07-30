package com.killerud.skydrive;

/**
 * User: William
 * Date: 07.05.12
 * Time: 16:59
 */

import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.objects.SkyDriveVideo;

/**
 * The Video dialog. Automatically starts buffering and playing a video using VideoView
 */
public class PlayVideoActivity extends SherlockActivity
{
    private SkyDriveVideo mVideo;
    private VideoView mVideoHolder;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
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
