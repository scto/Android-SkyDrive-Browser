package com.killerud.skydrive;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.v4.util.LruCache;
import android.support.v7.app.ActionBar;
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
import com.killerud.skydrive.constants.SortCriteria;
import com.killerud.skydrive.dialogs.DownloadDialog;
import com.killerud.skydrive.dialogs.NewFolderDialog;
import com.killerud.skydrive.dialogs.RenameDialog;
import com.killerud.skydrive.dialogs.SharingDialog;
import com.killerud.skydrive.objects.*;
import com.killerud.skydrive.util.ActionBarListActivity;
import com.killerud.skydrive.util.IOUtil;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;


public class BrowserActivity extends ActionBarListActivity
{
    /* Live Client and download/upload class */
    private LiveConnectClient liveConnectClient;
    private XLoader xLoader;
    private CameraImageObserver cameraImageObserver;

    /* Directory navigation */
    private SkyDriveListAdapter skyDriveListAdapter;
    private static final String HOME_FOLDER = "me/skydrive";
    private String currentFolderId;
    private Stack<String> previousFolderIds;
    private Stack<String> folderHierarchy;
    private TextView folderHierarchyView;
    private ActionBar actionBar;
    private Stack<ArrayList<SkyDriveObject>> navigationHistory;


    /* File manipulation */
    private boolean isCutNotPaste;
    private ArrayList<SkyDriveObject> filesToBePasted;
    private ArrayList<SkyDriveObject> currentlySelectedFiles;

    /*
     * Holder for the ActionMode, part of the contectual action bar
     * for selecting and manipulating items
     */
    private ActionMode actionMode;

    /* Browser state. If this is set to true only folders will be shown
     * and a button starting an upload of a given file (passed through
     * an intent) to the current folder is added to the layout.
     *
     * Used by the share receiver activity.
     */
    private boolean isUploadDialog = false;
    private boolean isDataOnWifiOnly;
    private ConnectivityManager connectivityManager;

    private LruCache thumbCache;

    /**
     * Handles the chosen file from the UploadFile dialog
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == UploadFileActivity.PICK_FILE_REQUEST)
        {
            if (resultCode == RESULT_OK)
            {
                XLoader loader = new XLoader(this);
                loader.uploadFile(liveConnectClient,
                        data.getStringArrayListExtra(UploadFileActivity.EXTRA_FILES_LIST),
                        currentFolderId);
            }
        } else if (requestCode == DownloadDialog.DOWNLOAD_REQUEST)
        {
            if (resultCode == RESULT_OK)
            {
                XLoader loader = new XLoader(this);
                ArrayList<SkyDriveObject> file = new ArrayList<SkyDriveObject>();

                SkyDriveObject fileToAdd = ((SkyDriveListAdapter) getListAdapter())
                        .getItem(data.getIntExtra(DownloadDialog.EXTRA_FILE_POSITION, 0));
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
                fileToAdd.setLocalDownloadLocation(preferences.getString("download_location",
                        Environment.getExternalStorageDirectory() + "/SkyDrive/"));

                file.add(fileToAdd);
                loader.downloadFiles(((BrowserForSkyDriveApplication) getApplication()).getConnectClient(), file);
            }
        }
    }

    public void addBitmapToThumbCache(String key, Bitmap bitmap)
    {
        if(key == null || bitmap == null)
        {
            return;
        }
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
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        // Get memory class of this device, exceeding this amount will throw an
        // OutOfMemory exception.
        final int memClass = ((ActivityManager) getSystemService(
                Context.ACTIVITY_SERVICE)).getMemoryClass();
        final int cacheSize = 1024 * 1024 * memClass / 10;
        thumbCache = new LruCache(cacheSize);

        xLoader = new XLoader(this);
        skyDriveListAdapter = new SkyDriveListAdapter(this);
        setListAdapter(skyDriveListAdapter);

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();

        liveConnectClient = app.getConnectClient();

        currentlySelectedFiles = new ArrayList<SkyDriveObject>();
        filesToBePasted = new ArrayList<SkyDriveObject>();
        previousFolderIds = new Stack<String>();
        currentFolderId = HOME_FOLDER;

        determineBrowserStateAndLayout(getIntent());
        setupListView(getListView());

        LocalPersistentStorageManager localPersistentStorageManager = new LocalPersistentStorageManager();
        localPersistentStorageManager.createLocalSkyDriveFolderIfNotExists();

        folderHierarchyView = (TextView) findViewById(R.id.folder_hierarchy);
        folderHierarchy = new Stack<String>();
        folderHierarchy.push(getString(R.string.rootFolderTitle));

        navigationHistory = new Stack<ArrayList<SkyDriveObject>>();

        updateFolderHierarchy(null);
        app.setCurrentBrowser(this);

        actionBar = getSupportActionBar();
        if (savedInstanceState != null)
        {
            restoreSavedInstanceState(savedInstanceState);
        }

        loadFolder(currentFolderId);
    }



    private void restoreSavedInstanceState(Bundle savedInstanceState)
    {
        assert savedInstanceState != null;

        if (savedInstanceState.containsKey(Constants.STATE_CURRENT_FOLDER))
        {
            currentFolderId = savedInstanceState.getString(Constants.STATE_CURRENT_FOLDER);
        }

        if (savedInstanceState.containsKey(Constants.STATE_CURRENT_HIERARCHY))
        {
            folderHierarchy = new Stack<String>();
            String[] hierarchy = savedInstanceState.getStringArray(Constants.STATE_CURRENT_HIERARCHY);
            for (int i = 0; i < hierarchy.length; i++)
            {
                folderHierarchy.push(hierarchy[i]);
            }
            updateFolderHierarchy(null);
        }

        if (savedInstanceState.containsKey(Constants.STATE_PREVIOUS_FOLDERS))
        {
            previousFolderIds = new Stack<String>();
            String[] folderIds = savedInstanceState.getStringArray(Constants.STATE_PREVIOUS_FOLDERS);
            for (int i = 0; i < folderIds.length; i++)
            {
                previousFolderIds.push(folderIds[i]);
            }
        }

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        if(app.getNavigationHistory() != null)
        {
            navigationHistory = app.getNavigationHistory();
        }

        if (savedInstanceState.containsKey(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
        {
            if (savedInstanceState.getBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON))
            {
                actionMode = startSupportActionMode(new SkyDriveActionMode());
            }
        }


        ((SkyDriveListAdapter) getListAdapter()).setCheckedPositions(((BrowserForSkyDriveApplication) getApplication())
                .getCurrentlyCheckedPositions());

        if (actionMode != null)
        {
            updateActionModeTitleWithSelectedCount();
        }

    }


    private void setupListView(ListView lv)
    {
        lv.setOnItemClickListener(new OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (actionMode != null)
                {
                    boolean rowIsChecked = skyDriveListAdapter.isChecked(position);
                    if (position >= skyDriveListAdapter.getCount())
                    {
                        return;
                    }
                    if (rowIsChecked)
                    {
                        currentlySelectedFiles.remove(
                                ((SkyDriveListAdapter) getListAdapter()).getItem(position));
                    } else
                    {
                        SkyDriveObject fileToAdd = ((SkyDriveListAdapter) getListAdapter()).getItem(position);
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());

                        fileToAdd.setLocalDownloadLocation(preferences.getString("download_location",
                                Environment.getExternalStorageDirectory() + "/SkyDrive/"));

                        currentlySelectedFiles.add(fileToAdd);
                    }
                    skyDriveListAdapter.setChecked(position, !rowIsChecked);

                    updateActionModeTitleWithSelectedCount();
                } else
                {
                    handleListItemClick(parent, position);
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
                    actionMode = startSupportActionMode(new SkyDriveActionMode());
                    skyDriveListAdapter.setChecked(position, true);
                    SkyDriveObject fileToAdd = ((SkyDriveListAdapter) getListAdapter()).getItem(position);
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());

                    fileToAdd.setLocalDownloadLocation(preferences.getString("download_location",
                            Environment.getExternalStorageDirectory() + "/SkyDrive/"));

                    currentlySelectedFiles.add(fileToAdd);
                    updateActionModeTitleWithSelectedCount();
                }
                return true;
            }
        });
    }

    private void updateActionModeTitleWithSelectedCount()
    {
        final int checkedCount = ((SkyDriveListAdapter) getListAdapter()).getCheckedCount();
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


    private void determineBrowserStateAndLayout(Intent startIntent)
    {
        if (startIntent.getAction() != null && startIntent.getAction().equalsIgnoreCase("killerud.skydrive.UPLOAD_PICK_FOLDER"))
        {
            setContentView(R.layout.skydrive_upload_picker);
            Button uploadButton = (Button) findViewById(R.id.uploadToThisFolder);
            uploadButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    uploadFilesSentThroughShareButton(getIntent());
                }
            });

            isUploadDialog = true;
        } else
        {
            setContentView(R.layout.skydrive);
            isUploadDialog = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            if (canNavigateBack())
            {
                navigateBack();
                return true;
            } else
            {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
                if (preferences.getBoolean(Constants.CONFIRM_EXIT, false))
                {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(R.string.appExitConfirmationHeader)
                            .setCancelable(false)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i)
                                {
                                    BrowserActivity.this.finish();
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i)
                                {
                                    dialogInterface.cancel();
                                }
                            });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();

                    return true;
                } else
                {
                    return super.onKeyDown(keyCode, event);
                }
            }
        } else
        {
            return super.onKeyDown(keyCode, event);
        }
    }

    private boolean canNavigateBack()
    {
        if (connectionIsUnavailable())
        {
            return false;
        }

        if (previousFolderIds.isEmpty())
        {
            if (actionBar != null)
            {
                actionBar.setDisplayHomeAsUpEnabled(false);
            }

            return false;
        }
        return true;
    }

    private void navigateBack()
    {
        if(!navigationHistory.isEmpty() && navigationHistory.peek() != null)
        {

            if (actionBar != null && !previousFolderIds.empty())
            {
                actionBar.setDisplayHomeAsUpEnabled(true);
            } else
            {
                actionBar.setDisplayHomeAsUpEnabled(false);
            }

            currentFolderId = previousFolderIds.pop();

            if (currentlySelectedFiles != null)
            {
                currentlySelectedFiles.clear();
            }


            ArrayList<SkyDriveObject> adapterContent = ((SkyDriveListAdapter) getListAdapter()).getSkyDriveObjects();
            adapterContent.clear();

            adapterContent.addAll(navigationHistory.pop());
            ((SkyDriveListAdapter) getListAdapter()).notifyDataSetChanged();
        }

        if (!folderHierarchy.isEmpty())
        {
            folderHierarchy.pop();
            updateFolderHierarchy(null);
        }
    }

    private void pushPreviousFolderId(String folderId)
    {

        if (!previousFolderIds.isEmpty()
                && previousFolderIds.peek().equals(folderId))
        {
            return;
        } else
        {
            previousFolderIds.push(folderId);
        }
    }

    private void handleListItemClick(AdapterView<?> parent, final int position)
    {
        SkyDriveObject skyDriveObj = (SkyDriveObject) parent.getItemAtPosition(position);


        skyDriveObj.accept(new SkyDriveObject.Visitor()
        {
            @Override
            public void visit(SkyDriveAlbum album)
            {
                pushPreviousFolderId(currentFolderId);
                updateFolderHierarchy(album);
                loadFolder(album.getId());
            }

            @Override
            public void visit(SkyDrivePhoto photo)
            {
                if (isUploadDialog || connectionIsUnavailable())
                {
                    return;
                }
                Intent startPhotoDialog = new Intent(getApplicationContext(), ImageGalleryActivity.class);
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_ID", photo.getId());
                startPhotoDialog.putExtra("killerud.skydrive.PHOTO_NAME", photo.getName());
                if (!connectionIsUnavailable())
                {
                    startActivity(startPhotoDialog);
                }
            }

            @Override
            public void visit(SkyDriveFolder folder)
            {
                pushPreviousFolderId(currentFolderId);
                updateFolderHierarchy(folder);
                loadFolder(folder.getId());
            }

            @Override
            public void visit(SkyDriveFile file)
            {
                if (isUploadDialog || connectionIsUnavailable())
                {
                    return;
                }

                if (isDisplayableByWebBrowser(file))
                {
                    Intent startWebBrowser = new Intent(getApplicationContext(), WebActivity.class);
                    startWebBrowser.putExtra(WebActivity.EXTRA_FILE_LINK, file.getLink());
                    startActivity(startWebBrowser);
                    return;
                }

                Intent confirmDownload = new Intent(getApplicationContext(), DownloadDialog.class);
                confirmDownload.putExtra(DownloadDialog.EXTRA_FILE_POSITION, position);
                startActivityForResult(confirmDownload, DownloadDialog.DOWNLOAD_REQUEST);
            }

            @Override
            public void visit(SkyDriveVideo video)
            {
                if (isUploadDialog || connectionIsUnavailable())
                {
                    return;
                }
                ((BrowserForSkyDriveApplication) getApplication()).setCurrentVideo(video);
                Intent startVideoDialog = new Intent(getApplicationContext(), PlayVideoActivity.class);
                if (!connectionIsUnavailable())
                {
                    startActivity(startVideoDialog);
                }
            }

            @Override
            public void visit(SkyDriveAudio audio)
            {
                if (isUploadDialog || connectionIsUnavailable())
                {
                    return;
                }
                if (audioPlaybackService != null)
                {
                    if (!audioServiceHasSongsToPlay())
                    {
                        startActivity(new Intent(getApplicationContext(), AudioControlActivity.class));
                    }
                    audioPlaybackService.NOW_PLAYING.add(audio);


                    Toast.makeText(getApplicationContext(),
                            audioPlaybackService.getAudioTitle(audio) + " " + getString(R.string.audioAddedToPlayingQueue),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean audioServiceHasSongsToPlay()
    {
        return audioPlaybackService.NOW_PLAYING.size() > 0;
    }

    private boolean isDisplayableByWebBrowser(SkyDriveFile file)
    {
        String fileExtension = IOUtil.getFileExtension(file.getName());
        if (fileExtension.equalsIgnoreCase("doc") ||
                fileExtension.equalsIgnoreCase("odt") ||
                fileExtension.equalsIgnoreCase("fodt") ||
                fileExtension.equalsIgnoreCase("docx") ||
                fileExtension.equalsIgnoreCase("odf"))
        {
            return true;
        } else if (fileExtension.equalsIgnoreCase("ppt") ||
                fileExtension.equalsIgnoreCase("pps") ||
                fileExtension.equalsIgnoreCase("pptx") ||
                fileExtension.equalsIgnoreCase("ppsx") ||
                fileExtension.equalsIgnoreCase("odp") ||
                fileExtension.equalsIgnoreCase("fodp"))
        {
            return true;
        } else if (fileExtension.equalsIgnoreCase("ods") ||
                fileExtension.equalsIgnoreCase("xls") ||
                fileExtension.equalsIgnoreCase("xlr") ||
                fileExtension.equalsIgnoreCase("xlsx") ||
                fileExtension.equalsIgnoreCase("ots"))
        {
            return true;
        } else
        {
            return false;
        }
    }

    private void uploadFilesSentThroughShareButton(Intent intentThatStartedMe)
    {
        if (intentThatStartedMe.getExtras().getStringArrayList(UploadFileActivity.EXTRA_FILES_LIST) != null)
        {
            if (!connectionIsUnavailable())
            {
                xLoader.uploadFile(liveConnectClient,
                        intentThatStartedMe.getStringArrayListExtra(UploadFileActivity.EXTRA_FILES_LIST),
                        currentFolderId);
            }
        }
        finish();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean("automatic_camera_upload", false))
        {
            startService(new Intent(this, CameraObserverService.class));
        } else
        {
            stopService(new Intent(this, CameraObserverService.class));
        }
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        try
        {
            unbindService(audioPlaybackServiceConnection);
        } catch (IllegalArgumentException e)
        {
        } catch (RuntimeException e)
        {
        }
    }

    private void handleIllegalConnectionState()
    {
        ((BrowserForSkyDriveApplication) getApplication())
                .getAuthClient()
                .initialize(Arrays.asList(Constants.APP_SCOPES), new LiveAuthListener()
                {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                    {
                        if (status == LiveStatus.CONNECTED)
                        {
                            reloadFolder();
                        } else
                        {
                            informUserOfConnectionProblemAndDismiss();
                        }
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
                        Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                        informUserOfConnectionProblemAndDismiss();
                    }
                });
    }

    private void informUserOfConnectionProblemAndDismiss()
    {
        Toast.makeText(this, R.string.errorLoggedOut, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, SignInAndShareHandlerActivity.class));
        finish();
    }


    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);

        savedInstanceState.putString(Constants.STATE_CURRENT_FOLDER, currentFolderId);

        String[] hierarcy = new String[folderHierarchy.size()];
        for (int i = 0; i < hierarcy.length; i++)
        {
            hierarcy[i] = folderHierarchy.get(i);
        }

        String[] previous = new String[previousFolderIds.size()];
        for (int i = 0; i < previous.length; i++)
        {
            previous[i] = previousFolderIds.get(i);
        }

        savedInstanceState.putStringArray(Constants.STATE_CURRENT_HIERARCHY, hierarcy);
        savedInstanceState.putStringArray(Constants.STATE_PREVIOUS_FOLDERS, previous);

        ((BrowserForSkyDriveApplication) getApplication()).setNavigationHistory(navigationHistory);

        if (actionMode != null)
        {
            savedInstanceState.putBoolean(Constants.STATE_ACTION_MODE_CURRENTLY_ON, true);
        }

        ((BrowserForSkyDriveApplication) getApplication())
                .setCurrentlyCheckedPositions(
                        ((SkyDriveListAdapter) getListAdapter())
                                .getCheckedPositions());
    }

    private void updateFolderHierarchy(SkyDriveObject folder)
    {
        String currentText = folderHierarchyView.getText().toString();
        String newText = "";

        if (folder == null)
        {
            newText = updateFolderHierarchyWhenNavigatingUp();
        } else
        {
            if (!folderHierarchy.empty() &&
                    !folderHierarchy.peek().equals(folder.getName()))
            {
                folderHierarchy.push(folder.getName());
                newText = currentText + ">" + folderHierarchy.peek();
                setTitle(folder.getName());
            } else
            {
                newText = currentText;
            }
        }
        folderHierarchyView.setText(newText);
    }

    private String updateFolderHierarchyWhenNavigatingUp()
    {
        if (!folderHierarchy.isEmpty())
        {
            setTitle(folderHierarchy.peek());
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < folderHierarchy.size(); i++)
        {
            if (i > 0) //If not root
            {
                builder.append(">");
            }
            builder.append(folderHierarchy.get(i));
        }
        return builder.toString();
    }

    public void reloadFolder()
    {
        try
        {
            setSupportProgressBarIndeterminateVisibility(false);
            supportInvalidateOptionsMenu();
            loadFolder(currentFolderId);
        } catch (NullPointerException e)
        {
            /* At this point an XLoader object has attempted a reload of a BrowserActivity that no longer exists.
            * We do nothing in this case, as it is pointless to update a UI that doesn't exist.
            * */
        }
    }

    public void setDefaultBrowserBehaviour()
    {
        setContentView(R.layout.skydrive);
        isUploadDialog = false;
        setupListView(getListView());

        folderHierarchyView = (TextView) findViewById(R.id.folder_hierarchy);
        folderHierarchy = new Stack<String>();
        folderHierarchy.push(getString(R.string.rootFolderTitle));

        updateFolderHierarchy(null);
    }

    private void loadSharedFiles()
    {
        setTitle(R.string.sharedFiles);
        previousFolderIds.push(currentFolderId);
        loadFolder("me/skydrive/shared");
    }

    private void loadFolder(String folderId)
    {
        if (folderId == null)
        {
            return;
        }
        if (connectionIsUnavailable())
        {
            return;
        }

        setSupportProgressBarIndeterminateVisibility(true);

        if (actionBar != null && !previousFolderIds.empty())
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        } else
        {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        if(!previousFolderIds.empty() && !currentFolderId.equals(folderId))
        {
            navigationHistory.push((ArrayList<SkyDriveObject>)
                    ((SkyDriveListAdapter) getListAdapter()).getSkyDriveObjects().clone());
        }

        currentFolderId = folderId;

        if (currentlySelectedFiles != null)
        {
            currentlySelectedFiles.clear();
        }

        if (actionMode == null)
        {
            /* If there is an action mode, we are currently selecting files and the state has just changed.
             * No actual navigation has taken place, so we don't want to clear selected. */
            ((SkyDriveListAdapter) getListAdapter()).clearChecked();
        }

        try
        {
            if (liveConnectClient != null)
            {
                liveConnectClient.getAsync(folderId + "/files?sort_by=" +
                        SortCriteria.NAME + "&sort_order=" + SortCriteria.ASCENDING, new LiveOperationListener()
                {
                    @Override
                    public void onComplete(LiveOperation operation)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);

                        JSONObject result = operation.getResult();
                        if (result.has(JsonKeys.ERROR))
                        {
                            JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                            String message = error.optString(JsonKeys.MESSAGE);
                            String code = error.optString(JsonKeys.CODE);
                            Log.e("ASE", code + ": " + message);
                            return;
                        }

                        ArrayList<SkyDriveObject> skyDriveObjects = skyDriveListAdapter.getSkyDriveObjects();
                        skyDriveObjects.clear();

                        JSONArray data = result.optJSONArray(JsonKeys.DATA);
                        for (int i = 0; i < data.length(); i++)
                        {
                            SkyDriveObject skyDriveObject = SkyDriveObject.create(data.optJSONObject(i));
                            skyDriveObjects.add(skyDriveObject);
                        }


                        skyDriveListAdapter.notifyDataSetChanged();



                        SparseBooleanArray checkedPositions = skyDriveListAdapter.getCheckedPositions();
                        for (int i = 0; i < checkedPositions.size(); i++)
                        {
                            int adapterPosition = checkedPositions.keyAt(i);
                            if (adapterPosition >= skyDriveListAdapter.getCount())
                            {
                                continue;
                            }

                            SkyDriveObject objectSelected = skyDriveListAdapter.getItem(adapterPosition);
                            currentlySelectedFiles.add(objectSelected);
                        }
                    }


                    @Override
                    public void onError(LiveOperationException exception, LiveOperation operation)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);
                        Log.e("ASE", exception.getMessage());
                    }
                });
            }
        } catch (IllegalStateException e)
        {
            handleIllegalConnectionState();
        }
    }

    private boolean connectionIsUnavailable()
    {
        getPreferences();
        boolean unavailable = (isDataOnWifiOnly &&
                (connectivityManager.getActiveNetworkInfo().getType()
                        != ConnectivityManager.TYPE_WIFI));
        if (unavailable)
        {
            Toast.makeText(this, R.string.noInternetConnection, Toast.LENGTH_LONG).show();
        }
        return unavailable;
    }

    private void getPreferences()
    {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplication());
        isDataOnWifiOnly = preferences.getBoolean("limit_all_to_wifi", false);
    }

    /* Menus and AB */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if (filesToBePasted.size() > 0)
        {
            menu.getItem(3).setVisible(true); //Paste
        } else if (filesToBePasted.size() < 1)
        {
            menu.getItem(3).setVisible(false); //Paste
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.browser_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                if (canNavigateBack())
                {
                    navigateBack();
                }
                return true;

            case R.id.newFolder:
                Intent startNewFolderDialog = new Intent(getApplicationContext(), NewFolderDialog.class);
                startNewFolderDialog.putExtra("killerud.skydrive.CURRENT_FOLDER", currentFolderId);
                if (!connectionIsUnavailable())
                {
                    startActivity(startNewFolderDialog);
                }
                supportInvalidateOptionsMenu();
                return true;
            case R.id.uploadFile:
                Intent intent = new Intent(getApplicationContext(), UploadFileActivity.class);
                intent.putExtra(UploadFileActivity.EXTRA_CURRENT_FOLDER_NAME, getTitle());
                if (!connectionIsUnavailable())
                {
                    startActivityForResult(intent, UploadFileActivity.PICK_FILE_REQUEST);
                }
                supportInvalidateOptionsMenu();
                return true;
            case R.id.reload:
                reloadFolder();
                return true;
            case R.id.paste:
                setSupportProgressBarIndeterminateVisibility(true);
                if (!connectionIsUnavailable())
                {
                    xLoader.pasteFiles(liveConnectClient, filesToBePasted, currentFolderId, isCutNotPaste);
                }
                return true;
            case R.id.sharedFiles:
                loadSharedFiles();
                return true;
            case R.id.savedFiles:
                startActivity(new Intent(getApplicationContext(), FileBrowserActivity.class));
                return true;
            case R.id.settings:
                startActivity(new Intent(getApplicationContext(), PreferencesActivity.class));
                return true;
            case R.id.audioControls:
                startActivity(new Intent(getApplicationContext(), AudioControlActivity.class));
                return true;
            case R.id.signOut:
                setSupportProgressBarIndeterminateVisibility(true);
                ((BrowserForSkyDriveApplication) getApplication()).getAuthClient().logout(new LiveAuthListener()
                {
                    @Override
                    public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);
                        Toast.makeText(getApplicationContext(), R.string.loggedOut, Toast.LENGTH_SHORT);
                        startActivity(new Intent(getApplicationContext(), SignInAndShareHandlerActivity.class));
                        finish();
                        Log.e(Constants.LOGTAG, "Logged out. Status is " + status + ".");
                    }

                    @Override
                    public void onAuthError(LiveAuthException exception, Object userState)
                    {
                        setSupportProgressBarIndeterminateVisibility(false);
                        startActivity(new Intent(getApplicationContext(), SignInAndShareHandlerActivity.class));
                        finish();
                        Log.e(Constants.LOGTAG, "Error: " + exception.getMessage());
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private class SkyDriveListAdapter extends BaseAdapter
    {
        private final LayoutInflater inflater;
        private final ArrayList<SkyDriveObject> skyDriveObjs;
        private View view;
        private SparseBooleanArray checkedPositions;
        private int position;
        private int checked;
        private Stack<Runnable> taskQueue;


        public SkyDriveListAdapter(Context context)
        {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            skyDriveObjs = new ArrayList<SkyDriveObject>();
            checkedPositions = new SparseBooleanArray();
            checked = 0;
            taskQueue = new Stack<Runnable>();
        }

        /**
         * @return The underlying array of the class. If changes are made to this object and you
         *         want them to be seen, call {@link #notifyDataSetChanged()}.
         */
        public ArrayList<SkyDriveObject> getSkyDriveObjects()
        {
            return skyDriveObjs;
        }

        @Override
        public int getCount()
        {
            return skyDriveObjs.size();
        }

        public int getCheckedCount()
        {
            return this.checked;
        }

        public boolean isChecked(int pos)
        {
            return checkedPositions.get(pos, false);
        }


        public void setChecked(int pos, boolean checked)
        {
            if (checked && !isChecked(pos))
            {
                this.checked++;
            } else if (isChecked(pos) && !checked)
            {
                this.checked--;
            }

            checkedPositions.put(pos, checked);
            notifyDataSetChanged();
        }


        public void setCheckedPositions(SparseBooleanArray checkedPositions)
        {
            checked = checkedPositions.size();
            this.checkedPositions = checkedPositions;
            notifyDataSetChanged();
        }

        public SparseBooleanArray getCheckedPositions()
        {
            return this.checkedPositions;
        }

        public void clearChecked()
        {
            checked = 0;
            checkedPositions = new SparseBooleanArray();
            currentlySelectedFiles = new ArrayList<SkyDriveObject>();
            notifyDataSetChanged();
        }

        public void checkAll()
        {
            for (int i = 0; i < skyDriveObjs.size(); i++)
            {
                if (!isChecked(i))
                {
                    checked++;
                }
                checkedPositions.put(i, true);
                currentlySelectedFiles.add(skyDriveObjs.get(i));
            }
            notifyDataSetChanged();
        }

        @Override
        public SkyDriveObject getItem(int position)
        {
            if (position >= skyDriveObjs.size())
            {
                return null;
            }

            return skyDriveObjs.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        private void loadThumbnail(View view, SkyDriveObject skyDriveObject)
        {
            final WeakReference viewReference = new WeakReference(view);
            final WeakReference objectReference = new WeakReference(skyDriveObject);

            if (viewReference == null || objectReference == null
                    || objectReference.get() == null || viewReference.get() == null )
            {
                return;
            }

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            if (preferences.getBoolean(Constants.THUMBNAILS_DISABLED, false))
            {
                return;
            }

            /* Take something relatively unique as a key if the reference has been lost, so no bitmap is found */
            final long time = System.currentTimeMillis();
            final String imageKey = String.valueOf(
                    (objectReference != null && objectReference.get() != null? ((SkyDriveObject) objectReference.get()).getName() : "" + time));


            final Bitmap bitmap = getBitmapFromThumbCache(imageKey);
            if (bitmap != null)
            {
                ImageView imgView = (ImageView) view.findViewById(R.id.skyDriveItemIcon);
                imgView.setImageBitmap(bitmap);
            } else if (objectReference != null && viewReference != null
                    && objectReference.get() != null && viewReference.get() != null)
            {
                File cacheFolder = new File(Environment.getExternalStorageDirectory()
                        + "/Android/data/com.killerud.skydrive/thumbs/");
                if (!cacheFolder.exists())
                {
                    cacheFolder.mkdirs();
                    return;
                }

                File thumbCache = new File(cacheFolder,
                        (objectReference != null && objectReference.get() != null ? ((SkyDriveObject) objectReference.get()).getName() : "" + time));
                if (thumbCache.exists())
                {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inScaled = false;

                    addBitmapToThumbCache(skyDriveObject.getName(), BitmapFactory.decodeFile(thumbCache.getPath(), options));
                    loadThumbnail(view, skyDriveObject);
                    Log.i(Constants.LOGTAG, "Thumb loaded from cache for image " + skyDriveObject.getName());
                }else if (objectReference != null && viewReference != null
                        && objectReference.get() != null && viewReference.get() != null)
                {
                try
                {
                    liveConnectClient.downloadAsync(
                            ((SkyDriveObject) objectReference.get()).getId() + "/picture?type=thumbnail", new LiveDownloadOperationListener()
                    {
                        @Override
                        public void onDownloadProgress(int totalBytes,
                                                       int bytesRemaining,
                                                       LiveDownloadOperation operation)
                        {
                        }

                        @Override
                        public void onDownloadFailed(LiveOperationException exception,
                                                     LiveDownloadOperation operation)
                        {
                            if (objectReference != null && objectReference.get() != null)
                            {
                                Log.i(Constants.LOGTAG, "Thumb download failed for " + ((SkyDriveObject) objectReference.get()).getName()
                                        + ". " + exception.getMessage());
                            }
                            setIcon(R.drawable.image_x_generic);
                        }

                        @Override
                        public void onDownloadCompleted(LiveDownloadOperation operation)
                        {
                            if (objectReference != null && objectReference.get() != null)
                            {
                                Log.i(Constants.LOGTAG, "Thumb loaded from web for image " + ((SkyDriveObject) objectReference.get()).getName());
                            }
                            if (Build.VERSION.SDK_INT >= 11)
                            {
                                try
                                {
                                    AsyncTask task = new AsyncTask<Object, Void, Bitmap>()
                                    {

                                        @Override
                                        protected Bitmap doInBackground(Object... inputStreams)
                                        {
                                            Bitmap bm = null;

                                            try
                                            {
                                                bm = BitmapFactory.decodeStream(
                                                        ((LiveDownloadOperation) inputStreams[0]).getStream());
                                            } catch (Exception e)
                                            {
                                                if (objectReference != null && objectReference.get() != null)
                                                {
                                                    Log.i(Constants.LOGTAG, "Thumb download failed for " + ((SkyDriveObject) objectReference.get()).getName()
                                                            + ". " + e.getMessage());
                                                }
                                            }

                                            return bm;
                                        }

                                        protected void onPostExecute(Bitmap bm)
                                        {
                                            File cacheFolder = new File(Environment.getExternalStorageDirectory()
                                                    + "/Android/data/com.killerud.skydrive/thumbs/");

                                            if (!cacheFolder.exists())
                                            {
                                                cacheFolder.mkdirs();
                                                /*
                                                VERY important that this is mkdirS, not mkdir,
                                                or just the last folder will be created, which won't
                                                work with the other folders absent...
                                                */
                                            }
                                            if (objectReference != null && objectReference.get() != null)
                                            {
                                                File thumb = new File(cacheFolder,
                                                        ((SkyDriveObject) objectReference.get()).getName());
                                                OutputStream out;
                                                try
                                                {
                                                    out = new BufferedOutputStream(new FileOutputStream(thumb));
                                                    bm.compress(Bitmap.CompressFormat.PNG, 85, out);
                                                    out.flush();
                                                    out.close();
                                                    Log.i(Constants.LOGTAG, "Thumb cached for image " +
                                                            thumb.getName());
                                                } catch (Exception e)
                                                {
                                                    /* Couldn't save thumbnail. No biggie.
                                                   * Exception here rather than IOException
                                                   * doe to rare cases of crashes when activity
                                                   * loses focus during load.
                                                   * */
                                                    if (objectReference != null && objectReference.get() != null)
                                                    {
                                                        Log.e(Constants.LOGTAG, "Could not cache thumbnail for " +
                                                                thumb.getName() + ". " + e.toString());
                                                    }
                                                }

                                                addBitmapToThumbCache(thumb.getName(), bm);
                                                if (objectReference != null && viewReference != null
                                                        && objectReference.get() != null && viewReference.get() != null)
                                                {
                                                    loadThumbnail((View) viewReference.get(),
                                                            (SkyDriveObject) objectReference.get());
                                                }
                                            }
                                        }

                                    };

                                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, operation);
                                } catch (Exception e)
                                {
                                    Log.i(Constants.LOGTAG, "OnDownloadCompleted failed for " +
                                            (objectReference != null && objectReference.get() != null ? ((SkyDriveObject) objectReference.get()).getName() : " file")
                                            + ". " + e.getMessage());
                                    setIcon(R.drawable.image_x_generic);
                                }

                            } else
                            {
                                Bitmap bm = BitmapFactory.decodeStream(operation.getStream());

                                File cacheFolder = new File(Environment.getExternalStorageDirectory()
                                        + "/Android/data/com.killerud.skydrive/thumbs/");

                                if (!cacheFolder.exists())
                                {
                                    cacheFolder.mkdirs();
                                    /*
                                    VERY important that this is mkdirS, not mkdir,
                                    or just the last folder will be created, which won't
                                    work with the other folders absent...
                                    */
                                }

                                if (objectReference != null&& objectReference.get() != null)
                                {
                                    File thumb = new File(cacheFolder,
                                            ((SkyDriveObject) objectReference.get()).getName());
                                    try
                                    {
                                        FileOutputStream fileOut = new FileOutputStream(thumb);
                                        bm.compress(Bitmap.CompressFormat.PNG, 85, fileOut);
                                        fileOut.flush();
                                        fileOut.close();
                                        Log.i(Constants.LOGTAG, "Thumb cached for image " + thumb.getName());
                                    } catch (Exception e)
                                    {
                                        /* Couldn't save thumbnail. No biggie.
                                       * Exception here rather than IOException
                                       * doe to rare cases of crashes when activity
                                       * loses focus during load.
                                       * */
                                        Log.e(Constants.LOGTAG, "Could not cache thumbnail for " + thumb.getName()
                                                + ". " + e.getMessage());
                                    }

                                    addBitmapToThumbCache(thumb.getName(), bm);
                                    if (viewReference != null && objectReference != null
                                            && objectReference.get() != null && viewReference.get() != null)
                                    {
                                        loadThumbnail((View) viewReference.get(), (SkyDriveObject) objectReference.get());
                                    }
                                }
                            }

                        }

                    });
                } catch (IllegalStateException e)
                {
                    handleIllegalConnectionState();
                }
            }

            }
        }


        @Override
        public View getView(int position, View convertView, final ViewGroup parent)
        {
            SkyDriveObject skyDriveObj = getItem(position);
            view = convertView;
            this.position = position;
            skyDriveObj.accept(new SkyDriveObject.Visitor()
            {
                @Override
                public void visit(final SkyDriveVideo video)
                {

                    if (view == null)
                    {
                        view = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.video_x_generic);
                    setName(video);

                    TextView detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
                    detailsTextView.setText(
                        IOUtil.getFittingByteAndSizeDescriptor(video.getSize())
                            + " - " + parseTimeString(video.getUpdatedTime()));

                    TextView sharedTextView = (TextView) view.findViewById(R.id.sharedTextView);
                    sharedTextView.setText((video.getSharedWith().getAccess().equalsIgnoreCase("Just me")?"":video.getSharedWith().getAccess()));

                    setSelected(isChecked(SkyDriveListAdapter.this.position));
                    View view = SkyDriveListAdapter.this.view;
                    loadThumbnail(view, video);
                }

                @Override
                public void visit(SkyDriveFile file)
                {

                    if (view == null)
                    {
                        view = inflateNewSkyDriveListItem();
                    }

                    setIcon(determineFileIcon(file));
                    setName(file);

                    TextView detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
                    detailsTextView.setText(
                            IOUtil.getFittingByteAndSizeDescriptor(file.getSize())
                                    + " - " + parseTimeString(file.getUpdatedTime()));

                    TextView sharedTextView = (TextView) view.findViewById(R.id.sharedTextView);
                    sharedTextView.setText((file.getSharedWith().getAccess().equalsIgnoreCase("Just me")?"":file.getSharedWith().getAccess()));

                    setSelected(isChecked(SkyDriveListAdapter.this.position));
                }

                @Override
                public void visit(SkyDriveFolder folder)
                {
                    if (view == null)
                    {
                        view = inflateNewSkyDriveListItem();
                    }

                    setIcon(R.drawable.folder);
                    setName(folder);

                    TextView detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
                    detailsTextView.setText(
                            folder.getCount() + " " + getString(R.string.items)
                                    + " - " + parseTimeString(folder.getUpdatedTime()));

                    TextView sharedTextView = (TextView) view.findViewById(R.id.sharedTextView);
                    sharedTextView.setText((folder.getSharedWith().getAccess().equalsIgnoreCase("Just me")?"":folder.getSharedWith().getAccess()));


                    setSelected(isChecked(SkyDriveListAdapter.this.position));
                }

                @Override
                public void visit(SkyDriveAlbum album)
                {
                    if (view == null)
                    {
                        view = inflateNewSkyDriveListItem();
                    }
                    setIcon(R.drawable.folder_image);
                    setName(album);

                    TextView detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
                    detailsTextView.setText(
                            album.getCount() + " " + getString(R.string.items)
                                    + " - " + parseTimeString(album.getUpdatedTime()));

                    TextView sharedTextView = (TextView) view.findViewById(R.id.sharedTextView);
                    sharedTextView.setText((album.getSharedWith().getAccess().equalsIgnoreCase("Just me")?"":album.getSharedWith().getAccess()));


                    setSelected(isChecked(SkyDriveListAdapter.this.position));
                }

                @Override
                public void visit(SkyDriveAudio audio)
                {
                    if (view == null)
                    {
                        view = inflateNewSkyDriveListItem();
                    }

                    startService(new Intent(getApplicationContext(), AudioPlaybackService.class));
                    bindService(new Intent(getApplicationContext(), AudioPlaybackService.class),
                            audioPlaybackServiceConnection, Context.BIND_ABOVE_CLIENT);
                    setIcon(R.drawable.audio_x_generic);
                    setName(audio);

                    TextView detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
                    detailsTextView.setText(
                            IOUtil.getFittingByteAndSizeDescriptor(audio.getSize())
                                    + " - " + parseTimeString(audio.getUpdatedTime()));

                    TextView sharedTextView = (TextView) view.findViewById(R.id.sharedTextView);
                    sharedTextView.setText((audio.getSharedWith().getAccess().equalsIgnoreCase("Just me")?"":audio.getSharedWith().getAccess()));


                    setSelected(isChecked(SkyDriveListAdapter.this.position));
                }

                @Override
                public void visit(final SkyDrivePhoto photo)
                {
                    if (view == null)
                    {
                        view = inflateNewSkyDriveListItem();
                    }
                    final View view = SkyDriveListAdapter.this.view;
                    // Since we are doing async calls and view is constantly changing,
                    // we need to hold on to this reference.

                    setIcon(R.drawable.image_x_generic);
                    setName(photo);
                    setSelected(isChecked(SkyDriveListAdapter.this.position));

                    TextView detailsTextView = (TextView) view.findViewById(R.id.detailsTextView);
                    detailsTextView.setText(
                            IOUtil.getFittingByteAndSizeDescriptor(photo.getSize())
                                    + " - " + parseTimeString(photo.getUpdatedTime()));

                    TextView sharedTextView = (TextView) view.findViewById(R.id.sharedTextView);
                    sharedTextView.setText((photo.getSharedWith().getAccess().equalsIgnoreCase("Just me")?"":photo.getSharedWith().getAccess()));


                    loadThumbnail(view, photo);
                }


                private View inflateNewSkyDriveListItem()
                {
                    if(actionBar!= null && actionBar.getSelectedNavigationIndex() == 1)
                    {
                        return  inflater.inflate(R.layout.skydrive_grid_item, parent, false );
                    }
                    return inflater.inflate(R.layout.skydrive_list_item, parent, false);
                }

            });

            return view;
        }

        private void setName(SkyDriveObject skyDriveObj)
        {
            TextView tv = (TextView) view.findViewById(R.id.nameTextView);
            tv.setText(skyDriveObj.getName());
        }

        private void setIcon(int iconResId)
        {
            ImageView img = (ImageView) view.findViewById(R.id.skyDriveItemIcon);
            img.setImageResource(iconResId);
        }

        private String parseTimeString(String time)
        {
            String[] dateTimeSplit = time.split("T");
            String[] timeSplit = dateTimeSplit[1].split("\\+");
            return dateTimeSplit[0].replace("-",".") + " " + timeSplit[0].substring(0,5);
        }

        private void setSelected(boolean checked)
        {
            if (checked)
            {
                view.setBackgroundResource(R.color.HightlightBlue);
            } else
            {
                view.setBackgroundResource(android.R.color.white);
            }
        }


        private int determineFileIcon(SkyDriveFile file)
        {
            int index = file.getName().lastIndexOf(".");
            if (index != -1)
            {
                /* Starting from the index includes the dot, so we add one  */
                String extension = file.getName().substring(index + 1,
                        file.getName().length());

                return IOUtil.determineFileTypeDrawable(extension);
            }

            return R.drawable.text_x_preview;
        }
    }

    private class SkyDriveActionMode implements ActionMode.Callback
    {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {

            menu.add(getString(R.string.download))
                    .setIcon(R.drawable.ic_menu_save)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.copy))
                    .setIcon(R.drawable.ic_menu_copy_holo_light)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.cut))
                    .setIcon(R.drawable.ic_menu_cut_holo_light)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.rename))
                    .setIcon(R.drawable.ic_menu_edit)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.delete))
                    .setIcon(R.drawable.ic_menu_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add((getString(R.string.share)))
                    .setIcon(R.drawable.ic_menu_share)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(getString(R.string.selectAll))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
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
            if (title.equalsIgnoreCase(getString(R.string.download)))
            {
                /* Downloads are done by calling recursively on a trimmed version of the same arraylist onComplete
                *  Create a clone so selected aren't cleared logically.
                */
                if (!connectionIsUnavailable())
                {
                    xLoader.downloadFiles(liveConnectClient, (ArrayList<SkyDriveObject>) currentlySelectedFiles.clone());
                }

                resetSelection();
                mode.finish();
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.copy)))
            {
                copySelectedFiles(mode);
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.cut)))
            {
                cutSelectedFiles(mode);
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.delete)))
            {
                createDeleteDialog(mode);
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.rename)))
            {
                createRenameDialog();
                return true;
            } else if (title.equalsIgnoreCase(getString((R.string.share))))
            {
                createSharingDialog();
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.selectAll)))
            {
                ((SkyDriveListAdapter) getListAdapter()).checkAll();
                item.setTitle(getString(R.string.selectNone));
                updateActionModeTitleWithSelectedCount();
                return true;
            } else if (title.equalsIgnoreCase(getString(R.string.selectNone)))
            {
                resetSelection();
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
            resetSelection();
            actionMode = null;
            supportInvalidateOptionsMenu();
        }


    }

    private void copySelectedFiles(ActionMode mode)
    {
        filesToBePasted = (ArrayList<SkyDriveObject>) currentlySelectedFiles.clone();
        isCutNotPaste = false;

        Toast.makeText(getApplicationContext(), R.string.copyCutSelectedFiles, Toast.LENGTH_SHORT).show();

        resetSelection();
        mode.finish();
    }

    private void cutSelectedFiles(ActionMode mode)
    {
        filesToBePasted = (ArrayList<SkyDriveObject>) currentlySelectedFiles.clone();
        isCutNotPaste = true;

        Toast.makeText(getApplicationContext(), R.string.copyCutSelectedFiles, Toast.LENGTH_SHORT).show();

        resetSelection();
        mode.finish();
    }

    private void createDeleteDialog(final ActionMode mode)
    {
        final AlertDialog dialog = new AlertDialog.Builder(getSupportActionBar().getThemedContext()).create();
        dialog.setTitle(getString(R.string.deleteConfirmationTitle));
        dialog.setIcon(R.drawable.warning_triangle);
        StringBuilder deleteMessage = new StringBuilder();
        deleteMessage.append(getString(R.string.deleteConfirmationBody));
        for (int i = 0; i < currentlySelectedFiles.size(); i++)
        {
            deleteMessage.append(currentlySelectedFiles.get(i).getName());
            deleteMessage.append("\n");
        }
        deleteMessage.append(getString(R.string.deleteConfirmationQuestion));

        dialog.setMessage(deleteMessage.toString());
        dialog.setButton(getString(R.string.yes), new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                setSupportProgressBarIndeterminateVisibility(true);
                if (!connectionIsUnavailable())
                {
                    xLoader.deleteFiles(liveConnectClient, (ArrayList<SkyDriveObject>) currentlySelectedFiles.clone());
                }
                resetSelection();
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
    }

    private void createRenameDialog()
    {
        Intent startRenameDialog = new Intent(getSupportActionBar().getThemedContext(), RenameDialog.class);
        ArrayList<String> fileIds = new ArrayList<String>();
        ArrayList<String> fileNames = new ArrayList<String>();
        for (int i = 0; i < currentlySelectedFiles.size(); i++)
        {
            fileIds.add(currentlySelectedFiles.get(i).getId());
            fileNames.add(currentlySelectedFiles.get(i).getName());
        }
        startRenameDialog.putExtra(RenameDialog.EXTRAS_FILE_IDS, fileIds);
        startRenameDialog.putExtra(RenameDialog.EXTRAS_FILE_NAMES, fileNames);
        resetSelection();

        if (!connectionIsUnavailable())
        {
            startActivity(startRenameDialog);
        }
    }

    private void createSharingDialog()
    {
        Intent startSharingDialog = new Intent(getSupportActionBar().getThemedContext(), SharingDialog.class);
        ArrayList<String> fileIds = new ArrayList<String>();
        for (int i = 0; i < currentlySelectedFiles.size(); i++)
        {
            fileIds.add(currentlySelectedFiles.get(i).getId());
        }
        startSharingDialog.putExtra(RenameDialog.EXTRAS_FILE_IDS, fileIds);
        if (!connectionIsUnavailable())
        {
            startActivity(startSharingDialog);
        }
    }

    private void resetSelection()
    {
        ((SkyDriveListAdapter) getListAdapter()).clearChecked();
        currentlySelectedFiles.clear();
        updateActionModeTitleWithSelectedCount();
    }

    private AudioPlaybackService audioPlaybackService;
    private ServiceConnection audioPlaybackServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder)
        {
            audioPlaybackService = ((AudioPlaybackService.AudioPlaybackServiceBinder) iBinder).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            audioPlaybackService = null;
        }
    };

}



