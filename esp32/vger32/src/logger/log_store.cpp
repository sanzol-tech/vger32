/*
 * log_store.cpp
 *
 * Shared implementation for circular event log stores.
 * Navigates caller-provided 2D arrays via char* + entry_len stride.
 *
 * No dependency on config/logger.h — log_store is a lower-level module
 * than sys_logger, so including the log macros here would create a
 * circular dependency (sys_logger calls log_store_push internally).
 *
 * Persistence: entries are written to LittleFS one line at a time,
 * without an intermediate serialization buffer.
 */

#include "logger/log_store.h"

#include <LittleFS.h>

static inline char *entry_at(char *entries, uint8_t entry_len, uint8_t i) {
    return entries + (i * entry_len);
}

static inline const char *entry_at_c(const char *entries, uint8_t entry_len, uint8_t i) {
    return entries + (i * entry_len);
}

uint8_t log_store_load(const char *filepath,
                       char *entries, uint8_t max_entries, uint8_t entry_len,
                       uint8_t *cursor_out) {
    if (!LittleFS.exists(filepath)) return 0;

    File f = LittleFS.open(filepath, "r");
    if (!f) return 0;

    uint8_t idx = 0;
    while (f.available() && idx < max_entries) {
        String line = f.readStringUntil('\n');
        line.trim();
        if (line.length() == 0) continue;
        strncpy(entry_at(entries, entry_len, idx), line.c_str(), entry_len - 1);
        entry_at(entries, entry_len, idx)[entry_len - 1] = '\0';
        idx++;
    }

    f.close();
    *cursor_out = idx % max_entries;
    return idx;
}

void log_store_push(const char *filepath,
                    char *entries, uint8_t max_entries, uint8_t entry_len,
                    uint8_t *cursor, uint8_t *count,
                    uint32_t timestamp, const char *payload,
                    bool persist) {
    snprintf(entry_at(entries, entry_len, *cursor), entry_len,
             "%lu %s", (unsigned long)timestamp, payload);

    *cursor = (*cursor + 1) % max_entries;
    if (*count < max_entries) (*count)++;

    if (persist) {
        File f = LittleFS.open(filepath, "w");
        if (f) {
            uint8_t start = (*count >= max_entries) ? *cursor : 0;
            for (uint8_t i = 0; i < *count; i++) {
                uint8_t    idx = (start + i) % max_entries;
                const char *row = entry_at_c(entries, entry_len, idx);
                f.printf("%s\n", row);
            }
            f.close();
        }
    }
}

const char *log_store_get_history(const char *entries, uint8_t max_entries,
                                  uint8_t entry_len, uint8_t cursor, uint8_t count,
                                  char *out_buf, size_t out_buf_len) {
    char *p   = out_buf;
    char *end = out_buf + out_buf_len - 1;

    uint8_t start = (count >= max_entries) ? cursor : 0;

    for (uint8_t i = 0; i < count && p < end; i++) {
        uint8_t     idx = (start + i) % max_entries;
        const char *row = entry_at_c(entries, entry_len, idx);
        size_t      len = strlen(row);
        if (p + len + 1 >= end) break;
        memcpy(p, row, len);
        p += len;
        *p++ = '\n';
    }

    *p = '\0';
    return out_buf;
}
