/*
 * mn_waypoint_alert_main.cpp
 *
 * Setup and loop for the waypoint alert mission.
 * Infrastructure (WiFi, MQTT, dashboard, sensors, discovery, sleep) is in main.cpp.
 */

#include "profiles/active_profile.h"
#ifdef MISSION_WAYPOINT_ALERT

#include <Arduino.h>

#include "mn_waypoint_alert_main.h"

#include "drivers/actuators/board_led.h"
#include "drivers/actuators/digital_out.h"
#include "drivers/actuators/rgb_out.h"
#include "localizer/wifi_localizer.h"
#include "mn_waypoint_alert.h"
#include "mqtt/cmd_dispatcher.h"
#include "profiles/led_manager.h"

void mn_waypoint_alert_setup() {
    board_led_register(BOARD_LED_PIN, BOARD_LED_RGB, BOARD_LED_ACTIVE_HIGH);
    led_manager_init();
    led_manager_set_state(LED_STATE_FLASHING_YELLOW);

    cmd_dispatcher_init(mn_waypoint_alert_on_cmd);
    mn_waypoint_alert_init();
    localizer_init(mn_waypoint_alert_get_callback());
}

void mn_waypoint_alert_loop() {
    mn_waypoint_alert_update();
    digital_out_update();
    rgb_out_update();
}

#endif
