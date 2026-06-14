#ifndef SENSOR_BMP280_H
#define SENSOR_BMP280_H

#ifdef HW_HAS_BMP280

#include "core/sensor_types.h"
extern Sensor_value_t bmp280_pressure;
extern Sensor_value_t bmp280_temp;

void bmp280_register(uint8_t addr = 0x76);
void bmp280_read();

#endif // HW_HAS_BMP280

#endif // SENSOR_BMP280_H