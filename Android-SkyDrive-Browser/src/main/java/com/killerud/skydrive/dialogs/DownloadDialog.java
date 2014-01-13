package com.killerud.skydrive.dialogs;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;
import com.killerud.skydrive.R;

/**
 * Created with IntelliJ IDEA.
 * User: William
 * Date: 04.07.12
 * Time: 21:12
 * To change this template use File | Settings | File Templates.
 */
public class DownloadDialog extends ActionBarActivity
{

    public static final String EXTRA_FILE_POSITION = "file_position";
    public static final int DOWNLOAD_REQUEST = 5;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.download_dialog);
        ((Button) findViewById(R.id.cancelButton)).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });

        ((Button) findViewById(R.id.saveButton)).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Intent data = new Intent();
                data.putExtra(EXTRA_FILE_POSITION, getIntent().getIntExtra(EXTRA_FILE_POSITION, 0));
                setResult(Activity.RESULT_OK, data);
                finish();
            }
        });
    }
}
