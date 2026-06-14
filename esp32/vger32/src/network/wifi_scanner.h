/*
 * wifi_scanner.h
 *
 * Centralized non-blocking WiFi scan manager. Single owner of the scan
 * hardware — prevents concurrent scan conflicts between the localizer,
 * the dashboard wifi-list endpoint, and the wifi manager.
 *
 * Usage:
 *   - Call wifi_scanner_handle() every loop.
 *   - Call wifi_scanner_request() when fresh results are needed.
 *     If cached results are still valid, the request is a no-op.
 *   - Check wifi_scanner_has_results() before consuming.
 *   - Access results via wifi_scanner_count() / wifi_scanner_get().
 *
 * Cache validity: WIFI_SCANNER_CACHE_MS. After expiry, results remain
 * accessible but wifi_scanner_has_results() returns false until the
 * next completed scan.
 */

#ifndef WIFI_SCANNER_H
#define WIFI_SCANNER_H

#include <Arduino.h>

#include "config/constants.h"

// ==========================================
// CONFIG
// ==========================================

static constexpr uint32_t WIFI_SCANNER_CACHE_MS = 30_seconds;
static constexpr uint8_t WIFI_SCANNER_MAX_APS = 32;
static constexpr uint8_t WIFI_SCANNER_ROW_LEN = 55; // max chars per text row

// ==========================================
// RESULT TYPE
// ==========================================

struct WifiApEntry {
    char ssid[33]; // null-terminated
    char mac[13]; // 12 hex chars + null, uppercase, no colons
    int8_t rssi;
    uint8_t channel;
};

// ==========================================
// API
// ==========================================

void wifi_scanner_init();

void wifi_scanner_handle();

// Request a fresh scan. No-op if a scan is already running or the
// cached results are still within WIFI_SCANNER_CACHE_MS.
void wifi_scanner_request();

// True if the last scan completed and results are still within cache window.
bool wifi_scanner_has_results();

// Number of APs in the last completed scan (0 if no scan yet).
uint8_t wifi_scanner_count();

// Returns a pointer to the i-th scan entry, or nullptr if out of range.
// Pointer is valid until the next completed scan overwrites the cache.
const WifiApEntry *wifi_scanner_get(uint8_t index);

// Returns scan results as pipe-separated plain text for the dashboard.
// Format per line: SSID|MAC|RSSI|CHANNEL\n
// Points to an internal static buffer — valid until next call.
// Returns empty string if no results are available.
const char *wifi_scanner_as_text();

#endif
