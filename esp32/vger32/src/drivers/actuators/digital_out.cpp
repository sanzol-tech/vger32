/*
 * digital_out.cpp
 *
 * Responsibility: Generic digital output driver with pattern playback.
 * Maintains a fixed-size table of registered actuators and drives their
 * on/off sequences via a tick function called every loop cycle.
 */

#include "drivers/actuators/digital_out.h"

#include "logger/sys_logger.h"

// ==========================================
// INTERNAL STATE
// ==========================================

struct DigitalOutEntry {
    uint8_t pin;
    const char *name;
    bool active_high;
    bool state;
    const DigitalOutStep *pattern;
    uint8_t pattern_len;
    uint8_t step;
    uint8_t repeat;
    uint8_t repeat_count;
    uint32_t last_ms;
};

static DigitalOutEntry entries[DIGITAL_OUT_MAX];
static uint8_t entry_count = 0;

// ==========================================
// INTERNAL HELPERS
// ==========================================

static void apply(DigitalOutEntry &e, bool on) {
    e.state = on;
    digitalWrite(e.pin, e.active_high ? on : !on);
}

static uint8_t pattern_length(const DigitalOutStep *pattern) {
    uint8_t len = 0;
    while (pattern[len].ms != 0) len++;
    return len;
}

// ==========================================
// PUBLIC API
// ==========================================

int digital_out_register(uint8_t pin, const char *name, bool active_high) {
    if (entry_count >= DIGITAL_OUT_MAX) {
        sys_log(LOG_ERROR, "DigitalOut", "Max outputs reached");
        return -1;
    }

    uint8_t i = entry_count++;

    entries[i].pin = pin;
    entries[i].name = name;
    entries[i].active_high = active_high;
    entries[i].state = false;
    entries[i].pattern = nullptr;
    entries[i].pattern_len = 0;
    entries[i].step = 0;
    entries[i].repeat = 0;
    entries[i].repeat_count = 0;
    entries[i].last_ms = 0;

    pinMode(pin, OUTPUT);
    apply(entries[i], false);

    sys_log(LOG_INFO, "DigitalOut", "'%s' @ pin %u (%s)",
             name, pin, active_high ? "active HIGH" : "active LOW");
    return i;
}

void digital_out_set(int id, bool on) {
    if (id < 0 || id >= entry_count) return;
    entries[id].pattern = nullptr;
    apply(entries[id], on);
}

void digital_out_toggle(int id) {
    if (id < 0 || id >= entry_count) return;
    entries[id].pattern = nullptr;
    apply(entries[id], !entries[id].state);
}

void digital_out_run(int id, const DigitalOutStep *pattern, uint8_t repeat) {
    if (id < 0 || id >= entry_count) return;
    if (!pattern || pattern[0].ms == 0) return;

    entries[id].pattern = pattern;
    entries[id].pattern_len = pattern_length(pattern);
    entries[id].step = 0;
    entries[id].repeat = repeat;
    entries[id].repeat_count = 0;
    entries[id].last_ms = millis();

    apply(entries[id], pattern[0].on);
}

void digital_out_stop(int id) {
    if (id < 0 || id >= entry_count) return;
    entries[id].pattern = nullptr;
}

void digital_out_update() {
    uint32_t now = millis();

    for (uint8_t i = 0; i < entry_count; i++) {
        DigitalOutEntry &e = entries[i];
        if (!e.pattern) continue;

        if (now - e.last_ms < e.pattern[e.step].ms) continue;

        e.last_ms = now;
        e.step++;

        if (e.step >= e.pattern_len) {
            // end of one cycle
            e.step = 0;
            if (e.repeat != DIGITAL_OUT_REPEAT_FOREVER) {
                e.repeat_count++;
                if (e.repeat_count >= e.repeat) {
                    e.pattern = nullptr;
                    apply(e, false);
                    continue;
                }
            }
        }

        apply(e, e.pattern[e.step].on);
    }
}