/*
 * sensor_sw420.h
 *
 * Responsibility: Driver for the SW-420 vibration sensor.
 *                 Interrupt-driven — fires on CHANGE and pushes to the iqueue.
 *                 sw420_update() drives the simulation cycle in demo mode.
 */

#ifndef SENSOR_SW420_H
#define SENSOR_SW420_H

#ifdef HW_HAS_SW420

#include "core/sensor_types.h"

extern Sensor_value_t sw420_vibration;

void sw420_register(uint8_t pin);
void sw420_update();

#endif // HW_HAS_SW420

#endif // SENSOR_SW420_H