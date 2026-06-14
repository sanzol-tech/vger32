/*
 * active_profile.h
 *
 * The only file to edit when changing what the firmware compiles for.
 * Uncomment exactly one MISSION_* and exactly one HARDWARE_*.
 *
 * Available missions:
 *   MISSION_WAYPOINT_ALERT   — audible alert when entering a known WiFi waypoint
 *   MISSION_WEATHER_STATION  — publishes sensor data over MQTT
 *   MISSION_FULL             — all subsystems active, demo mode
 *   MISSION_NOTIFIER_LITE    — waypoint + MQTT display on C6-LCD-1.47
 *
 * Available hardware profiles (see profiles/hardware/):
 *   HARDWARE_WAYPOINT_V1     — ESP32-DevKitC + KY-012 buzzer
 *   HARDWARE_WEATHER_V1      — ESP32-DevKitC + SHT31 + BMP280
 *   HARDWARE_FULL_V1         — no real hardware, simulated sensors
 *   HARDWARE_NOTIFIER_V1       — Waveshare ESP32-C6-LCD-1.47
 */

#ifndef ACTIVE_PROFILE_H
#define ACTIVE_PROFILE_H

// ==========================================
// ACTIVE MISSION — uncomment exactly one
// ==========================================
// #define MISSION_WAYPOINT_ALERT
// #define MISSION_WEATHER_STATION
#define MISSION_FULL
// #define MISSION_NOTIFIER_LITE

// ==========================================
// ACTIVE HARDWARE — uncomment exactly one
// ==========================================
// #define HARDWARE_WAYPOINT_V1
// #define HARDWARE_WEATHER_V1
#define HARDWARE_FULL_V1
// #define HARDWARE_NOTIFIER_V1

// ==========================================
// AUTO-RESOLVE — hardware profile
// ==========================================
#include "profiles/hardware_manager.h"

// ==========================================
// AUTO-RESOLVE — mission preference defaults
// Each mn_*_defaults.h defines only the MN_DEFAULT_PREF_* values that
// differ from the global fallbacks. MISSION_FULL uses all fallbacks.
// prefs_defaults.h closes any value the mission did not define.
// ==========================================
#if defined(MISSION_WAYPOINT_ALERT)
#include "mission/waypointalert/mn_waypoint_alert_defaults.h"
#elif defined(MISSION_WEATHER_STATION)
#include "mission/weatherstation/mn_weather_station_defaults.h"
#elif defined(MISSION_NOTIFIER_LITE)
#include "mission/notifierlite/mn_notifier_lite_defaults.h"
#elif defined(MISSION_FULL)
// No overrides — all defaults come from prefs_defaults.h below.
#else
#error "No MISSION_* defined. Set one above."
#endif

#include "config/prefs_defaults.h"

#endif