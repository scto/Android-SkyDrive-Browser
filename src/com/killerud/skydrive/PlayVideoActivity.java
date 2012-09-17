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
    private SkyDriveVideo video;
    private VideoView videoHolder;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_activity);

        video = ((BrowserForSkyDriveApplication) getApplication()).getCurrentVideo();
        if(video == null)
        {
            setTitle(R.string.errorFileNotFound);
        }else
        {
            setTitle(video.getName());
            videoHolder = (VideoView) findViewById(R.id.videoView);
            videoHolder.setMediaController(new MediaController(videoHolder.getContext()));
            videoHolder.setVideoURI(Uri.parse(video.getSource()));
            videoHolder.start();
        }
    }


}
