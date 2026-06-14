/*
 * board.h
 *
 * Chip-level hardware resource definitions.
 * Auto-detected from the board identifier set by PlatformIO — no manual flags needed.
 * Include through hardware profiles only — never directly from mission profiles.
 *
 * Defines:
 *   BOARD_LED_PIN          Built-in LED GPIO pin
 *   BOARD_LED_RGB          1 = NeoPixel/RGB, 0 = plain GPIO
 *   BOARD_LED_ACTIVE_HIGH  1 = HIGH is on (ignored when BOARD_LED_RGB = 1)
 *   BOARD_I2C_SDA          Default I2C SDA pin
 *   BOARD_I2C_SCL          Default I2C SCL pin
 */

#ifndef BOARD_H
#define BOARD_H

#if defined(ARDUINO_ESP32S3_DEV)
#define BOARD_LED_PIN           48
#define BOARD_LED_RGB           1
#define BOARD_LED_ACTIVE_HIGH   1
#define BOARD_I2C_SDA           8
#define BOARD_I2C_SCL           9

#elif defined(ARDUINO_LOLIN_C3_MINI)
#define BOARD_LED_PIN           7
#define BOARD_LED_RGB           0
#define BOARD_LED_ACTIVE_HIGH   1
#define BOARD_I2C_SDA           8
#define BOARD_I2C_SCL           9

#elif defined(BOARD_ESP32C6)
// esp32-c6-devkitm-1 — flag set in platformio.ini (no standard Arduino macro available yet)
#define BOARD_LED_PIN           8
#define BOARD_LED_RGB           1
#define BOARD_LED_ACTIVE_HIGH   1
#define BOARD_I2C_SDA           6
#define BOARD_I2C_SCL           7

#else
// Default: ESP32-DevKitC (38-pin)
#define BOARD_LED_PIN           2
#define BOARD_LED_RGB           0
#define BOARD_LED_ACTIVE_HIGH   1
#define BOARD_I2C_SDA           21
#define BOARD_I2C_SCL           22
#endif

#endif
