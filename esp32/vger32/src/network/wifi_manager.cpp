/*
 * wifi_manager.cpp
 *
 * WiFi connection management via a non-blocking FSM.
 *
 * FSM OVERVIEW
 *
 *   BOOT (force_ap flag set in NVS)
 *    ↓
 *   AP_START → AP_CONNECTED (stays until reboot)
 *
 *   BOOT (no flag, or no known networks)
 *    ↓
 *   STA_START → requests scan → STA_SCANNING (scan_index = 0)
 *    ↓
 *   STA_SCANNING — walks scan results from scan_index
 *    ├─ match found      → STA_CONNECTING (saves scan_index)
 *    └─ scan exhausted   → STA_START (new scan cycle, after backoff delay)
 *    ↓
 *   STA_CONNECTING  (WIFI_CONNECT_TIMEOUT_MS)
 *    ├─ connected        → STA_CONNECTED (resets backoff)
 *    └─ timeout          → scan_index++ → STA_SCANNING (resumes scan)
 *    ↓
 *   STA_CONNECTED
 *    └─ link lost        → STA_START
 *
 *   Backoff: each failed scan cycle increases the wait before the next
 *   STA_START: 15s, 30s, 60s, 120s, 300s (capped). Resets on connect.
 *
 *   Notes
 *   - AP mode is only entered on boot when the force_ap NVS flag is set.
 *     Use wifi_manager_force_ap() to set the flag and reboot.
 *   - No automatic fallback to AP — if no known network is reachable,
 *     the FSM retries STA indefinitely.
 *   - Scan results come RSSI-sorted from the driver — best signal tried first.
 *   - No candidate array — scan is walked directly via scan_index.
 *   - BY_MAC networks connect via bssid, BY_SSID via name.
 */

#include <Preferences.h>
#include <WiFi.h>

#include "wifi_manager.h"

#include "config/keys.h"
#include "config/known_networks.h"
#include "logger/sys_logger.h"
#include "config/preferences.h"
#include "wifi_scanner.h"

// ==========================================
// CONFIG
// ==========================================

static constexpr uint32_t WIFI_CONNECT_TIMEOUT_MS = 15_seconds;
static constexpr uint32_t WIFI_SCAN_TIMEOUT_MS     = 10_seconds;

// Backoff delays between failed scan cycles (index = fail count, capped at last).
static constexpr uint32_t WIFI_BACKOFF_MS[] = {
    15_seconds,
    30_seconds,
    60_seconds,
    120_seconds,
    300_seconds,
};
static constexpr uint8_t WIFI_BACKOFF_MAX = sizeof(WIFI_BACKOFF_MS) / sizeof(WIFI_BACKOFF_MS[0]) - 1;

// NVS namespace and key for the force-AP boot flag.
static constexpr const char *NVS_NAMESPACE    = "wifi_mgr";
static constexpr const char *NVS_KEY_FORCE_AP = "force_ap";

// ==========================================
// STATE MACHINE
// ==========================================

typedef enum {
    WIFI_STATE_IDLE = 0,
    WIFI_STATE_STA_START,
    WIFI_STATE_STA_SCANNING,
    WIFI_STATE_STA_CONNECTING,
    WIFI_STATE_STA_CONNECTED,
    WIFI_STATE_AP_START,
    WIFI_STATE_AP_CONNECTED
} wifi_state_t;

// ==========================================
// RUNTIME CONTEXT
// ==========================================

typedef struct {
    wifi_state_t state;
    uint32_t     state_timestamp;
    uint8_t      scan_index; // current position in wifi_scanner results
} wifi_manager_t;

// ==========================================
// STATIC STATE
// ==========================================

static wifi_manager_t manager;
static uint8_t sta_fail_count = 0; // incremented each failed scan cycle, drives backoff

// ==========================================
// HELPERS
// ==========================================

static void nvs_set_force_ap(bool value) {
    Preferences prefs;
    prefs.begin(NVS_NAMESPACE, false);
    prefs.putBool(NVS_KEY_FORCE_AP, value);
    prefs.end();
}

static bool nvs_get_force_ap() {
    Preferences prefs;
    prefs.begin(NVS_NAMESPACE, true);
    bool val = prefs.getBool(NVS_KEY_FORCE_AP, false);
    prefs.end();
    return val;
}

// Parse a hex MAC string (12 chars, no colons) into a 6-byte array.
static void parse_mac(const char *str, uint8_t out[6]) {
    for (uint8_t i = 0; i < 6; i++) {
        char byte_str[3] = {str[i * 2], str[i * 2 + 1], '\0'};
        out[i] = (uint8_t) strtol(byte_str, nullptr, 16);
    }
}

// Find a known network matching the given AP. Returns index into
// known_networks[], or -1 if not found.
static int8_t find_known_network(const WifiApEntry *ap) {
    for (uint8_t n = 0; n < known_network_count; n++) {
        const KnownNetwork &net = known_networks[n];
        bool match = (net.type == KNOWN_NET_BY_SSID)
                         ? strncmp(ap->ssid, net.identifier, KNOWN_NET_SSID_LEN) == 0
                         : strncmp(ap->mac, net.identifier, KNOWN_NET_MAC_LEN) == 0;
        if (match) return (int8_t) n;
    }
    return -1;
}

// ==========================================
// WIFI CONTROL
// ==========================================

static void connect_ap(const WifiApEntry *ap, const KnownNetwork &net) {
    const char *pass = net.pass[0] ? net.pass : nullptr;

    if (net.type == KNOWN_NET_BY_MAC) {
        uint8_t bssid[6];
        parse_mac(ap->mac, bssid);
        sys_log(LOG_INFO, "WiFi", "Connecting to '%s'", ap->ssid);
        sys_log(LOG_DEBUG, "WiFi", "MAC %02X:%02X:%02X:%02X:%02X:%02X", bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);
        WiFi.begin(ap->ssid, pass, 0, bssid);
    } else {
        sys_log(LOG_INFO, "WiFi", "Connecting to '%s'", ap->ssid);
        WiFi.begin(ap->ssid, pass);
    }

    manager.state           = WIFI_STATE_STA_CONNECTING;
    manager.state_timestamp = millis();
}

static void start_ap() {
    WiFi.disconnect(true);
    WiFi.mode(WIFI_AP);
    WiFi.softAP(cfg_module_id.c_str(), cfg_ap_pass.c_str());

    sys_log(LOG_INFO, "WiFi", "Starting AP '%s' — IP=%s",
             cfg_module_id.c_str(), WiFi.softAPIP().toString().c_str());

    manager.state           = WIFI_STATE_AP_CONNECTED;
    manager.state_timestamp = millis();
}

// ==========================================
// STATE HANDLERS
// ==========================================

static void wifi_state_sta_start() {
    if (known_network_count == 0) {
        sys_log(LOG_INFO, "WiFi", "No networks configured, starting AP");
        manager.state           = WIFI_STATE_AP_START;
        manager.state_timestamp = millis();
        return;
    }

    if (sta_fail_count > 0 && wifi_manager_backoff_enabled) {
        uint8_t backoff_idx = sta_fail_count <= WIFI_BACKOFF_MAX ? sta_fail_count - 1 : WIFI_BACKOFF_MAX;
        uint32_t backoff_ms = WIFI_BACKOFF_MS[backoff_idx];
        if (millis() - manager.state_timestamp < backoff_ms) return;
        sys_log(LOG_INFO, "WiFi", "Backoff elapsed (%lus), scanning", (unsigned long)(backoff_ms / 1000));
    }

    WiFi.disconnect(true);
    WiFi.mode(WIFI_STA);
    wifi_scanner_request();

    manager.scan_index      = 0;
    manager.state           = WIFI_STATE_STA_SCANNING;
    manager.state_timestamp = millis();
    sys_log(LOG_INFO, "WiFi", "Scanning for known networks...");
}

static void wifi_state_sta_scanning() {
    if (!wifi_scanner_has_results()) {
        if (millis() - manager.state_timestamp > WIFI_SCAN_TIMEOUT_MS) {
            sys_log(LOG_WARN, "WiFi", "Scan timeout, retrying");
            if (sta_fail_count < WIFI_BACKOFF_MAX) sta_fail_count++;
            manager.state           = WIFI_STATE_STA_START;
            manager.state_timestamp = millis();
        }
        return;
    }

    uint8_t total = wifi_scanner_count();
    sys_log(LOG_INFO, "WiFi", "Scan: %u APs", total);

    while (manager.scan_index < total) {
        const WifiApEntry *ap = wifi_scanner_get(manager.scan_index);
        if (!ap) {
            manager.scan_index++;
            continue;
        }

        int8_t net_idx = find_known_network(ap);
        sys_log(LOG_DEBUG, "WiFi", "'%s' %s", ap->ssid, net_idx >= 0 ? "known" : "skip");

        if (net_idx >= 0) {
            connect_ap(ap, known_networks[net_idx]);
            return;
        }
        manager.scan_index++;
    }

    sys_log(LOG_INFO, "WiFi", "No known networks visible, retrying");
    if (sta_fail_count < WIFI_BACKOFF_MAX) sta_fail_count++;
    manager.state           = WIFI_STATE_STA_START;
    manager.state_timestamp = millis();
}

static void wifi_state_sta_connecting() {
    if (WiFi.status() == WL_CONNECTED) {
        sys_log(LOG_INFO, "WiFi", "Connected, IP: %s", WiFi.localIP().toString().c_str());
        sta_fail_count          = 0;
        manager.state           = WIFI_STATE_STA_CONNECTED;
        manager.state_timestamp = millis();
        return;
    }

    if (millis() - manager.state_timestamp < WIFI_CONNECT_TIMEOUT_MS) return;

    sys_log(LOG_WARN, "WiFi", "Connection timeout WiFi.status=%d, trying next...",
             (int) WiFi.status());
    manager.scan_index++;
    manager.state           = WIFI_STATE_STA_SCANNING;
    manager.state_timestamp = millis();
}

static void wifi_state_sta_connected() {
    if (WiFi.status() != WL_CONNECTED) {
        sys_log(LOG_WARN, "WiFi", "Connection lost WiFi.status=%d, retrying",
                 (int) WiFi.status());
        manager.state           = WIFI_STATE_STA_START;
        manager.state_timestamp = millis();
    }
}

static void wifi_state_ap_start() {
    start_ap();
}

// AP_CONNECTED is a terminal state — only exited by reboot.
static void wifi_state_ap_connected() {}

// ==========================================
// PROCESS STATE
// ==========================================

static void process_state() {
    switch (manager.state) {
        case WIFI_STATE_STA_START:      wifi_state_sta_start();      break;
        case WIFI_STATE_STA_SCANNING:   wifi_state_sta_scanning();   break;
        case WIFI_STATE_STA_CONNECTING: wifi_state_sta_connecting(); break;
        case WIFI_STATE_STA_CONNECTED:  wifi_state_sta_connected();  break;
        case WIFI_STATE_AP_START:       wifi_state_ap_start();       break;
        case WIFI_STATE_AP_CONNECTED:   wifi_state_ap_connected();   break;
        default: break;
    }
}

// ==========================================
// HARDWARE QUIRKS
// ==========================================

static void wifi_set_tx_power() {
    // Start from the mission/runtime preference — may be WIFI_TX_POWER_FULL (uncapped).
    wifi_power_t power = preferences_get().wifi_tx_power;

    // ESP32-C3 Super Mini has an underpowered onboard voltage regulator
    // that cannot sustain WiFi TX at default power (20dBm).
    // Symptoms: softAP crash on client association, STA crash on connect.
    // Hard ceiling — overrides both preference and hardware profile.
#if defined(ARDUINO_LOLIN_C3_MINI)
    if (power == WIFI_TX_POWER_FULL || power > WIFI_POWER_15dBm)
        power = WIFI_POWER_15dBm;
#endif

    // Hardware profile may define HW_WIFI_TX_POWER as a thermal or range cap.
    // Takes precedence over the preference value but not over the board quirk above.
#if defined(HW_WIFI_TX_POWER)
    if (power == WIFI_TX_POWER_FULL || power > HW_WIFI_TX_POWER)
        power = HW_WIFI_TX_POWER;
#endif

    // If still FULL here, no ceiling was applied — use the hardware maximum.
    if (power == WIFI_TX_POWER_FULL)
        power = WIFI_POWER_19_5dBm;

    WiFi.setTxPower(power);
}

// ==========================================
// PUBLIC API
// ==========================================

void wifi_manager_set_enabled(bool enabled) { wifi_manager_enabled = enabled; }

void wifi_manager_init() {
    if (!wifi_manager_enabled) return;

    WiFi.persistent(false);
    WiFi.setHostname(cfg_module_id.c_str());

    // WiFi sleep mode enabled — allows the radio to sleep between packets.
    // Disable (false) for more responsive webserver and MQTT at the cost of ~20mA.
    WiFi.setSleep(false);

    WiFi.mode(WIFI_STA);

    wifi_set_tx_power();

    known_networks_load();
    wifi_scanner_init();

    // Consume the force-AP flag atomically: read, clear, then act.
    // Clearing before acting ensures a crash in start_ap() does not
    // leave the device permanently stuck in AP mode.
    bool force_ap = nvs_get_force_ap();
    if (force_ap) {
        nvs_set_force_ap(false);
        sys_log(LOG_INFO, "WiFi", "force_ap flag consumed — booting in AP");
    }

    manager.state           = force_ap ? WIFI_STATE_AP_START : WIFI_STATE_STA_START;
    manager.state_timestamp = millis();
    manager.scan_index      = 0;

    sys_log(LOG_INFO, "WiFi", "Initialized — %s, known_networks: %u",
             force_ap ? "AP mode" : "STA mode", known_network_count);
}

void wifi_manager_handle() {
    if (!wifi_manager_enabled) return;
    wifi_scanner_handle();
    process_state();
}

// Persists the force-AP flag to NVS and reboots.
// On next boot, wifi_manager_init() will start in AP_START.
// To exit AP mode, reboot without setting the flag (e.g. via /api/reboot).
void wifi_manager_force_ap() {
    sys_log(LOG_INFO, "WiFi", "Forcing AP mode — persisting flag and rebooting");
    nvs_set_force_ap(true);
    ESP.restart();
}

bool wifi_manager_is_connected() {
    return manager.state == WIFI_STATE_STA_CONNECTED;
}

bool wifi_manager_is_ap_active() {
    return manager.state == WIFI_STATE_AP_CONNECTED;
}

const char *wifi_manager_get_ip() {
    static char buf[16];

    IPAddress ip = (manager.state == WIFI_STATE_AP_CONNECTED)
                       ? WiFi.softAPIP()
                       : WiFi.localIP();

    snprintf(buf, sizeof(buf), "%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3]);
    return buf;
}