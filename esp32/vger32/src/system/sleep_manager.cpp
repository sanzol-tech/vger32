/*
 * sleep_manager.cpp
 *
 * Responsibility: Controls deep sleep cycles to reduce power consumption.
 *
 * Flow:
 *   1. Device stays awake for SLEEP_ACTIVE_MS.
 *   2. A grace period of SLEEP_GRACE_MS begins — tasks continue running normally.
 *      The grace period exists to let pending work finish (MQTT publish, HTTP flush).
 *   3. At the end of grace, hard blockers are checked (dashboard activity, WiFi scan).
 *      If any blocker is active, the sleep cycle is cancelled and the timer resets.
 *   4. If no blockers, the device enters deep sleep for SLEEP_DURATION_MS.
 *      On wakeup the chip resets and setup() runs from scratch.
 */

#include <WiFi.h>

#include "sleep_manager.h"

#include "logger/sys_logger.h"
#include "webserver/dashboard_server.h"

// ==========================================
// PRIVATE STATE
// ==========================================
static uint32_t wake_time_ms = 0;
static uint32_t grace_start_ms = 0;
static bool in_grace = false;

// ==========================================
// PRIVATE
// ==========================================
static bool is_hard_blocked() {
    if (WiFi.scanComplete() == WIFI_SCAN_RUNNING) return true;
    if (dashboard_last_activity_ms() < SLEEP_DASHBOARD_IDLE_MS) return true;
    return false;
}

// ==========================================
// PUBLIC API
// ==========================================
void sleep_manager_set_enabled(bool enabled) { sleep_enabled = enabled; }

void sleep_manager_init() {
    if (!sleep_enabled) {
        sys_log(LOG_INFO, "Sleep", "Disabled by capability");
        return;
    }
    wake_time_ms = millis();
    in_grace = false;
    sys_log(LOG_INFO, "Sleep", "Initialized");
}

void sleep_manager_handle() {
    if (!sleep_enabled) return;

    uint32_t now = millis();

    if (!in_grace) {
        if (now - wake_time_ms < SLEEP_ACTIVE_MS) return;
        in_grace = true;
        grace_start_ms = now;
        sys_log(LOG_INFO, "Sleep", "Grace period started (%lu s)...", SLEEP_GRACE_MS / 1000);
        return;
    }

    if (now - grace_start_ms < SLEEP_GRACE_MS) return;

    if (is_hard_blocked()) {
        sys_log(LOG_INFO, "Sleep", "Cancelled — activity detected, resetting timer");
        wake_time_ms = now;
        in_grace = false;
        return;
    }

    sys_log(LOG_INFO, "Sleep", "Deep sleeping for %lu s", SLEEP_DURATION_MS / 1000);
    Serial.flush();

    esp_sleep_enable_timer_wakeup((uint64_t) SLEEP_DURATION_MS * 1000ULL);
    esp_deep_sleep_start(); // noreturn — setup() runs on wakeup
}
