package com.killerud.skydrive;

import android.os.Bundle;
import com.actionbarsherlock.app.SherlockPreferenceActivity;

/**
 * User: William
 * Date: 11.06.12
 * Time: 14:16
 */
public class SettingsActivity extends SherlockPreferenceActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

}
