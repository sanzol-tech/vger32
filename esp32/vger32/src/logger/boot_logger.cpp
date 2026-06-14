/*
 * boot_logger.cpp
 *
 * Thin wrapper over log_store for boot events.
 * Payload is the reset reason string (POWERON, RESET, PANIC, WDT, OTHER).
 * Deep sleep wake-ups are silently ignored.
 * Timestamp is corrected to reflect actual boot time, not NTP sync time.
 */

#include "logger/boot_logger.h"

#include <esp_system.h>
#include <LittleFS.h>

#include "logger/sys_logger.h"
#include "logger/log_store.h"
#include "network/time_manager.h"

static constexpr char    LOG_FILE[]  = "/reboots.log";
static constexpr uint8_t MAX_ENTRIES = 10;
static constexpr uint8_t ENTRY_LEN   = 24;  // ts(10) + sp(1) + reason(≤7) + null + margin

static char    entries[MAX_ENTRIES][ENTRY_LEN];
static uint8_t cursor      = 0;
static uint8_t count       = 0;
static char    history_buf[MAX_ENTRIES * (ENTRY_LEN + 1) + 1];
static uint32_t boot_millis = 0;
static bool    boot_logged  = false;

static const char *reset_reason_str(esp_reset_reason_t reason) {
    switch (reason) {
        case ESP_RST_POWERON:  return "POWERON";
        case ESP_RST_SW:       return "RESET";
        case ESP_RST_PANIC:    return "PANIC";
        case ESP_RST_INT_WDT:
        case ESP_RST_TASK_WDT:
        case ESP_RST_WDT:      return "WDT";
        default:               return "OTHER";
    }
}

void boot_logger_set_enabled(bool enabled) { boot_logger_enabled = enabled; }

void boot_logger_init() {
    if (!boot_logger_enabled) return;
    memset(entries, 0, sizeof(entries));
    cursor      = 0;
    boot_logged = false;
    boot_millis = millis();
    count = log_store_load(LOG_FILE, (char*)entries, MAX_ENTRIES, ENTRY_LEN, &cursor);
}

void boot_logger_log() {
    if (!boot_logger_enabled) return;
    if (boot_logged) return;
    if (!time_is_synced()) return;

    esp_reset_reason_t reason = esp_reset_reason();
    if (reason == ESP_RST_DEEPSLEEP) {
        sys_log(LOG_INFO, "BootLog", "Deep sleep wake-up — skipping");
        boot_logged = true;
        return;
    }

    uint32_t now            = time_get_timestamp_fallback();
    uint32_t uptime_seconds = (millis() - boot_millis) / 1000;
    uint32_t boot_ts        = now - uptime_seconds;

    log_store_push(LOG_FILE, (char*)entries, MAX_ENTRIES, ENTRY_LEN,
                   &cursor, &count, boot_ts, reset_reason_str(reason), true);

    boot_logged = true;
}

const char *boot_logger_get_history() {
    return log_store_get_history((const char*)entries, MAX_ENTRIES, ENTRY_LEN,
                                 cursor, count,
                                 history_buf, sizeof(history_buf));
}
