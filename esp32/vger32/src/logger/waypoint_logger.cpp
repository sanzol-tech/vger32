/*
 * waypoint_logger.cpp
 *
 * Thin wrapper over log_store for waypoint detection events.
 * Memory only — entries are not persisted to LittleFS.
 *
 * Entry format: "<timestamp> <name> <score>"
 *   e.g. "1718000000 OFFICE 87"
 */

#include "logger/waypoint_logger.h"

#include "localizer/wifi_localizer.h"
#include "logger/log_store.h"
#include "network/time_manager.h"

static constexpr char    LOG_FILE[]  = "";   // unused — persist=false
static constexpr uint8_t MAX_ENTRIES = 10;
// ts(10) + sp(1) + name(LOCALIZER_WAY_NAME_LEN) + sp(1) + score(3) + null + margin
static constexpr uint8_t ENTRY_LEN   = 10 + 1 + LOCALIZER_WAY_NAME_LEN + 1 + 3 + 1 + 2;

static char    entries[MAX_ENTRIES][ENTRY_LEN];
static uint8_t cursor = 0;
static uint8_t count  = 0;
static char    history_buf[MAX_ENTRIES * (ENTRY_LEN + 1) + 1];

void waypoint_logger_set_enabled(bool enabled) { waypoint_logger_enabled = enabled; }

void waypoint_logger_init() {
    if (!waypoint_logger_enabled) return;
    memset(entries, 0, sizeof(entries));
    cursor = 0;
    count  = 0;
}

void waypoint_logger_log(const char *name, uint8_t score) {
    if (!waypoint_logger_enabled) return;
    if (!name || name[0] == '\0') return;

    char payload[LOCALIZER_WAY_NAME_LEN + 1 + 3 + 1];
    snprintf(payload, sizeof(payload), "%s %u", name, score);

    uint32_t ts = time_get_timestamp_fallback();
    log_store_push(LOG_FILE, (char*)entries, MAX_ENTRIES, ENTRY_LEN,
                   &cursor, &count, ts, payload, false);
}

const char *waypoint_logger_get_history() {
    return log_store_get_history((const char*)entries, MAX_ENTRIES, ENTRY_LEN,
                                 cursor, count,
                                 history_buf, sizeof(history_buf));
}
