package com.killerud.skydrive;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;
import com.killerud.skydrive.dialogs.*;
import com.killerud.skydrive.objects.*;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Stack;


/**
 * User: William
 * Date: 25.04.12
 * Time: 15:07
 */
public class BrowserActivity extends ListActivity {
    /* Live Client and download/upload class */
    private LiveConnectClient mClient;
    private XLoader mXloader;

    /* Dialogs and notification */
    private NotificationManager mNotificationManager;
    private static final int DIALOG_DOWNLOAD_ID = 0;
    private SkyDriveListAdapter mPhotoAdapter;

    /* Directory navigation */
    private static final String HOME_FOLDER = "me/skydrive";
    private String mCurrentFolderId;
    private Stack<String> mPreviousFolderIds;
    private Stack<String> mFolderHierarchy;
    private TextView mFolderHierarchyView;

    /* File manipulation */
    private boolean mCutNotPaste;
    private IOUtil mIOUtil;
    private ArrayList<SkyDriveObject> mCopyCutFiles;
    private ArrayList<SkyDriveObject> mCurrentlySelectedFiles;

    /*
     * Holder for the ActionMode, part of the contectual action bar
     * for selecting and manipulating items
     */
    private ActionMode mActionMode;

    /* Browser state. If this is set to true only folders will be shown
     * and a button starting an upload of a given file (passed through
     * an intent) to the current folder is added to the layout.
     *
     * Used by the share receiver activity.
     */
    private boolean mUploadDialog = false;

    /**
     *  Handles the chosen file from the UploadFile dialog
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UploadFileDialog.PICK_FILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                if(data.getAction().equals(UploadFileDialog.ACTION_SINGLE_FILE)){
                    XLoader loader = new XLoader(getApplicationContext());
                    loader.uploadFile(mClient,data.getStringExtra(UploadFileDialog.EXTRA_FILE_PATH),mCurrentFolderId);
                }else if(data.getAction().equals(UploadFileDialog.ACTION_MULTIPLE_FILES)){
                    XLoader loader = new XLoader(getApplicationContext());
                    ArrayList<String> files = data.getStringArrayListExtra(UploadFileDialog.EXTRA_FILES_LIST);
                    for(int i=0;i<files.size();i++){
                        loader.uploadFile(mClient,files.get(i),mCurrentFolderId);
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent startIntent = getIntent();
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mXloader = new XLoader(getApplicationContext());
        mPhotoAdapter = new SkyDriveListAdapter(this);

        setListAdapter(mPhotoAdapter);
        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();

        mCurrentlySelectedFiles = new ArrayList<SkyDriveObject>();
        mCopyCutFiles = new ArrayList<SkyDriveObject>();
        mIOUtil = new IOUtil();
        mClient = app.getConnectClient();

        determineBrowserStateAndLayout(startIntent);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mPreviousFolderIds = new Stack<String>();

        /* Makes sure that a local SkyDrive folder exists */
        File sdcard = new File(Environment.getExternalStorageDirectory() + "/SkyDrive/");
        if (!sdcard.exists()) {
            sdcard.mkdir();
        }

        ListView lv = getListView();
        setupListView(lv);

        mFolderHierarchyView = (TextView) findViewById(R.id.folder_hierarchy);
        mFolderHierarchy = new Stack<String>();
        updateFolderHierarchy(false);

    }

    /**
     *  Sets up the List View click- and selection listeners
     *
     * @param lv The lisst view to set up
     */
    private void setupListView(ListView lv) {
        lv.setTextFilterEnabled(true);
        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setupBrowserAdapterOnClick(parent, position);
            }


        });

        /* Contextual Action Bar. Pre-11 functionality solved by checkbox selection and permanent menus. */
    }

    /**
     * Determines whether or not the activity was started from the sharing activity.
     * If yes, load the uploading layout and set the state to uploading. This makes
     * the activity display only folders.
     *
     * @param startIntent
     */
    private void determineBrowserStateAndLayout(Intent startIntent) {
        if (startIntent.getAction() != null && startIntent.getAction().equals("killerud.skydrive.UPLOAD_PICK_FOLDER")) {
            setContentView(R.layout.skydrive_upload_picker);
            Button uploadButton = (Button) findViewById(R.id.uploadToThisFolder);
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //uploadFile(new Intent().putExtra(UploadFileDialog.EXTRA_FILE_PATH,
                      //      getIntent().getExtras().getString(UploadFileDialog.EXTRA_FILE_PATH)));
                }
            });
            mUploadDialog = true;
        } else {
            setContentView(R.layout.skydrive);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !mPreviousFolderIds.isEmpty()) {
            /* If the previous folder stack is empty, exit the application */
            loadFolder(mPreviousFolderIds.pop());
            mFolderHierarchy.pop();
            updateFolderHierarchy(false);
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void onCheckboxClicked(View checkedView) {
        SkyDriveListAdapter adapter = (SkyDriveListAdapter) getListAdapter();
        ListView listView = getListView();
        SkyDriveObject skyDriveObject = adapter.getItem(listView.getPositionForView(checkedView));
        if (((CheckBox) checkedView).isChecked()) {

            adapter.setChecked(listView.getPositionForView(checkedView), true);
            mCurrentlySelectedFiles.add(skyDriveObject);
        } else {
            adapter.setChecked(listView.getPositionForView(checkedView), false);
            mCurrentlySelectedFiles.remove(skyDriveObject);
            mCurrentlySelectedFiles.trimToSize();

        }

        if(Build.VERSION.SDK_INT>10) {
            invalidateOptionsMenu();
        }
    }



    /**
     *  Sets up the listadapter for the browser, making sure the correct dialogs are opened for different file types
     */
    private void setupBrowserAdapterOnClick(AdapterView<?> parent, int position) {
        SkyDriveObject skyDriveObj = (SkyDriveObject) parent.getItemAtPosition(position);


        skyDriveObj.accept(new SkyDriveObject.Visitor() {
            @Override
            public void visit(SkyDriveAlbum album) {
                mPreviousFolderIds.push(mCurrentFolderId);
                loadFolder(album.getId());
            }

            @Override
            public void visit(SkyDrivePhoto photo) {
                if (mUploadDialog) return;
                Intent startPhotoDialog = new Intent(getApplicationContext(), ViewPhotoDialog.class);
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_ID", photo.getId());
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_NAME", photo.getName());
                startActivity(startPhotoDialog);
            }

            @Override
            public void visit(SkyDriveFolder folder) {
                mPreviousFolderIds.push(mCurrentFolderId);
                mFolderHierarchy.push(folder.getName());
                updateFolderHierarchy(true);
                setTitle(folder.getName());
                loadFolder(folder.getId());
            }

            @Override
            public void visit(SkyDriveFile file) {
                if (mUploadDialog) return;
                mXloader.downloadFile(mClient,file.getId(),
                        new File(Environment.getExternalStorageDirectory() + "/SkyDrive/" + file.getName()));
            }

            @Override
            public void visit(SkyDriveVideo video) {
                if (mUploadDialog) return;
                Bundle b = new Bundle();
                b.putString(JsonKeys.NAME, video.getName());
                b.putString(JsonKeys.ID, video.getId());
                b.putString(JsonKeys.LOCATION, video.getUploadLocation());
                showDialog(DIALOG_DOWNLOAD_ID, b);
            }

            @Override
            public void visit(SkyDriveAudio audio) {
                if (mUploadDialog) return;
                Intent startAudioDialog = new Intent(getApplicationContext(), PlayAudioDialog.class);
                startAudioDialog.putExtra("killerud.skydrive.AUDIO_ID", audio.getId());
                startAudioDialog.putExtra("killerud.skydrive.AUDIO_NAME", audio.getName());
                startAudioDialog.putExtra("killerud.skydrive.AUDIO_SOURCE", audio.getSource());
                startActivity(startAudioDialog);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        /* Checks to see if the Sharing activity started the browser. If yes, some changes are made. */
        Intent intentThatStartedMe = getIntent();
        if (intentThatStartedMe.getAction() != null &&
                intentThatStartedMe.getAction().equals("killerud.skydrive.SHARE_UPLOAD")) {
            if (intentThatStartedMe.getExtras().getString(UploadFileDialog.EXTRA_FILE_PATH) != null) {
                //uploadFile(new Intent().putExtra(UploadFileDialog.EXTRA_FILE_PATH,
                  //      intentThatStartedMe.getExtras().getString(UploadFileDialog.EXTRA_FILE_PATH)));
            }
        }
    }

    private void updateFolderHierarchy(boolean push){
        String currentText =  mFolderHierarchyView.getText().toString();
        String newText =  "";
        if(push){
            newText = currentText + ">" + mFolderHierarchy.get(mFolderHierarchy.size()-1);
        }else{
            StringBuilder builder = new StringBuilder();
            for(int i=0;i<mFolderHierarchy.size();i++){
                builder.append(mFolderHierarchy.get(i));
                builder.append(">");
            }
            newText = builder.toString();
        }
        mFolderHierarchyView.setText(newText);
    }

    /**
     * Gets the folder content for displaying
     */
    private void loadFolder(String folderId) {
        if(folderId == null) return;
        setProgressBarIndeterminateVisibility(true);
        mCurrentFolderId = folderId;

        if(mCurrentlySelectedFiles != null) mCurrentlySelectedFiles.clear();
        ((SkyDriveListAdapter) getListAdapter()).clearChecked();

        if(mClient!= null) mClient.getAsync(folderId + "/files", new LiveOperationListener() {
            @Override
            public void onComplete(LiveOperation operation) {
                setProgressBarIndeterminateVisibility(false);

                JSONObject result = operation.getResult();
                if (result.has(JsonKeys.ERROR)) {
                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                    String message = error.optString(JsonKeys.MESSAGE);
                    String code = error.optString(JsonKeys.CODE);
                    Log.e("ASE", code + ": " + message);
                    return;
                }

                ArrayList<SkyDriveObject> skyDriveObjs = mPhotoAdapter.getSkyDriveObjs();
                skyDriveObjs.clear();

                JSONArray data = result.optJSONArray(JsonKeys.DATA);
                for (int i = 0; i < data.length(); i++) {
                    SkyDriveObject skyDriveObj = SkyDriveObject.create(data.optJSONObject(i));
                    if (mUploadDialog && (skyDriveObj.getType().equals(SkyDriveFolder.TYPE)
                            || skyDriveObj.getType().equals(SkyDriveAlbum.TYPE))) {
                        skyDriveObjs.add(skyDriveObj);
                    } else if (!mUploadDialog) {
                        skyDriveObjs.add(skyDriveObj);
                    }
                }

                mPhotoAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(LiveOperationException exception, LiveOperation operation) {
                setProgressBarIndeterminateVisibility(false);
                Log.e("ASE",exception.getMessage());
            }
        });
    }

    /* Menus and AB */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        if(mCurrentlySelectedFiles.size()>0){
            menu.getItem(8).setVisible(true); //Delete
            menu.getItem(4).setVisible(true); //Rename
            menu.getItem(5).setVisible(true); //Copy
            menu.getItem(6).setVisible(true);  //Cut
            menu.getItem(3).setVisible(true); //Download

            menu.getItem(2).setVisible(false); //Reload
            menu.getItem(0).setVisible(false); //New folder
            menu.getItem(1).setVisible(false); //Upload file
        }else if(mCurrentlySelectedFiles.size()<1){
            menu.getItem(8).setVisible(false); //Delete
            menu.getItem(4).setVisible(false); //Rename
            menu.getItem(5).setVisible(false); //Copy
            menu.getItem(6).setVisible(false); //Cut
            menu.getItem(3).setVisible(false); //Download

            menu.getItem(2).setVisible(true); //Reload
            menu.getItem(0).setVisible(true); //New folder
            menu.getItem(1).setVisible(true); //Upload file
        }

        if(mCopyCutFiles.size()>0){
            menu.getItem(7).setVisible(true); //Paste
        }else if(mCopyCutFiles.size()<1){
            menu.getItem(7).setVisible(false); //Paste
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.browser_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.download:
                downloadSelectedFiles(mCurrentlySelectedFiles);
                return true;
            case R.id.newFolder:
                Intent startNewFolderDialog = new Intent(getApplicationContext(), NewFolderDialog.class);
                startNewFolderDialog.putExtra("killerud.skydrive.CURRENT_FOLDER", mCurrentFolderId);
                startActivity(startNewFolderDialog);
                return true;
            case R.id.uploadFile:
                Intent intent = new Intent(getApplicationContext(), UploadFileDialog.class);
                startActivityForResult(intent, UploadFileDialog.PICK_FILE_REQUEST);
                return true;
            case R.id.reload:
                loadFolder(mCurrentFolderId);
                return true;
            case R.id.copy:
                mCopyCutFiles = (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone();
                mCutNotPaste = false;
                Toast.makeText(getApplicationContext(), "Selected files ready to paste", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.cut:
                mCopyCutFiles = (ArrayList<SkyDriveObject>) mCurrentlySelectedFiles.clone();
                mCutNotPaste = true;
                Toast.makeText(getApplicationContext(), "Selected files ready to paste", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.paste:
                mXloader.pasteFiles(mClient, mCopyCutFiles, mCurrentFolderId, mCutNotPaste);
                mCopyCutFiles.clear();
                return true;
            case R.id.delete:
                mXloader.deleteFiles(mClient, mCurrentlySelectedFiles);
                ((SkyDriveListAdapter) getListAdapter()).clearChecked();
                mCurrentlySelectedFiles.clear();
                return true;
            case R.id.rename:
                Intent startRenameDialog = new Intent(getApplicationContext(), RenameDialog.class);
                ArrayList<String> fileIds = new ArrayList<String>();
                ArrayList<String> fileNames = new ArrayList<String>();
                for(int i=0;i<mCurrentlySelectedFiles.size();i++){
                    fileIds.add(mCurrentlySelectedFiles.get(i).getId());
                    fileNames.add(mCurrentlySelectedFiles.get(i).getName());
                }
                startRenameDialog.putExtra(RenameDialog.EXTRAS_FILE_IDS,fileIds);
                startRenameDialog.putExtra(RenameDialog.EXTRAS_FILE_NAMES,fileNames);
                startActivity(startRenameDialog);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void downloadSelectedFiles(ArrayList<SkyDriveObject> currentlySelectedFiles) {
        for(int i = 0;i<currentlySelectedFiles.size();i++){
            mXloader.downloadFile(mClient,currentlySelectedFiles.get(i).getId(),
                    new File(Environment.getExternalStorageDirectory() +
                            "/SkyDrive/" + currentlySelectedFiles.get(i).getName()));
        }
    }




    /**
     * The SkyDrive list adapter. Determines the list item layout and display behaviour.
     */
    private class SkyDriveListAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final ArrayList<SkyDriveObject> mSkyDriveObjs;
        private View mView;
        private SparseBooleanArray mCheckedPositions;
        private int mPosition;

        public SkyDriveListAdapter(Context context) {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSkyDriveObjs = new ArrayList<SkyDriveObject>();
            mCheckedPositions = new SparseBooleanArray();
        }

        /**
         * @return The underlying array of the class. If changes are made to this object and you
         *         want them to be seen, call {@link #notifyDataSetChanged()}.
         */
        public ArrayList<SkyDriveObject> getSkyDriveObjs() {
            return mSkyDriveObjs;
        }

        @Override
        public int getCount() {
            return mSkyDriveObjs.size();
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

        @Override
        public SkyDriveObject getItem(int position) {
            return mSkyDriveObjs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            SkyDriveObject skyDriveObj = getItem(position);
            mView = convertView != null ? convertView : null;
            mPosition = position;

            skyDriveObj.accept(new SkyDriveObject.Visitor() {
                @Override
                public void visit(SkyDriveVideo video) {

                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.video_x_generic);
                    setName(video);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveFile file) {

                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.text_x_preview);
                    setName(file);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveFolder folder) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.folder);
                    setName(folder);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDrivePhoto photo) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.image_x_generic);
                    setName(photo);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveAlbum album) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }
                    setIcon(R.drawable.folder_image);
                    setName(album);
                    setChecked(isChecked(mPosition));
                }

                @Override
                public void visit(SkyDriveAudio audio) {
                    if (mView == null) {
                        mView = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.audio_x_generic);
                    setName(audio);
                    setChecked(isChecked(mPosition));
                }

                private void setName(SkyDriveObject skyDriveObj) {
                    TextView tv = (TextView) mView.findViewById(R.id.nameTextView);
                    tv.setText(skyDriveObj.getName());
                }

                private View inflateNewSkyDriveListItem() {
                    return mInflater.inflate(R.layout.skydrive_list_item, parent, false);
                }

                private void setIcon(int iconResId) {
                    ImageView img = (ImageView) mView.findViewById(R.id.skyDriveItemIcon);
                    img.setImageResource(iconResId);
                }

                private void setChecked(boolean checked){
                    CheckBox checkBox = (CheckBox) mView.findViewById(R.id.selectedSkyDrive);
                    checkBox.setChecked(checked);
                }
            });


            return mView;
        }
    }
}

