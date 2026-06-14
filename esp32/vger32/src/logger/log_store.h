/*
 * log_store.h
 *
 * Shared functions for circular event log stores.
 * No structs, no constants — each logger defines its own array and sizes.
 *
 * Each logger declares in its .cpp:
 *   static constexpr uint8_t MAX_ENTRIES = N;
 *   static constexpr uint8_t ENTRY_LEN   = M;  // max chars per entry + null
 *   static char    entries[MAX_ENTRIES][ENTRY_LEN];
 *   static uint8_t cursor = 0;
 *   static uint8_t count  = 0;
 *   static char    history_buf[MAX_ENTRIES * (ENTRY_LEN + 1) + 1];
 *
 * Entry format: "<timestamp> <payload>"
 * The payload is opaque — callers define its internal structure.
 * Oldest entry is overwritten first when the buffer is full.
 */

#ifndef LOG_STORE_H
#define LOG_STORE_H

#include <Arduino.h>

// Load entries from LittleFS into the provided array.
// Returns the number of entries loaded.
uint8_t log_store_load(const char *filepath,
                       char *entries, uint8_t max_entries, uint8_t entry_len,
                       uint8_t *cursor_out);

// Append one entry to the circular buffer.
// Caller provides the timestamp — allows boot_logger to pass corrected boot time.
// If persist is true, writes the full buffer to LittleFS entry by entry.
void log_store_push(const char *filepath,
                    char *entries, uint8_t max_entries, uint8_t entry_len,
                    uint8_t *cursor, uint8_t *count,
                    uint32_t timestamp, const char *payload,
                    bool persist = true);

// Serialize all entries into out_buf (oldest first), one entry per line.
// Returns out_buf. Caller owns the buffer — valid until caller decides.
const char *log_store_get_history(const char *entries, uint8_t max_entries,
                                  uint8_t entry_len, uint8_t cursor, uint8_t count,
                                  char *out_buf, size_t out_buf_len);

#endif
