package com.killerud.skydrive;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/*
    Based on http://www.blackmoonit.com/2012/07/all_api_prefsactivity/
 */
public class PreferencesActivity extends SherlockPreferenceActivity
{
    protected Method loadHeaders = null;
    protected Method hasHeaders = null;

    public boolean isPreferenceActivityToBeUsedOnSDKV11OrHigher()
    {
        if (hasNeededMethodsForSDKV11OrHigher())
        {
            try
            {
                return (Boolean) hasHeaders.invoke(this);
            } catch (IllegalArgumentException e)
            {
            } catch (IllegalAccessException e)
            {
            } catch (InvocationTargetException e)
            {
            }
        }
        return false;
    }

    private boolean hasNeededMethodsForSDKV11OrHigher()
    {
        return hasHeaders != null && loadHeaders != null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try
        {
            loadHeaders = getClass().getMethod("loadHeadersFromResource", int.class, List.class);
            hasHeaders = getClass().getMethod("hasHeaders");
        } catch (NoSuchMethodException e)
        {
        }

        super.onCreate(savedInstanceState);
        if (!isPreferenceActivityToBeUsedOnSDKV11OrHigher())
        {
            addPreferencesFromResource(R.xml.preferences_features);
            addPreferencesFromResource(R.xml.preferences_data);
            addPreferencesFromResource(R.xml.preferences_about);
            //addPreferencesFromResource(R.xml.app_prefs_cat3);
        }
    }

    @Override
    public void onBuildHeaders(List<Header> headers)
    {
        try
        {
            loadHeaders.invoke(this, new Object[]{R.xml.preference_headers, headers});
        } catch (IllegalArgumentException e)
        {
        } catch (IllegalAccessException e)
        {
        } catch (InvocationTargetException e)
        {
        }
    }

    static public class CompatabilityPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle aSavedState)
        {
            super.onCreate(aSavedState);
            addPreferencesFromResource(
                    getActivity().getApplicationContext().getResources().getIdentifier(
                            getArguments().getString("pref-resource"),
                            "xml",
                            getActivity().getApplicationContext().getPackageName()));
        }
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                finish();
                return true;
            default:
                return false;
        }
    }


}
