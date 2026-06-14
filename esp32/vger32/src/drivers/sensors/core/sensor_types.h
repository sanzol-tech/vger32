#ifndef SENSOR_TYPES_H
#define SENSOR_TYPES_H

#include <Arduino.h>

static constexpr uint8_t SENSOR_HISTORY_SIZE = 10;

// ==========================================
// METADATA
// ==========================================
typedef struct {
    const char *code;
    const char *label;
    const char *unit;
} Metric_t;

typedef struct {
    const char *code;
    const char *model;
    const char *brand;
} Hardware_t;

// ==========================================
// DATA POINT
// ==========================================
typedef struct {
    uint32_t timestamp;
    float value;
} Data_point_t;

// ==========================================
// HISTORY
// ==========================================
typedef struct {
    Data_point_t samples[SENSOR_HISTORY_SIZE];
    uint8_t cursor;
    uint8_t count;
} Sensor_history_t;

// ==========================================
// SENSOR VALUE
// ==========================================
typedef struct {
    const Hardware_t *hardware;
    const Metric_t *metric;

    Data_point_t latest;
    Sensor_history_t history;
} Sensor_value_t;

#endif