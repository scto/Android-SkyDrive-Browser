package com.killerud.skydrive;

/**
 * User: William
 * Date: 25.04.12
 * Time: 14:03
 */
final public class Constants {
    public static final String APP_CLIENT_ID = "00000000480BBC67";

    /* App permissions. Be as precise as possible. */
    public static final String[] APP_SCOPES = {
            "wl.basic",
            "wl.offline_access",
            "wl.signin",
            "wl.skydrive_update"
    };

    public static final String LOGTAG = "Browser for SkyDrive";

    private Constants() {
        throw new AssertionError("Unable to create Constants object.");
    }
}
