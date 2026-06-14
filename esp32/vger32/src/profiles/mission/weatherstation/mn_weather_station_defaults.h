/*
 * mn_weather_station_defaults.h
 *
 * Mission-specific preference defaults for WEATHER_STATION.
 * Only values that differ from the global fallbacks in prefs_defaults.h.
 *
 * Weather stations publish telemetry via MQTT from a fixed location —
 * no localizer needed. Reduced TX power: mains-powered but indoor range
 * is sufficient at 17dBm.
 */

#ifndef MN_WEATHER_STATION_DEFAULTS_H
#define MN_WEATHER_STATION_DEFAULTS_H

#include "config/wifi_types.h"

#define MN_DEFAULT_PREF_LOCALIZER       false
#define MN_DEFAULT_PREF_WIFI_TX_POWER   WIFI_POWER_15dBm

#endif