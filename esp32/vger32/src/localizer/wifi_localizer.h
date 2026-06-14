/*
 * wifi_localizer.h
 *
 * WiFi-based indoor localization. Compares live WiFi scans against stored
 * fingerprints to detect which known waypoint the device is in.
 * Call localizer_init() from setup(), localizer_handle() each loop().
 */

#ifndef WIFI_LOCALIZER_H
#define WIFI_LOCALIZER_H

#include <Arduino.h>

#include "config/constants.h"

inline bool localizer_enabled = false;

static constexpr uint32_t LOCALIZER_SCAN_INTERVAL_MS = 1_minutes;

static constexpr uint8_t LOCALIZER_WAY_NAME_LEN = 6;
static constexpr uint8_t LOCALIZER_MIN_SCORE = 40;
static constexpr int8_t LOCALIZER_RSSI_TOLERANCE = 10;
static constexpr int8_t LOCALIZER_RSSI_MAX_DIFF = 30;
static constexpr uint8_t LOCALIZER_MAX_NETWORKS = 20;
static constexpr uint8_t LOCALIZER_MAX_WAYPOINTS = 32;
static constexpr uint8_t LOCALIZER_MAX_HISTORY = 5;

typedef void (*waypoint_callback_t)(const char *prev, const char *next);

void localizer_set_enabled(bool enabled);

void localizer_init(waypoint_callback_t callback);

void localizer_handle();

// Returns the name of the current waypoint, or nullptr if none.
const char *localizer_current();

// Returns the match score (0-100) of the current waypoint.
// Returns 0 if no waypoint is currently detected.
uint8_t localizer_current_score();

// Returns the last LOCALIZER_MAX_HISTORY detections as a plain string.
// Format (most recent first): "WAY:ts:score|WAY:ts:score|..."
// Points to an internal static buffer — valid until next call.
const char *localizer_history();

#endif
