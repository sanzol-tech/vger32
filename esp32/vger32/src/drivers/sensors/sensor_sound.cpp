#include "profiles/active_profile.h"

#ifdef HW_HAS_SOUND

#include "drivers/sensors/sensor_sound.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

static uint8_t analog_pin;
static bool initialized = false;

Sensor_value_t sound_level = {
    .hardware = &HW_SOUND,
    .metric = &METRIC_SOUND_LEVEL,
    .history = {0}
};

void sound_register(uint8_t pin) {
    analog_pin = pin;

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "Sound", "DEMO mode @ pin %u", analog_pin);
#else
pinMode(analog_pin, INPUT);
sys_log(LOG_INFO, "Sound", "Init @ pin %u", analog_pin);
#endif

initialized=true;
}

void sound_read() {
    if (!initialized) return;

    float db;

#ifdef HARDWARE_DEMO_MODE
db=40.0f + random (-100, 400) / 10.0f;
#else
int raw = analogRead(analog_pin);
db= map(raw, 0, 4095, 30, 100);
#endif

uint32_t now = time_get_timestamp_fallback();
sensor_add_sample (&sound_level, db, now);
}

#endif // HW_HAS_SOUND