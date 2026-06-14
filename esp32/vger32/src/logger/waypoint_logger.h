/*
 * waypoint_logger.h
 *
 * Records waypoint detection events in a circular in-memory buffer.
 * Entries are not persisted to LittleFS — history is lost on reboot.
 * Each entry stores a Unix timestamp, waypoint name, and match score (0-100).
 *
 * Call waypoint_logger_get_history() to get the serialized log for MQTT publish.
 * Returns a pointer to an internal static buffer — valid until next call.
 */

#ifndef WAYPOINT_LOGGER_H
#define WAYPOINT_LOGGER_H

#include <Arduino.h>

inline bool waypoint_logger_enabled = false;

void waypoint_logger_set_enabled(bool enabled);

void waypoint_logger_init();

// name: waypoint name (up to LOCALIZER_WAY_NAME_LEN chars)
// score: match percentage 0-100
void waypoint_logger_log(const char *name, uint8_t score);

const char *waypoint_logger_get_history();

#endif
