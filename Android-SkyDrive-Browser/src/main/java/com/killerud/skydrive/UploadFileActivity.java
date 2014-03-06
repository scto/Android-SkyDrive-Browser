package com.killerud.skydrive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.killerud.skydrive.constants.Constants;

import java.io.File;
import java.util.ArrayList;

public class UploadFileActivity extends BaseFileActivity
{
    public static final int PICK_FILE_REQUEST = 0;
    public static final String EXTRA_FILES_LIST = "filePaths";
    public static final String EXTRA_CURRENT_FOLDER_NAME = "currentFolderName";


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle(getString(R.string.uploadTo) + " " + getIntent().getStringExtra(EXTRA_CURRENT_FOLDER_NAME));
        setContentView(R.layout.saved_files_activity);

        super.fileListAdapter = new FileListAdapter(getApplicationContext());
        setListAdapter(super.fileListAdapter);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                File file = (File) parent.getItemAtPosition(position);
                if (actionMode == null)
                {
                    if (file.isDirectory())
                    {
                        previousFolders.push(currentFolder);
                        loadFolder(file);
                    } else
                    {
                        Intent data = new Intent();
                        ArrayList<String> filePath = new ArrayList<String>();
                        filePath.add(file.getPath());
                        data.putExtra(EXTRA_FILES_LIST, filePath);
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    }
                } else
                {
                    boolean isChecked = fileListAdapter.isChecked(position);
                    if (isChecked)
                    {
                        fileListAdapter.setChecked(position, false);
                        currentlySelectedFiles.remove(
                                ((FileListAdapter) getListAdapter()).getItem(position).getPath());
                    } else
                    {
                        fileListAdapter.setChecked(position, true);
                        currentlySelectedFiles.add(
                                ((FileListAdapter) getListAdapter()).getItem(position).getPath());
                    }
                    updateActionModeTitleWithSelectedCount();
                }
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l)
            {
                if (actionMode == null)
                {
                    actionMode = startSupportActionMode(new FileActionMode());
                    fileListAdapter.setChecked(position, true);
                    currentlySelectedFiles.add(
                            ((FileListAdapter) getListAdapter()).getItem(position).getPath());
                    updateActionModeTitleWithSelectedCount();
                }
                return true;
            }
        });
    }
}
