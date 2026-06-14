/*
 * rgb_out.cpp
 *
 * Responsibility: Generic RGB output driver for the on-board RGB LED.
 * Handles both PWM (analogWrite on three pins) and NeoPixel (Adafruit_NeoPixel
 * on a single data pin) via compile-time flag RGB_OUT_NEOPIXEL.
 */

#include "rgb_out.h"

#include "logger/sys_logger.h"

#ifdef RGB_OUT_NEOPIXEL
#include <Adafruit_NeoPixel.h>
#endif

// ==========================================
// INTERNAL STATE
// ==========================================

#ifdef RGB_OUT_NEOPIXEL
static Adafruit_NeoPixel *neo = nullptr;
static uint8_t neo_pin = 0;
#else
static uint8_t pin_r = 0;
static uint8_t pin_g = 0;
static uint8_t pin_b = 0;
#endif

static uint8_t cur_r = 0, cur_g = 0, cur_b = 0;
static const RgbOutStep *pattern = nullptr;
static uint8_t pattern_len = 0;
static uint8_t step = 0;
static uint8_t repeat = 0;
static uint8_t repeat_count = 0;
static uint32_t last_ms = 0;

// ==========================================
// INTERNAL HELPERS
// ==========================================

static void apply(uint8_t r, uint8_t g, uint8_t b) {
    cur_r = r;
    cur_g = g;
    cur_b = b;

#ifdef RGB_OUT_NEOPIXEL
    if (neo) {
        neo->setPixelColor(0, neo->Color(r, g, b));
        neo->show();
    }
#else
    analogWrite(pin_r, r);
    analogWrite(pin_g, g);
    analogWrite(pin_b, b);
#endif
}

static uint8_t pattern_length(const RgbOutStep *p) {
    uint8_t len = 0;
    while (p[len].ms != 0) len++;
    return len;
}

// ==========================================
// PUBLIC API
// ==========================================

void rgb_out_init_pwm(uint8_t r, uint8_t g, uint8_t b) {
#ifdef RGB_OUT_NEOPIXEL
    sys_log(LOG_WARN, "RGB", "init_pwm ignored — RGB_OUT_NEOPIXEL defined");
#else
    pin_r = r;
    pin_g = g;
    pin_b = b;
    pinMode(pin_r, OUTPUT);
    pinMode(pin_g, OUTPUT);
    pinMode(pin_b, OUTPUT);
    apply(0, 0, 0);
    sys_log(LOG_INFO, "RGB", "PWM mode @ pins R:%u G:%u B:%u", r, g, b);
#endif
}

void rgb_out_init_neo(uint8_t pin) {
#ifdef RGB_OUT_NEOPIXEL
    neo_pin = pin;
    neo = new Adafruit_NeoPixel(1, pin, NEO_GRB + NEO_KHZ800);
    neo->begin();
    neo->setBrightness(255);
    apply(0, 0, 0);
    sys_log(LOG_INFO, "RGB", "NeoPixel mode @ pin %u", pin);
#else
    sys_log(LOG_WARN, "RGB", "init_neo ignored — RGB_OUT_NEOPIXEL not defined");
#endif
}

void rgb_out_set(uint8_t r, uint8_t g, uint8_t b) {
    pattern = nullptr;
    apply(r, g, b);
}

void rgb_out_off() {
    pattern = nullptr;
    apply(0, 0, 0);
}

void rgb_out_run(const RgbOutStep *p, uint8_t rep) {
    if (!p || p[0].ms == 0) return;
    pattern = p;
    pattern_len = pattern_length(p);
    step = 0;
    repeat = rep;
    repeat_count = 0;
    last_ms = millis();
    apply(p[0].r, p[0].g, p[0].b);
}

void rgb_out_stop() {
    pattern = nullptr;
}

void rgb_out_update() {
    if (!pattern) return;

    uint32_t now = millis();
    if (now - last_ms < pattern[step].ms) return;

    last_ms = now;
    step++;

    if (step >= pattern_len) {
        step = 0;
        if (repeat != RGB_OUT_REPEAT_FOREVER) {
            repeat_count++;
            if (repeat_count >= repeat) {
                pattern = nullptr;
                apply(0, 0, 0);
                return;
            }
        }
    }

    apply(pattern[step].r, pattern[step].g, pattern[step].b);
}