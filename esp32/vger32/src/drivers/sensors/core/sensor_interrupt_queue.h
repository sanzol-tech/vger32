#ifndef SENSOR_INTERRUPT_QUEUE_H
#define SENSOR_INTERRUPT_QUEUE_H

#include <Arduino.h>

// ==========================================
// SENSOR INTERRUPT QUEUE
//
// ISR-safe circular queue for interrupt-driven sensors.
// The ISR calls sensor_iqueue_push() — minimal work, no blocking.
// The main loop calls sensor_iqueue_process() to process pending events.
//
// To add a new interrupt-driven sensor:
//   1. Write an ISR that calls sensor_iqueue_push()
//   2. Register the ISR with attachInterrupt() in the driver init
//   3. That's it — sensor_iqueue_process() handles the rest
// ==========================================

static constexpr uint8_t SENSOR_IQUEUE_SIZE = 32;

// Callback called by sensor_iqueue_process() for each pending event.
// Runs in main loop context — safe to call sensor_add_sample() here.
typedef void (*iqueue_callback_t)(float value, uint32_t timestamp);

// Push an event from an ISR.
// IRAM_ATTR ensures the function lives in RAM — required for ISR on ESP32.
void IRAM_ATTR sensor_iqueue_push(iqueue_callback_t callback, float value, uint32_t timestamp);

// Process all pending events. Call once per loop from sensor_reader_update().
void sensor_iqueue_process();

#endif