/*
 * sensor_pir.h
 *
 * Responsibility: Driver for the HC-SR501 PIR motion sensor.
 *                 Interrupt-driven — fires on CHANGE and pushes to the iqueue.
 *                 pir_update() drives the simulation cycle in demo mode.
 */

#ifndef SENSOR_PIR_H
#define SENSOR_PIR_H

#ifdef HW_HAS_PIR

#include "core/sensor_types.h"

extern Sensor_value_t pir_motion;

void pir_register(uint8_t pin);
void pir_update();

#endif // HW_HAS_PIR

#endif // SENSOR_PIR_H