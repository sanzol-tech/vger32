package ar.vger32app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

import ar.vger32app.logger.LogManager;

/*
 * Network interface helpers.
 */

public class NetworkUtils {

    private static final String LOG_TAG = "NetworkUtils";

    private NetworkUtils() {
    }

    public static String getWifiSubnet(Context context) {
        String subnet = subnetFromConnectivityManager(context);
        if (subnet != null) return subnet;
        return subnetFromNetworkInterfaces();
    }

    // --------------------------------------------------------
    // --- INTERNAL -------------------------------------------

    private static String subnetFromConnectivityManager(Context context) {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return null;
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) return null;
            for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                String subnet = toSubnetPrefix(linkAddress.getAddress());
                if (subnet != null) return subnet;
            }
        } catch (Exception e) {
            LogManager.APP_LOGGER.debug(LOG_TAG, "subnetFromConnectivityManager: " + e.getMessage());
        }
        return null;
    }

    private static String subnetFromNetworkInterfaces() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                for (java.net.InterfaceAddress interfaceAddress : iface.getInterfaceAddresses()) {
                    String subnet = toSubnetPrefix(interfaceAddress.getAddress());
                    if (subnet != null) {
                        LogManager.APP_LOGGER.debug(LOG_TAG,
                                "subnet via " + iface.getName() + ": " + subnet);
                        return subnet;
                    }
                }
            }
        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "subnetFromNetworkInterfaces: " + e.getMessage());
        }
        return null;
    }

    private static String toSubnetPrefix(java.net.InetAddress addr) {
        if (!(addr instanceof Inet4Address)) return null;
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) return null;
        byte[] b = addr.getAddress();
        int first = b[0] & 0xff;
        // Accept private ranges: 10.x, 192.168.x, 172.x
        if (first != 10 && first != 192 && first != 172) return null;
        return first + "." + (b[1] & 0xff) + "." + (b[2] & 0xff);
    }
}