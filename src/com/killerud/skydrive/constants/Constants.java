package com.killerud.skydrive.constants;

/**
 * User: William
 * Date: 25.04.12
 * Time: 14:03
 */
final public class Constants
{
    public static final String APP_CLIENT_ID = "00000000400C35DF";

    /* App permissions. Be as precise as possible. */
    public static final String[] APP_SCOPES = {
            "wl.offline_access",
            "wl.skydrive_update"
    };


    public static final String LOGTAG = "ASE";


    /* Actions */
    public static final String ACTION_CANCEL_DOWN = "com.killerud.skydrive.CANCEL_DOWN";
    public static final String ACTION_CANCEL_UP = "com.killerud.skydrive.CANCEL_UP";

    /* Saved state */
    public static final String STATE_CURRENT_FOLDER = "currentFolderState";
    public static final String STATE_CURRENT_HIERARCHY = "currentFolderHierarchyState";
    public static final String STATE_PREVIOUS_FOLDERS = "previousFolderIdsState";
    public static final String STATE_CURRENTLY_SELECTED = "currentlySelectedFiles";
    public static final String STATE_ACTION_MODE_CURRENTLY_ON = "actionModeOn";

    /* Bytes */
    public static final long THUMBS_MAX_SIZE = 10485760; //10MB
    public static final long CACHE_MAX_SIZE = 104857600; //100MB

    /* Settings */
    public static final String  CONFIRM_EXIT = "confirm_exit";


    private Constants()
    {
        throw new AssertionError("Unable to create Constants object.");
    }
}
