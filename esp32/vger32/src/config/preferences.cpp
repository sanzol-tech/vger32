/*
 * preferences.cpp
 *
 * Storage: NVS namespace "config" (merged from former "config" + "caps").
 * Devices upgrading from a split-namespace build will get first-boot defaults
 * for the former capability keys — acceptable one-time migration cost.
 *
 * Text exchange format (used by /api/preferences), one field per line:
 *   KEY<0x1F>VALUE\n
 *
 * Keys and value formats:
 *   moduleId      <string>
 *   mqttServer    <string>
 *   mqttPort      <int>
 *   mqttInterval  <int>
 *   mqttScrambled 0|1
 *   wifi          0|1
 *   txpwr         8_5|11|13|15|17|full
 *   mqtt          0|1
 *   dash          0|1
 *   mdns          0|1
 *   udp           0|1
 *   locl          0|1
 *   slep          0|1
 *   time          0|1
 *   blog          0|1
 *   log           F|E|W|I|D|off
 */

#include <Preferences.h>

#include "preferences.h"

#include "config/constants.h"
#include "logger/sys_logger.h"
#include "discovery/mdns_discovery.h"
#include "discovery/udp_discovery.h"
#include "localizer/wifi_localizer.h"
#include "mqtt/mqtt_client.h"
#include "network/time_manager.h"
#include "network/wifi_manager.h"
#include "profiles/active_profile.h"
#include "logger/boot_logger.h"
#include "system/sleep_manager.h"
#include "webserver/dashboard_server.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static PrefsConfig s_cfg;
static Preferences prefs;

// ==========================================
// GLOBALS — references into s_cfg for modules
// that access cfg_* fields by name
// ==========================================

String &cfg_module_id      = s_cfg.module_id;
String &cfg_mqtt_server    = s_cfg.mqtt_server;
int    &cfg_mqtt_port      = s_cfg.mqtt_port;
int    &cfg_mqtt_interval  = s_cfg.mqtt_interval;
bool   &cfg_mqtt_scrambled = s_cfg.mqtt_scrambled;

// ==========================================
// PRIVATE — NVS KEYS
// ==========================================

static const char *K_MODULE_ID      = "module_id";
static const char *K_MQTT_SERVER    = "mqtt_server";
static const char *K_MQTT_PORT      = "mqtt_port";
static const char *K_MQTT_INTERVAL  = "mqtt_interval";
static const char *K_MQTT_SCRAMBLED = "mqtt_scrambled";
static const char *K_WIFI           = "wifi";
static const char *K_WIFI_TX_POWER  = "wifi_tx_pwr";
static const char *K_MQTT_ENABLED   = "mqtt";
static const char *K_WEBSERVER      = "webserver";
static const char *K_MDNS           = "mdns";
static const char *K_UDP            = "udp";
static const char *K_LOCALIZER      = "localizer";
static const char *K_SLEEP          = "sleep";
static const char *K_TIME           = "time";
static const char *K_BOOT_LOGGER    = "bootLog";
static const char *K_LOG_LEVEL      = "logLevel";

// ==========================================
// PRIVATE — SERIALIZATION HELPERS
// ==========================================

static const char *tx_power_to_str(wifi_power_t p) {
    if (p == WIFI_TX_POWER_FULL)  return "full";
    if (p >= WIFI_POWER_17dBm)    return "17";
    if (p >= WIFI_POWER_15dBm)    return "15";
    if (p >= WIFI_POWER_13dBm)    return "13";
    if (p >= WIFI_POWER_11dBm)    return "11";
    return "8_5";
}

static wifi_power_t tx_power_from_str(const char *s) {
    if (strcmp(s, "full") == 0) return WIFI_TX_POWER_FULL;
    if (strcmp(s, "17")   == 0) return WIFI_POWER_17dBm;
    if (strcmp(s, "15")   == 0) return WIFI_POWER_15dBm;
    if (strcmp(s, "13")   == 0) return WIFI_POWER_13dBm;
    if (strcmp(s, "11")   == 0) return WIFI_POWER_11dBm;
    if (strcmp(s, "8_5")  == 0) return WIFI_POWER_8_5dBm;
    return WIFI_TX_POWER_FULL;
}

static const char *log_level_to_str(int level) {
    switch (level) {
        case LOG_FATAL: return "F";
        case LOG_ERROR: return "E";
        case LOG_WARN:  return "W";
        case LOG_INFO:  return "I";
        case LOG_DEBUG: return "D";
        case -1:              return "off";
        default:              return "I";
    }
}

static int log_level_from_str(const char *s) {
    if (strcmp(s, "F")   == 0) return LOG_FATAL;
    if (strcmp(s, "E")   == 0) return LOG_ERROR;
    if (strcmp(s, "W")   == 0) return LOG_WARN;
    if (strcmp(s, "I")   == 0) return LOG_INFO;
    if (strcmp(s, "D")   == 0) return LOG_DEBUG;
    if (strcmp(s, "off") == 0) return -1;
    return LOG_INFO;
}

// ==========================================
// PRIVATE — COMPILE-TIME GATE ENFORCEMENT
// ==========================================

static void enforce_compile_gates(PrefsConfig &cfg) {
    if (cfg.log_level > LOG_DEBUG) cfg.log_level = LOG_DEBUG;
    if (cfg.log_level < -1)        cfg.log_level = -1;
}

// ==========================================
// PRIVATE — PUSH FLAGS INTO MODULES
// ==========================================

static void push_to_modules(const PrefsConfig &cfg) {
    wifi_manager_set_enabled(cfg.wifi_enabled);
    time_set_enabled(cfg.time_enabled);
    boot_logger_set_enabled(cfg.boot_logger_enabled);
    mqtt_set_enabled(cfg.mqtt_enabled);
    dashboard_set_enabled(cfg.webserver_enabled);
    localizer_set_enabled(cfg.localizer_enabled);
    mdns_discovery_set_enabled(cfg.mdns_enabled);
    udp_discovery_set_enabled(cfg.udp_enabled);
    sleep_manager_set_enabled(cfg.sleep_enabled);
    log_set_level(cfg.log_level);
}

// ==========================================
// PUBLIC API
// ==========================================

void preferences_load() {
    prefs.begin("config", false);

    s_cfg.module_id = prefs.getString(K_MODULE_ID, "");
    if (s_cfg.module_id == "") {
        s_cfg.module_id = String(MODULE_ID_PREFIX) + String(esp_random() % 90000 + 10000);
        prefs.putString(K_MODULE_ID, s_cfg.module_id);
    }

    s_cfg.mqtt_server    = prefs.getString(K_MQTT_SERVER,    MN_DEFAULT_PREF_MQTT_SERVER);
    s_cfg.mqtt_port      = prefs.getInt   (K_MQTT_PORT,      MN_DEFAULT_PREF_MQTT_PORT);
    s_cfg.mqtt_interval  = prefs.getInt   (K_MQTT_INTERVAL,  MN_DEFAULT_PREF_MQTT_INTERVAL);
    s_cfg.mqtt_scrambled = prefs.getBool  (K_MQTT_SCRAMBLED, MN_DEFAULT_PREF_MQTT_SCRAMBLED);

    s_cfg.wifi_enabled  = prefs.getBool  (K_WIFI,           MN_DEFAULT_PREF_WIFI_ENABLED);
    s_cfg.wifi_tx_power = tx_power_from_str(
        prefs.getString(K_WIFI_TX_POWER,
                        tx_power_to_str(MN_DEFAULT_PREF_WIFI_TX_POWER)).c_str());

    s_cfg.mqtt_enabled        = prefs.getBool(K_MQTT_ENABLED, MN_DEFAULT_PREF_MQTT_ENABLED);
    s_cfg.webserver_enabled   = prefs.getBool(K_WEBSERVER,    MN_DEFAULT_PREF_WEBSERVER);
    s_cfg.mdns_enabled        = prefs.getBool(K_MDNS,         MN_DEFAULT_PREF_MDNS);
    s_cfg.udp_enabled         = prefs.getBool(K_UDP,          MN_DEFAULT_PREF_UDP);
    s_cfg.localizer_enabled   = prefs.getBool(K_LOCALIZER,    MN_DEFAULT_PREF_LOCALIZER);
    s_cfg.sleep_enabled       = prefs.getBool(K_SLEEP,        MN_DEFAULT_PREF_SLEEP);
    s_cfg.time_enabled        = prefs.getBool(K_TIME,         MN_DEFAULT_PREF_TIME);
    s_cfg.boot_logger_enabled = prefs.getBool(K_BOOT_LOGGER,  MN_DEFAULT_PREF_BOOT_LOGGER);
    s_cfg.log_level           = prefs.getInt (K_LOG_LEVEL,    MN_DEFAULT_PREF_LOG_LEVEL);

    prefs.end();

    enforce_compile_gates(s_cfg);
    push_to_modules(s_cfg);

    sys_log(LOG_INFO, "Prefs", "Loaded — %s", s_cfg.module_id.c_str());
}

void preferences_save(const PrefsConfig &cfg) {
    s_cfg = cfg;

    enforce_compile_gates(s_cfg);

    prefs.begin("config", false);
    prefs.putString(K_MODULE_ID,      s_cfg.module_id);
    prefs.putString(K_MQTT_SERVER,    s_cfg.mqtt_server);
    prefs.putInt   (K_MQTT_PORT,      s_cfg.mqtt_port);
    prefs.putInt   (K_MQTT_INTERVAL,  s_cfg.mqtt_interval);
    prefs.putBool  (K_MQTT_SCRAMBLED, s_cfg.mqtt_scrambled);
    prefs.putBool  (K_WIFI,           s_cfg.wifi_enabled);
    prefs.putString(K_WIFI_TX_POWER,  tx_power_to_str(s_cfg.wifi_tx_power));
    prefs.putBool  (K_MQTT_ENABLED,   s_cfg.mqtt_enabled);
    prefs.putBool  (K_WEBSERVER,      s_cfg.webserver_enabled);
    prefs.putBool  (K_MDNS,           s_cfg.mdns_enabled);
    prefs.putBool  (K_UDP,            s_cfg.udp_enabled);
    prefs.putBool  (K_LOCALIZER,      s_cfg.localizer_enabled);
    prefs.putBool  (K_SLEEP,          s_cfg.sleep_enabled);
    prefs.putBool  (K_TIME,           s_cfg.time_enabled);
    prefs.putBool  (K_BOOT_LOGGER,    s_cfg.boot_logger_enabled);
    prefs.putInt   (K_LOG_LEVEL,      s_cfg.log_level);
    prefs.end();

    push_to_modules(s_cfg);

    sys_log(LOG_INFO, "Prefs", "Saved");
}

bool preferences_from_text(const char *payload) {
    if (!payload || strlen(payload) == 0) return false;

    static char buf[512];
    strncpy(buf, payload, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    PrefsConfig cfg = s_cfg;  // start from current — only update what arrives

    char sep[2] = {FIELD_SEP, '\0'};
    char *outer_ctx = nullptr;

    char *line = strtok_r(buf, "\n", &outer_ctx);
    while (line) {
        size_t len = strlen(line);
        if (len > 0 && line[len - 1] == '\r') line[--len] = '\0';

        char *inner_ctx = nullptr;
        char *key = strtok_r(line, sep, &inner_ctx);
        char *val = strtok_r(nullptr, sep, &inner_ctx);

        if (key && val) {
            if      (strcmp(key, "moduleId")      == 0) cfg.module_id         = val;
            else if (strcmp(key, "mqttServer")    == 0) cfg.mqtt_server       = val;
            else if (strcmp(key, "mqttPort")      == 0) cfg.mqtt_port         = atoi(val);
            else if (strcmp(key, "mqttInterval")  == 0) cfg.mqtt_interval     = atoi(val);
            else if (strcmp(key, "mqttScrambled") == 0) cfg.mqtt_scrambled    = atoi(val) != 0;
            else if (strcmp(key, "wifi")          == 0) cfg.wifi_enabled      = atoi(val) != 0;
            else if (strcmp(key, "txpwr")         == 0) cfg.wifi_tx_power     = tx_power_from_str(val);
            else if (strcmp(key, "mqtt")          == 0) cfg.mqtt_enabled      = atoi(val) != 0;
            else if (strcmp(key, "dash")          == 0) cfg.webserver_enabled = atoi(val) != 0;
            else if (strcmp(key, "mdns")          == 0) cfg.mdns_enabled      = atoi(val) != 0;
            else if (strcmp(key, "udp")           == 0) cfg.udp_enabled       = atoi(val) != 0;
            else if (strcmp(key, "locl")          == 0) cfg.localizer_enabled = atoi(val) != 0;
            else if (strcmp(key, "slep")          == 0) cfg.sleep_enabled     = atoi(val) != 0;
            else if (strcmp(key, "time")          == 0) cfg.time_enabled      = atoi(val) != 0;
            else if (strcmp(key, "blog")          == 0) cfg.boot_logger_enabled = atoi(val) != 0;
            else if (strcmp(key, "log")           == 0) cfg.log_level         = log_level_from_str(val);
        }

        line = strtok_r(nullptr, "\n", &outer_ctx);
    }

    if (cfg.module_id.length() == 0) return false;

    preferences_save(cfg);
    return true;
}

const char *preferences_to_text() {
    static char buf[512];

    snprintf(buf, sizeof(buf),
             "moduleId%c%s\n"
             "mqttServer%c%s\n"
             "mqttPort%c%d\n"
             "mqttInterval%c%d\n"
             "mqttScrambled%c%d\n"
             "wifi%c%d\n"
             "txpwr%c%s\n"
             "mqtt%c%d\n"
             "dash%c%d\n"
             "mdns%c%d\n"
             "udp%c%d\n"
             "locl%c%d\n"
             "slep%c%d\n"
             "time%c%d\n"
             "blog%c%d\n"
             "log%c%s\n",
             FIELD_SEP, s_cfg.module_id.c_str(),
             FIELD_SEP, s_cfg.mqtt_server.c_str(),
             FIELD_SEP, s_cfg.mqtt_port,
             FIELD_SEP, s_cfg.mqtt_interval,
             FIELD_SEP, s_cfg.mqtt_scrambled      ? 1 : 0,
             FIELD_SEP, s_cfg.wifi_enabled        ? 1 : 0,
             FIELD_SEP, tx_power_to_str(s_cfg.wifi_tx_power),
             FIELD_SEP, s_cfg.mqtt_enabled        ? 1 : 0,
             FIELD_SEP, s_cfg.webserver_enabled   ? 1 : 0,
             FIELD_SEP, s_cfg.mdns_enabled        ? 1 : 0,
             FIELD_SEP, s_cfg.udp_enabled         ? 1 : 0,
             FIELD_SEP, s_cfg.localizer_enabled   ? 1 : 0,
             FIELD_SEP, s_cfg.sleep_enabled       ? 1 : 0,
             FIELD_SEP, s_cfg.time_enabled        ? 1 : 0,
             FIELD_SEP, s_cfg.boot_logger_enabled ? 1 : 0,
             FIELD_SEP, log_level_to_str(s_cfg.log_level));

    return buf;
}

const PrefsConfig &preferences_get() {
    return s_cfg;
}