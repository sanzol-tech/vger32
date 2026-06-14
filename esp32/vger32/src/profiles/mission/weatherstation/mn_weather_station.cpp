/*
 * mn_weather_station.cpp
 *
 * Weather station mission logic: LED state tracking, periodic MQTT sensor
 * publish, and command handling.
 */

#include "profiles/active_profile.h"
#ifdef MISSION_WEATHER_STATION

#include "mn_weather_station.h"

#include <string.h>

#include "logger/sys_logger.h"
#include "config/preferences.h"
#include "drivers/sensors/core/sensor_serializer.h"
#include "mqtt/mqtt_client.h"
#include "network/wifi_manager.h"
#include "profiles/led_manager.h"

// ==========================================
// PRIVATE — LED TRACKING
// ==========================================

static led_state_t last_led_state = LED_STATE_OFF;

static void update_led() {
    led_state_t next;

    if (wifi_manager_is_connected())
        next = LED_STATE_STEADY_GREEN;
    else if (wifi_manager_is_ap_active())
        next = LED_STATE_STEADY_BLUE;
    else
        next = LED_STATE_FLASHING_YELLOW;

    if (next != last_led_state) {
        last_led_state = next;
        led_manager_set_state(next);
    }
}

// ==========================================
// PRIVATE — MQTT PUBLISH
// ==========================================

static unsigned long last_sensor_publish = 0;

static void publish_sensors_if_due() {
    if (cfg_mqtt_interval == 0) return;
    unsigned long now = millis();
    if (!mqtt_publish_now &&
        now - last_sensor_publish < (unsigned long) cfg_mqtt_interval * 1000)
        return;
    mqtt_publish_now = false;
    last_sensor_publish = now;
    mqtt_publish("sensors/latest", sensor_latest_text());
}

// ==========================================
// PUBLIC API
// ==========================================

void mn_weather_station_init() {
    last_led_state      = LED_STATE_OFF;
    last_sensor_publish = 0;
    sys_log(LOG_INFO, "Mission", "Weather station initialized");
}

void mn_weather_station_update() {
    update_led();
    led_manager_update();
    publish_sensors_if_due();
}

void mn_weather_station_on_cmd(const char *topic, const char *payload) {
    const char *cmd = strrchr(topic, '/');
    cmd = cmd ? cmd + 1 : topic;

    if (strcmp(cmd, "publish_now") == 0) {
        mqtt_publish_now = true;
        sys_log(LOG_INFO, "WeatherStation", "publish_now flagged");
        return;
    }

    if (strcmp(cmd, "msg") == 0) {
        if (!payload || payload[0] == '\0') return;
        sys_log(LOG_INFO, "MSG", "%s", payload);
        return;
    }

    sys_log(LOG_DEBUG, "WeatherStation", "cmd ignored — %s", cmd);
}

#endif
