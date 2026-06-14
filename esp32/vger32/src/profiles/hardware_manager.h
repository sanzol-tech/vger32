/*
* hardware_manager.h
 *
 * Single include point for the active hardware profile.
 * Include from active_profile.h only — never include hw_*.h files directly.
 * Produces a compile error if no HARDWARE_* flag is defined.
 */

#ifndef HARDWARE_MANAGER_H
#define HARDWARE_MANAGER_H

#if defined(HARDWARE_WAYPOINT_V1)
#include "hardware/hw_waypoint_alert_v1.h"
#elif defined(HARDWARE_WEATHER_V1)
#include "hardware/hw_weather_v1.h"
#elif defined(HARDWARE_FULL_V1)
#include "hardware/hw_full_v1.h"
#elif defined(HARDWARE_NOTIFIER_V1)
#include "hardware/hw_notifier_v1.h"
#else
#error "No HARDWARE_* defined. Set one in active_profile.h."
#endif

#endif