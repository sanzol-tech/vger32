/*
 * sensor_pir.cpp
 *
 * Responsibility: Driver for the HC-SR501 PIR motion sensor.
 *                 Interrupt-driven — fires on CHANGE and pushes to the iqueue.
 *                 pir_update() drives the simulation cycle in demo mode.
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_PIR

#include "drivers/sensors/sensor_pir.h"

#include "logger/sys_logger.h"
#include "core/sensor_interrupt_queue.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

// ==========================================
// STATE
// ==========================================
static uint8_t pir_pin = 0;
static bool initialized = false;
static uint32_t pir_last_ms = 0;

static constexpr uint32_t PIR_INTERVAL_MS = 7000;

// ==========================================
// SENSOR VALUES
// ==========================================
Sensor_value_t pir_motion = {
    .hardware = &HW_PIR,
    .metric = &METRIC_MOTION,
    .history = {0}
};

// ==========================================
// FORWARD DECLARATIONS
// ==========================================
#ifndef HARDWARE_DEMO_MODE
static void IRAM_ATTR pir_isr();
#endif

// ==========================================
// CALLBACK — runs in main loop context
// ==========================================
static void pir_on_event(float value, uint32_t timestamp) {
    sensor_add_sample(&pir_motion, value, timestamp);
}

// ==========================================
// PUBLIC API
// ==========================================
void pir_register(uint8_t pin) {
    pir_pin = pin;

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "PIR", "DEMO mode");
#else
pinMode(pir_pin, INPUT);
attachInterrupt (digitalPinToInterrupt(pir_pin), pir_isr, CHANGE);
sys_log(LOG_INFO, "PIR", "Init @ pin %u", pir_pin);
#endif

initialized=true;
}

#ifdef HARDWARE_DEMO_MODE

void pir_update() {
    uint32_t now = millis();
    if (now - pir_last_ms >= PIR_INTERVAL_MS) {
        pir_last_ms = now;
        sensor_iqueue_push(pir_on_event, 1.0f, time_get_timestamp_fallback());
    }
}

#else

// ISR — minimal work, runs in IRAM
static void IRAM_ATTR pir_isr() {
    if (digitalRead(pir_pin) == LOW) return; // motion ended — ignore
    sensor_iqueue_push(pir_on_event, 1.0f, millis() / 1000);
}

void pir_update() {
    // interrupt-driven — ISR → iqueue → pir_on_event
}

#endif

#endif // HW_HAS_PIR