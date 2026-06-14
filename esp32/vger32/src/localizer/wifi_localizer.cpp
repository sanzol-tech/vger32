/*
 * wifi_localizer.cpp
 *
 * Responsibility: WiFi fingerprint matching engine for indoor positioning.
 *                 Compares the current scan against stored fingerprints,
 *                 scores each waypoint, and fires a callback on location change.
 *                 Maintains a circular buffer (LOCALIZER_MAX_HISTORY entries)
 *                 of recent detections, exposed via localizer_history().
 *
 * Scan hardware is managed by wifi_scanner — localizer_handle() requests
 * a scan at LOCALIZER_SCAN_INTERVAL_MS and processes results when ready.
 *
 * waypoint_logger is called directly on each detection if enabled.
 * Missions are responsible for enabling and initializing the logger.
 */

#include <LittleFS.h>
#include <time.h>

#include "wifi_localizer.h"

#include "logger/sys_logger.h"
#include "logger/waypoint_logger.h"
#include "network/wifi_scanner.h"
#include "wifi_storage.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static waypoint_callback_t on_change = nullptr;
static char current_wp_buf[LOCALIZER_WAY_NAME_LEN + 1] = {};
static const char *current_wp = nullptr;
static uint8_t current_score = 0;
static uint32_t last_request_ms = 0;
static bool scan_processed = false;

struct WaypointScore {
    char name[LOCALIZER_WAY_NAME_LEN + 1];
    float total;
    uint16_t count;
};

static WaypointScore scores[LOCALIZER_MAX_WAYPOINTS];
static uint8_t scores_count = 0;

// ==========================================
// LOCATION HISTORY — circular buffer
// ==========================================

struct LocationEntry {
    time_t timestamp;
    char waypoint[LOCALIZER_WAY_NAME_LEN + 1];
    uint8_t score;
};

static LocationEntry loc_history[LOCALIZER_MAX_HISTORY];
static uint8_t loc_head = 0;
static uint8_t loc_count = 0;

static void loc_push(const char *waypoint, uint8_t score) {
    loc_history[loc_head].timestamp = time(nullptr);
    loc_history[loc_head].score = score;
    if (waypoint) {
        strncpy(loc_history[loc_head].waypoint, waypoint, LOCALIZER_WAY_NAME_LEN);
        loc_history[loc_head].waypoint[LOCALIZER_WAY_NAME_LEN] = '\0';
    } else {
        loc_history[loc_head].waypoint[0] = '\0';
    }
    loc_head = (loc_head + 1) % LOCALIZER_MAX_HISTORY;
    if (loc_count < LOCALIZER_MAX_HISTORY) loc_count++;
}

// ==========================================
// RSSI PROXIMITY SCORE  0.0 – 1.0
// ==========================================

static float rssi_score(int8_t measured, int8_t stored) {
    int8_t diff = abs(measured - stored);
    if (diff <= LOCALIZER_RSSI_TOLERANCE) return 1.0f;
    if (diff >= LOCALIZER_RSSI_MAX_DIFF) return 0.0f;
    return 1.0f - (float) (diff - LOCALIZER_RSSI_TOLERANCE) /
           (LOCALIZER_RSSI_MAX_DIFF - LOCALIZER_RSSI_TOLERANCE);
}

static WaypointScore *get_or_create_score(const char *name) {
    for (uint8_t i = 0; i < scores_count; i++) {
        if (strncmp(scores[i].name, name, LOCALIZER_WAY_NAME_LEN) == 0) return &scores[i];
    }
    if (scores_count >= LOCALIZER_MAX_WAYPOINTS) return nullptr;
    WaypointScore *s = &scores[scores_count++];
    strncpy(s->name, name, LOCALIZER_WAY_NAME_LEN);
    s->name[LOCALIZER_WAY_NAME_LEN] = '\0';
    s->total = 0.0f;
    s->count = 0;
    return s;
}

// ==========================================
// SCORING — reads from wifi_scanner cache
// ==========================================

static bool find_in_scan(const char *mac, int8_t &out_rssi) {
    for (uint8_t i = 0; i < wifi_scanner_count(); i++) {
        const WifiApEntry *e = wifi_scanner_get(i);
        if (e && strncmp(e->mac, mac, 12) == 0) {
            out_rssi = e->rssi;
            return true;
        }
    }
    return false;
}

static const char *score_from_file(uint8_t &out_score) {
    scores_count = 0;

    File f = LittleFS.open(WSTORE_PATH, "r");
    if (!f) return nullptr;

    char current_way[LOCALIZER_WAY_NAME_LEN + 1] = {};
    uint16_t line_num = 0;

    while (f.available()) {
        char line[WSTORE_DATA_LEN + 2];
        uint8_t len = f.readBytesUntil('\n', line, sizeof(line));
        if (len < 1) continue;
        line[len] = '\0';

        if (line[0] == '>') {
            strncpy(current_way, line + 1, LOCALIZER_WAY_NAME_LEN);
            current_way[LOCALIZER_WAY_NAME_LEN] = '\0';
            for (int i = LOCALIZER_WAY_NAME_LEN - 1;
                 i >= 0 && current_way[i] == ' '; i--)
                current_way[i] = '\0';
            continue;
        }

        if (current_way[0] == '\0') continue;
        if (len < WSTORE_DATA_LEN) continue;

        char mac[13] = {};
        strncpy(mac, line, 12);

        char rsi[4] = {};
        strncpy(rsi, line + 14, 3);
        int8_t stored_rssi = -(int8_t) atoi(rsi);

        WaypointScore *s = get_or_create_score(current_way);
        if (!s) continue;

        s->count++;

        int8_t measured_rssi;
        if (find_in_scan(mac, measured_rssi)) {
            s->total += rssi_score(measured_rssi, stored_rssi);
        }

        if (++line_num % 100 == 0) yield();
    }

    f.close();

    const char *best = nullptr;
    float best_pct = (float) LOCALIZER_MIN_SCORE / 100.0f;

    for (uint8_t i = 0; i < scores_count; i++) {
        if (scores[i].count == 0) continue;
        float pct = scores[i].total / scores[i].count;
        sys_log(LOG_DEBUG, "Localizer", "%s score: %u%%",
                  scores[i].name, (uint8_t)(pct * 100));
        if (pct > best_pct) {
            best_pct = pct;
            best = scores[i].name;
        }
    }

    out_score = best ? (uint8_t)(best_pct * 100) : 0;
    return best;
}

// ==========================================
// PUBLIC API
// ==========================================

void localizer_set_enabled(bool enabled) { localizer_enabled = enabled; }

void localizer_init(waypoint_callback_t callback) {
    if (!localizer_enabled) {
        sys_log(LOG_INFO, "Localizer", "Disabled by capability");
        return;
    }

    on_change = callback;
    current_wp = nullptr;
    last_request_ms = 0;
    scan_processed = false;
    loc_head = 0;
    loc_count = 0;

    sys_log(LOG_INFO, "Localizer", "Initialized");
}

void localizer_handle() {
    if (!localizer_enabled) return;

    uint32_t now = millis();

    if (now - last_request_ms >= LOCALIZER_SCAN_INTERVAL_MS) {
        last_request_ms = now;
        scan_processed = false;
        wifi_scanner_request();
        sys_log(LOG_INFO, "Localizer", "Scan requested");
    }

    if (scan_processed) return;
    if (!wifi_scanner_has_results()) return;

    scan_processed = true;

    uint8_t score = 0;
    const char *detected = score_from_file(score);

    bool changed = (detected != current_wp) &&
                   (detected == nullptr || current_wp == nullptr ||
                    strcmp(detected, current_wp) != 0);

    if (changed) {
        sys_log(LOG_INFO, "Localizer", "%s -> %s",
                 current_wp ? current_wp : "?",
                 detected ? detected : "?");

        const char *prev = current_wp;

        if (detected) {
            strncpy(current_wp_buf, detected, LOCALIZER_WAY_NAME_LEN);
            current_wp_buf[LOCALIZER_WAY_NAME_LEN] = '\0';
            current_wp = current_wp_buf;
        } else {
            current_wp = nullptr;
        }

        current_score = current_wp ? score : 0;
        loc_push(current_wp, score);
        if (current_wp && waypoint_logger_enabled)
            waypoint_logger_log(current_wp, score);
        if (on_change) on_change(prev, current_wp);
    }
}

const char *localizer_current() {
    return current_wp;
}

uint8_t localizer_current_score() {
    return current_score;
}

/*
 * Returns the last LOCALIZER_MAX_HISTORY detections as a plain string.
 * Format (most recent first): "WAY:ts:score|WAY:ts:score|..."
 *   WAY   — waypoint name (up to 6 chars), or "-" if location was lost
 *   ts    — Unix timestamp (seconds)
 *   score — match percentage 0-100
 * Returns "" if no detections have occurred yet.
 * Points to an internal static buffer — valid until next call.
 *
 * Max length: LOCALIZER_MAX_HISTORY * (6 + 1 + 10 + 1 + 3) + (MAX_HISTORY-1) pipes
 *           = 5 * 21 + 4 = 109 chars → buffer of 120 is sufficient.
 */
const char *localizer_history() {
    static char buf[120];

    if (loc_count == 0) {
        buf[0] = '\0';
        return buf;
    }

    char *p = buf;
    char *end = buf + sizeof(buf) - 1;

    for (uint8_t i = 0; i < loc_count && p < end; i++) {
        uint8_t slot = (loc_head + LOCALIZER_MAX_HISTORY - 1 - i) % LOCALIZER_MAX_HISTORY;

        if (i > 0 && p < end) *p++ = '|';

        const char *way = loc_history[slot].waypoint[0] != '\0'
                              ? loc_history[slot].waypoint
                              : "-";

        while (*way && p < end) *p++ = *way++;

        p += snprintf(p, end - p, ":%lu:%u",
                      (unsigned long) loc_history[slot].timestamp,
                      loc_history[slot].score);
    }

    *p = '\0';
    return buf;
}