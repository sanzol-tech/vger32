/*
 * rgb_out.h
 *
 * Generic RGB output driver for the on-board RGB LED.
 * Supports PWM (three pins, ESP32) and NeoPixel (one pin, ESP32-S3/C3/C6).
 * Select mode via build flags:
 *   -DRGB_OUT_NEOPIXEL   → NeoPixel mode (single data pin)
 *   (default)            → PWM mode (three pins, analogWrite)
 *
 * Call rgb_out_update() every loop cycle.
 */

#ifndef RGB_OUT_H
#define RGB_OUT_H

#include <Arduino.h>

// ==========================================
// PATTERN
// ==========================================

struct RgbOutStep {
    uint8_t r, g, b;
    uint32_t ms;
};

#define RGB_OUT_PATTERN_END  { 0, 0, 0, 0 }
#define RGB_OUT_REPEAT_FOREVER 0

// ==========================================
// PUBLIC API
// ==========================================

// Initialize in PWM mode — three independent pins.
// Ignored if RGB_OUT_NEOPIXEL is defined.
void rgb_out_init_pwm(uint8_t pin_r, uint8_t pin_g, uint8_t pin_b);

// Initialize in NeoPixel mode — single data pin.
// Ignored if RGB_OUT_NEOPIXEL is not defined.
void rgb_out_init_neo(uint8_t pin);

// Set color immediately. Stops any running pattern.
void rgb_out_set(uint8_t r, uint8_t g, uint8_t b);

// Turn off immediately. Stops any running pattern.
void rgb_out_off();

// Run a pattern. pattern must be a static array terminated by RGB_OUT_PATTERN_END.
// repeat: number of full cycles. RGB_OUT_REPEAT_FOREVER (0) = loop indefinitely.
void rgb_out_run(const RgbOutStep *pattern, uint8_t repeat);

// Stop the running pattern and leave the output in its current state.
void rgb_out_stop();

// Drive pattern playback. Must be called every loop cycle.
void rgb_out_update();

#endif // RGB_OUT_H