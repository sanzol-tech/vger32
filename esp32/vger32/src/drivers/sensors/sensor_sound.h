#ifndef SENSOR_SOUND_H
#define SENSOR_SOUND_H

#ifdef HW_HAS_SOUND

#include "core/sensor_types.h"

extern Sensor_value_t sound_level;

void sound_register(uint8_t analog_pin);
void sound_read();

#endif // HW_HAS_SOUND

#endif // SENSOR_SOUND_H