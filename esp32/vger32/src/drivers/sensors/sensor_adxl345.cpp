/*
 * sensor_adxl345.cpp
 *
 * Responsibility: Driver for the ADXL345 3-axis accelerometer.
 *                 Direct sampling per _update() tick — no FIFO, no interrupt.
 *                 adxl345_update() drives both the real and simulated cycles.
 *
 * Rationale:
 *   The Adafruit ADXL345 library (1.3.4) does not expose FIFO control APIs
 *   (setFIFOMode, setFIFOSamples, enableInterrupts, getFIFOSamples). The
 *   previous implementation depended on those and did not compile. This
 *   version reads one sample per tick via getEvent() — simpler and
 *   sufficient for telemetry-style use cases.
 *
 *   If a future mission requires FIFO + watermark interrupt for burst
 *   sampling, bypass the library and talk to registers directly via Wire
 *   (FIFO_CTL @ 0x38, INT_ENABLE @ 0x2E, FIFO_STATUS @ 0x39).
 */

#include "profiles/active_profile.h"

#ifdef HW_HAS_ADXL345

#include "drivers/sensors/sensor_adxl345.h"

#include "logger/sys_logger.h"
#include "core/sensor_metadata.h"
#include "core/sensor_utils.h"
#include "network/time_manager.h"

#ifndef HARDWARE_DEMO_MODE

#include <Adafruit_ADXL345_U.h>

static Adafruit_ADXL345_Unified adxl(12345); // unique sensor ID
#endif

// ==========================================
// CONFIGURATION
// ==========================================
static constexpr uint32_t ADXL345_INTERVAL_MS = 3000;

// ==========================================
// STATE
// ==========================================
static bool initialized = false;
static uint32_t adxl_last_ms = 0;

// ==========================================
// SENSOR VALUES
// ==========================================
Sensor_value_t adxl345_x = {
    .hardware = &HW_ADXL345,
    .metric = &METRIC_ACCEL_X,
    .history = {0}
};

Sensor_value_t adxl345_y = {
    .hardware = &HW_ADXL345,
    .metric = &METRIC_ACCEL_Y,
    .history = {0}
};

Sensor_value_t adxl345_z = {
    .hardware = &HW_ADXL345,
    .metric = &METRIC_ACCEL_Z,
    .history = {0}
};

// ==========================================
// PUBLIC API
// ==========================================
void adxl345_register(uint8_t int_pin, uint8_t addr) {
    (void) int_pin; // unused — FIFO/interrupt path removed

#ifdef HARDWARE_DEMO_MODE
sys_log(LOG_INFO, "ADXL345", "DEMO mode @ I2C 0x%02X", addr);
initialized=true;
#else
if (!adxl.begin (addr)) {
        sys_log(LOG_INFO, "ADXL345", "Not found @ 0x%02X — check wiring", addr);
        return;
    }

adxl.setRange (ADXL345_RANGE_16_G);
adxl.setDataRate (ADXL345_DATARATE_100_HZ);

sys_log(LOG_INFO, "ADXL345", "Init @ 0x%02X", addr);
initialized=true;
#endif
}

void adxl345_update() {
    if (!initialized) return;

    uint32_t now = millis();
    if (now - adxl_last_ms < ADXL345_INTERVAL_MS) return;
    adxl_last_ms = now;

    uint32_t ts = time_get_timestamp_fallback();

#ifdef HARDWARE_DEMO_MODE
sensor_add_sample (&adxl345_x, random (-200, 200) / 100.0f, ts);
sensor_add_sample (&adxl345_y, random (-200, 200) / 100.0f, ts);
sensor_add_sample (&adxl345_z, random (800, 1200) / 100.0f, ts); // ~1g on Z at rest
#else
sensors_event_t event;
adxl.getEvent (&event);

// Library returns m/s^2. Divide by 9.80665 to express in g.
sensor_add_sample (&adxl345_x, event.acceleration.x/ 9.80665f, ts);
sensor_add_sample (&adxl345_y, event.acceleration.y/ 9.80665f, ts);
sensor_add_sample (&adxl345_z, event.acceleration.z/ 9.80665f, ts);
#endif
}

#endif // HW_HAS_ADXL345