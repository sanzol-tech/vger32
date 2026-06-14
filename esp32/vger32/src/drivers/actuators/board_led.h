/*
 * board_led.h
 *
 * Thin wrapper over digital_out (GPIO) and rgb_out (RGB) for the built-in
 * board LED. No FSM of its own — update is driven by digital_out_update()
 * and rgb_out_update() in the main loop.
 *
 * Call board_led_register() from setup(). No board_led_update() needed.
 * Call board_led_set_state() to change LED behavior at any time.
 *
 * State names describe intent. Actual output is best-effort depending on
 * hardware: RGB boards use color and patterns; GPIO boards use blink patterns.
 */

#ifndef BOARD_LED_H
#define BOARD_LED_H

#include <Arduino.h>

#include "drivers/actuators/digital_out.h"

// ==========================================
// CONFIG
// ==========================================

static constexpr uint32_t LED_STROBE_MS      = 3000;
static constexpr uint32_t LED_STROBE_STEP_MS = 150;

// ==========================================
// LED STATES
// ==========================================

typedef enum {
    LED_STATE_OFF,
    LED_STATE_FLASHING,
    LED_STATE_FLASHING_YELLOW,
    LED_STATE_STEADY_GREEN,
    LED_STATE_STEADY_BLUE,
    LED_STATE_STROBE,
} led_state_t;

// Initialize the board LED.
// is_rgb:      true for NeoPixel/RGB LED (uses rgb_out).
// active_high: only relevant when is_rgb is false.
void board_led_register(uint8_t pin, bool is_rgb, bool active_high = true);

// Set LED state. Resolves to color+pattern (RGB) or blink pattern (GPIO).
void board_led_set_state(led_state_t state);

// Immediate on/off. Stops any running pattern.
void board_led_on();
void board_led_off();

// Run a DigitalOutStep pattern. GPIO only — no-op on RGB boards.
// For RGB pattern control use rgb_out_run() directly.
void board_led_run(const DigitalOutStep *pattern, uint8_t repeat);

// Stop any running pattern.
void board_led_stop();

// Set color for the next on(). RGB only — ignored on GPIO boards.
void board_led_set_color(uint8_t r, uint8_t g, uint8_t b);

// Query
uint8_t board_led_get_pin();
const char *board_led_get_type(); // "RGB" or "GPIO"

#endif