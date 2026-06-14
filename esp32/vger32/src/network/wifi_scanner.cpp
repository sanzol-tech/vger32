/*
 * wifi_scanner.cpp
 *
 * Responsibility: Single owner of the WiFi scan hardware. Manages the
 *                 async scan lifecycle, caches results, and exposes them
 *                 to consumers without blocking the main loop.
 *
 * Scan lifecycle:
 *   IDLE  →  request()  →  SCANNING  →  handle() detects completion
 *         →  copies results to cache  →  READY
 *         →  after WIFI_SCANNER_CACHE_MS  →  IDLE (cache still readable)
 */

#include <WiFi.h>

#include "wifi_scanner.h"

#include "logger/sys_logger.h"

// ==========================================
// STATE
// ==========================================

typedef enum {
    SCAN_IDLE = 0,
    SCAN_RUNNING,
    SCAN_READY
} scan_state_t;

static scan_state_t scan_state = SCAN_IDLE;
static uint32_t scan_start_ms = 0;
static uint32_t scan_ready_ms = 0;

// ==========================================
// CACHE
// ==========================================

static WifiApEntry cache[WIFI_SCANNER_MAX_APS];
static uint8_t cache_count = 0;

// ==========================================
// PRIVATE
// ==========================================

static void copy_results(int n) {
    cache_count = 0;

    if (n > WIFI_SCANNER_MAX_APS) n = WIFI_SCANNER_MAX_APS;

    for (int i = 0; i < n; i++) {
        String bssid = WiFi.BSSIDstr(i);
        bssid.toUpperCase();
        bssid.replace(":", "");
        if (bssid.length() != 12) continue;

        WifiApEntry &e = cache[cache_count];

        strncpy(e.ssid, WiFi.SSID(i).c_str(), 32);
        e.ssid[32] = '\0';

        strncpy(e.mac, bssid.c_str(), 12);
        e.mac[12] = '\0';

        e.rssi = (int8_t) WiFi.RSSI(i);
        e.channel = (uint8_t) WiFi.channel(i);

        cache_count++;
    }
}

// ==========================================
// PUBLIC API
// ==========================================

void wifi_scanner_init() {
    scan_state = SCAN_IDLE;
    cache_count = 0;
}

void wifi_scanner_handle() {
    if (scan_state != SCAN_RUNNING) return;

    int n = WiFi.scanComplete();

    if (n == WIFI_SCAN_RUNNING) return;

    if (n == WIFI_SCAN_FAILED || n == 0) {
        sys_log(LOG_WARN, "Scanner", "Scan failed or no APs found");
        WiFi.scanDelete();
        scan_state = SCAN_IDLE;
        return;
    }

    copy_results(n);
    WiFi.scanDelete();

    scan_state = SCAN_READY;
    scan_ready_ms = millis();

    sys_log(LOG_DEBUG, "Scanner", "Scan complete — %u APs", cache_count);
}

void wifi_scanner_request() {
    if (scan_state == SCAN_RUNNING) return;

    if (scan_state == SCAN_READY) {
        if (millis() - scan_ready_ms < WIFI_SCANNER_CACHE_MS) return;
        scan_state = SCAN_IDLE;
    }

    WiFi.scanNetworks(true, true); // async, include hidden
    scan_state = SCAN_RUNNING;
    scan_start_ms = millis();

    sys_log(LOG_DEBUG, "Scanner", "Scan started");
}

bool wifi_scanner_has_results() {
    if (scan_state != SCAN_READY) return false;
    return (millis() - scan_ready_ms) < WIFI_SCANNER_CACHE_MS;
}

uint8_t wifi_scanner_count() {
    return cache_count;
}

const WifiApEntry *wifi_scanner_get(uint8_t index) {
    if (index >= cache_count) return nullptr;
    return &cache[index];
}

const char *wifi_scanner_as_text() {
    // "SSID|MAC|RSSI|CHANNEL\n" per entry
    // max row: 32+1+12+1+4+1+3+1 = 55 chars  (WIFI_SCANNER_ROW_LEN)
    static char buf[WIFI_SCANNER_MAX_APS * WIFI_SCANNER_ROW_LEN + 1];

    if (cache_count == 0) {
        buf[0] = '\0';
        return buf;
    }

    char *p = buf;
    char *end = buf + sizeof(buf) - 1;

    for (uint8_t i = 0; i < cache_count && p < end; i++) {
        p += snprintf(p, end - p, "%s|%s|%d|%u\n",
                      cache[i].ssid,
                      cache[i].mac,
                      (int) cache[i].rssi,
                      (unsigned) cache[i].channel);
    }

    *p = '\0';
    return buf;
}
