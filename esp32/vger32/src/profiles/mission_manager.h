/*
 * mission_manager.h
 *
 * Single compile-time entry point for the active mission profile.
 * main.cpp only includes this file — never individual mission files directly.
 *
 * To add a new mission:
 *   1. Create mission/mn_<n>.h/.cpp and mn_<n>_main.h/.cpp
 *   2. Add MISSION_<n> to active_profile.h
 *   3. Add the matching #ifdef block in mission_manager.cpp
 *   4. Create a matching HARDWARE_* profile in hardware/
 */

#ifndef MISSION_MANAGER_H
#define MISSION_MANAGER_H

#include "active_profile.h"

// ==========================================
// PROFILE NAME (used by dashboard and logs)
// ==========================================
#if defined(MISSION_WAYPOINT_ALERT)
#define PROFILE_NAME "waypoint_alert"
#elif defined(MISSION_WEATHER_STATION)
#define PROFILE_NAME "weather_station"
#elif defined(MISSION_FULL)
#define PROFILE_NAME "full"
#elif defined(MISSION_NOTIFIER_LITE)
#define PROFILE_NAME "notifier_lite"
#else
#error "No MISSION_* defined. Set one in active_profile.h."
#endif

void mission_manager_setup();

void mission_manager_loop();

#endif // MISSION_MANAGER_H
