#include "profiles/active_profile.h"

#ifdef HW_HAS_HCSR04

#include "drivers/sensors/sensor_hcsr04.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

static uint8_t trigger_pin;
static uint8_t echo_pin;
static bool initialized = false;

Sensor_value_t hcsr04_distance = {
    .hardware = &HW_HCSR04,
    .metric = &METRIC_DISTANCE,
    .history = {0}
};

void hcsr04_register(uint8_t trig_pin, uint8_t ech_pin) {
    trigger_pin = trig_pin;
    echo_pin = ech_pin;

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "HC-SR04", "DEMO mode @ Trigger:%u Echo:%u", trigger_pin, echo_pin);
#else
pinMode(trigger_pin, OUTPUT);
pinMode(echo_pin, INPUT);
sys_log(LOG_INFO, "HC-SR04", "Init @ Trigger:%u Echo:%u", trigger_pin, echo_pin);
#endif

initialized=true;
}

void hcsr04_read() {
    if (!initialized) return;

    float distance;

#ifdef HARDWARE_DEMO_MODE
distance=20.0f + random (-100, 300) / 10.0f;
#else
digitalWrite(trigger_pin, LOW);
delayMicroseconds (2);
digitalWrite(trigger_pin, HIGH);
delayMicroseconds (10);
digitalWrite(trigger_pin, LOW);

long duration = pulseIn(echo_pin, HIGH, 30000);
distance= duration* 0.034f / 2.0f;

    if (distance== 0 || distance> 400) {
        distance = -1.0f; // out of range
    }
#endif

uint32_t now = time_get_timestamp_fallback();
sensor_add_sample (&hcsr04_distance, distance, now);
}

#endif // HW_HAS_HCSR04