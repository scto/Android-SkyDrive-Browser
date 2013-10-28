package com.killerud.skydrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Starts the camera observer service for automatic uploads on boot,
 * if the preference is set to true.
 */
public class OnBootBroadcastReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("automatic_camera_upload", false))
        {
            context.startService(new Intent(context, CameraObserverService.class));
        } else
        {
            context.stopService(new Intent(context, CameraObserverService.class));
        }
    }
}
