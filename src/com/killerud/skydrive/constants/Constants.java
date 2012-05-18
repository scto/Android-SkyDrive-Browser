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
            "wl.signin",
            "wl.skydrive_update"
    };

    public static final String LOGTAG = "ASE";

    private Constants() {
        throw new AssertionError("Unable to create Constants object.");
    }
}
