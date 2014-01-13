package com.killerud.skydrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.killerud.skydrive.constants.Constants;

public class ConnectionStateBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getExtras() == null) return;
        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo mobileNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isWiFiOnly = preferences.getBoolean("limit_all_to_wifi", false);
        boolean isCameraWiFiOnly = preferences.getBoolean("camera_upload_wifi_only", false);
        boolean serviceIsWanted = preferences.getBoolean("automatic_camera_upload", false);


        Intent uploadImagesInQueue = new Intent(context, CameraObserverService.class);
        uploadImagesInQueue.setAction(Constants.ACTION_UPLOAD_QUEUE);
        if(mobileNetInfo != null && mobileNetInfo.isConnected())
        {
            if(isWiFiOnly || isCameraWiFiOnly || !serviceIsWanted) return;
            context.startService(uploadImagesInQueue);
        }else if(wifiNetInfo != null && wifiNetInfo.isConnected())
        {
            if(!serviceIsWanted) return;
            context.startService(uploadImagesInQueue);
        }
    }
}
