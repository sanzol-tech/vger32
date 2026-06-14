/*
 * board_led.cpp
 *
 * Responsibility: Thin wrapper for the built-in board LED.
 * Delegates all work to digital_out (GPIO boards) or rgb_out (RGB boards).
 * Carries no FSM — pattern timing is driven by digital_out_update() and
 * rgb_out_update() called from the main loop.
 *
 * board_led_set_state() resolves each led_state_t to the best available
 * representation: color + pattern on RGB boards, blink pattern on GPIO boards.
 *
 * Strobe: LED_STROBE_MS total duration, LED_STROBE_STEP_MS per color step.
 * The number of repeats is computed from these two constants automatically.
 */

#include "drivers/actuators/board_led.h"

#include "logger/sys_logger.h"
#include "drivers/actuators/digital_out.h"
#include "drivers/actuators/rgb_out.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static int     board_led_id  = -1;
static bool    is_rgb_led    = false;
static uint8_t board_led_pin = 0;
static uint8_t led_r         = 255;
static uint8_t led_g         = 0;
static uint8_t led_b         = 0;

// ==========================================
// PRIVATE — GPIO PATTERNS
// ==========================================

static const DigitalOutStep PAT_FLASHING[] = {
    {true,  300},
    {false, 300},
    DIGITAL_OUT_PATTERN_END
};

static const DigitalOutStep PAT_FLASHING_SLOW[] = {
    {true,  600},
    {false, 400},
    DIGITAL_OUT_PATTERN_END
};

static const DigitalOutStep PAT_STROBE_GPIO[] = {
    {true,   80},
    {false,  80},
    DIGITAL_OUT_PATTERN_END
};

// ==========================================
// PRIVATE — RGB PATTERNS
// ==========================================

static const RgbOutStep PAT_RGB_FLASHING[] = {
    {200, 200, 200, 400},
    {  0,   0,   0, 300},
    RGB_OUT_PATTERN_END
};

static const RgbOutStep PAT_RGB_FLASHING_YELLOW[] = {
    {255, 200,   0, 400},
    {  0,   0,   0, 300},
    RGB_OUT_PATTERN_END
};

// red → green → blue → white with smooth intermediate steps
static const RgbOutStep PAT_RGB_STROBE[] = {
    {255,   0,   0, LED_STROBE_STEP_MS},
    {180,  80,   0, LED_STROBE_STEP_MS},
    { 80, 180,   0, LED_STROBE_STEP_MS},
    {  0, 255,   0, LED_STROBE_STEP_MS},
    {  0, 180,  80, LED_STROBE_STEP_MS},
    {  0,  80, 180, LED_STROBE_STEP_MS},
    {  0,   0, 255, LED_STROBE_STEP_MS},
    { 80,  80, 200, LED_STROBE_STEP_MS},
    {180, 180, 220, LED_STROBE_STEP_MS},
    {255, 255, 255, LED_STROBE_STEP_MS},
    {255, 120,  60, LED_STROBE_STEP_MS},
    {255,  40,  20, LED_STROBE_STEP_MS},
    RGB_OUT_PATTERN_END
};

// ==========================================
// PRIVATE HELPERS
// ==========================================

static constexpr uint8_t STROBE_STEPS = 12;

static uint8_t strobe_repeats() {
    uint32_t cycle_ms = STROBE_STEPS * LED_STROBE_STEP_MS;
    uint8_t  repeats  = (uint8_t)(LED_STROBE_MS / cycle_ms);
    return repeats < 1 ? 1 : repeats;
}

// ==========================================
// PUBLIC API
// ==========================================

void board_led_register(uint8_t pin, bool is_rgb, bool active_high) {
    board_led_pin = pin;
    is_rgb_led    = is_rgb;

    if (is_rgb) {
        rgb_out_init_neo(pin);
    } else {
        board_led_id = digital_out_register(pin, "board_led", active_high);
    }

    sys_log(LOG_INFO, "BoardLED", "Init @ pin %u (%s)", pin, board_led_get_type());
}

void board_led_set_state(led_state_t state) {
    if (is_rgb_led) {
        switch (state) {
            case LED_STATE_OFF:
                rgb_out_off();
                break;
            case LED_STATE_FLASHING:
                rgb_out_run(PAT_RGB_FLASHING, RGB_OUT_REPEAT_FOREVER);
                break;
            case LED_STATE_FLASHING_YELLOW:
                rgb_out_run(PAT_RGB_FLASHING_YELLOW, RGB_OUT_REPEAT_FOREVER);
                break;
            case LED_STATE_STEADY_GREEN:
                rgb_out_set(0, 200, 80);
                break;
            case LED_STATE_STEADY_BLUE:
                rgb_out_set(0, 80, 255);
                break;
            case LED_STATE_STROBE:
                rgb_out_run(PAT_RGB_STROBE, strobe_repeats());
                break;
        }
    } else {
        switch (state) {
            case LED_STATE_OFF:
                digital_out_set(board_led_id, false);
                break;
            case LED_STATE_FLASHING:
            case LED_STATE_FLASHING_YELLOW:
                digital_out_run(board_led_id, PAT_FLASHING_SLOW, DIGITAL_OUT_REPEAT_FOREVER);
                break;
            case LED_STATE_STEADY_GREEN:
            case LED_STATE_STEADY_BLUE:
                digital_out_set(board_led_id, true);
                break;
            case LED_STATE_STROBE:
                digital_out_run(board_led_id, PAT_STROBE_GPIO, strobe_repeats());
                break;
        }
    }
}

void board_led_on() {
    if (is_rgb_led) {
        rgb_out_set(led_r, led_g, led_b);
    } else {
        digital_out_set(board_led_id, true);
    }
}

void board_led_off() {
    if (is_rgb_led) {
        rgb_out_off();
    } else {
        digital_out_set(board_led_id, false);
    }
}

void board_led_run(const DigitalOutStep *pattern, uint8_t repeat) {
    if (is_rgb_led) return;
    digital_out_run(board_led_id, pattern, repeat);
}

void board_led_stop() {
    if (is_rgb_led) {
        rgb_out_stop();
    } else {
        digital_out_stop(board_led_id);
    }
}

void board_led_set_color(uint8_t r, uint8_t g, uint8_t b) {
    led_r = r;
    led_g = g;
    led_b = b;
}

uint8_t board_led_get_pin() {
    return board_led_pin;
}

const char *board_led_get_type() {
    return is_rgb_led ? "RGB" : "GPIO";
}