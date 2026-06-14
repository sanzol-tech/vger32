/*
 * sensor_guva_s12s.cpp
 *
 * Responsibility: Driver for the CJMCU GUVA-S12S UV sensor (analog output).
 *
 * The GUVA-S12S outputs a voltage proportional to UV irradiance.
 * The UV index is derived from the ADC reading using the formula from the
 * datasheet: UV index ≈ (V_out / 0.1) where V_out is in volts.
 *
 * ADC reference: 3.3V, 12-bit resolution (0–4095).
 * Formula: UV_index = (adc * 3.3 / 4095) / 0.1
 *
 * Polling sensor — call guva_s12s_read() from sensor_reader at the desired interval.
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_GUVA_S12S

#include "drivers/sensors/sensor_guva_s12s.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

static uint8_t uv_pin = 0;
static bool initialized = false;

Sensor_value_t guva_uv_index = {
    .hardware = &HW_GUVA_S12S,
    .metric = &METRIC_UV_INDEX,
    .history = {0}
};

void guva_s12s_register(uint8_t analog_pin) {
    uv_pin = analog_pin;

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "GUVA-S12S", "DEMO mode @ pin %u", uv_pin);
#else
pinMode(uv_pin, INPUT);
sys_log(LOG_INFO, "GUVA-S12S", "Init @ pin %u", uv_pin);
#endif

initialized=true;
}

void guva_s12s_read() {
    if (!initialized) return;

    float uv_index;

#ifdef HARDWARE_DEMO_MODE
uv_index= random(0, 110) / 10.0f; // 0.0 – 11.0 UV index
#else
int raw = analogRead(uv_pin);
float voltage = raw * 3.3f / 4095.0f;
uv_index= voltage/ 0.1f;

    if (uv_index<0.0f) uv_index=0.0f;
#endif

uint32_t now = time_get_timestamp_fallback();
sensor_add_sample (&guva_uv_index, uv_index, now);
}

#endif // HW_HAS_GUVA_S12S