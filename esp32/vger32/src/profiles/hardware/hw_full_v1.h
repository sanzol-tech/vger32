/*
 * hw_full_v1.h
 *
 * Hardware profile — Generic / Demo.
 * No real hardware required: sensors are simulated, actuators are no-ops.
 * Useful for development without a physical board or wiring.
 *
 * HARDWARE_DEMO_MODE is the only flag that controls whether drivers run
 * their real or simulated code path. It must live in the hardware profile,
 * not in constants.h.
 */

#ifndef HW_FULL_V1_H
#define HW_FULL_V1_H

#include "board.h"

// ==========================================
// DEMO MODE FLAG
// ==========================================
#define HARDWARE_DEMO_MODE

// ==========================================
// SIMULATED PERIPHERALS
// All drivers simulate their output in demo mode.
// ==========================================
#define HW_HAS_SHT31
#define HW_HAS_BMP180
#define HW_HAS_BMP280
#define HW_HAS_PIR
#define HW_HAS_SW420
#define HW_HAS_ADXL345
#define HW_HAS_PUSH_BUTTON
#define HW_HAS_SOUND
#define HW_HAS_HCSR04
#define HW_HAS_GUVA_S12S
#define HW_HAS_LCD2004

// ==========================================
// BUZZER (simulated)
// ==========================================
#define HW_BUZZER_PIN           18
#define HW_BUZZER_ACTIVE_HIGH   1

#endif
