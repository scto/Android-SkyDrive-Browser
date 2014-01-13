//------------------------------------------------------------------------------
// Copyright (c) 2012 Microsoft Corporation. All rights reserved.
//------------------------------------------------------------------------------

package com.killerud.skydrive.objects;

import org.json.JSONObject;

public class SkyDriveAudio extends SkyDriveObject
{

    public static final String TYPE = "audio";

    public SkyDriveAudio(JSONObject object)
    {
        super(object);
    }

    @Override
    public void accept(Visitor visitor)
    {
        visitor.visit(this);
    }

    public long getSize()
    {
        return mObject.optLong("size");
    }

    public int getCommentsCount()
    {
        return mObject.optInt("comments_count");
    }

    public boolean getCommentsEnabled()
    {
        return mObject.optBoolean("comments_enabled");
    }

    public String getSource()
    {
        return mObject.optString("source");
    }

    public boolean getIsEmbeddable()
    {
        return mObject.optBoolean("is_embeddable");
    }

    public String getArtist()
    {
        return mObject.optString("artist");
    }

    public String getAlbum()
    {
        return mObject.optString("album");
    }

    public String getTitle()
    {
        return mObject.optString("title");
    }

    @Override
    public String toString()
    {
        return getTitle();
    }
}
