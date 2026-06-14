/*
 * digital_out.h
 *
 * Generic driver for digital output actuators: LEDs, buzzers, relays, or any
 * on/off device. Supports immediate set/toggle and pattern playback (sequences
 * of on/off steps with optional repeat). Call digital_out_update() every loop.
 */

#ifndef DIGITAL_OUT_H
#define DIGITAL_OUT_H

#include <Arduino.h>

// ==========================================
// CONFIG
// ==========================================

static constexpr uint8_t DIGITAL_OUT_MAX = 8;

// ==========================================
// PATTERN
// ==========================================

struct DigitalOutStep {
    bool on;
    uint32_t ms;
};

#define DIGITAL_OUT_PATTERN_END { false, 0 }
#define DIGITAL_OUT_REPEAT_FOREVER 0

// ==========================================
// PUBLIC API
// ==========================================

// Register a digital output actuator.
// active_high: true if the pin is HIGH when on, false if active low.
// Returns slot index (>= 0) on success, -1 if DIGITAL_OUT_MAX is reached.
int digital_out_register(uint8_t pin, const char *name, bool active_high);

// Set output state immediately. Stops any running pattern.
void digital_out_set(int id, bool on);

// Toggle output state immediately. Stops any running pattern.
void digital_out_toggle(int id);

// Run a pattern. pattern must be a static array terminated by DIGITAL_OUT_PATTERN_END.
// repeat: number of full pattern cycles. DIGITAL_OUT_REPEAT_FOREVER (0) = loop indefinitely.
// Starts from the first step immediately.
void digital_out_run(int id, const DigitalOutStep *pattern, uint8_t repeat);

// Stop the running pattern and leave the output in its current state.
void digital_out_stop(int id);

// Drive pattern playback. Must be called every loop cycle.
void digital_out_update();

#endif // DIGITAL_OUT_H