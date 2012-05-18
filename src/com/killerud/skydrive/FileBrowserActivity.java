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
import com.killerud.skydrive.constants.ContextItems;

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
    private BrowserListAdapter mAdapter;
    private ActionMode mActionMode;
    private Context mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setTitle("Saved files");
        setContentView(R.layout.file_picker);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCurrentlySelectedFiles = new ArrayList<String>();
        mPreviousFolders = new Stack<File>();
        mAdapter = new BrowserListAdapter(getApplicationContext());
        setListAdapter(mAdapter);

        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                File file = (File) parent.getItemAtPosition(position);
                if(mActionMode == null){
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
                }else{
                    mAdapter.setChecked(position, true);
                    mCurrentlySelectedFiles.add(
                            ((BrowserListAdapter) getListAdapter()).getItem(position).getPath());
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
                    mActionMode = startActionMode(new BrowserActionMode());
                    mAdapter.setChecked(position, true);
                    mCurrentlySelectedFiles.add(
                            ((BrowserListAdapter) getListAdapter()).getItem(position).getPath());
                }
                return true;
            }
        });
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
        if(!skyDriveFolder.exists()){
            skyDriveFolder.mkdirs();
        }
        loadFolder(skyDriveFolder);
        mContext = getApplicationContext();
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
        ArrayList<File> adapterFiles = mAdapter.getFiles();
        adapterFiles.clear();
        adapterFiles.addAll(Arrays.asList(folder.listFiles()));
        mAdapter.clearChecked();
        mAdapter.notifyDataSetChanged();
        setSupportProgressBarIndeterminateVisibility(false);
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
        if (fileType.equalsIgnoreCase("png") ||
                fileType.equalsIgnoreCase("jpg") ||
                fileType.equalsIgnoreCase("jpeg") ||
                fileType.equalsIgnoreCase("tiff") ||
                fileType.equalsIgnoreCase("gif") ||
                fileType.equalsIgnoreCase("bmp") ||
                fileType.equalsIgnoreCase("raw"))
        {
            return R.drawable.image_x_generic;
        }
        else if (fileType.equalsIgnoreCase("mp3") ||
                fileType.equalsIgnoreCase("wav") ||
                fileType.equalsIgnoreCase("wma") ||
                fileType.equalsIgnoreCase("acc") ||
                fileType.equalsIgnoreCase("ogg"))
        {
            return R.drawable.audio_x_generic;
        }
        else if (fileType.equalsIgnoreCase("mov") ||
                fileType.equalsIgnoreCase("avi") ||
                fileType.equalsIgnoreCase("divx") ||
                fileType.equalsIgnoreCase("wmv") ||
                fileType.equalsIgnoreCase("ogv") ||
                fileType.equalsIgnoreCase("mkv") ||
                fileType.equalsIgnoreCase("mp4"))
        {
            return R.drawable.video_x_generic;
        }
        else if (fileType.equalsIgnoreCase("doc") ||
                fileType.equalsIgnoreCase("odt") ||
                fileType.equalsIgnoreCase("fodt") ||
                fileType.equalsIgnoreCase("docx") ||
                fileType.equalsIgnoreCase("odf"))
        {
            return R.drawable.office_document;
        }
        else if (fileType.equalsIgnoreCase("ppt") ||
                fileType.equalsIgnoreCase("pps") ||
                fileType.equalsIgnoreCase("pptx") ||
                fileType.equalsIgnoreCase("ppsx") ||
                fileType.equalsIgnoreCase("odp") ||
                fileType.equalsIgnoreCase("fodp"))
        {
            return R.drawable.office_presentation;
        }
        else if (fileType.equalsIgnoreCase("ods") ||
                fileType.equalsIgnoreCase("xls") ||
                fileType.equalsIgnoreCase("xlr") ||
                fileType.equalsIgnoreCase("xlsx") ||
                fileType.equalsIgnoreCase("ots"))
        {
            return R.drawable.office_spreadsheet;
        }
        else if (fileType.equalsIgnoreCase("pdf"))
        {
            return R.drawable.document_pdf;
        }
        else if (fileType.equalsIgnoreCase("zip") ||
                fileType.equalsIgnoreCase("rar") ||
                fileType.equalsIgnoreCase("gz") ||
                fileType.equalsIgnoreCase("bz2") ||
                fileType.equalsIgnoreCase("tar") ||
                fileType.equalsIgnoreCase("jar"))
        {
            return R.drawable.archive_generic;
        }
        else if (fileType.equalsIgnoreCase("7z"))
        {
            return R.drawable.archive_sevenzip;
        }
        else if (fileType.equalsIgnoreCase("torrent"))
        {
            return R.drawable.document_torrent;
        }
        else if (fileType.equalsIgnoreCase("exe") ||
                fileType.equalsIgnoreCase("msi"))
        {
            return R.drawable.executable_generic;
        }
        else if (fileType.equalsIgnoreCase("iso") ||
                fileType.equalsIgnoreCase("nrg") ||
                fileType.equalsIgnoreCase("img") ||
                fileType.equalsIgnoreCase("bin"))
        {
            return R.drawable.archive_disc_image;
        }
        else if (fileType.equalsIgnoreCase("apk"))
        {
            return R.drawable.executable_apk;
        }
        else if (fileType.equalsIgnoreCase("html") ||
                fileType.equalsIgnoreCase("htm"))
        {
            return R.drawable.text_html;
        }
        else if (fileType.equalsIgnoreCase("css"))
        {
            return R.drawable.text_css;
        }
        else if (fileType.equalsIgnoreCase("deb"))
        {
            return R.drawable.executable_deb;
        }
        else if (fileType.equalsIgnoreCase("rpm"))
        {
            return R.drawable.executable_rpm;
        }
        else if (fileType.equalsIgnoreCase("java") ||
                fileType.equalsIgnoreCase("class"))
        {
            return R.drawable.document_java;
        }
        else if (fileType.equalsIgnoreCase("pl") ||
                fileType.equalsIgnoreCase("plc"))
        {
            return R.drawable.document_perl;
        }
        else if (fileType.equalsIgnoreCase("php"))
        {
            return R.drawable.document_php;
        }
        else if (fileType.equalsIgnoreCase("py"))
        {
            return R.drawable.document_python;
        }
        else if (fileType.equalsIgnoreCase("rb"))
        {
            return R.drawable.document_ruby;
        }
        return R.drawable.text_x_preview;
    }

    private class BrowserListAdapter extends BaseAdapter
    {
        private final LayoutInflater mInflater;
        private final ArrayList<File> mFiles;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mPosition;

        public BrowserListAdapter(Context context)
        {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mFiles = new ArrayList<File>();
            mCheckedPositions = new SparseBooleanArray();
        }


        public boolean isChecked(int pos)
        {
            return mCheckedPositions.get(pos, false);
        }

        public void setChecked(int pos, boolean checked)
        {
            mCheckedPositions.put(pos, checked);
            notifyDataSetChanged();
        }

        public void clearChecked()
        {
            mCheckedPositions = new SparseBooleanArray();
            notifyDataSetChanged();
        }

        public void checkAll()
        {
            for (int i = 0; i < mFiles.size(); i++)
            {
                mCheckedPositions.put(i, true);
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


    private class BrowserActionMode implements com.actionbarsherlock.view.ActionMode.Callback
    {

        @Override
        public boolean onCreateActionMode(com.actionbarsherlock.view.ActionMode mode, Menu menu)
        {
           menu.add(ContextItems.MENU_TITLE_DELETE)
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM|
                            MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            menu.add(ContextItems.MENU_TITLE_SELECT_ALL)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT
                            | MenuItem.SHOW_AS_ACTION_NEVER);
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
            if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_SELECT_ALL))
            {
                mAdapter.checkAll();
                item.setTitle(ContextItems.MENU_TITLE_DESELECT_ALL);
                return true;
            }
            else if (title.equalsIgnoreCase(ContextItems.MENU_TITLE_DESELECT_ALL))
            {
                mAdapter.clearChecked();
                item.setTitle(ContextItems.MENU_TITLE_SELECT_ALL);
                return true;
            }else if(title.equalsIgnoreCase(ContextItems.MENU_TITLE_DELETE)){
                final AlertDialog dialog = new AlertDialog.Builder(getSupportActionBar().getThemedContext()).create();
                dialog.setTitle("Delete files?");
                dialog.setIcon(R.drawable.warning_triangle);
                StringBuilder deleteMessage = new StringBuilder();
                deleteMessage.append("The following files will be deleted: \n\n");
                for (int i = 0; i < mCurrentlySelectedFiles.size(); i++)
                {
                    int index =     mCurrentlySelectedFiles.get(i).lastIndexOf("/");
                    if(index != -1){
                        deleteMessage.append(mCurrentlySelectedFiles.get(i)
                            .substring(index+1));
                        deleteMessage.append("\n");
                    }
                }
                deleteMessage.append("Are you sure you want to do this?");

                dialog.setMessage(deleteMessage.toString());
                dialog.setButton("Yes", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        for(int j=0;j<mCurrentlySelectedFiles.size();j++){
                            File file = new File(mCurrentlySelectedFiles.get(j));
                            if(file.exists()){
                                file.delete();
                            }
                        }
                        ((BrowserListAdapter) getListAdapter()).notifyDataSetChanged();
                        mode.finish();
                    }
                });
                dialog.setButton2("No!", new DialogInterface.OnClickListener()
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
            mAdapter.clearChecked();
            mActionMode = null;
            mCurrentlySelectedFiles.clear();
            ((BrowserListAdapter) getListAdapter()).notifyDataSetChanged();
            supportInvalidateOptionsMenu();
        }
    }
}
