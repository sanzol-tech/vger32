/*
 * mn_notifier_lite_defaults.h
 *
 * Mission-specific preference defaults for NOTIFIER_LITE.
 * Only values that differ from the global fallbacks in prefs_defaults.h.
 *
 * Indoor fixed device — localizer is core, short WiFi range is sufficient.
 * Sleep disabled: display events require the device always awake.
 */

#ifndef MN_NOTIFIER_LITE_DEFAULTS_H
#define MN_NOTIFIER_LITE_DEFAULTS_H

#define MN_DEFAULT_PREF_LOCALIZER       true
#define MN_DEFAULT_PREF_WIFI_TX_POWER   WIFI_POWER_8_5dBm

#endif