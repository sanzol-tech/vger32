/*
 * time_manager.h
 *
 * Responsibility: NTP synchronization interface.
 *                 Provides init/handle entry points, sync status query,
 *                 external time injection, and a timestamp helper that falls
 *                 back to millis()-based uptime before first sync.
 */

#ifndef TIME_MANAGER_H
#define TIME_MANAGER_H

#include <stdint.h>

inline bool time_enabled = false;

void time_set_enabled(bool enabled);

// Returns true if the clock has been set — either by NTP or by time_set().
bool time_is_synced();

void time_init();

void time_handle();

// Set the system clock from an externally provided Unix timestamp.
// Marks the clock as synced — NTP will not override it until RESYNC_INTERVAL_MS.
// Intended for use after deep sleep, when NTP has not had time to sync yet.
void time_set(uint32_t unix_ts);

// Returns a Unix timestamp if the clock is synced, or seconds since boot otherwise.
uint32_t time_get_timestamp_fallback();

#endif
