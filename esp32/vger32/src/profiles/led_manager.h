/*
* led_manager.h
 *
 * Mission-level LED state manager.
 * Sits between missions and board_led — missions call led_manager_set_state(),
 * led_manager handles all timing, transitions, and post-event recovery.
 *
 * Call led_manager_init() from mission setup().
 * Call led_manager_update() every loop() cycle.
 * Call led_manager_is_strobing() to check if strobe is active —
 * used by missions to avoid overriding the strobe with a WiFi state change.
 */

#ifndef LED_MANAGER_H
#define LED_MANAGER_H

#include "drivers/actuators/board_led.h"

// Interval between yellow pulses while searching for WiFi.
static constexpr uint32_t LED_PULSE_INTERVAL_MS = 2000;

// Duration of a single connection pulse (green/blue) before turning off.
static constexpr uint32_t LED_PULSE_DURATION_MS = 300;

void led_manager_init();
void led_manager_update();
void led_manager_set_state(led_state_t state);
bool led_manager_is_strobing();

#endif