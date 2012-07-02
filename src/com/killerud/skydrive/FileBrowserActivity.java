package com.killerud.skydrive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.killerud.skydrive.constants.Constants;
import com.killerud.skydrive.util.IOUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

public class FileBrowserActivity extends SherlockListActivity
{
    private ArrayList<String> mCurrentlySelectedFiles;

    public static final String EXTRA_FILES_LIST = "filePaths";

    private File mCurrentFolder;
    private Stack<File> mPreviousFolders;
    private FileBrowserListAdapter mFileBrowserAdapter;
    private ActionMode mActionMode;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setTitle(getString(R.string.savedFilesTitle));
        setContentView(R.layout.file_picker);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCurrentlySelectedFiles = new ArrayList<String>();
        mPreviousFolders = new Stack<File>();
        mFileBrowserAdapter = new FileBrowserListAdapter(getApplicationContext());
        setListAdapter(mFileBrowserAdapter);

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
                                ((FileBrowserListAdapter) getListAdapter()).getItem(position).getPath());
                    }else
                    {
                        mFileBrowserAdapter.setChecked(position, true);
                        mCurrentlySelectedFiles.add(
                            ((FileBrowserListAdapter) getListAdapter()).getItem(position).getPath());
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
                    mActionMode = startActionMode(new FileBrowserActionMode());
                    mFileBrowserAdapter.setChecked(position, true);
                    mCurrentlySelectedFiles.add(
                            ((FileBrowserListAdapter) getListAdapter()).getItem(position).getPath());
                    updateActionModeTitleWithSelectedCount();
                }
                return true;
            }
        });

        if(savedInstanceState != null)
        {
            restoreInstanceState(savedInstanceState);
        }
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
                mActionMode = startActionMode(new FileBrowserActionMode());
            }
        }

        ((FileBrowserListAdapter) getListAdapter()).setCheckedPositions(((BrowserForSkyDriveApplication) getApplication())
                .getCurrentlyCheckedPositions());
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
                        ((FileBrowserListAdapter) getListAdapter())
                                .getCheckedPositions());
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

    @Override
    protected void onStart()
    {
        super.onStart();
        File skyDriveFolder = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/");
        if (!skyDriveFolder.exists())
        {
            skyDriveFolder.mkdirs();
        }
        loadFolder(skyDriveFolder);
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
        adapterFiles.addAll(Arrays.asList(folder.listFiles()));
        if(mActionMode == null)
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

    private int determineFileDrawable(File file)
    {
        if (file.isDirectory())
        {
            return R.drawable.folder;
        }
        String fileType = IOUtil.getFileExtension(file);
        return IOUtil.determineFileTypeDrawable(fileType);
    }

    private class FileBrowserListAdapter extends BaseAdapter
    {
        private final LayoutInflater mInflater;
        private final ArrayList<File> mFiles;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mChecked;

        public FileBrowserListAdapter(Context context)
        {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mFiles = new ArrayList<File>();
            mCheckedPositions = new SparseBooleanArray();
            mChecked = 0;
        }


        public boolean isChecked(int pos)
        {
            return mCheckedPositions.get(pos, false);
        }

        public int getCheckedCount()
        {
            return this.mChecked;
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

        public void setChecked(int pos, boolean checked)
        {
            if(checked != isChecked(pos) && checked)
            {
                mChecked++;
            }else if(checked != isChecked(pos)){
                mChecked--;
            }

            mCheckedPositions.put(pos, checked);
            notifyDataSetChanged();
        }

        public void clearChecked()
        {
            mChecked = 0;
            mCheckedPositions = new SparseBooleanArray();
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

            File file = getItem(position);
            name.setText(file.getName());
            type.setImageResource(determineFileDrawable(file));
            setChecked(isChecked(position));
            return mView;
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


    private class FileBrowserActionMode implements com.actionbarsherlock.view.ActionMode.Callback
    {

        @Override
        public boolean onCreateActionMode(com.actionbarsherlock.view.ActionMode mode, Menu menu)
        {
            menu.add(getString(R.string.delete))
                    .setIcon(R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM |
                            MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            menu.add(getString(R.string.selectAll))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
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
            if (title.equalsIgnoreCase(getString(R.string.selectAll)))
            {
                mFileBrowserAdapter.checkAll();
                item.setTitle(getString(R.string.selectNone));
                updateActionModeTitleWithSelectedCount();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.selectNone)))
            {
                mFileBrowserAdapter.clearChecked();
                mCurrentlySelectedFiles.clear();
                item.setTitle(getString(R.string.selectAll));
                updateActionModeTitleWithSelectedCount();
                return true;
            }
            else if (title.equalsIgnoreCase(getString(R.string.delete)))
            {
                final AlertDialog dialog = new AlertDialog.Builder(getSupportActionBar().getThemedContext()).create();
                dialog.setTitle(getString(R.string.deleteConfirmationTitle));
                dialog.setIcon(R.drawable.warning_triangle);
                StringBuilder deleteMessage = new StringBuilder();
                deleteMessage.append(getString(R.string.deleteConfirmationBody));
                for (int i = 0; i < mCurrentlySelectedFiles.size(); i++)
                {
                    int index = mCurrentlySelectedFiles.get(i).lastIndexOf("/");
                    if (index != -1)
                    {
                        deleteMessage.append(mCurrentlySelectedFiles.get(i)
                                .substring(index + 1));
                        deleteMessage.append("\n");
                    }
                }
                deleteMessage.append(getString(R.string.deleteConfirmationQuestion));

                dialog.setMessage(deleteMessage.toString());
                dialog.setButton(getString(R.string.yes), new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        ArrayList<String> files = mCurrentlySelectedFiles;
                        for (int j = 0; j < files.size(); j++)
                        {
                            File file = new File(files.get(j));
                            if (file.exists())
                            {
                                if(file.isDirectory())
                                {
                                    File[] directoryFiles = file.listFiles();
                                    for(int k=0;k<directoryFiles.length;k++)
                                    {
                                        files.add(directoryFiles[k].getPath());
                                    }
                                }else
                                {
                                    file.delete();
                                }
                            }
                        }

                        /* Second round to remove empty folders */
                        for (int j = files.size()-1; j >= 0; j--)
                        {
                            File file = new File(files.get(j));
                            file.delete();
                        }

                        finish();
                        startActivity(getIntent());

                        mode.finish();
                    }
                });
                dialog.setButton2(getString(R.string.no), new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        dialog.dismiss();
                    }
                });
                dialog.show();
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
            ((FileBrowserListAdapter) getListAdapter()).notifyDataSetChanged();
            supportInvalidateOptionsMenu();
        }
    }

    private void updateActionModeTitleWithSelectedCount() {
        final int checkedCount = ((FileBrowserListAdapter) getListAdapter()).getCheckedCount();
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
