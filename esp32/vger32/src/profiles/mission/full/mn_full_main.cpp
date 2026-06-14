/*
 * mn_full_main.cpp
 *
 * Setup and loop for the full test mission.
 * Infrastructure (WiFi, MQTT, dashboard, sensors, discovery, sleep) is in main.cpp.
 */

#include "profiles/active_profile.h"
#ifdef MISSION_FULL

#include <Arduino.h>

#include "mn_full_main.h"

#include "drivers/actuators/board_led.h"
#include "drivers/actuators/digital_out.h"
#include "drivers/actuators/rgb_out.h"
#include "drivers/sensors/core/sensor_catalog.h"
#include "drivers/sensors/core/sensor_reader.h"
#include "mn_full.h"
#include "mqtt/cmd_dispatcher.h"
#include "profiles/led_manager.h"

void mn_full_setup() {
    sensor_catalog_init();
    sensor_reader_init();

    board_led_register(BOARD_LED_PIN, BOARD_LED_RGB, BOARD_LED_ACTIVE_HIGH);
    led_manager_init();
    led_manager_set_state(LED_STATE_FLASHING_YELLOW);

    cmd_dispatcher_init(mn_full_on_cmd);
    mn_full_init();
}

void mn_full_loop() {
    sensor_reader_update();
    mn_full_update();
    digital_out_update();
    rgb_out_update();
}

#endif
