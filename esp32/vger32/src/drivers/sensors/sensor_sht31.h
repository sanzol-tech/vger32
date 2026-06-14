#ifndef SENSOR_SHT31_H
#define SENSOR_SHT31_H

#ifdef HW_HAS_SHT31

#include "core/sensor_types.h"

extern Sensor_value_t sht31_temp;
extern Sensor_value_t sht31_hum;

void sht31_register(uint8_t i2c_addr);
void sht31_read();

#endif // HW_HAS_SHT31

#endif // SENSOR_SHT31_H