/*
 * sensor_push_button.cpp
 *
 * Responsibility: Driver for generic push buttons.
 *                 Supports up to PUSH_BUTTON_MAX buttons registered dynamically.
 *                 Interrupt-driven with debounce cooldown.
 *                 push_button_update() drives the simulation cycle in demo mode.
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_PUSH_BUTTON

#include <string.h>

#include "drivers/sensors/sensor_push_button.h"

#include "logger/sys_logger.h"
#include "core/sensor_interrupt_queue.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

// ==========================================
// INTERNAL STATE
// ==========================================
struct ButtonEntry {
    uint8_t pin;
    const char *name;
    Sensor_value_t sensor;
    volatile uint32_t last_press_ts;
};

static ButtonEntry entries[PUSH_BUTTON_MAX];
static uint8_t entry_count = 0;

static constexpr uint32_t BTN_INTERVAL_MIN_MS = 8000;
static constexpr uint32_t BTN_INTERVAL_MAX_MS = 15000;
static uint32_t btn_next_ms[PUSH_BUTTON_MAX] = {0};

// ==========================================
// PER-SLOT CALLBACKS
// Called by sensor_iqueue_process() in main loop context.
// One function per slot — plain fn ptr, no captures needed.
// ==========================================
static void btn_cb_0(float v, uint32_t ts) { sensor_add_sample(&entries[0].sensor, v, ts); }
static void btn_cb_1(float v, uint32_t ts) { sensor_add_sample(&entries[1].sensor, v, ts); }
static void btn_cb_2(float v, uint32_t ts) { sensor_add_sample(&entries[2].sensor, v, ts); }
static void btn_cb_3(float v, uint32_t ts) { sensor_add_sample(&entries[3].sensor, v, ts); }
static void btn_cb_4(float v, uint32_t ts) { sensor_add_sample(&entries[4].sensor, v, ts); }
static void btn_cb_5(float v, uint32_t ts) { sensor_add_sample(&entries[5].sensor, v, ts); }
static void btn_cb_6(float v, uint32_t ts) { sensor_add_sample(&entries[6].sensor, v, ts); }
static void btn_cb_7(float v, uint32_t ts) { sensor_add_sample(&entries[7].sensor, v, ts); }

static const iqueue_callback_t callbacks[PUSH_BUTTON_MAX] = {
    btn_cb_0, btn_cb_1, btn_cb_2, btn_cb_3,
    btn_cb_4, btn_cb_5, btn_cb_6, btn_cb_7
};

// ==========================================
// PER-SLOT ISR STUBS (real hardware only)
// Defined before push_button_register() so the attachInterrupt call below
// can see them. Each stub applies the cooldown and pushes to the queue.
// ==========================================
#ifndef HARDWARE_DEMO_MODE

static void IRAM_ATTR btn_isr(uint8_t index) {
    uint32_t ts = millis();
    if (ts - entries[index].last_press_ts < BUTTON_COOLDOWN_MS) return;
    entries[index].last_press_ts = ts;
    sensor_iqueue_push(callbacks[index], 1.0f, ts / 1000);
}

static void IRAM_ATTR btn_isr_0() { btn_isr(0); }
static void IRAM_ATTR btn_isr_1() { btn_isr(1); }
static void IRAM_ATTR btn_isr_2() { btn_isr(2); }
static void IRAM_ATTR btn_isr_3() { btn_isr(3); }
static void IRAM_ATTR btn_isr_4() { btn_isr(4); }
static void IRAM_ATTR btn_isr_5() { btn_isr(5); }
static void IRAM_ATTR btn_isr_6() { btn_isr(6); }
static void IRAM_ATTR btn_isr_7() { btn_isr(7); }

typedef void (*isr_fn_t)();

static const isr_fn_t isr_stubs[PUSH_BUTTON_MAX] = {
    btn_isr_0, btn_isr_1, btn_isr_2, btn_isr_3,
    btn_isr_4, btn_isr_5, btn_isr_6, btn_isr_7
};

#endif

// ==========================================
// PUBLIC API
// ==========================================
int push_button_register(uint8_t pin, const char *name) {
    if (entry_count >= PUSH_BUTTON_MAX) {
        sys_log(LOG_INFO, "Button", "Max buttons reached");
        return -1;
    }

    uint8_t i = entry_count++;

    entries[i].pin = pin;
    entries[i].name = name;
    entries[i].last_press_ts = 0;
    memset(&entries[i].sensor, 0, sizeof(Sensor_value_t));
    entries[i].sensor.hardware = &HW_PUSH_BUTTON;
    entries[i].sensor.metric = &METRIC_EVENT;

#ifndef HARDWARE_DEMO_MODE
pinMode(pin, INPUT_PULLUP);
attachInterrupt (digitalPinToInterrupt(pin), isr_stubs[i], FALLING);
sys_log(LOG_INFO, "Button", "'%s' slot %u", name, i);
#else
sys_log(LOG_INFO, "Button", "'%s' slot %u (DEMO)", name, i);
#endif

return i;
}

#ifdef HARDWARE_DEMO_MODE

void push_button_update() {
    uint32_t now = millis();
    for (uint8_t i = 0; i < entry_count; i++) {
        if (btn_next_ms[i] == 0) {
            btn_next_ms[i] = now + random(BTN_INTERVAL_MIN_MS, BTN_INTERVAL_MAX_MS);
        }
        if (now >= btn_next_ms[i]) {
            btn_next_ms[i] = now + random(BTN_INTERVAL_MIN_MS, BTN_INTERVAL_MAX_MS);
            sensor_iqueue_push(callbacks[i], 1.0f, time_get_timestamp_fallback());
        }
    }
}

#else

void push_button_update() {
    // interrupt-driven — ISR stubs → iqueue → btn_cb_N
}

#endif

#endif // HW_HAS_PUSH_BUTTON