package com.killerud.skydrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Starts the camera observer service for automatic uploads on boot,
 * if the preference is set to true.
 */
public class OnBootBroadcastReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("AEfS", "Received boot completed");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (preferences.getBoolean("automatic_camera_upload", false))
        {
            Log.i("AEfS", "Post-boot starting of the service");
            context.startService(new Intent(context, CameraObserverService.class));
        } else
        {
            context.stopService(new Intent(context, CameraObserverService.class));
        }
    }
}
