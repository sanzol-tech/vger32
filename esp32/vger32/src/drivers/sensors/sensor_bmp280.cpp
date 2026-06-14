#include "profiles/active_profile.h"

#ifdef HW_HAS_BMP280

#include "drivers/sensors/sensor_bmp280.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

#ifndef HARDWARE_DEMO_MODE
#include <Adafruit_BMP280.h>

static Adafruit_BMP280 bmp;
#endif

static bool initialized = false;

Sensor_value_t bmp280_pressure = {
    .hardware = &HW_BMP280,
    .metric = &METRIC_PRESSURE,
    .history = {0}
};

Sensor_value_t bmp280_temp = {
    .hardware = &HW_BMP280,
    .metric = &METRIC_TEMPERATURE,
    .history = {0}
};

void bmp280_register(uint8_t addr) {

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "BMP280", "DEMO mode");
initialized=true;
#else
if (!bmp.begin (addr)) {
        sys_log(LOG_INFO, "BMP280", "Sensor not found @ 0x%02X — check wiring", addr);
        return;
    }
bmp.setSampling(Adafruit_BMP280::MODE_NORMAL,
                Adafruit_BMP280::SAMPLING_X2, // temperature
                Adafruit_BMP280::SAMPLING_X16, // pressure
                Adafruit_BMP280::FILTER_X16,
                Adafruit_BMP280::STANDBY_MS_500);
sys_log(LOG_INFO, "BMP280", "Init @ 0x%02X", addr);
initialized=true;
#endif
}

void bmp280_read() {
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
sensor_add_sample (&bmp280_pressure, pressure, now);
sensor_add_sample (&bmp280_temp, temp, now);
}

#endif // HW_HAS_BMP280