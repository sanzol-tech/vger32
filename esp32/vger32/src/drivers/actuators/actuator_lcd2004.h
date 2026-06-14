/*
 * actuator_lcd2004.h
 *
 * Responsibility: Driver for the LCD 2004 display with PCF8574 I2C backpack.
 *                 Provides a simple interface to show text on the 4-line display.
 */

#ifndef ACTUATOR_LCD2004_H
#define ACTUATOR_LCD2004_H

#ifdef HW_HAS_LCD2004

#include <Arduino.h>
// Initialize the display. addr: PCF8574 I2C address (0x27 or 0x3F).
// Call once from setup().
void lcd2004_register(uint8_t addr = 0x27);

// Write up to 4 lines. Pass nullptr to leave a line unchanged.
// Each line is truncated to 20 characters.
void lcd2004_show(const char *line1,
                  const char *line2 = nullptr,
                  const char *line3 = nullptr,
                  const char *line4 = nullptr);

// Clear the display.
void lcd2004_clear();

// Turn backlight on or off.
void lcd2004_backlight(bool on);

#endif // HW_HAS_LCD2004

#endif // ACTUATOR_LCD2004_H