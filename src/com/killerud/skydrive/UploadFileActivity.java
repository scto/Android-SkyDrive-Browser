package com.killerud.skydrive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.dialogs.NewFolderDialog;
import com.killerud.skydrive.util.IOUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

public class UploadFileActivity extends SherlockListActivity
{
    private ArrayList<String> mCurrentlySelectedFiles;


    public static final int PICK_FILE_REQUEST = 0;
    public static final String EXTRA_FILES_LIST = "filePaths";

    private File mCurrentFolder;
    private Stack<File> mPreviousFolders;
    private UploadFileListAdapter mFileBrowserAdapter;
    private ActionMode mActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);


        setTitle(getString(R.string.uploadToSkyDrive));
        setContentView(R.layout.file_picker);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        mCurrentlySelectedFiles = new ArrayList<String>();
        mPreviousFolders = new Stack<File>();
        mFileBrowserAdapter = new UploadFileListAdapter(getApplicationContext());
        setListAdapter(mFileBrowserAdapter);

        mCurrentFolder = new File("/");

        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                File file = (File) parent.getItemAtPosition(position);
                if (mActionMode == null)
                {
                    if (file.isDirectory())
                    {
                        mPreviousFolders.push(mCurrentFolder);
                        loadFolder(file);
                    }
                    else
                    {
                        Intent data = new Intent();
                        ArrayList<String> filePath = new ArrayList<String>();
                        filePath.add(file.getPath());
                        data.putExtra(EXTRA_FILES_LIST, filePath);
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    }
                }
                else
                {
                    boolean isChecked = mFileBrowserAdapter.isChecked(position);
                    if(isChecked)
                    {
                        mFileBrowserAdapter.setChecked(position, false);
                        mCurrentlySelectedFiles.remove(
                                ((UploadFileListAdapter) getListAdapter()).getItem(position).getPath());
                    }else
                    {
                        mFileBrowserAdapter.setChecked(position, true);
                        mCurrentlySelectedFiles.add(
                                ((UploadFileListAdapter) getListAdapter()).getItem(position).getPath());
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
                if (mActionMode == null)
                {
                    mActionMode = startActionMode(new UploadFileActionMode());
                    mFileBrowserAdapter.setChecked(position, true);
                    mCurrentlySelectedFiles.add(
                            ((UploadFileListAdapter) getListAdapter()).getItem(position).getPath());
                    updateActionModeTitleWithSelectedCount();
                }
                return true;
            }
        });

        if (savedInstanceState != null)
        {
            restoreInstanceState(savedInstanceState);
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putString(Constants.STATE_CURRENT_FOLDER, mCurrentFolder.getPath());


        String[] previous = new String[mPreviousFolders.size()];
        for (int i = 0; i < previous.length; i++)
        {
            previous[i] = mPreviousFolders.get(i).getPath();
        }

        savedInstanceState.putStringArray(Constants.STATE_PREVIOUS_FOLDERS, previous);

        if (mActionMode != null)
        {
            savedInstanceState.putBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON, true);
        }

        ((BrowserForSkyDriveApplication) getApplication())
                .setCurrentlyCheckedPositions(
                        ((UploadFileListAdapter) getListAdapter())
                                .getCheckedPositions());
    }

    private void restoreInstanceState(Bundle savedInstanceState)
    {
        assert savedInstanceState != null;

        if (savedInstanceState.containsKey(Constants.STATE_CURRENT_FOLDER))
        {
            mCurrentFolder = new File(savedInstanceState.getString(Constants.STATE_CURRENT_FOLDER));
        }


        if (savedInstanceState.containsKey(Constants.STATE_PREVIOUS_FOLDERS))
        {
            mPreviousFolders = new Stack<File>();
            String[] folderIds = savedInstanceState.getStringArray(Constants.STATE_PREVIOUS_FOLDERS);
            for (int i = 0; i < folderIds.length; i++)
            {
                mPreviousFolders.push(new File(folderIds[i]));
            }
        }

        if (savedInstanceState.containsKey(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
        {
            if (savedInstanceState.getBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
            {
                mActionMode = startActionMode(new UploadFileActionMode());
            }
        }

        ((UploadFileListAdapter) getListAdapter()).setCheckedPositions(((BrowserForSkyDriveApplication) getApplication())
                .getCurrentlyCheckedPositions());

        if(mActionMode != null)
        {
            updateActionModeTitleWithSelectedCount();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && !mPreviousFolders.isEmpty())
        {
            File folder = mPreviousFolders.pop();
            loadFolder(folder);
            return true;
        }
        else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    private String getFileExtension(File file)
    {
        String fileName = file.getName();
        String extension = "";
        int positionOfLastDot = fileName.lastIndexOf(".");
        extension = fileName.substring(positionOfLastDot + 1, fileName.length());
        return extension;
    }

    private int determineFileDrawable(File file)
    {
        if (file.isDirectory())
        {
            return R.drawable.folder;
        }

        String fileType = getFileExtension(file);
        return IOUtil.determineFileTypeDrawable(fileType);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        loadFolder(mCurrentFolder);
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                navigateBack();
                return true;
            default:
                return false;
        }
    }

    private void navigateBack()
    {
        if (mPreviousFolders.isEmpty())
        {
            finish();
            return;
        }
        loadFolder(mPreviousFolders.pop());
    }

    private void loadFolder(File folder)
    {
        assert folder.isDirectory();
        mCurrentFolder = folder;
        setSupportProgressBarIndeterminateVisibility(true);

        ArrayList<File> adapterFiles = mFileBrowserAdapter.getFiles();
        adapterFiles.clear();
        try{
            adapterFiles.addAll(Arrays.asList(folder.listFiles()));
        }catch (NullPointerException e){
            adapterFiles.add(new File(mCurrentFolder + "/" + getString(R.string.noFilesInFolder)));
        }

        if (mActionMode == null)
        {
            mFileBrowserAdapter.clearChecked();
        }
        mFileBrowserAdapter.notifyDataSetChanged();
        SparseBooleanArray checkedPositions = mFileBrowserAdapter.getCheckedPositions();
        for (int i = 0; i < checkedPositions.size(); i++)
        {
            int adapterPosition = checkedPositions.keyAt(i);
            File fileSelected = mFileBrowserAdapter.getItem(adapterPosition);
            mCurrentlySelectedFiles.add(fileSelected.getPath());
        }
        setSupportProgressBarIndeterminateVisibility(false);
    }



    private class UploadFileListAdapter extends BaseAdapter
    {
        private final LayoutInflater mInflater;
        private final ArrayList<File> mFiles;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mChecked;

        public UploadFileListAdapter(Context context)
        {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mFiles = new ArrayList<File>();
            mCheckedPositions = new SparseBooleanArray();
            mChecked = 0;
        }


        public void setCheckedPositions(SparseBooleanArray checkedPositions)
        {
            mChecked = checkedPositions.size();
            this.mCheckedPositions = checkedPositions;
            notifyDataSetChanged();
        }

        public SparseBooleanArray getCheckedPositions()
        {
            return this.mCheckedPositions;
        }

        public int getCheckedCount()
        {
            return this.mChecked;
        }

        public boolean isChecked(int pos)
        {
            return mCheckedPositions.get(pos, false);
        }

        public void setChecked(int pos, boolean checked)
        {
            if(checked && !isChecked(pos))
            {
                mChecked++;
            }else if(isChecked(pos) && !checked)
            {
                mChecked--;
            }
            mCheckedPositions.put(pos, checked);
            notifyDataSetChanged();
        }

        public void clearChecked()
        {
            mChecked = 0;
            mCheckedPositions = new SparseBooleanArray();
            mCurrentlySelectedFiles.clear();
            notifyDataSetChanged();
        }

        public void checkAll()
        {
            for (int i = 0; i < mFiles.size(); i++)
            {
                if(!isChecked(i))
                {
                    mChecked++;
                }
                mCheckedPositions.put(i, true);
                mCurrentlySelectedFiles.add(mFiles.get(i).getPath());
            }
            notifyDataSetChanged();
        }

        public ArrayList<File> getFiles()
        {
            return mFiles;
        }

        @Override
        public int getCount()
        {
            return mFiles.size();
        }

        @Override
        public File getItem(int position)
        {
            return mFiles.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            mView = convertView != null ? convertView :
                    mInflater.inflate(R.layout.skydrive_list_item,
                            parent, false);
            TextView name = (TextView) mView.findViewById(R.id.nameTextView);
            ImageView type = (ImageView) mView.findViewById(R.id.skyDriveItemIcon);

            final View view = mView; //Changes all the time, so we need a reference

            File file = getItem(position);
            name.setText(file.getName());

            int fileDrawable = determineFileDrawable(file);
            if(fileDrawable == R.drawable.image_x_generic)
            {
                AsyncTask getThumb = new AsyncTask<File, Void, Bitmap>()
                {
                    @Override
                    protected Bitmap doInBackground(File... files) {
                        File thumbCacheFile = new File(Environment.getExternalStorageDirectory()
                                + "/Android/data/com.killerud.skydrive/thumbs/" + files[0].getPath());
                        if(thumbCacheFile.exists())
                        {
                            return BitmapFactory.decodeFile(thumbCacheFile.getPath());
                        }

                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(files[0].getPath(), options);

                        int sampleSize = (options.outHeight>options.outWidth?options.outHeight/60:options.outWidth/60);
                        Log.i(Constants.LOGTAG, "Sample size is " + sampleSize);
                        options = new BitmapFactory.Options();
                        options.inSampleSize = sampleSize;

                        Bitmap thumb = BitmapFactory.decodeFile(files[0].getPath(), options);

                        cacheThumb(thumbCacheFile, thumb);
                        return thumb;
                    }

                    protected void onPostExecute(Bitmap thumb)
                    {
                        ((ImageView) view.findViewById(R.id.skyDriveItemIcon)).setImageBitmap(thumb);
                    }
                };

                getThumb.execute(new File[]{file});
            }else{
                type.setImageResource(fileDrawable);
            }
            setChecked(isChecked(position));
            return mView;
        }

        private void cacheThumb(File thumbCacheFile, Bitmap thumb) {
            File cacheFolder = new File(Environment.getExternalStorageDirectory()
                    + "/Android/data/com.killerud.skydrive/thumbs/");
            if (!cacheFolder.exists())
            {
                cacheFolder.mkdirs();
            }

            OutputStream out;
            try
            {
                out = new BufferedOutputStream(new FileOutputStream(thumbCacheFile));
                thumb.compress(Bitmap.CompressFormat.PNG, 85, out);
                out.flush();
                out.close();
                Log.i(Constants.LOGTAG, "Thumb cached for image " + thumbCacheFile.getName());
            } catch (Exception e)
            {
                Log.e(Constants.LOGTAG, "Could not cache thumbnail for " + thumbCacheFile.getName()
                        + ". " + e.toString());
            }
        }


        private void setChecked(boolean checked)
        {
            if (checked)
            {
                mView.setBackgroundResource(R.color.HightlightBlue);
            }
            else
            {
                mView.setBackgroundResource(android.R.color.white);
            }
        }
    }


    private class UploadFileActionMode implements com.actionbarsherlock.view.ActionMode.Callback
    {

        @Override
        public boolean onCreateActionMode(com.actionbarsherlock.view.ActionMode mode, Menu menu)
        {
            menu.add(getString(R.string.selectAll))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(getString(R.string.uploadSelected))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(com.actionbarsherlock.view.ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onActionItemClicked(final com.actionbarsherlock.view.ActionMode mode, MenuItem item)
        {
            String title = item.getTitle().toString();
            if (title.equalsIgnoreCase(getString(R.string.uploadSelected)))
            {
                Intent data = new Intent();
                data.putExtra(EXTRA_FILES_LIST, (ArrayList<String>) mCurrentlySelectedFiles.clone());
                setResult(Activity.RESULT_OK, data);

                mFileBrowserAdapter.clearChecked();
                updateActionModeTitleWithSelectedCount();

                mode.finish();
                finish();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.selectAll)))
            {
                mFileBrowserAdapter.checkAll();
                updateActionModeTitleWithSelectedCount();
                item.setTitle(getString(R.string.selectNone));
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.selectNone)))
            {
                mFileBrowserAdapter.clearChecked();
                updateActionModeTitleWithSelectedCount();
                item.setTitle(getString(R.string.selectAll));
                return true;
            }
            else
            {
                return false;
            }

        }


        @Override
        public void onDestroyActionMode(com.actionbarsherlock.view.ActionMode mode)
        {
            mActionMode = null;
            ((UploadFileListAdapter) getListAdapter()).clearChecked();
            ((UploadFileListAdapter) getListAdapter()).notifyDataSetChanged();
            supportInvalidateOptionsMenu();
        }
    }

    private void updateActionModeTitleWithSelectedCount() {
        final int checkedCount = ((UploadFileListAdapter) getListAdapter()).getCheckedCount();
        switch (checkedCount) {
            case 0:
                mActionMode.setTitle(null);
                break;
            case 1:
                mActionMode.setTitle(getString(R.string.selectedOne));
                break;
            default:
                mActionMode.setTitle("" + checkedCount + " " + getString(R.string.selectedSeveral));
                break;
        }
    }
}
