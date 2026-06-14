#ifndef SENSOR_BMP180_H
#define SENSOR_BMP180_H

#ifdef HW_HAS_BMP180

#include "core/sensor_types.h"

extern Sensor_value_t bmp180_pressure;
extern Sensor_value_t bmp180_temp;

void bmp180_register(uint8_t i2c_addr);
void bmp180_read();

#endif // HW_HAS_BMP180

#endif // SENSOR_BMP180_H