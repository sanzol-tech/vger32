package ar.vger32app.localizer;

import android.net.wifi.ScanResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.ui.localizer.LocalizerWifiAdapter;

/*
 * Applies user preferences to decide which WiFi networks enter a fingerprint.
 *
 * Filters: 2.4 GHz band, minimum signal, maximum network count, dual-band dedup.
 * The two includedKeys methods produce composite keys used by LocalizerWifiAdapter
 * to visually mark networks that will not be saved.
 */

public class FingerprintFilter {

    // 2.4 GHz upper bound in MHz. Channels 1–14 map to frequencies below this.
    private static final int MAX_FREQ_2_4GHZ = 2500;

    private FingerprintFilter() {
    }

    // --------------------------------------------------------
    // --- PHONE SCAN -----------------------------------------

    public static List<WifiNetwork> fromPhoneScan(List<ScanResult> results) {
        boolean save5ghz = SettingsManager.saveScan5GhzNetworks();
        int minSignal = SettingsManager.getFingerprintMinSignal();
        int maxNetworks = SettingsManager.getFingerprintMaxNetworks();

        Set<String> seen = new HashSet<>();
        List<WifiNetwork> networks = new ArrayList<>();

        for (ScanResult r : results) {
            if (!save5ghz && r.frequency > MAX_FREQ_2_4GHZ) continue;
            if (minSignal != 0 && r.level < minSignal) continue;
            if (seen.contains(r.BSSID)) continue;
            networks.add(new WifiNetwork(
                    r.BSSID, WifiNetwork.frequencyToChannel(r.frequency), r.level));
            seen.add(r.BSSID);
            if (networks.size() == maxNetworks) break;
        }
        return networks;
    }

    public static Set<String> phoneIncludedKeys(List<ScanResult> results) {
        boolean save5ghz = SettingsManager.saveScan5GhzNetworks();
        int minSignal = SettingsManager.getFingerprintMinSignal();
        int maxNetworks = SettingsManager.getFingerprintMaxNetworks();

        Set<String> seen = new HashSet<>();
        Set<String> keys = new LinkedHashSet<>();

        for (ScanResult r : results) {
            if (!save5ghz && r.frequency > MAX_FREQ_2_4GHZ) continue;
            if (minSignal != 0 && r.level < minSignal) continue;
            if (seen.contains(r.BSSID)) continue;
            if (keys.size() >= maxNetworks) break;
            keys.add(r.BSSID + ":" + r.frequency);
            seen.add(r.BSSID);
        }
        return keys;
    }

    // --------------------------------------------------------
    // --- MODULE SCAN ----------------------------------------

    public static List<WifiNetwork> fromModuleScan(List<LocalizerWifiAdapter.WifiItem> items) {
        boolean save5ghz = SettingsManager.saveScan5GhzNetworks();
        int minSignal = SettingsManager.getFingerprintMinSignal();
        int maxNetworks = SettingsManager.getFingerprintMaxNetworks();

        List<LocalizerWifiAdapter.WifiItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> b.rssi - a.rssi);

        Set<String> seen = new HashSet<>();
        List<WifiNetwork> networks = new ArrayList<>();

        for (LocalizerWifiAdapter.WifiItem item : sorted) {
            if (!save5ghz && item.channel > 14) continue;
            if (minSignal != 0 && item.rssi < minSignal) continue;
            if (seen.contains(item.mac)) continue;
            networks.add(new WifiNetwork(item.mac, item.channel, item.rssi));
            seen.add(item.mac);
            if (networks.size() == maxNetworks) break;
        }
        return networks;
    }

    public static Set<String> moduleIncludedKeys(List<LocalizerWifiAdapter.WifiItem> items) {
        boolean save5ghz = SettingsManager.saveScan5GhzNetworks();
        int minSignal = SettingsManager.getFingerprintMinSignal();
        int maxNetworks = SettingsManager.getFingerprintMaxNetworks();

        List<LocalizerWifiAdapter.WifiItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> b.rssi - a.rssi);

        Set<String> seen = new HashSet<>();
        Set<String> keys = new LinkedHashSet<>();

        for (LocalizerWifiAdapter.WifiItem item : sorted) {
            if (!save5ghz && item.channel > 14) continue;
            if (minSignal != 0 && item.rssi < minSignal) continue;
            if (seen.contains(item.mac)) continue;
            if (keys.size() >= maxNetworks) break;
            keys.add(item.mac + ":" + item.channel);
            seen.add(item.mac);
        }
        return keys;
    }
}