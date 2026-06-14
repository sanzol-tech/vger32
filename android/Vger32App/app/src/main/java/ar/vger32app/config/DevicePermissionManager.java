package ar.vger32app.config;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/*
 * Requests the runtime permissions the app needs (location for WiFi scan).
 * Normal permissions declared in AndroidManifest are granted at install time
 * and are not listed here.
 */

public class DevicePermissionManager {

    public static final int CODE_REQUEST = 10101;

    // Only permissions declared in AndroidManifest that require runtime grant.
    // Normal permissions (INTERNET, ACCESS_NETWORK_STATE, etc.) are granted
    // automatically at install time — no need to request them here.
    private static final String[] RUNTIME_PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };

    public static void checkPermissions(Activity activity) {
        List<String> toRequest = new ArrayList<>();

        for (String permission : RUNTIME_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(permission);
            }
        }

        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    toRequest.toArray(new String[0]),
                    CODE_REQUEST
            );
        }
    }
}