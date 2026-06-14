/*
* prefs_defaults.h
 *
 * Global fallback defaults for all MN_DEFAULT_PREF_* constants.
 * Included by active_profile.h after the active mission's _defaults.h,
 * so any value the mission did not define gets a safe default here.
 *
 * To override a value for a specific mission: define MN_DEFAULT_PREF_<KEY>
 * in that mission's mn_*_defaults.h before this file is included.
 */

#ifndef PREFS_DEFAULTS_H
#define PREFS_DEFAULTS_H

#include "logger/sys_logger.h"

// MQTT connection
#ifndef MN_DEFAULT_PREF_MQTT_SERVER
#define MN_DEFAULT_PREF_MQTT_SERVER     "broker.hivemq.com"
#endif
#ifndef MN_DEFAULT_PREF_MQTT_PORT
#define MN_DEFAULT_PREF_MQTT_PORT       1883
#endif
#ifndef MN_DEFAULT_PREF_MQTT_INTERVAL
#define MN_DEFAULT_PREF_MQTT_INTERVAL   120
#endif
#ifndef MN_DEFAULT_PREF_MQTT_SCRAMBLED
#define MN_DEFAULT_PREF_MQTT_SCRAMBLED  true
#endif

// WiFi
#ifndef MN_DEFAULT_PREF_WIFI_ENABLED
#define MN_DEFAULT_PREF_WIFI_ENABLED    true
#endif
#ifndef MN_DEFAULT_PREF_WIFI_TX_POWER
#define MN_DEFAULT_PREF_WIFI_TX_POWER   WIFI_POWER_15dBm
#endif

// Feature flags
#ifndef MN_DEFAULT_PREF_MQTT_ENABLED
#define MN_DEFAULT_PREF_MQTT_ENABLED    true
#endif
#ifndef MN_DEFAULT_PREF_WEBSERVER
#define MN_DEFAULT_PREF_WEBSERVER       true
#endif
#ifndef MN_DEFAULT_PREF_MDNS
#define MN_DEFAULT_PREF_MDNS            true
#endif
#ifndef MN_DEFAULT_PREF_UDP
#define MN_DEFAULT_PREF_UDP             true
#endif
#ifndef MN_DEFAULT_PREF_LOCALIZER
#define MN_DEFAULT_PREF_LOCALIZER       true
#endif
#ifndef MN_DEFAULT_PREF_SLEEP
#define MN_DEFAULT_PREF_SLEEP           false
#endif
#ifndef MN_DEFAULT_PREF_TIME
#define MN_DEFAULT_PREF_TIME            true
#endif
#ifndef MN_DEFAULT_PREF_BOOT_LOGGER
#define MN_DEFAULT_PREF_BOOT_LOGGER     true
#endif
#ifndef MN_DEFAULT_PREF_LOG_LEVEL
#define MN_DEFAULT_PREF_LOG_LEVEL       LOG_WARN
#endif

#endif