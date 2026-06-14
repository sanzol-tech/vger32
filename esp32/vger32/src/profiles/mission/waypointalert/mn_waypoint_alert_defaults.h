/*
 * mn_waypoint_alert_defaults.h
 *
 * Mission-specific preference defaults for WAYPOINT_ALERT.
 * Only values that differ from the global fallbacks in prefs_defaults.h.
 *
 * Waypoint alert is mobile and battery-powered. WiFi is used only for
 * scanning and occasional config — low TX power is sufficient.
 * Localizer is the core of this mission.
 */

#ifndef MN_WAYPOINT_ALERT_DEFAULTS_H
#define MN_WAYPOINT_ALERT_DEFAULTS_H

#define MN_DEFAULT_PREF_LOCALIZER       true
#define MN_DEFAULT_PREF_WIFI_TX_POWER   WIFI_POWER_8_5dBm

#endif