/*
 * wifi_storage.cpp
 *
 * Responsibility: LittleFS persistence layer for WiFi fingerprint records.
 *                 Appends scan snapshots in a section-based format and trims
 *                 the file when it exceeds the size ceiling.
 *
 * File format:
 *   Header line: >WAY(1-6, space-padded to 6)TS_MIN(8)\n  — 16 chars + \n
 *   Data line:   MAC(12)CH(2)RSSI(3)\n                    — 17 chars + \n
 *   The '>' sentinel distinguishes headers from data lines.
 */

#include <LittleFS.h>

#include "wifi_storage.h"

#include "logger/sys_logger.h"
#include "network/time_manager.h"
#include "wifi_localizer.h"

static void trim_file() {
    sys_log(LOG_INFO, "wstore", "Trimming...");

    File src = LittleFS.open(WSTORE_PATH, "r");
    if (!src) return;

    File tmp = LittleFS.open(WSTORE_TMP, "w");
    if (!tmp) {
        src.close();
        return;
    }

    // 1. Skip WSTORE_TRIM_LINES data lines (headers don't count)
    uint16_t skipped = 0;
    while (skipped < WSTORE_TRIM_LINES && src.available()) {
        char line[WSTORE_DATA_LEN + 2];
        src.readBytesUntil('\n', line, sizeof(line));
        if (line[0] != '>') skipped++;
    }

    // 2. Advance to the next header to avoid starting mid-waypoint
    while (src.available()) {
        char line[WSTORE_HEADER_LEN + 2];
        uint8_t len = src.readBytesUntil('\n', line, sizeof(line));
        if (len > 0 && line[0] == '>') {
            line[len] = '\n';
            tmp.write((uint8_t *) line, len + 1);
            break;
        }
    }

    // 3. Copy the rest as-is
    uint16_t kept = 0;
    while (src.available()) {
        char line[WSTORE_DATA_LEN + 2];
        uint8_t len = src.readBytesUntil('\n', line, sizeof(line));
        if (len < 1) continue;
        line[len] = '\n';
        tmp.write((uint8_t *) line, len + 1);
        kept++;
    }

    src.close();
    tmp.close();

    LittleFS.remove(WSTORE_PATH);
    LittleFS.rename(WSTORE_TMP, WSTORE_PATH);

    sys_log(LOG_INFO, "wstore", "Trim done — skipped %u, kept %u lines", skipped, kept);
}

bool wstore_save(const String &payload) {
    // Payload: WAY(1-6) + N x [MAC(12) + CH(2) + RSSI(3)]
    uint8_t way_len = 0;
    const char *p = payload.c_str();

    // Read waypoint name — [A-Z0-9], 1-6 chars
    while (way_len < WSTORE_WAY_LEN && p[way_len] != '\0') {
        char c = p[way_len];
        if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) break;
        way_len++;
    }

    if (way_len == 0) return false;
    if (payload.length() < (uint32_t)(way_len + 17)) return false;

    char waypoint[WSTORE_WAY_LEN + 1] = {};
    strncpy(waypoint, p, way_len);

    // Timestamp in minutes, 8 digits
    char ts_buf[9];
    snprintf(ts_buf, sizeof(ts_buf), "%08lu", time_get_timestamp_fallback() / 60UL);

    File f = LittleFS.open(WSTORE_PATH, "a");
    if (!f) return false;

    // Write header: >WAY(left-aligned, space-padded to 6)TS_MIN(8)\n
    char header[WSTORE_HEADER_LEN + 2];
    snprintf(header, sizeof(header), ">%-6s%8s\n", waypoint, ts_buf);
    f.write((uint8_t *) header, WSTORE_HEADER_LEN + 1);

    // Write data lines: MAC(12) + CH(2) + RSSI(3)
    p += way_len;
    uint16_t count = 0;
    while (strlen(p) >= 17 && count < LOCALIZER_MAX_NETWORKS) {
        char line[WSTORE_DATA_LEN + 2];
        snprintf(line, sizeof(line), "%.12s%.2s%.3s\n", p, p + 12, p + 14);
        f.write((uint8_t *) line, WSTORE_DATA_LEN + 1);
        p += 17;
        count++;
    }

    size_t file_size = f.size();
    f.close();

    sys_log(LOG_INFO, "wstore", "%s with %u nets", waypoint, count);

    if (file_size > WSTORE_MAX_BYTES) trim_file();

    return true;
}

bool wstore_replace(const String &payload) {
    if (payload.length() == 0) return false;

    // Minimal validation: the payload must start with '>' (header sentinel).
    // Malformed lines are silently skipped by the localizer at read time.
    if (payload.charAt(0) != '>') {
        sys_log(LOG_WARN, "wstore", "replace — invalid header");
        return false;
    }

    if (payload.length() > WSTORE_MAX_BYTES) {
        sys_log(LOG_WARN, "wstore", "replace — payload exceeds max size (%u bytes)", WSTORE_MAX_BYTES);
        return false;
    }

    File f = LittleFS.open(WSTORE_PATH, "w");
    if (!f) {
        sys_log(LOG_ERROR, "wstore", "replace — could not open file for writing");
        return false;
    }

    f.print(payload);
    size_t written = f.size();
    f.close();

    sys_log(LOG_INFO, "wstore", "Replaced — %u bytes written", written);
    return true;
}