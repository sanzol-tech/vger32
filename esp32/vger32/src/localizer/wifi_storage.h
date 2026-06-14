/*
 * wifi_storage.h
 *
 * LittleFS persistence layer for WiFi fingerprint records.
 */

#ifndef WIFI_STORAGE_H
#define WIFI_STORAGE_H

#include <Arduino.h>

// Waypoint name: [A-Z0-9], 1-6 chars
static constexpr uint8_t WSTORE_WAY_LEN = 6;

// Header line: '>'(1) + WAY(6) + TS_MIN(8) + '\n' = 16 chars (excluding \n in LEN)
static constexpr uint8_t WSTORE_HEADER_LEN = 15;

// Data line: MAC(12) + CH(2) + RSSI(3) + '\n' = 17 chars (excluding \n in LEN)
static constexpr uint8_t WSTORE_DATA_LEN = 17;

static constexpr uint32_t WSTORE_MAX_BYTES = 81920;
static constexpr uint16_t WSTORE_TRIM_LINES = 258;

static constexpr char WSTORE_PATH[] = "/wifi_fingerprints.dat";
static constexpr char WSTORE_TMP[] = "/wifi_fingerprints.tmp";

// Append one fingerprint snapshot to the database.
// Payload format: WAY(1-6) + N × [MAC(12) + CH(2) + RSSI(3)]
bool wstore_save(const String &payload);

// Replace the entire fingerprint database with the provided content.
// Payload must be a valid .dat file (same format returned by GET /api/wifi-fingerprints).
// First line must start with '>' (header sentinel). Returns false on validation
// failure or storage error.
bool wstore_replace(const String &payload);

#endif