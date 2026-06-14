/*
 * sys_logger.cpp
 *
 * Implementation of sys_logger.h.
 *
 * Entry format in log_store: "<unix_ts> <level_char> <module> <message>"
 * Messages longer than ENTRY_LEN are truncated — no line splitting.
 */

#include "logger/sys_logger.h"

#include <stdarg.h>

#include "logger/log_store.h"
#include "network/time_manager.h"

static constexpr char    LOG_FILE[]  = "";  // unused — persist=false
static constexpr uint8_t MAX_ENTRIES = 64;
// ts(10) + sp(1) + level_char(1) + sp(1) + module(≤12) + sp(1) + message(≤54) + null
static constexpr uint8_t ENTRY_LEN   = 80;

static char    entries[MAX_ENTRIES][ENTRY_LEN];
static uint8_t cursor = 0;
static uint8_t count  = 0;
static char    history_buf[MAX_ENTRIES * (ENTRY_LEN + 1) + 1];

static constexpr char LEVEL_CHARS[] = "FEWID";

void log_set_level(int level) { log_level_runtime = level; }

void sys_logger_init() {
    memset(entries, 0, sizeof(entries));
    cursor = 0;
    count  = 0;
}

void sys_log(log_level_t level, const char *module, const char *fmt, ...) {
    if (log_level_runtime < (int) level) return;

    char level_char = (level >= LOG_FATAL && level <= LOG_DEBUG)
                      ? LEVEL_CHARS[(int) level] : '?';

    // Format message
    char msg[ENTRY_LEN];
    va_list args;
    va_start(args, fmt);
    vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);

    // Strip trailing \n — log_store adds its own separator
    size_t len = strlen(msg);
    if (len > 0 && msg[len - 1] == '\n') msg[--len] = '\0';

    // Write to Serial
    Serial.printf("[%c][%s] %s\n", level_char, module, msg);

    // Build payload and push to buffer
    char payload[ENTRY_LEN];
    snprintf(payload, sizeof(payload), "%c %s %s", level_char, module, msg);

    uint32_t ts = time_get_timestamp_fallback();
    log_store_push(LOG_FILE, (char*) entries, MAX_ENTRIES, ENTRY_LEN,
                   &cursor, &count, ts, payload, false);
}

const char *sys_logger_get_history() {
    return log_store_get_history((const char*) entries, MAX_ENTRIES, ENTRY_LEN,
                                 cursor, count,
                                 history_buf, sizeof(history_buf));
}

void sys_logger_clear() {
    memset(entries, 0, sizeof(entries));
    cursor = 0;
    count  = 0;
}