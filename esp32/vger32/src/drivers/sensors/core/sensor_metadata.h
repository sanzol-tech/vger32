/*
 * sensor_metadata.h
 *
 * Responsibility: Declarations for all known sensor hardware and metrics.
 */

#ifndef SENSOR_METADATA_H
#define SENSOR_METADATA_H

#include "sensor_types.h"

// ==========================================
// METRICS
// ==========================================
extern const Metric_t METRIC_TEMPERATURE;
extern const Metric_t METRIC_HUMIDITY;
extern const Metric_t METRIC_PRESSURE;
extern const Metric_t METRIC_MOTION;
extern const Metric_t METRIC_DISTANCE;
extern const Metric_t METRIC_SOUND_LEVEL;
extern const Metric_t METRIC_VIBRATION;
extern const Metric_t METRIC_EVENT;
extern const Metric_t METRIC_UV_INDEX;
extern const Metric_t METRIC_ACCEL_X;
extern const Metric_t METRIC_ACCEL_Y;
extern const Metric_t METRIC_ACCEL_Z;

// ==========================================
// HARDWARE
// ==========================================
extern const Hardware_t HW_SHT31;
extern const Hardware_t HW_BMP180;
extern const Hardware_t HW_BMP280;
extern const Hardware_t HW_PIR;
extern const Hardware_t HW_HCSR04;
extern const Hardware_t HW_SOUND;
extern const Hardware_t HW_SW420;
extern const Hardware_t HW_PUSH_BUTTON;
extern const Hardware_t HW_GUVA_S12S;
extern const Hardware_t HW_ADXL345;

#endif