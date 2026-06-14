/*
 * mn_waypoint_alert.h
 *
 * Waypoint alert mission logic.
 * Handles LED state tracking, buzzer/LED actuation, waypoint logging,
 * and command dispatch.
 *
 * Call mn_waypoint_alert_init() from setup().
 * Call mn_waypoint_alert_update() every loop() cycle.
 * mn_waypoint_alert_get_callback() returns the waypoint_callback_t to
 * pass to localizer_init().
 * mn_waypoint_alert_on_cmd() is the mission observer for cmd_dispatcher_init().
 */

#ifndef MN_WAYPOINT_ALERT_H
#define MN_WAYPOINT_ALERT_H

#include "localizer/wifi_localizer.h"

void mn_waypoint_alert_init();

void mn_waypoint_alert_update();

void mn_waypoint_alert_on_led_strobe();

waypoint_callback_t mn_waypoint_alert_get_callback();

void mn_waypoint_alert_on_cmd(const char *topic, const char *payload);

#endif
