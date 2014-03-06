package com.killerud.skydrive;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.util.LruCache;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.util.ActionBarListActivity;
import com.killerud.skydrive.util.IOUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.*;

public class FileBrowserActivity extends BaseFileActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setTitle(getString(R.string.savedFilesTitle));

        setContentView(R.layout.saved_files_activity);
        super.fileListAdapter = new FileListAdapter(getApplicationContext());
        setListAdapter(super.fileListAdapter);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        File skyDriveFolder = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/");
        if (!skyDriveFolder.exists())
        {
            skyDriveFolder.mkdirs();
        }
        currentFolder = skyDriveFolder;

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
                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(file), IOUtil.findMimeTypeOfFile(file));
                        startActivity(intent);
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
