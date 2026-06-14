/*
 * sensor_sw420.cpp
 *
 * Responsibility: Driver for the SW-420 vibration sensor.
 *                 Interrupt-driven — fires on CHANGE and pushes to the iqueue.
 *                 sw420_update() drives the simulation cycle in demo mode.
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_SW420

#include "drivers/sensors/sensor_sw420.h"

#include "logger/sys_logger.h"
#include "core/sensor_interrupt_queue.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

// ==========================================
// STATE
// ==========================================
static uint8_t vibration_pin = 0;
static bool initialized = false;
static uint32_t sw420_last_ms = 0;

static constexpr uint32_t SW420_INTERVAL_MS = 11000;

// ==========================================
// SENSOR VALUES
// ==========================================
Sensor_value_t sw420_vibration = {
    .hardware = &HW_SW420,
    .metric = &METRIC_VIBRATION,
    .history = {0}
};

// ==========================================
// FORWARD DECLARATIONS
// ==========================================
#ifndef HARDWARE_DEMO_MODE
static void IRAM_ATTR sw420_isr();
#endif

// ==========================================
// CALLBACK — runs in main loop context
// ==========================================
static void sw420_on_event(float value, uint32_t timestamp) {
    sensor_add_sample(&sw420_vibration, value, timestamp);
}

// ==========================================
// PUBLIC API
// ==========================================
void sw420_register(uint8_t pin) {
    vibration_pin = pin;

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "SW-420", "DEMO mode");
#else
pinMode(vibration_pin, INPUT);
attachInterrupt (digitalPinToInterrupt(vibration_pin), sw420_isr, CHANGE);
sys_log(LOG_INFO, "SW-420", "Init @ pin %u", vibration_pin);
#endif

initialized=true;
}

#ifdef HARDWARE_DEMO_MODE

void sw420_update() {
    uint32_t now = millis();
    if (now - sw420_last_ms >= SW420_INTERVAL_MS) {
        sw420_last_ms = now;
        sensor_iqueue_push(sw420_on_event, 1.0f, time_get_timestamp_fallback());
    }
}

#else

// ISR — minimal work, runs in IRAM
// SW-420 gives LOW on vibration detected
static void IRAM_ATTR sw420_isr() {
    if (digitalRead(vibration_pin) == HIGH) return;
    sensor_iqueue_push(sw420_on_event, 1.0f, millis() / 1000);
}

void sw420_update() {
    // interrupt-driven — ISR → iqueue → sw420_on_event
}

#endif

#endif // HW_HAS_SW420