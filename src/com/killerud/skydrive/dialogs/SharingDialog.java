package com.killerud.skydrive.dialogs;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.constants.Constants;
import com.microsoft.live.*;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;


public class SharingDialog extends SherlockActivity{

    public static final String EXTRAS_FILE_IDS = "fileIds";
    public static final String EXTRAS_FILE_NAMES = "fileNames";
    private StringBuilder mResultBuilder;
    private EditText mResultView;
    private int mLinkCounter;
    private ArrayList<String> mFileIds;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.share_file);
        setTitle(R.string.sharingLinks);

        mResultBuilder = new StringBuilder();
        mResultView = (EditText) findViewById(R.id.sharingLinks);
        mLinkCounter = 0;

        mFileIds = getIntent().getStringArrayListExtra(EXTRAS_FILE_IDS);

        final BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        final LiveConnectClient client = app.getConnectClient();

        for(int i=0;i<mFileIds.size();i++)
        {
            getLinkToFile(mFileIds.get(i));
        }

        findViewById(R.id.shareButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Intent shareLinks = new Intent();
                shareLinks.setType("text/plain");
                shareLinks.setAction(Intent.ACTION_SEND);
                shareLinks.putExtra(Intent.EXTRA_TEXT, mResultBuilder.toString());
                startActivity(Intent.createChooser(shareLinks, getString(R.string.share)));
            }
        });

        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });

        ((CheckBox) findViewById(R.id.sharingEditable)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                mResultView.setText(R.string.sharingGeneratingLinks);
                mResultBuilder = new StringBuilder();
                mLinkCounter = 0;

                if(checked)
                {
                    for(int i=0;i<mFileIds.size();i++)
                    {
                        getLinkToEditFile(mFileIds.get(i));
                    }
                }else
                {
                    for(int i=0;i<mFileIds.size();i++)
                    {
                        getLinkToFile(mFileIds.get(i));
                    }
                }
            }
        });
    }



    private void getLinkToFile(String fileId) {
        final String path = fileId + "/shared_read_link";
        final LiveOperationListener operationListener = createOperationListener();
        ((BrowserForSkyDriveApplication) getApplication()).getConnectClient().getAsync(path, operationListener);
    }

    private void getLinkToEditFile(String fileId)
    {
        final String path = fileId + "/shared_edit_link";
        final LiveOperationListener operationListener = createOperationListener();
        ((BrowserForSkyDriveApplication) getApplication()).getConnectClient().getAsync(path, operationListener);
    }

    private LiveOperationListener createOperationListener() {
        return new LiveOperationListener() {
                public void onError(LiveOperationException exception, LiveOperation operation) {
                    Log.e(Constants.LOGTAG, "Error getting link to file: " + exception.getMessage());
                    return;
                }
                public void onComplete(LiveOperation operation) {
                    JSONObject result = operation.getResult();
                    mResultBuilder.append(result.optString("link"));
                    mResultBuilder.append("\n");
                    mLinkCounter++;
                    updateResultView();
                }
            };
    }

    private void updateResultView()
    {
        if(mLinkCounter == mFileIds.size())
            mResultView.setText(mResultBuilder.toString());
    }
}
