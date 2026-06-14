package ar.vger32app.localizer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ar.vger32app.R;
import ar.vger32app.logger.LogManager;

/*
 * Triggers startScan() and listens for SCAN_RESULTS_AVAILABLE_ACTION.
 * Falls back to reading cached results after TIMEOUT_MS if the broadcast
 * never arrives (throttled device, old cache, etc.).
 *
 * Results are sorted by signal strength (strongest first). No frequency
 * filtering is applied — the caller decides what to display and save.
 *
 * ACCESS_FINE_LOCATION must be granted before calling scan().
 * Call release() from Fragment.onDestroyView().
 */

public class WifiScanner {

    public interface Callback {
        void onResult(List<ScanResult> results);

        void onError(String message);
    }

    private static final String LOG_TAG = "WifiScanner";
    private static final long TIMEOUT_MS = 8000;

    private final Context appContext;
    private final WifiManager wifiManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BroadcastReceiver receiver;
    private Runnable timeoutRunnable;
    private boolean delivered;

    public WifiScanner(Context context) {
        this.appContext = context.getApplicationContext();
        this.wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
    }

    public void scan(Callback callback) {
        cleanup();
        delivered = false;

        if (!wifiManager.isWifiEnabled()) {
            callback.onError(appContext.getString(R.string.wifi_error_disabled));
            return;
        }

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError(appContext.getString(R.string.wifi_error_location_permission));
            return;
        }

        // Register receiver BEFORE startScan()
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                LogManager.APP_LOGGER.debug(LOG_TAG,
                        "SCAN_RESULTS_AVAILABLE received — fresh=" + fresh);
                deliver(callback);
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }

        // Trigger scan — may be throttled on Android 9+, that's OK
        try {
            boolean started = wifiManager.startScan();
            LogManager.APP_LOGGER.debug(LOG_TAG, "startScan: " + started);
        } catch (Exception e) {
            LogManager.APP_LOGGER.warn(LOG_TAG, "startScan exception: " + e.getMessage());
        }

        // Fallback: if broadcast never arrives, deliver cached results
        timeoutRunnable = () -> {
            LogManager.APP_LOGGER.warn(LOG_TAG, "timeout — delivering cached results");
            deliver(callback);
        };
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    public void release() {
        cleanup();
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private void deliver(Callback callback) {
        if (delivered) return;
        delivered = true;
        cleanup();

        try {
            List<ScanResult> raw = new ArrayList<>(wifiManager.getScanResults());
            LogManager.APP_LOGGER.info(LOG_TAG, "Phone scan: " + raw.size() + " networks");

            if (raw.isEmpty()) {
                callback.onError(appContext.getString(R.string.wifi_error_no_networks));
                return;
            }

            raw.sort((a, b) -> b.level - a.level);
            callback.onResult(new ArrayList<>(raw));

        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "getScanResults failed: " + e.getMessage());
            callback.onError(appContext.getString(R.string.wifi_error_scan_fmt, e.getMessage()));
        }
    }

    private void cleanup() {
        if (receiver != null) {
            try {
                appContext.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiver = null;
        }
        if (timeoutRunnable != null) {
            handler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }
}