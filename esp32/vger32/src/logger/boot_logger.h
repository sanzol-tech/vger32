/*
 * boot_logger.h
 *
 * Tracks device boots in LittleFS. Deep sleep wake-ups are ignored —
 * only meaningful boots (power-on, reset, panic, watchdog) are recorded.
 *
 * Call boot_logger_init() from setup() before time_init().
 * Call boot_logger_log() once time is synced (time_is_synced() == true).
 * Returns a pointer to an internal static buffer — valid until next call.
 */

#ifndef BOOT_LOGGER_H
#define BOOT_LOGGER_H

#include <Arduino.h>

inline bool boot_logger_enabled = false;

void boot_logger_set_enabled(bool enabled);

void boot_logger_init();

void boot_logger_log();

const char *boot_logger_get_history();

#endif
