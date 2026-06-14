/*
 * actuator_lcd147.cpp
 *
 * Responsibility: Driver for the Waveshare 1.47" ST7789 LCD (172x320).
 *
 * Uses Arduino_GFX library (moononournation/GFX Library for Arduino).
 * All display parameters come from the active hardware profile (HW_LCD147_*).
 *
 * lcd147_show_text() handles title + payload display:
 *   - Title on line 1 in title_color
 *   - Payload split into lines of LCD147_MAX_LINE_LEN chars starting at line 2
 *   - Unused lines are cleared automatically
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_LCD147

#include "drivers/actuators/actuator_lcd147.h"

#include "logger/sys_logger.h"

#define LCD_PHYS_W  172
#define LCD_PHYS_H  320

#define LCD_W  (HW_LCD147_LANDSCAPE ? LCD_PHYS_H : LCD_PHYS_W)
#define LCD_H  (HW_LCD147_LANDSCAPE ? LCD_PHYS_W : LCD_PHYS_H)

#define LCD_LINE_H  (HW_LCD147_FONT_SIZE == 3 ? 28 : \
                     HW_LCD147_FONT_SIZE == 2 ? 18 : \
                     HW_LCD147_FONT_SIZE * 8 + 2)

#ifndef HARDWARE_DEMO_MODE
#include <Arduino_GFX_Library.h>
static Arduino_DataBus *bus = nullptr;
static Arduino_GFX     *gfx = nullptr;
#endif

static bool initialized = false;

// ==========================================
// PRIVATE HELPERS
// ==========================================

static void write_line(uint8_t row, const char *text, uint16_t color = HW_LCD147_COLOR_FG) {
#ifndef HARDWARE_DEMO_MODE
    if (!gfx) return;
    int y = HW_LCD147_PAD + row * LCD_LINE_H;
    gfx->fillRect(0, y, LCD_W, LCD_LINE_H, HW_LCD147_COLOR_BG);
    gfx->setTextColor(color);
    gfx->setCursor(HW_LCD147_PAD, y);
    gfx->print(text);
    gfx->setTextColor(HW_LCD147_COLOR_FG);  // restore default
#else
    sys_log(LOG_INFO, "LCD147", "row%u [%s]: %s", row, color == HW_LCD147_COLOR_FG ? "fg" : "color", text);
#endif
}

static void clear_line(uint8_t row) {
#ifndef HARDWARE_DEMO_MODE
    if (!gfx) return;
    int y = HW_LCD147_PAD + row * LCD_LINE_H;
    gfx->fillRect(0, y, LCD_W, LCD_LINE_H, HW_LCD147_COLOR_BG);
#endif
}

// ==========================================
// PUBLIC API
// ==========================================

void lcd147_register() {
#ifndef HARDWARE_DEMO_MODE
    bus = new Arduino_ESP32SPI(
        HW_LCD147_DC, HW_LCD147_CS, HW_LCD147_SCK, HW_LCD147_MOSI, GFX_NOT_DEFINED
    );
    gfx = new Arduino_ST7789(
        bus, HW_LCD147_RST,
        HW_LCD147_LANDSCAPE ? 1 : 0,
        HW_LCD147_IPS,
        LCD_PHYS_W, LCD_PHYS_H,
        HW_LCD147_COL_OFFSET1, HW_LCD147_ROW_OFFSET1,
        HW_LCD147_COL_OFFSET2, HW_LCD147_ROW_OFFSET2
    );
    gfx->begin();
    gfx->fillScreen(HW_LCD147_COLOR_BG);
    gfx->setTextColor(HW_LCD147_COLOR_FG);
    gfx->setTextSize(HW_LCD147_FONT_SIZE);
    gfx->setTextWrap(false);

    pinMode(HW_LCD147_BL, OUTPUT);
    digitalWrite(HW_LCD147_BL, HIGH);

    sys_log(LOG_INFO, "LCD147", "Init — %ux%u %s, size %u",
             LCD_W, LCD_H,
             HW_LCD147_LANDSCAPE ? "landscape" : "portrait",
             HW_LCD147_FONT_SIZE);
#else
    sys_log(LOG_INFO, "LCD147", "DEMO mode");
#endif
    initialized = true;
}

void lcd147_show_text(const char *title, uint16_t title_color, const char *payload) {
    if (!initialized) return;

    // Line 1 — title
    write_line(0, title ? title : "", title_color);

    // Lines 2..MAX_LINES — payload split into chunks
    uint8_t payload_len = payload ? strlen(payload) : 0;

    for (uint8_t i = 0; i < LCD147_MAX_LINES - 1; i++) {
        uint8_t offset = i * LCD147_MAX_LINE_LEN;
        if (offset < payload_len) {
            char buf[LCD147_MAX_LINE_LEN + 1];
            strncpy(buf, payload + offset, LCD147_MAX_LINE_LEN);
            buf[LCD147_MAX_LINE_LEN] = '\0';
            write_line(i + 1, buf);
        } else {
            clear_line(i + 1);
        }
    }
}

void lcd147_clear() {
    if (!initialized) return;
#ifndef HARDWARE_DEMO_MODE
    if (gfx) gfx->fillScreen(HW_LCD147_COLOR_BG);
#else
    sys_log(LOG_INFO, "LCD147", "CLEAR");
#endif
}

void lcd147_backlight(bool on) {
    if (!initialized) return;
#ifndef HARDWARE_DEMO_MODE
    digitalWrite(HW_LCD147_BL, on ? HIGH : LOW);
#else
    sys_log(LOG_INFO, "LCD147", "Backlight: %s", on ? "ON" : "OFF");
#endif
}

#endif // HW_HAS_LCD147