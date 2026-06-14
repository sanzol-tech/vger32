/*
 * mn_waypoint_alert.cpp
 *
 * Waypoint alert mission logic: LED state tracking, buzzer/LED actuation
 * on waypoint detection, and command handling.
 *
 * The waypoint logger is enabled here — wifi_localizer calls it directly
 * on each detection with name + score.
 *
 * Behavior:
 *   - Entering a waypoint → HW_PATTERN_OK on buzzer and LED + RGB strobe
 *   - Losing location     → no action (device is in transit)
 *   - publish_now         → publishes sensor data + waypoint history
 *   - msg                 → logged to Serial
 */

#include "profiles/active_profile.h"
#ifdef MISSION_WAYPOINT_ALERT

#include "mn_waypoint_alert.h"

#include <string.h>

#include "logger/sys_logger.h"
#include "drivers/actuators/digital_out.h"
#include "localizer/waypoint_logger.h"
#include "mqtt/mqtt_client.h"
#include "network/wifi_manager.h"
#include "profiles/led_manager.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static int         buzzer_id      = -1;
static int         alert_led_id   = -1;
static led_state_t last_led_state = LED_STATE_OFF;

// ==========================================
// PRIVATE — LED TRACKING
// ==========================================

static void update_led() {
    if (led_manager_is_strobing()) return;

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
// PRIVATE — WAYPOINT CALLBACK
// ==========================================

static void on_waypoint_change(const char *prev, const char *next) {
    if (next != nullptr) {
        sys_log(LOG_INFO, "Mission", "Waypoint: %s → %s", prev ? prev : "?", next);
        digital_out_run(buzzer_id, HW_PATTERN_OK, 1);
        digital_out_run(alert_led_id, HW_PATTERN_OK, 1);
        mn_waypoint_alert_on_led_strobe();
    } else {
        sys_log(LOG_INFO, "Mission", "Waypoint lost (was: %s)", prev ? prev : "?");
    }
}

// ==========================================
// PUBLIC API
// ==========================================

void mn_waypoint_alert_init() {
    buzzer_id    = digital_out_register(HW_BUZZER_PIN, "buzzer", HW_BUZZER_ACTIVE_HIGH);
    alert_led_id = digital_out_register(HW_ALERT_LED_PIN, "alert_led", HW_ALERT_LED_ACTIVE_HIGH);
    last_led_state = LED_STATE_OFF;

    waypoint_logger_set_enabled(true);
    waypoint_logger_init();

    sys_log(LOG_INFO, "Mission", "Waypoint alert initialized");
}

void mn_waypoint_alert_update() {
    update_led();
    led_manager_update();
}

void mn_waypoint_alert_on_led_strobe() {
    led_manager_set_state(LED_STATE_STROBE);
}

waypoint_callback_t mn_waypoint_alert_get_callback() {
    return on_waypoint_change;
}

void mn_waypoint_alert_on_cmd(const char *topic, const char *payload) {
    const char *cmd = strrchr(topic, '/');
    cmd = cmd ? cmd + 1 : topic;

    if (strcmp(cmd, "publish_now") == 0) {
        mqtt_publish_now = true;
        mqtt_publish("waypoints/history", waypoint_logger_get_history());
        sys_log(LOG_INFO, "Mission", "publish_now — sensors + waypoint history");
        return;
    }

    if (strcmp(cmd, "msg") == 0) {
        if (!payload || payload[0] == '\0') return;
        sys_log(LOG_INFO, "MSG", "%s", payload);
        return;
    }

    sys_log(LOG_DEBUG, "Mission", "cmd ignored — %s", cmd);
}

#endif