/*
 * mn_notifier_lite.cpp
 *
 * Display events share a single show timer. Each new event resets it.
 * The timer runs in mn_notifier_lite_update(), called every loop() cycle.
 * Backlight goes off when the timer expires; screen content is not cleared.
 *
 * Display layout (handled by lcd147_show_text):
 *   Boot     → title (green): "vger32"      payload: <module-id>
 *   Waypoint → title (blue):  "WAYPOINT"    payload: "<name> <score>%"
 *   MSG      → title (blue):  "MESSAGE IN:" payload: message (up to 80 chars)
 *
 * LED state tracks WiFi connection and fires strobe on waypoint/message events.
 */

#include "profiles/active_profile.h"
#ifdef MISSION_NOTIFIER_LITE

#include "mn_notifier_lite.h"

#include <Arduino.h>
#include <string.h>
#include <stdio.h>

#include "logger/sys_logger.h"
#include "config/preferences.h"
#include "drivers/actuators/actuator_lcd147.h"
#include "localizer/wifi_localizer.h"
#include "mqtt/mqtt_client.h"
#include "network/wifi_manager.h"
#include "profiles/led_manager.h"

// Max payload length: 5 lines × 16 chars
static constexpr uint8_t LCD_MSG_MAX = (LCD147_MAX_LINES - 1) * LCD147_MAX_LINE_LEN;

// ==========================================
// PRIVATE STATE
// ==========================================

static uint32_t    show_until_ms  = 0;
static bool        bl_on          = false;
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
// PRIVATE — DISPLAY
// ==========================================

static void show(const char *title, uint16_t title_color, const char *payload) {
    lcd147_show_text(title, title_color, payload);
    if (!bl_on) {
        lcd147_backlight(true);
        bl_on = true;
    }
    show_until_ms = millis() + LCD_SHOW_MS;
}

// ==========================================
// PRIVATE — WAYPOINT CALLBACK
// ==========================================

static void on_waypoint(const char *prev, const char *next) {
    if (next == nullptr) {
        sys_log(LOG_INFO, "Notifier", "Waypoint lost");
        return;
    }
    char payload[LCD147_MAX_LINE_LEN + 1];
    snprintf(payload, sizeof(payload), "%s %u%%", next, localizer_current_score());
    show("WAYPOINT", HW_LCD147_COLOR_TITLE, payload);
    mn_notifier_lite_on_led_strobe();
    sys_log(LOG_INFO, "Notifier", "Waypoint: %s", payload);
}

// ==========================================
// PUBLIC API
// ==========================================

void mn_notifier_lite_init() {
    lcd147_register();
    last_led_state = LED_STATE_OFF;

    char id[LCD147_MAX_LINE_LEN + 1];
    strncpy(id, cfg_module_id.c_str(), LCD147_MAX_LINE_LEN);
    id[LCD147_MAX_LINE_LEN] = '\0';
    show("vger32", HW_LCD147_COLOR_FG, id);

    sys_log(LOG_INFO, "Notifier", "Mission initialized");
}

void mn_notifier_lite_update() {
    update_led();
    led_manager_update();

    if (bl_on && millis() >= show_until_ms) {
        lcd147_backlight(false);
        bl_on = false;
        sys_log(LOG_INFO, "Notifier", "Backlight off");
    }
}

void mn_notifier_lite_on_led_strobe() {
    led_manager_set_state(LED_STATE_STROBE);
}

waypoint_callback_t mn_notifier_lite_get_waypoint_cb() {
    return on_waypoint;
}

void mn_notifier_lite_on_cmd(const char *topic, const char *payload) {
    const char *cmd = strrchr(topic, '/');
    cmd = cmd ? cmd + 1 : topic;

    if (strcmp(cmd, "msg") == 0) {
        if (!payload || payload[0] == '\0') return;
        char buf[LCD_MSG_MAX + 1];
        strncpy(buf, payload, LCD_MSG_MAX);
        buf[LCD_MSG_MAX] = '\0';
        show("MESSAGE IN:", HW_LCD147_COLOR_TITLE, buf);
        mn_notifier_lite_on_led_strobe();
        sys_log(LOG_INFO, "Notifier", "MSG: %s", buf);
        return;
    }

    if (strcmp(cmd, "publish_now") == 0) {
        mqtt_publish_now = true;
        sys_log(LOG_INFO, "Notifier", "publish_now flagged");
        return;
    }

    sys_log(LOG_DEBUG, "Notifier", "cmd ignored — %s", cmd);
}

#endif // MISSION_NOTIFIER_LITE