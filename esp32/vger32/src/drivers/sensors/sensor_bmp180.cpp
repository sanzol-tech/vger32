#include "profiles/active_profile.h"

#ifdef HW_HAS_BMP180

#include "drivers/sensors/sensor_bmp180.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

#ifndef HARDWARE_DEMO_MODE
#include <Adafruit_BMP085.h>

static Adafruit_BMP085 bmp;
#endif

static bool initialized = false;

Sensor_value_t bmp180_pressure = {
    .hardware = &HW_BMP180,
    .metric = &METRIC_PRESSURE,
    .history = {0}
};

Sensor_value_t bmp180_temp = {
    .hardware = &HW_BMP180,
    .metric = &METRIC_TEMPERATURE,
    .history = {0}
};

void bmp180_register(uint8_t addr) {

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "BMP180", "DEMO mode");
initialized=true;
#else
if (!bmp.begin()) {
        sys_log(LOG_INFO, "BMP180", "Sensor not found — check wiring");
        return;
    }
sys_log(LOG_INFO, "BMP180", "Init @ 0x%02X", addr);
initialized=true;
#endif
}

void bmp180_read() {
    if (!initialized) return;

    float pressure, temp;

#ifdef HARDWARE_DEMO_MODE
pressure=1013.25f + random (-100, 100) / 10.0f;
temp=22.0f + random (-30, 30) / 10.0f;
#else
pressure= bmp.readPressure()/ 100.0f; // Pa → hPa
temp= bmp.readTemperature();
#endif

uint32_t now = time_get_timestamp_fallback();
sensor_add_sample (&bmp180_pressure, pressure, now);
sensor_add_sample (&bmp180_temp, temp, now);
}

#endif // HW_HAS_BMP180