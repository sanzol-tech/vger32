package ar.vger32app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import ar.vger32app.logger.LogManager;

/*
 * Singleton. Requests location updates from the OS.
 * Requesting a location update causes the OS to trigger an internal WiFi
 * scan, which populates the WifiManager.getScanResults() cache.
 *
 * Also provides isLocationEnabled() to check if the system location
 * service is active before attempting any location or WiFi scan work.
 *
 * minSdk = 30: isLocationEnabled() and getCurrentLocation() are always available.
 * FUSED_PROVIDER still requires API 31 — falls back to GPS on API 30.
 */

public class LocationHelper {

    private static final String LOG_TAG = "LocationHelper";

    // FUSED_PROVIDER was added in API 31. Fall back to GPS on API 30.
    @SuppressLint("InlinedApi")
    private static final String PROVIDER =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? LocationManager.FUSED_PROVIDER
                    : LocationManager.GPS_PROVIDER;

    private static volatile LocationHelper instance;

    private final Context appContext;
    private final LocationManager locationManager;
    private Location lastLocation;

    private LocationHelper(Context context) {
        this.appContext = context.getApplicationContext();
        this.locationManager = (LocationManager) appContext
                .getSystemService(Context.LOCATION_SERVICE);
    }

    public static synchronized LocationHelper getInstance(Context context) {
        if (instance == null) {
            instance = new LocationHelper(context);
        }
        return instance;
    }

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    public boolean isLocationEnabled() {
        return locationManager.isLocationEnabled();
    }

    @SuppressLint("MissingPermission")
    public LocationHelper requestSingleUpdate() {
        if (!hasLocationPermission()) return this;
        if (!isLocationEnabled()) return this;

        try {
            locationManager.getCurrentLocation(
                    PROVIDER,
                    null,
                    ContextCompat.getMainExecutor(appContext),
                    location -> {
                        if (location != null) {
                            lastLocation = location;
                            LogManager.APP_LOGGER.debug(LOG_TAG, "onLocationChanged");
                        }
                    }
            );
        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "getCurrentLocation: " + e.getMessage());
        }

        return this;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(appContext,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}