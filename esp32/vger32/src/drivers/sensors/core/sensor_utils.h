#ifndef SENSOR_UTILS_H
#define SENSOR_UTILS_H

#include "sensor_types.h"

void sensor_add_sample(Sensor_value_t *tel, float value, uint32_t timestamp);

void sensor_clear_history(Sensor_value_t * tel);

#endif