/*
 * mn_notifier_lite_main.cpp
 *
 * Setup and loop for the notifier lite mission.
 * Infrastructure (WiFi, MQTT, dashboard, discovery, sleep) is in main.cpp.
 */

#include "profiles/active_profile.h"
#ifdef MISSION_NOTIFIER_LITE

#include <Arduino.h>

#include "mn_notifier_lite_main.h"

#include "drivers/actuators/board_led.h"
#include "drivers/actuators/rgb_out.h"
#include "localizer/wifi_localizer.h"
#include "mn_notifier_lite.h"
#include "mqtt/cmd_dispatcher.h"
#include "profiles/led_manager.h"

void mn_notifier_lite_setup() {
    board_led_register(BOARD_LED_PIN, BOARD_LED_RGB, BOARD_LED_ACTIVE_HIGH);
    led_manager_init();
    led_manager_set_state(LED_STATE_FLASHING_YELLOW);

    cmd_dispatcher_init(mn_notifier_lite_on_cmd);
    mn_notifier_lite_init();
    localizer_init(mn_notifier_lite_get_waypoint_cb());
}

void mn_notifier_lite_loop() {
    mn_notifier_lite_update();
    rgb_out_update();
}

#endif // MISSION_NOTIFIER_LITE
