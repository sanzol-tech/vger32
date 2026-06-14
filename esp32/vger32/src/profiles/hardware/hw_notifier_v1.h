/*
 * hw_notifier_v1.h
 *
 * Hardware profile: Waveshare ESP32-C6-LCD-1.47
 *
 * Chip:    ESP32-C6
 * Display: 1.47" ST7789, 172x320, SPI
 * LED:     RGB NeoPixel on pin 8
 *
 * Pinout (display):
 *   SCK  → 7
 *   MOSI → 6
 *   DC   → 15
 *   CS   → 14
 *   RST  → 21
 *   BL   → 22
 */

#ifndef HW_NOTIFIER_V1_H
#define HW_NOTIFIER_V1_H

#include "board.h"

// ==========================================
// DEMO MODE FLAG
// ==========================================
// Uncomment to enable simulated sensor values (no real hardware needed).
// #define HARDWARE_DEMO_MODE

// ==========================================
// DISPLAY — ST7789 1.47"
// ==========================================
#define HW_HAS_LCD147
#define HW_LCD147_SCK          7
#define HW_LCD147_MOSI         6
#define HW_LCD147_DC           15
#define HW_LCD147_CS           14
#define HW_LCD147_RST          21
#define HW_LCD147_BL           22

#define HW_LCD147_LANDSCAPE    true
#define HW_LCD147_IPS          true
#define HW_LCD147_FONT_SIZE    3
#define HW_LCD147_COLOR_BG     0x0000   // black
#define HW_LCD147_COLOR_FG     0x07E0   // neon green
#define HW_LCD147_COLOR_TITLE  0x04DF
#define HW_LCD147_PAD          8
#define HW_LCD147_COL_OFFSET1  34
#define HW_LCD147_ROW_OFFSET1  0
#define HW_LCD147_COL_OFFSET2  34
#define HW_LCD147_ROW_OFFSET2  0

// ==========================================
// BOARD LED — RGB NeoPixel (defined in board.h)
// ==========================================
#define BOARD_LED_PIN          8

// ==========================================
// WIFI — reduced TX power to limit heat
// ==========================================
#define HW_WIFI_TX_POWER       WIFI_POWER_8_5dBm

#endif // HW_NOTIFIER_V1_H