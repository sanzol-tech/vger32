/*
 * led_manager.cpp
 *
 * State machine:
 *
 *   SEARCHING  — pulses yellow every LED_PULSE_INTERVAL_MS, off between pulses
 *   CONNECTED  — single green pulse for LED_PULSE_DURATION_MS, then off
 *   AP         — single blue pulse for LED_PULSE_DURATION_MS, then off
 *   STROBE     — fires strobe for LED_STROBE_MS, then restores previous state
 *   OFF        — off
 */

#include "profiles/led_manager.h"

#include "logger/sys_logger.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static led_state_t current_state    = LED_STATE_OFF;
static led_state_t pre_strobe_state = LED_STATE_OFF;
static uint32_t    state_start_ms   = 0;
static bool        pulse_on         = false;

// ==========================================
// PRIVATE HELPERS
// ==========================================

static void apply(led_state_t state) {
    board_led_set_state(state);
}

// ==========================================
// PUBLIC API
// ==========================================

void led_manager_init() {
    current_state    = LED_STATE_OFF;
    pre_strobe_state = LED_STATE_OFF;
    state_start_ms   = 0;
    pulse_on         = false;
    apply(LED_STATE_OFF);
    sys_log(LOG_INFO, "LEDMgr", "Init");
}

bool led_manager_is_strobing() {
    return current_state == LED_STATE_STROBE;
}

void led_manager_set_state(led_state_t state) {
    if (state == LED_STATE_STROBE) {
        if (current_state != LED_STATE_STROBE)
            pre_strobe_state = current_state;
    }

    current_state  = state;
    state_start_ms = millis();
    pulse_on       = false;

    switch (state) {
        case LED_STATE_OFF:            apply(LED_STATE_OFF);            break;
        case LED_STATE_FLASHING_YELLOW: apply(LED_STATE_FLASHING_YELLOW); pulse_on = true; break;
        case LED_STATE_STEADY_GREEN:   apply(LED_STATE_STEADY_GREEN);   pulse_on = true; break;
        case LED_STATE_STEADY_BLUE:    apply(LED_STATE_STEADY_BLUE);    pulse_on = true; break;
        case LED_STATE_STROBE:         apply(LED_STATE_STROBE);         break;
        case LED_STATE_FLASHING:       apply(LED_STATE_FLASHING);       pulse_on = true; break;
    }
}

void led_manager_update() {
    uint32_t now     = millis();
    uint32_t elapsed = now - state_start_ms;

    switch (current_state) {

        case LED_STATE_FLASHING_YELLOW:
        case LED_STATE_FLASHING:
            if (pulse_on && elapsed >= LED_PULSE_DURATION_MS) {
                apply(LED_STATE_OFF);
                pulse_on = false;
            } else if (!pulse_on && elapsed >= LED_PULSE_INTERVAL_MS) {
                apply(current_state);
                pulse_on       = true;
                state_start_ms = now;
            }
            break;

        case LED_STATE_STEADY_GREEN:
        case LED_STATE_STEADY_BLUE:
            if (pulse_on && elapsed >= LED_PULSE_DURATION_MS) {
                apply(LED_STATE_OFF);
                pulse_on = false;
            }
            break;

        case LED_STATE_STROBE:
            if (elapsed >= LED_STROBE_MS) {
                led_manager_set_state(pre_strobe_state);
            }
            break;

        case LED_STATE_OFF:
            break;
    }
}