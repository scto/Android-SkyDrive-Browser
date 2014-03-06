package com.killerud.skydrive;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.util.ActionBarListActivity;
import com.killerud.skydrive.util.IOUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

/**
 * Created by william on 14.01.14.
 */
public class BaseFileActivity extends ActionBarListActivity
{
    protected ArrayList<String> currentlySelectedFiles;


    public static final int PICK_FILE_REQUEST = 0;
    public static final String EXTRA_FILES_LIST = "filePaths";
    public static final String EXTRA_CURRENT_FOLDER_NAME = "currentFolderName";

    protected File currentFolder;
    protected Stack<File> previousFolders;
    protected FileListAdapter fileListAdapter;
    protected ActionMode actionMode;

    protected LruCache thumbCache;


    public void addBitmapToThumbCache(String key, Bitmap bitmap)
    {
        if (getBitmapFromThumbCache(key) == null)
        {
            thumbCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromThumbCache(String key)
    {
        return (Bitmap) thumbCache.get(key);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Get memory class of this device, exceeding this amount will throw an
        // OutOfMemory exception.
        final int memClass = ((ActivityManager) getSystemService(
                Context.ACTIVITY_SERVICE)).getMemoryClass();

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = 1024 * 1024 * memClass / 8;
        thumbCache = new LruCache(cacheSize);


        currentlySelectedFiles = new ArrayList<String>();
        previousFolders = new Stack<File>();
        currentFolder = new File("/");

        if (savedInstanceState != null)
        {
            restoreInstanceState(savedInstanceState);
        }
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putString(Constants.STATE_CURRENT_FOLDER, currentFolder.getPath());


        String[] previous = new String[previousFolders.size()];
        for (int i = 0; i < previous.length; i++)
        {
            previous[i] = previousFolders.get(i).getPath();
        }

        savedInstanceState.putStringArray(Constants.STATE_PREVIOUS_FOLDERS, previous);

        if (actionMode != null)
        {
            savedInstanceState.putBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON, true);
        }

        ((BrowserForSkyDriveApplication) getApplication())
                .setCurrentlyCheckedPositions(
                        ((FileListAdapter) getListAdapter())
                                .getCheckedPositions());
    }

    protected void restoreInstanceState(Bundle savedInstanceState)
    {
        assert savedInstanceState != null;

        if (savedInstanceState.containsKey(Constants.STATE_CURRENT_FOLDER))
        {
            currentFolder = new File(savedInstanceState.getString(Constants.STATE_CURRENT_FOLDER));
        }


        if (savedInstanceState.containsKey(Constants.STATE_PREVIOUS_FOLDERS))
        {
            previousFolders = new Stack<File>();
            String[] folderIds = savedInstanceState.getStringArray(Constants.STATE_PREVIOUS_FOLDERS);
            for (int i = 0; i < folderIds.length; i++)
            {
                previousFolders.push(new File(folderIds[i]));
            }
        }

        if (savedInstanceState.containsKey(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
        {
            if (savedInstanceState.getBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
            {
                actionMode = startSupportActionMode(new FileActionMode());
            }
        }

        ((FileListAdapter) getListAdapter()).setCheckedPositions(((BrowserForSkyDriveApplication) getApplication())
                .getCurrentlyCheckedPositions());

        if (actionMode != null)
        {
            updateActionModeTitleWithSelectedCount();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && !previousFolders.isEmpty())
        {
            File folder = previousFolders.pop();
            loadFolder(folder);
            return true;
        } else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    protected String getFileExtension(File file)
    {
        String fileName = file.getName();
        String extension = "";
        int positionOfLastDot = fileName.lastIndexOf(".");
        extension = fileName.substring(positionOfLastDot + 1, fileName.length());
        return extension;
    }

    protected int determineFileDrawable(File file)
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
        loadFolder(currentFolder);
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

    protected void navigateBack()
    {
        if (previousFolders.isEmpty())
        {
            finish();
            return;
        }
        loadFolder(previousFolders.pop());
    }

    protected void loadFolder(File folder)
    {
        assert folder.isDirectory();
        currentFolder = folder;
        setSupportProgressBarIndeterminateVisibility(true);

        ArrayList<File> adapterFiles = fileListAdapter.getFiles();
        adapterFiles.clear();
        try
        {
            adapterFiles.addAll(Arrays.asList(folder.listFiles()));
        } catch (NullPointerException e)
        {
            adapterFiles.add(new File(currentFolder + "/" + getString(R.string.noFilesInFolder)));
        }

        if (actionMode == null)
        {
            fileListAdapter.clearChecked();
        }
        fileListAdapter.notifyDataSetChanged();
        SparseBooleanArray checkedPositions = fileListAdapter.getCheckedPositions();
        for (int i = 0; i < checkedPositions.size(); i++)
        {
            int adapterPosition = checkedPositions.keyAt(i);
            File fileSelected = fileListAdapter.getItem(adapterPosition);
            currentlySelectedFiles.add(fileSelected.getPath());
        }
        setSupportProgressBarIndeterminateVisibility(false);
    }


    protected class FileListAdapter extends BaseAdapter
    {
        private final LayoutInflater mInflater;
        private final ArrayList<File> mFiles;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mChecked;

        public FileListAdapter(Context context)
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
            if (checked && !isChecked(pos))
            {
                mChecked++;
            } else if (isChecked(pos) && !checked)
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
            currentlySelectedFiles.clear();
            notifyDataSetChanged();
        }

        public void checkAll()
        {
            for (int i = 0; i < mFiles.size(); i++)
            {
                if (!isChecked(i))
                {
                    mChecked++;
                }
                mCheckedPositions.put(i, true);
                currentlySelectedFiles.add(mFiles.get(i).getPath());
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

            final WeakReference viewReference = new WeakReference(convertView);

            File file = getItem(position);
            name.setText(file.getName());

            int fileDrawable = determineFileDrawable(file);
            type.setImageResource(fileDrawable);

            if (fileDrawable == R.drawable.image_x_generic)
            {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                if (!preferences.getBoolean(Constants.THUMBNAILS_DISABLED, false))
                {
                    final Bitmap bitmap = getBitmapFromThumbCache(file.getName());
                    AsyncTask getThumb = new AsyncTask<File, Void, Bitmap>()
                    {
                        @Override
                        protected Bitmap doInBackground(File... files)
                        {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(files[0].getPath(), options);

                            int sampleSize = (options.outHeight > options.outWidth ? options.outHeight / 60 : options.outWidth / 60);

                            options = new BitmapFactory.Options();
                            options.inSampleSize = sampleSize;
                            options.inScaled = false;

                            Bitmap thumb = BitmapFactory.decodeFile(files[0].getPath(), options);

                            cacheThumb(files[0], thumb);
                            return thumb;
                        }

                        protected void onPostExecute(Bitmap thumb)
                        {
                            if (viewReference != null && viewReference.get() != null)
                            {
                                ((ImageView)
                                        ((View) viewReference.get())
                                                .findViewById(R.id.skyDriveItemIcon))
                                        .setImageBitmap(thumb);
                            }
                        }
                    };

                    File thumbCache = cacheOfThumbExists(file);
                    if (bitmap != null)
                    {
                        if (viewReference != null && viewReference.get() != null)
                        {
                            ((ImageView)
                                    ((View) viewReference.get())
                                            .findViewById(R.id.skyDriveItemIcon))
                                    .setImageBitmap(bitmap);
                        }
                    } else if (thumbCache != null)
                    {
                        if (viewReference != null
                                && viewReference.get() != null)
                        {
                            getThumb.execute(new File[]{thumbCache});
                        }
                    } else
                    {

                        if (viewReference != null
                                && viewReference.get() != null)
                        {
                            getThumb.execute(new File[]{file});
                        }
                    }
                }
            }

            setChecked(isChecked(position));
            return mView;
        }


        private File cacheOfThumbExists(File file)
        {
            File cacheFolder = new File(Environment.getExternalStorageDirectory()
                    + "/Android/data/com.killerud.skydrive/thumbs/");
            if (!cacheFolder.exists())
            {
                cacheFolder.mkdirs();
                return null;
            }

            File thumbFile = new File(cacheFolder, file.getName());
            if (thumbFile.exists())
            {
                return thumbFile;
            } else
            {
                return null;
            }
        }

        private void cacheThumb(File thumbCacheFile, Bitmap thumb)
        {
            File cacheFolder = new File(Environment.getExternalStorageDirectory()
                    + "/Android/data/com.killerud.skydrive/thumbs/");
            if (!cacheFolder.exists())
            {
                cacheFolder.mkdirs();
            }

            OutputStream out;
            try
            {
                out = new BufferedOutputStream(new FileOutputStream(new File(cacheFolder, thumbCacheFile.getName())));
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
            } else
            {
                mView.setBackgroundResource(android.R.color.white);
            }
        }
    }


    protected class FileActionMode implements ActionMode.Callback
    {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            menu.add(getString(R.string.selectAll));
            menu.add(getString(R.string.uploadSelected));
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            return false;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, MenuItem item)
        {
            String title = item.getTitle().toString();
            if (title.equalsIgnoreCase(getString(R.string.uploadSelected)))
            {
                Intent data = new Intent();
                data.putExtra(EXTRA_FILES_LIST, (ArrayList<String>) currentlySelectedFiles.clone());
                setResult(Activity.RESULT_OK, data);

                fileListAdapter.clearChecked();
                updateActionModeTitleWithSelectedCount();

                mode.finish();
                finish();
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.selectAll)))
            {
                fileListAdapter.checkAll();
                updateActionModeTitleWithSelectedCount();
                item.setTitle(getString(R.string.selectNone));
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.selectNone)))
            {
                fileListAdapter.clearChecked();
                updateActionModeTitleWithSelectedCount();
                item.setTitle(getString(R.string.selectAll));
                return true;
            } else
            {
                return false;
            }

        }


        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            actionMode = null;
            ((FileListAdapter) getListAdapter()).clearChecked();
            ((FileListAdapter) getListAdapter()).notifyDataSetChanged();
            supportInvalidateOptionsMenu();
        }
    }

    protected void updateActionModeTitleWithSelectedCount()
    {
        final int checkedCount = ((FileListAdapter) getListAdapter()).getCheckedCount();
        switch (checkedCount)
        {
            case 0:
                actionMode.setTitle(null);
                break;
            case 1:
                actionMode.setTitle(getString(R.string.selectedOne));
                break;
            default:
                actionMode.setTitle("" + checkedCount + " " + getString(R.string.selectedSeveral));
                break;
        }
    }
}
