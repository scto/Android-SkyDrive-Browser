//------------------------------------------------------------------------------
// Copyright (c) 2012 Microsoft Corporation. All rights reserved.
//------------------------------------------------------------------------------

package com.killerud.skydrive.dialogs;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.killerud.skydrive.R;
import com.killerud.skydrive.XLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

public class UploadFileDialog extends ListActivity {
    private ArrayList<String> mCurrentlySelectedFiles;





    public static final int PICK_FILE_REQUEST = 0;
    public static final String ACTION_SINGLE_FILE = "uploadOneFile";
    public static final String ACTION_MULTIPLE_FILES = "uploadMultipleFiles";
    public static final String EXTRA_FILE_PATH = "filePath";
    public static final String EXTRA_FILES_LIST = "filePaths";

    private File mCurrentFolder;
    private Stack<File> mPrevFolders;
    private UploadFileListAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_picker);
        mCurrentlySelectedFiles = new ArrayList<String>();
        mPrevFolders = new Stack<File>();

        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File file = (File) parent.getItemAtPosition(position);

                if (file.isDirectory()) {
                    mPrevFolders.push(mCurrentFolder);
                    loadFolder(file);
                } else {
                    Intent data = new Intent();
                    data.setAction(ACTION_SINGLE_FILE);
                    data.putExtra(EXTRA_FILE_PATH, file.getPath());
                    setResult(Activity.RESULT_OK, data);
                    finish();
                }
            }
        });

        mAdapter = new UploadFileListAdapter(UploadFileDialog.this);
        setListAdapter(mAdapter);

        XLoader loader = new XLoader(getApplicationContext());

        Button uploadButton = (Button) findViewById(R.id.uploadSelected);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent data = new Intent();
                data.setAction(ACTION_MULTIPLE_FILES);
                data.putExtra(EXTRA_FILES_LIST, mCurrentlySelectedFiles);
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !mPrevFolders.isEmpty()) {
            File folder = mPrevFolders.pop();
            loadFolder(folder);
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void onCheckboxClicked(View checkedView) {
        ListView listView = getListView();
        File file = mAdapter.getItem(listView.getPositionForView(checkedView));
        if (((CheckBox) checkedView).isChecked()) {

            mAdapter.setChecked(listView.getPositionForView(checkedView), true);
            mCurrentlySelectedFiles.add(file.getPath());
        } else {
            mAdapter.setChecked(listView.getPositionForView(checkedView), false);
            mCurrentlySelectedFiles.remove(file.getPath());
            mCurrentlySelectedFiles.trimToSize();

        }
    }

    private String getFileExtension(File file){
        String fileName = file.getName();
        String extension = "";
        int positionOfLastDot = fileName.lastIndexOf(".");
        extension = fileName.substring(positionOfLastDot+1,fileName.length());
        return extension;
    }

    private int determineFileDrawable(File file) {
        if(file.isDirectory()){
            return R.drawable.folder;
        }

        String fileType = getFileExtension(file);
        if (fileType.equalsIgnoreCase("png") ||
                fileType.equalsIgnoreCase("jpg") ||
                fileType.equalsIgnoreCase("jpeg") ||
                fileType.equalsIgnoreCase("tiff") ||
                fileType.equalsIgnoreCase("gif") ||
                fileType.equalsIgnoreCase("bmp") ||
                fileType.equalsIgnoreCase("raw")) {
            return R.drawable.image_x_generic;
        } else if (fileType.equalsIgnoreCase("mp3") ||
                fileType.equalsIgnoreCase("wav") ||
                fileType.equalsIgnoreCase("wma") ||
                fileType.equalsIgnoreCase("acc") ||
                fileType.equalsIgnoreCase("ogg")) {
            return R.drawable.audio_x_generic;
        } else if (fileType.equalsIgnoreCase("mov") ||
                fileType.equalsIgnoreCase("avi") ||
                fileType.equalsIgnoreCase("divx") ||
                fileType.equalsIgnoreCase("wmv") ||
                fileType.equalsIgnoreCase("ogv") ||
                fileType.equalsIgnoreCase("mkv") ||
                fileType.equalsIgnoreCase("mp4")) {
            return R.drawable.video_x_generic;
        }
        return R.drawable.text_x_preview;
    }

    @Override
    protected void onStart() {
        super.onStart();

        loadFolder(Environment.getExternalStorageDirectory());
    }

    private void loadFolder(File folder) {
        assert folder.isDirectory();
        mCurrentFolder = folder;

        ProgressDialog progressDialog =
                ProgressDialog.show(this, "", "Loading. Please wait...", true);
        ArrayList<File> adapterFiles = mAdapter.getFiles();
        adapterFiles.clear();
        adapterFiles.addAll(Arrays.asList(folder.listFiles()));
        mAdapter.notifyDataSetChanged();
        mAdapter.clearChecked();

        progressDialog.dismiss();
    }

    private class UploadFileListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final ArrayList<File> mFiles;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mPosition;

        public UploadFileListAdapter(Context context) {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mFiles = new ArrayList<File>();
            mCheckedPositions = new SparseBooleanArray();
        }


        public boolean isChecked(int pos){
            return mCheckedPositions.get(pos, false);
        }

        public void setChecked(int pos, boolean checked){
            mCheckedPositions.put(pos,checked);
            notifyDataSetChanged();
        }

        public void clearChecked(){
            mCheckedPositions = new SparseBooleanArray();
            notifyDataSetChanged();
        }

        public ArrayList<File> getFiles() {
            return mFiles;
        }

        @Override
        public int getCount() {
            return mFiles.size();
        }

        @Override
        public File getItem(int position) {
            return mFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            mView = convertView != null ? convertView :
                    mInflater.inflate(R.layout.skydrive_list_item,
                            parent,
                            false);
            TextView name = (TextView) mView.findViewById(R.id.nameTextView);
            ImageView type = (ImageView) mView.findViewById(R.id.skyDriveItemIcon);

            File file = getItem(position);
            name.setText(file.getName());
            type.setImageResource(determineFileDrawable(file));
            setChecked(isChecked(position));
            return mView;
        }

        private void setChecked(boolean checked){
            CheckBox checkBox = (CheckBox) mView.findViewById(R.id.selectedSkyDrive);
            checkBox.setChecked(checked);
        }
    }
}
