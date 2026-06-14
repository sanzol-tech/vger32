/*
 * cmd_dispatcher.cpp
 *
 * Responsibility: Parses incoming commands from any source, executes built-in
 *                 infrastructure commands, and forwards every command to the
 *                 mission observer.
 *
 * Built-in commands (infrastructure only):
 *   reboot     — restarts the device immediately
 *   ping       — publishes a pong with the device identity
 *   force_ap   — persists AP flag to NVS and reboots into AP mode
 *   sleep      — enters deep sleep for <seconds> seconds (one-shot)
 *
 * All other commands — including msg and publish_now — are forwarded
 * to the mission observer. Each mission decides what to do with them.
 *
 * Observer contract: called after every command — built-in or unknown —
 * with the original topic and payload. If no observer is registered,
 * unknown commands are logged as warnings.
 */

#include <Arduino.h>

#include "cmd_dispatcher.h"

#include "logger/sys_logger.h"
#include "network/wifi_manager.h"
#include "system/system_info.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static cmd_callback_t mission_observer = nullptr;

// ==========================================
// HELPERS
// ==========================================

static const char *last_segment(const char *topic) {
    const char *p = strrchr(topic, '/');
    return p ? p + 1 : topic;
}

static void notify_observer(const char *topic, const char *payload) {
    if (mission_observer) mission_observer(topic, payload);
}

// ==========================================
// BUILT-IN COMMANDS — infrastructure only
// ==========================================

static bool cmd_reboot(const char *args) {
    sys_log(LOG_INFO, "CMD", "Rebooting...");
    Serial.flush();
    delay(100);
    ESP.restart();
    return true;
}

static bool cmd_ping(const char *args) {
    mqtt_publish("pong", get_identity());
    return true;
}

static bool cmd_force_ap(const char *args) {
    wifi_manager_force_ap();
    return true;
}

static bool cmd_sleep(const char *args) {
    if (!args || args[0] == '\0') {
        sys_log(LOG_WARN, "CMD", "sleep — missing duration");
        return false;
    }
    int seconds = atoi(args);
    if (seconds <= 0) {
        sys_log(LOG_WARN, "CMD", "sleep — invalid duration: '%s'", args);
        return false;
    }
    sys_log(LOG_INFO, "CMD", "sleep — entering deep sleep for %d s", seconds);
    Serial.flush();
    esp_sleep_enable_timer_wakeup((uint64_t) seconds * 1000000ULL);
    esp_deep_sleep_start();
    return true;
}

// ==========================================
// PUBLIC API
// ==========================================

void cmd_dispatcher_init(cmd_callback_t observer) {
    mission_observer = observer;
    sys_log(LOG_INFO, "CMD", "Dispatcher initialized");
}

void cmd_dispatcher_dispatch(const char *topic, const char *payload) {
    if (!topic) return;

    const char *cmd  = last_segment(topic);
    const char *args = (payload && payload[0] != '\0') ? payload : "";

    sys_log(LOG_INFO, "CMD", "'%s' args='%s'", cmd, args);

    if (strcmp(cmd, "reboot") == 0) {
        cmd_reboot(args);
        notify_observer(topic, payload);
        return;
    }
    if (strcmp(cmd, "ping") == 0) {
        cmd_ping(args);
        notify_observer(topic, payload);
        return;
    }
    if (strcmp(cmd, "force_ap") == 0) {
        cmd_force_ap(args);
        notify_observer(topic, payload);
        return;
    }
    if (strcmp(cmd, "sleep") == 0) {
        cmd_sleep(args);
        notify_observer(topic, payload);
        return;
    }

    // All other commands go to the mission observer.
    if (mission_observer) {
        mission_observer(topic, payload);
        return;
    }

    sys_log(LOG_WARN, "CMD", "Unknown command: '%s'", cmd);
}