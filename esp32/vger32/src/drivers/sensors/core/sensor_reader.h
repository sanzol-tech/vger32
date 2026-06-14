#ifndef SENSOR_READER_H
#define SENSOR_READER_H

#include <Arduino.h>

// ==========================================
// INTERVALS
// ==========================================
static constexpr uint32_t INTERVAL_FAST_MS = 5000;
static constexpr uint32_t INTERVAL_MEDIUM_MS = 30000;
static constexpr uint32_t INTERVAL_SLOW_MS = 60000;

void sensor_reader_init();

void sensor_reader_update();

void sensor_reader_read_all();

#endif