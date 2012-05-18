package com.killerud.skydrive.constants;

/**
 * User: William
 * Date: 25.04.12
 * Time: 14:03
 */
final public class Constants {
    public static final String APP_CLIENT_ID = "00000000400C35DF";

    /* App permissions. Be as precise as possible. */
    public static final String[] APP_SCOPES = {
            "wl.offline_access",
            "wl.skydrive_update"
    };

    public static final String LOGTAG = "ASE";

    /* Bytes */
    public static final long CACHE_MAX_SIZE = 10485760l; //10MB

    private Constants() {
        throw new AssertionError("Unable to create Constants object.");
    }
}
