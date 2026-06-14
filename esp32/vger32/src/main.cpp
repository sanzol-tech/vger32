/*
 * main.cpp
 *
 * Responsibility: Entry point. Initializes all infrastructure subsystems
 *                 and runs the main loop. Each subsystem self-guards via
 *                 its own _enabled flag or capability check — the main
 *                 calls everything unconditionally.
 *
 * Infrastructure (every mission):
 *   WiFi, time, sensors, dashboard, MQTT, mDNS + UDP discovery,
 *   sleep, boot_logger.
 *
 * Mission-specific (in mn_*_main only):
 *   cmd_dispatcher_init, localizer_init, hardware registration,
 *   actuator updates (digital_out_update, rgb_out_update),
 *   publish logic (mqtt_publish_sensors_if_due).
 */

#include <Arduino.h>
#include <LittleFS.h>

#include "config/keys.h"
#include "logger/sys_logger.h"
#include "config/preferences.h"
#include "discovery/mdns_discovery.h"
#include "discovery/udp_discovery.h"
#include "localizer/wifi_localizer.h"
#include "mqtt/cmd_dispatcher.h"
#include "mqtt/mqtt_client.h"
#include "network/time_manager.h"
#include "network/wifi_manager.h"
#include "profiles/mission_manager.h"
#include "logger/boot_logger.h"
#include "system/sleep_manager.h"
#include "webserver/dashboard_server.h"

// ==========================================
// CONSTANTS
// ==========================================

static constexpr uint32_t SERIAL_BAUD_RATE = 115200;
static constexpr uint32_t STARTUP_DELAY_MS = 500;

// ==========================================
// SETUP
// ==========================================

void setup() {
    Serial.begin(SERIAL_BAUD_RATE);

#if ARDUINO_USB_CDC_ON_BOOT
    // C6 (and other USB-CDC chips): wait up to 5 s for the USB host to open
    // the CDC port. After timeout the device boots normally — Serial output
    // is lost but the system runs fine (no monitor connected).
    {
        unsigned long t = millis();
        while (!Serial && millis() - t < 5000) vTaskDelay(pdMS_TO_TICKS(10));
    }
#endif

    delay(STARTUP_DELAY_MS);

    if (!LittleFS.begin(true)) {
        sys_log(LOG_ERROR, "Main", "LittleFS mount failed");
    }

    preferences_load();
    keys_load();
    boot_logger_init();

    wifi_manager_init();
    time_init();

    dashboard_init();

    mission_manager_setup();

    mqtt_init(cmd_dispatcher_dispatch);

    sleep_manager_init();

    sys_log(LOG_INFO, "Main", "Boot complete — mission: " PROFILE_NAME "\n");
}

// ==========================================
// LOOP
// ==========================================

void loop() {
    boot_logger_log();

    wifi_manager_handle();
    time_handle();

    dashboard_handle();
    mqtt_handle();
    localizer_handle();

    mdns_discovery_handle();
    udp_discovery_handle();

    sleep_manager_handle();

    mission_manager_loop();

    vTaskDelay(pdMS_TO_TICKS(15));
}