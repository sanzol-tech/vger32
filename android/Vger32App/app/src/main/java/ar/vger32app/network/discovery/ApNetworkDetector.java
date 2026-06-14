package ar.vger32app.network.discovery;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import ar.vger32app.logger.LogManager;

/*
 * Detects whether the device is connected to a vger32 module acting as a
 * WiFi access point. Both conditions must hold: the WiFi interface gateway
 * must be 192.168.4.1 and the SSID must start with "VGER_".
 *
 * Iterates getAllNetworks() filtered by TRANSPORT_WIFI so that an active
 * mobile data interface does not mask the WiFi gateway check.
 *
 * ACCESS_FINE_LOCATION is required on Android 8+ to read the SSID;
 * if the permission is absent the method returns false even when the
 * gateway matches.
 */

public class ApNetworkDetector {

    private static final String LOG_TAG = "ApNetworkDetector";
    private static final String ESP32_AP_GATEWAY = "192.168.4.1";
    private static final String VGER_SSID_PREFIX = "VGER_";

    private ApNetworkDetector() {
    }

    @SuppressLint("MissingPermission")
    public static boolean isEsp32ApNetwork(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return false;

            // SSID — always reads from the WiFi interface, independent of active network
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return false;
            String ssid = info.getSSID();
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            if (ssid == null || !ssid.startsWith(VGER_SSID_PREFIX)) return false;

            return getWifiNetwork(context) != null;
        } catch (Exception e) {
            LogManager.APP_LOGGER.debug(LOG_TAG, "isEsp32ApNetwork: " + e.getMessage());
        }
        return false;
    }

    /*
     * Returns the WiFi Network object whose default route gateway is
     * 192.168.4.1, or null if no such network is found.
     */
    public static Network getWifiNetwork(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
                LinkProperties lp = cm.getLinkProperties(network);
                if (lp == null) continue;
                for (RouteInfo route : lp.getRoutes()) {
                    if (route.isDefaultRoute() && route.getGateway() != null) {
                        if (ESP32_AP_GATEWAY.equals(route.getGateway().getHostAddress())) return network;
                    }
                }
            }
        } catch (Exception e) {
            LogManager.APP_LOGGER.debug(LOG_TAG, "getWifiNetwork: " + e.getMessage());
        }
        return null;
    }
}