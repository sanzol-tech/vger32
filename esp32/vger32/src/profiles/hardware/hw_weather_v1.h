/*
 * hw_weather_v1.h
 *
 * Hardware profile — Weather Station, wiring v1.
 * Board: ESP32-DevKitC. Sensors: SHT31 (temp/humidity) + BMP280 (pressure) on I2C.
 *
 * To use different sensors in a future revision, create hw_weather_v2.h
 * with the updated HW_HAS_* defines.
 */

#ifndef HW_WEATHER_V1_H
#define HW_WEATHER_V1_H

#include "board.h"

// ==========================================
// DEMO MODE FLAG
// ==========================================
#define HARDWARE_DEMO_MODE

// ==========================================
// SENSORS
// ==========================================
#define HW_HAS_SHT31    // Temperature + humidity @ I2C 0x44
#define HW_HAS_BMP280   // Pressure @ I2C 0x76

// Uncomment to override the driver's default I2C address
// #define HW_SHT31_ADDR   0x45

#endif