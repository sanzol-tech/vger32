package ar.vger32app.config.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.logger.LogLevel;

/*
 * Read-only access to user preferences stored in SharedPreferences.
 * All keys mirror the ones declared in root_preferences.xml.
 *
 * Call SettingsManager.init(context) once from MyApplication.onCreate()
 * before any other use.
 *
 * Storage split:
 *   - Scrambler key       → SecurePreferencesManager (encrypted).
 *   - Default API key     → SecurePreferencesManager (encrypted), same as scrambler key
 *                           and per-module keys.
 *   - Everything else     → plain SharedPreferences (non-sensitive).
 */

public final class SettingsManager {

    private static SharedPreferences prefs;
    private static SecurePreferencesManager spm;

    public static void init(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        spm = SecurePreferencesManager.getInstance(context);
    }

    private SettingsManager() {
    }

    // --------------------------------------------------------
    // --- LOGS -----------------------------------------------

    public static String getLogLevel() {
        return prefs().getString("log_level", LogLevel.INFO.name());
    }

    // --------------------------------------------------------
    // --- SECURITY -------------------------------------------

    public static boolean isUnlockCodeEnabled() {
        return prefs().getBoolean("unlock_code_enabled", true);
    }

    // --------------------------------------------------------
    // --- MQTT -----------------------------------------------

    public static String getMqttBrokerHost() {
        return prefs().getString("mqtt_broker_host", "broker.hivemq.com");
    }

    public static String getMqttTopicBase() {
        return prefs().getString("mqtt_topic_base", "vger32");
    }

    public static int getMqttBrokerPort() {
        String value = prefs().getString("mqtt_broker_port", "1883");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1883;
        }
    }

    // --------------------------------------------------------
    // --- MODULES --------------------------------------------

    public static int getModulePurgeDays() {
        String value = prefs().getString("module_purge_days", "7");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    // --------------------------------------------------------
    // --- DISCOVERY ------------------------------------------

    public static int getModuleHttpPort() {
        String value = prefs().getString("module_http_port", "80");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 80;
        }
    }

    public static int getUdpDiscoveryPort() {
        String value = prefs().getString("udp_discovery_port", "4210");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 4210;
        }
    }

    public static String getUdpDiscoveryMagic() {
        return prefs().getString("udp_discovery_magic", "vger32:discover");
    }

    public static String getMdnsServiceType() {
        return prefs().getString("mdns_service_type", "_vger32._tcp");
    }

    public static int getScanTimeout() {
        String value = prefs().getString("scan_timeout", "400");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 400;
        }
    }

    // --------------------------------------------------------
    // --- FINGERPRINT ----------------------------------------

    public static int getFingerprintMaxNetworks() {
        String value = prefs().getString("fingerprint_max_networks", "20");
        try {
            int parsed = Integer.parseInt(value);
            return Math.min(Math.max(parsed, 5), 50);
        } catch (NumberFormatException e) {
            return 20;
        }
    }

    public static int getFingerprintMinSignal() {
        String value = prefs().getString("fingerprint_min_signal", "-85");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -85;
        }
    }

    public static boolean saveScan5GhzNetworks() {
        return prefs().getBoolean("save_5ghz_networks", false);
    }

    // --------------------------------------------------------
    // --- SCRAMBLER ------------------------------------------

    public static boolean isMqttScrambled() {
        return prefs().getBoolean("mqtt_scrambled", false);
    }

    public static boolean isHttpScramblerEnabled() {
        return prefs().getBoolean("http_scrambled", false);
    }

    public static String getDefaultScramblerKey() {
        return spm.getDefaultScramblerKey();
    }

    // --------------------------------------------------------
    // --- API KEY --------------------------------------------

    public static String getDefaultApiKey() {
        return spm.getDefaultApiKey();
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private static SharedPreferences prefs() {
        return prefs;
    }
}