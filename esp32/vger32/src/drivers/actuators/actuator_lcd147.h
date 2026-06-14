/*
* actuator_lcd147.h
 *
 * Driver for the Waveshare 1.47" ST7789 LCD (172x320).
 *
 * Display parameters (orientation, colors, font, offsets) are defined
 * in the active hardware profile as HW_LCD147_* — never hardcoded here.
 *
 * Call lcd147_register() once from setup().
 * Use lcd147_show_text() for all display events — passes title + payload,
 * driver handles line splitting, clearing, and color.
 * Use lcd147_clear() to blank the screen.
 * Use lcd147_backlight() to control the backlight.
 */

#ifndef ACTUATOR_LCD147_H
#define ACTUATOR_LCD147_H

#include "profiles/active_profile.h"

#ifdef HW_HAS_LCD147

#include <Arduino.h>

static constexpr uint8_t LCD147_MAX_LINES    = 6;
static constexpr uint8_t LCD147_MAX_LINE_LEN = 16;

void lcd147_register();

// Show a titled event on the display.
// title     — first line, drawn in title_color
// payload   — remaining text, split into lines of LCD147_MAX_LINE_LEN chars
//             unused lines are cleared automatically
// title_color — 16-bit ST7789 color for the title line
void lcd147_show_text(const char *title, uint16_t title_color, const char *payload);

void lcd147_clear();
void lcd147_backlight(bool on);

#endif // HW_HAS_LCD147
#endif // ACTUATOR_LCD147_H