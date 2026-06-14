#ifndef SENSOR_CATALOG_H
#define SENSOR_CATALOG_H

#include "sensor_types.h"

void sensor_catalog_init();

Sensor_value_t *sensor_catalog_find(const char *hardware_code, const char *metric_code);

Sensor_value_t **sensor_catalog_get_all();

uint8_t sensor_catalog_get_count();

#endif