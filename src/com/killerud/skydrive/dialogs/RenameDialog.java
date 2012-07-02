package com.killerud.skydrive.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.XLoader;
import com.microsoft.live.LiveConnectClient;

import java.util.ArrayList;

/**
 * User: William
 * Date: 10.05.12
 * Time: 23:22
 */
public class RenameDialog extends SherlockActivity
{
    public static final String EXTRAS_FILE_IDS = "fileIds";
    public static final String EXTRAS_FILE_NAMES = "fileNames";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rename_file);
        setTitle(R.string.renameTitle);

        final ArrayList<String> fileIds = getIntent().getStringArrayListExtra(EXTRAS_FILE_IDS);
        final ArrayList<String> fileNames = getIntent().getStringArrayListExtra(EXTRAS_FILE_NAMES);


        final BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        final LiveConnectClient client = app.getConnectClient();

        final EditText name = (EditText) findViewById(R.id.nameEditText);

        try
        {
            name.setText(fileNames.get(0));
        }catch (IndexOutOfBoundsException e)
        {
            name.setText(getString(R.string.rename));
        }

        final EditText description = (EditText) findViewById(R.id.descriptionEditText);
        final XLoader loader = new XLoader(app.getCurrentBrowser());

        findViewById(R.id.renameButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ((BrowserForSkyDriveApplication) getApplication()).getCurrentBrowser().setSupportProgressBarIndeterminateVisibility(true);
                loader.renameFiles(client, fileIds, fileNames,
                        name.getText().toString(),
                        description.getText().toString());
                finish();
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



    }
}
