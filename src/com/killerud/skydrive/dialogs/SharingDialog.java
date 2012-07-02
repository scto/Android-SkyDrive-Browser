package com.killerud.skydrive.dialogs;

import android.os.Bundle;
import com.actionbarsherlock.app.SherlockActivity;
import com.killerud.skydrive.R;


public class SharingDialog extends SherlockActivity{

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.share_file);
    }
}
