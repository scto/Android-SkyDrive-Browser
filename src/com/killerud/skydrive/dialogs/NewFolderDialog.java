package com.killerud.skydrive.dialogs;

/**
 * Created with IntelliJ IDEA.
 * User: William
 * Date: 07.05.12
 * Time: 15:41
 * To change this template use File | Settings | File Templates.
 */

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.killerud.skydrive.BrowserForSkyDriveApplication;
import com.killerud.skydrive.R;
import com.killerud.skydrive.util.JsonKeys;
import com.microsoft.live.LiveConnectClient;
import com.microsoft.live.LiveOperation;
import com.microsoft.live.LiveOperationException;
import com.microsoft.live.LiveOperationListener;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 *  The Create a new folder dialog. Always creates in the current directory.
 */
public class NewFolderDialog extends Activity {
    private LiveConnectClient mClient;
    private String mCurrentFolderId;
    private String LOGTAC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_folder);
        setTitle(getString(R.string.newFolderTitle));

        mCurrentFolderId = getIntent().getStringExtra("killerud.skydrive.CURRENT_FOLDER");

        BrowserForSkyDriveApplication app = (BrowserForSkyDriveApplication) getApplication();
        mClient = app.getConnectClient();
        LOGTAC = app.getDebugTag();


        final EditText name = (EditText) findViewById(R.id.nameEditText);
        final EditText description = (EditText) findViewById(R.id.descriptionEditText);

        findViewById(R.id.saveButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /* Uses a hashmap for creating a JSON object later on.
                * Communication with the SkyDrive API is in JSON.
                */
                Map<String, String> folder = new HashMap<String, String>();
                folder.put(JsonKeys.NAME, name.getText().toString());
                folder.put(JsonKeys.DESCRIPTION, description.getText().toString());



                /* Attempts to create the folder */
                mClient.postAsync(mCurrentFolderId,
                        new JSONObject(folder),
                        new LiveOperationListener() {
                            @Override
                            public void onError(LiveOperationException exception, LiveOperation operation) {

                                Toast.makeText(getApplicationContext(), R.string.errorFolderCouldNotBeCreated, Toast.LENGTH_SHORT).show();
                                Log.e(LOGTAC, exception.getMessage());
                            }

                            /* Gets the result of the operation and shows the user in a toast
                            * on error, reloads on success
                            */
                            @Override
                            public void onComplete(LiveOperation operation) {
                                //progressDialog.dismiss();

                                JSONObject result = operation.getResult();
                                if (result.has(JsonKeys.ERROR)) {
                                    JSONObject error = result.optJSONObject(JsonKeys.ERROR);
                                    String message = error.optString(JsonKeys.MESSAGE);
                                    String code = error.optString(JsonKeys.CODE);
                                    Toast.makeText(getApplicationContext(), code + ":" + message, Toast.LENGTH_SHORT).show();
                                } else {
                                    finish();
                                }
                            }
                        });
            }
        });

        findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}
