/*
 * sensor_guva_s12s.h
 *
 * Responsibility: Driver for the CJMCU GUVA-S12S UV sensor.
 *                 Analog output proportional to UV intensity.
 */

#ifndef SENSOR_GUVA_S12S_H
#define SENSOR_GUVA_S12S_H

#ifdef HW_HAS_GUVA_S12S

#include "core/sensor_types.h"

extern Sensor_value_t guva_uv_index;

void guva_s12s_register(uint8_t analog_pin);
void guva_s12s_read();

#endif // HW_HAS_GUVA_S12S

#endif // SENSOR_GUVA_S12S_H