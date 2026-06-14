/*
 * hw_waypoint_alert_v1.h
 *
 * Hardware profile — Waypoint Alert, wiring v1.
 * Board: ESP32-DevKitC.
 *   - KY-012 active buzzer on pin 18.
 *   - Alert LED (3mm, active high) on pin 19.
 *
 * Extends board.h (chip-level resources) with the wiring
 * specific to this version of the device.
 *
 * Patterns are defined here because their timings are a property
 * of the hardware (buzzer sensitivity, LED brightness). The mission
 * references them by name without knowing the raw values.
 */

#ifndef HW_WAYPOINT_V1_H
#define HW_WAYPOINT_V1_H

#include "board.h"
#include "drivers/actuators/digital_out.h"

// ==========================================
// DEMO MODE FLAG
// ==========================================
#define HARDWARE_DEMO_MODE

// ==========================================
// BUZZER
// ==========================================
#define HW_BUZZER_PIN           18
#define HW_BUZZER_ACTIVE_HIGH   1

// ==========================================
// ALERT LED
// ==========================================
#define HW_ALERT_LED_PIN          19
#define HW_ALERT_LED_ACTIVE_HIGH  1

// ==========================================
// PATTERNS
// Shared by buzzer and LED — they dance together.
// ==========================================

// Single short pulse — confirmation, waypoint entered
static const DigitalOutStep HW_PATTERN_OK[] = {
    {true,  150},
    {false,   0},
    DIGITAL_OUT_PATTERN_END
};

// Three fast pulses — error or rejected action
static const DigitalOutStep HW_PATTERN_ERROR[] = {
    {true,  100},
    {false, 100},
    DIGITAL_OUT_PATTERN_END
};

// Long repeated pulse — alarm, requires attention
static const DigitalOutStep HW_PATTERN_ALERT[] = {
    {true,  800},
    {false, 400},
    DIGITAL_OUT_PATTERN_END
};

#endif
