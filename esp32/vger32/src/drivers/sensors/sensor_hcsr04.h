#ifndef SENSOR_HCSR04_H
#define SENSOR_HCSR04_H

#ifdef HW_HAS_HCSR04

#include "core/sensor_types.h"

extern Sensor_value_t hcsr04_distance;

void hcsr04_register(uint8_t trigger_pin, uint8_t echo_pin);
void hcsr04_read();

#endif // HW_HAS_HCSR04

#endif // SENSOR_HCSR04_H