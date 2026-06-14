/*
 * preferences.h
 *
 * Persistent device configuration. Merges what was previously split between
 * preferences (identity, MQTT) and capabilities (feature flags).
 * All fields are backed by NVS namespace "config".
 *
 * First-boot defaults come from the active mission's mn_*_defaults.h,
 * via the global fallbacks in prefs_defaults.h. Both are resolved at
 * compile time through active_profile.h.
 *
 * Layer model:
 *   active_profile.h  →  mn_*_defaults.h  →  prefs_defaults.h
 *          ↓
 *   [MN_DEFAULT_PREF_* constants available at compile time]
 *          ↓
 *   preferences_load()  →  NVS (first-boot default = MN_DEFAULT_PREF_*)
 *          ↓
 *   push_to_modules()   →  each module receives its _set_enabled() call
 *
 * Compile-time gates enforced at load/save:
 *   log_level     cannot exceed LOG_LEVEL (build flag ceiling)
 *   wifi_tx_power clamped by board quirks and HW_WIFI_TX_POWER inside
 *                 wifi_set_tx_power() — not here
 *
 * Text exchange format (used by /api/preferences), one field per line:
 *   KEY<0x1F>VALUE\n
 *
 * Call preferences_load() once from setup(), before any module init.
 */

#ifndef PREFERENCES_H
#define PREFERENCES_H

#include <Arduino.h>
#include <WiFi.h>

#include "profiles/active_profile.h"

// Sentinel: no application-level TX power cap.
// Board quirks and HW_WIFI_TX_POWER ceilings still apply inside wifi_set_tx_power().
// This is the only place in the codebase where WIFI_POWER_19_5dBm appears.
static constexpr wifi_power_t WIFI_TX_POWER_FULL = WIFI_POWER_19_5dBm;

// ==========================================
// CONFIG STRUCT
// ==========================================

struct PrefsConfig {
    // Identity
    String       module_id;

    // MQTT connection
    String       mqtt_server         = MN_DEFAULT_PREF_MQTT_SERVER;
    int          mqtt_port           = MN_DEFAULT_PREF_MQTT_PORT;
    int          mqtt_interval       = MN_DEFAULT_PREF_MQTT_INTERVAL;
    bool         mqtt_scrambled      = MN_DEFAULT_PREF_MQTT_SCRAMBLED;

    // WiFi
    bool         wifi_enabled        = MN_DEFAULT_PREF_WIFI_ENABLED;
    wifi_power_t wifi_tx_power       = MN_DEFAULT_PREF_WIFI_TX_POWER;

    // Feature flags
    bool         mqtt_enabled        = MN_DEFAULT_PREF_MQTT_ENABLED;
    bool         webserver_enabled   = MN_DEFAULT_PREF_WEBSERVER;
    bool         mdns_enabled        = MN_DEFAULT_PREF_MDNS;
    bool         udp_enabled         = MN_DEFAULT_PREF_UDP;
    bool         localizer_enabled   = MN_DEFAULT_PREF_LOCALIZER;
    bool         sleep_enabled       = MN_DEFAULT_PREF_SLEEP;
    bool         time_enabled        = MN_DEFAULT_PREF_TIME;
    bool         boot_logger_enabled = MN_DEFAULT_PREF_BOOT_LOGGER;
    int          log_level           = MN_DEFAULT_PREF_LOG_LEVEL;
};

// ==========================================
// PUBLIC API
// ==========================================

// Loads all fields from NVS into the internal config, applies compile-time
// gates, and pushes flags to each module via its set_enabled() call.
// Call once from setup(), before any module init.
void preferences_load();

// Persists cfg to NVS, applies compile-time gates, and pushes to modules.
void preferences_save(const PrefsConfig &cfg);

// Parses a text payload from the dashboard and calls preferences_save().
// Returns false if the payload is empty or module_id is missing.
bool preferences_from_text(const char *payload);

// Serializes the current config for the dashboard.
// Returns a pointer to an internal static buffer — valid until next call.
const char *preferences_to_text();

// Read-only access to the current config.
// Returns a reference to the internal static instance.
const PrefsConfig &preferences_get();

// ==========================================
// GLOBALS — direct field access for modules
// that reference cfg_* by name
// ==========================================

extern String &cfg_module_id;
extern String &cfg_mqtt_server;
extern int    &cfg_mqtt_port;
extern int    &cfg_mqtt_interval;
extern bool   &cfg_mqtt_scrambled;

#endif