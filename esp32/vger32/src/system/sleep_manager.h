/*
 * sleep_manager.h
 *
 * Controls deep sleep cycles to reduce power consumption.
 * Transparent to the rest of the system — no module needs to check sleep
 * state or skip work because of it.
 * On wakeup the chip resets and setup() runs from scratch.
 */

#ifndef SLEEP_MANAGER_H
#define SLEEP_MANAGER_H

#include <Arduino.h>

#include "config/constants.h"

inline bool sleep_enabled = false;

static constexpr uint32_t SLEEP_ACTIVE_MS = 3_minutes;
static constexpr uint32_t SLEEP_DURATION_MS = 20_minutes;
static constexpr uint32_t SLEEP_GRACE_MS = 10_seconds;
static constexpr uint32_t SLEEP_DASHBOARD_IDLE_MS = 60_seconds;

void sleep_manager_set_enabled(bool enabled);

void sleep_manager_init();

void sleep_manager_handle();

#endif
