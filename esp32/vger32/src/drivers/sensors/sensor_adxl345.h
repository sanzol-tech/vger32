/*
 * sensor_adxl345.h
 *
 * Responsibility: Driver for the ADXL345 3-axis accelerometer.
 *                 Direct per-tick sampling (no FIFO) via getEvent().
 *                 adxl345_update() drives both the real and simulated cycles.
 */

#ifndef SENSOR_ADXL345_H
#define SENSOR_ADXL345_H

#ifdef HW_HAS_ADXL345

#include "core/sensor_types.h"

extern Sensor_value_t adxl345_x;
extern Sensor_value_t adxl345_y;
extern Sensor_value_t adxl345_z;

// int_pin: GPIO connected to ADXL345 INT1. Unused in current driver
//   (no FIFO + interrupt path); kept for future FIFO-based revision.
// addr:    I2C address — 0x53 (SDO low, default) or 0x1D (SDO high).
void adxl345_register(uint8_t int_pin, uint8_t addr = 0x53);

void adxl345_update();

#endif // HW_HAS_ADXL345

#endif // SENSOR_ADXL345_H