/*
 * time_manager.cpp
 *
 * Responsibility: NTP time synchronization.
 *                 Configures NTP servers on first STA connection and keeps
 *                 the system clock synced. Provides a timestamp fallback
 *                 (uptime in seconds) when NTP is not yet available.
 */

#include <time.h>

#include "time_manager.h"

#include "config/constants.h"
#include "logger/sys_logger.h"
#include "network/wifi_manager.h"

// ==========================================
// NTP CONFIGURATION
// ==========================================
static const char *NTP_PRIMARY = "ar.pool.ntp.org";
static const char *NTP_SECONDARY = "south-america.pool.ntp.org";
static const char *NTP_TERTIARY = "pool.ntp.org";

// ==========================================
// TIMING CONSTANTS
// ==========================================
static constexpr unsigned long RETRY_INTERVAL_MS = 5_seconds;
static constexpr unsigned long RESYNC_INTERVAL_MS = 8_hours;
static constexpr uint32_t NTP_TIMEOUT_MS = 3_seconds;

static constexpr time_t EPOCH_2025 = 1735689600; // 2025-01-01 00:00:00 UTC

// ==========================================
// PRIVATE STATE
// ==========================================
static bool ntp_configured = false;
static bool synced = false;
static unsigned long lastAttempt = 0;
static unsigned long lastSuccess = 0;

// ==========================================
// IMPLEMENTATION
// ==========================================

bool time_is_synced() {
    return synced;
}

void time_set_enabled(bool enabled) { time_enabled = enabled; }

void time_init() {
    if (!time_enabled) return;

    if (wifi_manager_is_connected()) {
        configTime(0, 0, NTP_PRIMARY, NTP_SECONDARY, NTP_TERTIARY);
        ntp_configured = true;
        lastAttempt = millis();
        sys_log(LOG_INFO, "NTP", "Initialized (UTC)");
    }
}

void time_handle() {
    if (!time_enabled) return;

    if (!wifi_manager_is_connected()) {
        if (synced) synced = false;
        return;
    }

    unsigned long now = millis();

    if (!ntp_configured) {
        time_init();
        return;
    }

    if (!synced) {
        if (now - lastAttempt < RETRY_INTERVAL_MS) return;
    } else {
        if (now - lastSuccess < RESYNC_INTERVAL_MS) return;
    }

    lastAttempt = now;
    struct tm timeinfo;

    if (getLocalTime(&timeinfo, NTP_TIMEOUT_MS)) {
        if (!synced) {
            sys_log(LOG_INFO, "NTP", "Synced");
            synced = true;
        }
        lastSuccess = now;
    }
}

void time_set(uint32_t unix_ts) {
    if (unix_ts < (uint32_t) EPOCH_2025) {
        sys_log(LOG_WARN, "NTP", "time_set — timestamp too old, ignoring (%lu)",
                 (unsigned long)unix_ts);
        return;
    }

    struct timeval tv = {.tv_sec = (time_t) unix_ts, .tv_usec = 0};
    settimeofday(&tv, nullptr);

    synced = true;
    lastSuccess = millis();

    sys_log(LOG_INFO, "NTP", "Clock set externally: %lu", (unsigned long)unix_ts);
}

uint32_t time_get_timestamp_fallback() {
    time_t t;
    time(&t);

    if (t < EPOCH_2025) {
        return millis() / 1000; // NTP not synced — return uptime in seconds
    }

    return static_cast<uint32_t>(t);
}
