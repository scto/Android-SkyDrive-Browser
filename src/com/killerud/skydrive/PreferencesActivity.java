package com.killerud.skydrive;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.killerud.skydrive.util.IOUtil;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveOperation;
import com.microsoft.live.LiveOperationException;
import com.microsoft.live.LiveOperationListener;
import org.json.JSONException;

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
            getAndUpdateSkyDriveQuota(null);
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

    static public class CompatibilityPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            String resourceArgument = getArguments().getString("pref-resource");

            addPreferencesFromResource(
                    getActivity().getApplicationContext().getResources().getIdentifier(
                            resourceArgument,
                            "xml",
                            getActivity().getApplicationContext().getPackageName()));

            if (resourceArgument.equals("preferences_data"))
            {
                ((PreferencesActivity) getActivity()).getAndUpdateSkyDriveQuota(this);
            }
        }
    }

    private void getAndUpdateSkyDriveQuota(final PreferenceFragment context)
    {
        LiveConnectClient client = ((BrowserForSkyDriveApplication) getApplication()).getConnectClient();
        if (client == null)
        {
            errorOnSkyDriveFetch(context);
            return;
        }

        client.getAsync("me/skydrive/quota", new LiveOperationListener()
        {
            @Override
            public void onComplete(LiveOperation operation)
            {
                String totalAvailableSpace = "";
                String unusedSpace = "";
                try
                {
                    totalAvailableSpace = operation.getResult().getString("quota");
                } catch (JSONException e)
                {
                    totalAvailableSpace = "unknown";
                }

                try
                {
                    unusedSpace = operation.getResult().getString("available");
                } catch (JSONException e)
                {
                    totalAvailableSpace = "unknown";
                }

                String baseString = getString(R.string.skyDriveQuotaSummary);

                Preference quotaPreference;
                if (context == null)
                {
                    quotaPreference = findPreference("skydrive_storage_quota");
                } else
                {
                    quotaPreference = context.findPreference("skydrive_storage_quota");
                }

                try
                {
                    quotaPreference.setSummary(createReadableSkyDriveQuotaString(baseString, unusedSpace, totalAvailableSpace));
                } catch (NumberFormatException e)
                {
                    errorOnSkyDriveFetch(context);
                }
            }

            @Override
            public void onError(LiveOperationException exception, LiveOperation operation)
            {
                errorOnSkyDriveFetch(context);
            }
        });
    }

    private void errorOnSkyDriveFetch(PreferenceFragment context)
    {
        Preference quotaPreference;
        if (context == null)
        {
            quotaPreference = findPreference("skydrive_storage_quota");
        } else
        {
            quotaPreference = context.findPreference("skydrive_storage_quota");
        }
        quotaPreference.setSummary(getString(R.string.errorQuotaFetch));
    }

    private String createReadableSkyDriveQuotaString(String baseString, String unusedSpace, String totalAvailableSpace) throws NumberFormatException
    {
        long unused = Long.parseLong(unusedSpace);
        long totalAvailable = Long.parseLong(totalAvailableSpace);
        long occupied = totalAvailable - unused;

        String occupiedInGigabytes = IOUtil.convertBytesToGigabytes(occupied) + "GB";
        String totalAvailableInGigabytes = IOUtil.convertBytesToGigabytes(totalAvailable) + "GB";

        return String.format(baseString, occupiedInGigabytes, totalAvailableInGigabytes);
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
