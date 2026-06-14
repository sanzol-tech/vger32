/*
 * sensor_catalog.cpp
 *
 * Responsibility: Central registry of all active Sensor_value_t instances.
 *                 Provides lookup and enumeration for the serializer and dashboard API.
 *
 * Only sensors enabled via HW_HAS_* in the active hardware profile are
 * included. This keeps the catalog (and the resulting binary) minimal.
 *
 * To add a sensor:
 *   1. Add HW_HAS_<NAME> guard block below with its extern declarations
 *   2. Add the same guard in the all_sensors[] array
 *   3. The sensor's own driver .cpp must define the Sensor_value_t
 */

#include "sensor_catalog.h"

#include "logger/sys_logger.h"
#include "profiles/active_profile.h"

// ==========================================
// EXTERN DECLARATIONS
// ==========================================

#ifdef HW_HAS_SHT31
extern Sensor_value_t sht31_temp;
extern Sensor_value_t sht31_hum;
#endif

#ifdef HW_HAS_BMP180
extern Sensor_value_t bmp180_temp;
extern Sensor_value_t bmp180_pressure;
#endif

#ifdef HW_HAS_BMP280
extern Sensor_value_t bmp280_temp;
extern Sensor_value_t bmp280_pressure;
#endif

#ifdef HW_HAS_PIR
extern Sensor_value_t pir_motion;
#endif

#ifdef HW_HAS_HCSR04
extern Sensor_value_t hcsr04_distance;
#endif

#ifdef HW_HAS_SW420
extern Sensor_value_t sw420_vibration;
#endif

#ifdef HW_HAS_SOUND
extern Sensor_value_t sound_level;
#endif

#ifdef HW_HAS_GUVA_S12S
extern Sensor_value_t guva_uv_index;
#endif

#ifdef HW_HAS_ADXL345
extern Sensor_value_t adxl345_x;
extern Sensor_value_t adxl345_y;
extern Sensor_value_t adxl345_z;
#endif

// ==========================================
// CATALOG ARRAY
// ==========================================

static Sensor_value_t *all_sensors[] = {
#ifdef HW_HAS_SHT31
    &sht31_temp,
    &sht31_hum,
#endif
#ifdef HW_HAS_BMP180
    &bmp180_temp,
    &bmp180_pressure,
#endif
#ifdef HW_HAS_BMP280
    &bmp280_temp,
    &bmp280_pressure,
#endif
#ifdef HW_HAS_PIR
    &pir_motion,
#endif
#ifdef HW_HAS_HCSR04
    &hcsr04_distance,
#endif
#ifdef HW_HAS_SW420
    &sw420_vibration,
#endif
#ifdef HW_HAS_SOUND
    &sound_level,
#endif
#ifdef HW_HAS_GUVA_S12S
    &guva_uv_index,
#endif
#ifdef HW_HAS_ADXL345
    &adxl345_x,
    &adxl345_y,
    &adxl345_z,
#endif
    NULL // sentinel
};

static const uint8_t SENSOR_COUNT = sizeof(all_sensors) / sizeof(all_sensors[0]) - 1;

// ==========================================
// PUBLIC API
// ==========================================

void sensor_catalog_init() {
    sys_log(LOG_INFO, "Sensors", "Catalog initialized (%u sensors)", SENSOR_COUNT);
}

Sensor_value_t *sensor_catalog_find(const char *hardware_code, const char *metric_code) {
    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
        Sensor_value_t *tel = all_sensors[i];
        if (strcmp(tel->hardware->code, hardware_code) == 0 &&
            strcmp(tel->metric->code, metric_code) == 0) {
            return tel;
        }
    }
    return NULL;
}

Sensor_value_t **sensor_catalog_get_all() {
    return all_sensors;
}

uint8_t sensor_catalog_get_count() {
    return SENSOR_COUNT;
}