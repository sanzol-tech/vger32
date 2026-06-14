/*
 * actuator_lcd2004.cpp
 *
 * Responsibility: Driver for the LCD 2004 display with PCF8574 I2C backpack.
 *
 * The PCF8574 expands the I2C bus to 8 GPIO pins that drive the LCD in 4-bit mode.
 * Uses LiquidCrystal_I2C library (Frank de Brabander / marcoschwartz).
 *
 * lcd2004_show() overwrites only the lines that receive a non-null argument.
 * Each line is padded with spaces to 20 characters to erase leftover characters.
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_LCD2004

#include "drivers/actuators/actuator_lcd2004.h"

#include "logger/sys_logger.h"

#ifndef HARDWARE_DEMO_MODE
#include <LiquidCrystal_I2C.h>
static LiquidCrystal_I2C lcd(0x27, 20, 4);
#endif

static bool initialized = false;

// ==========================================
// PRIVATE HELPERS
// ==========================================

// Write a single line, padded to 20 chars to clear leftovers.
static void write_line(uint8_t row, const char *text) {

#ifndef HARDWARE_DEMO_MODE
lcd.setCursor (0, row);
char buf[21];
snprintf(buf, sizeof(buf), "%-20s", text); // left-align, pad with spaces
lcd.print (buf);
#else
sys_log(LOG_INFO, "LCD", "row%u: %s", row, text);
#endif
}

// ==========================================
// PUBLIC API
// ==========================================

void lcd2004_register(uint8_t addr) {

#ifndef HARDWARE_DEMO_MODE
// Reinitialize with the correct address if different from default
new(&lcd) LiquidCrystal_I2C(addr, 20, 4);
lcd.init();
lcd.backlight();
sys_log(LOG_INFO, "LCD2004", "Init @ 0x%02X", addr);
#else
sys_log(LOG_INFO, "LCD2004", "DEMO mode @ 0x%02X", addr);
#endif

initialized=true;
}

void lcd2004_show(const char *line1, const char *line2,
                  const char *line3, const char *line4) {
    if (!initialized) return;
    if (line1) write_line(0, line1);
    if (line2) write_line(1, line2);
    if (line3) write_line(2, line3);
    if (line4) write_line(3, line4);
}

void lcd2004_clear() {
    if (!initialized) return;
#ifndef HARDWARE_DEMO_MODE
lcd.clear();
#else
sys_log(LOG_INFO, "LCD2004", "CLEAR");
#endif
}

void lcd2004_backlight(bool on) {
    if (!initialized) return;
#ifndef HARDWARE_DEMO_MODE
if (on) lcd.backlight();
    else lcd.noBacklight();
#else
sys_log(LOG_INFO, "LCD2004", "Backlight: %s", on? "ON" : "OFF");
#endif
}

#endif // HW_HAS_LCD2004