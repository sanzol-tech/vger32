/*
 * mn_notifier_lite.h
 *
 * Mission logic for MISSION_NOTIFIER_LITE on HARDWARE_NOTIFIER_V1.
 * Handles LED state tracking, display events, and command dispatch.
 *
 * Behavior:
 *   Boot        → "vger32" / <module-id> for LCD_SHOW_MS, then backlight off
 *   Waypoint in → waypoint name for LCD_SHOW_MS, then backlight off
 *   Waypoint out→ no action
 *   cmd/msg     → message text for LCD_SHOW_MS, then backlight off
 *   cmd/publish_now → flags immediate MQTT publish
 *
 * Any new event resets the timer. Screen content is not cleared on
 * timeout — only the backlight goes off (avoids flicker on next wake).
 *
 * Call mn_notifier_lite_init() from setup().
 * Call mn_notifier_lite_update() every loop() cycle.
 * mn_notifier_lite_get_waypoint_cb() returns the waypoint_callback_t
 * to pass to localizer_init().
 * mn_notifier_lite_on_cmd() is the mission observer for cmd_dispatcher_init().
 */

#ifndef MN_NOTIFIER_LITE_H
#define MN_NOTIFIER_LITE_H

#include "config/constants.h"
#include "localizer/wifi_localizer.h"

inline constexpr uint32_t LCD_SHOW_MS = 10_seconds;

void mn_notifier_lite_init();

void mn_notifier_lite_update();

void mn_notifier_lite_on_led_strobe();

waypoint_callback_t mn_notifier_lite_get_waypoint_cb();

void mn_notifier_lite_on_cmd(const char *topic, const char *payload);

#endif
