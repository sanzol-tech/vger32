package ar.vger32app.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;

/*
 * Static helpers for device-level queries and orientation policy.
 */

public class DeviceUtils {

    private DeviceUtils() {
    }

    public static boolean isTablet(Context context) {
        int deviceType = context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK;
        return deviceType == Configuration.SCREENLAYOUT_SIZE_XLARGE
                || deviceType == Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    // --------------------------------------------------------
    // --- ORIENTATION ----------------------------------------

    public static void applyOrientation(Activity activity) {
        if (isTablet(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }
}