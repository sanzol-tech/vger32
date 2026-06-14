/*
 * mission_manager.cpp
 *
 * Responsibility: Compile-time dispatcher to the active mission profile.
 * Zero runtime overhead — all resolution happens in the preprocessor.
 */

#include <Arduino.h>

#include "mission_manager.h"

#if defined(MISSION_NOTIFIER_LITE)
#include "mission/notifierlite/mn_notifier_lite_main.h"
#define MISSION_SETUP  mn_notifier_lite_setup
#define MISSION_LOOP   mn_notifier_lite_loop

#elif defined(MISSION_WAYPOINT_ALERT)
#include "mission/waypointalert/mn_waypoint_alert_main.h"
#define MISSION_SETUP  mn_waypoint_alert_setup
#define MISSION_LOOP   mn_waypoint_alert_loop

#elif defined(MISSION_WEATHER_STATION)
#include "mission/weatherstation/mn_weather_station_main.h"
#define MISSION_SETUP  mn_weather_station_setup
#define MISSION_LOOP   mn_weather_station_loop

#elif defined(MISSION_FULL)
#include "mission/full/mn_full_main.h"
#define MISSION_SETUP  mn_full_setup
#define MISSION_LOOP   mn_full_loop
#endif

void mission_manager_setup() {
    MISSION_SETUP();
}

void mission_manager_loop() {
    MISSION_LOOP();
}
