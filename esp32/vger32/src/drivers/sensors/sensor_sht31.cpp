#include "profiles/active_profile.h"

#ifdef HW_HAS_SHT31

#include "drivers/sensors/sensor_sht31.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

#ifndef HARDWARE_DEMO_MODE
#include <Adafruit_SHT31.h>
#include <Wire.h>

static Adafruit_SHT31 sht;
#endif

static bool initialized = false;

Sensor_value_t sht31_temp = {
    .hardware = &HW_SHT31,
    .metric = &METRIC_TEMPERATURE,
    .history = {0}
};

Sensor_value_t sht31_hum = {
    .hardware = &HW_SHT31,
    .metric = &METRIC_HUMIDITY,
    .history = {0}
};

void sht31_register(uint8_t addr) {

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "SHT31", "DEMO mode");
initialized=true;
#else
if (!sht.begin (addr)) {
        sys_log(LOG_INFO, "SHT31", "Sensor not found @ 0x%02X — check wiring", addr);
        return;
    }
sys_log(LOG_INFO, "SHT31", "Init @ 0x%02X", addr);
initialized=true;
#endif
}

void sht31_read() {
    if (!initialized) return;

    float temp, hum;

#ifdef HARDWARE_DEMO_MODE
temp=20.0f + random (-50, 100) / 10.0f;
hum=50.0f + random (-200, 200) / 10.0f;
#else
temp= sht.readTemperature();
hum= sht.readHumidity();
    if (isnan(temp)|| isnan (hum)) {
        sys_log(LOG_INFO, "SHT31", "Read failed");
        return;
    }
#endif

uint32_t now = time_get_timestamp_fallback();
sensor_add_sample (&sht31_temp, temp, now);
sensor_add_sample (&sht31_hum, hum, now);
}

#endif // HW_HAS_SHT31