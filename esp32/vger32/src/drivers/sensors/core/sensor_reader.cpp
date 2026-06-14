/*
 * sensor_reader.cpp
 *
 * Responsibility: Orchestrates all sensor drivers — initialization, polling intervals,
 *                 and interrupt queue processing.
 *
 * Sensors are grouped by read frequency:
 *   fast   (5s)  — high-frequency or event-driven sensors
 *   medium (30s) — distance, UV
 *   slow   (60s) — temperature, pressure, humidity
 *
 * Interrupt-driven sensors (PIR, SW420, push button, ADXL345) expose update()
 * which is called unconditionally every cycle. Each driver knows internally
 * whether to simulate events or rely on its ISR.
 *
 * Only drivers enabled via HW_HAS_* in the active hardware profile are
 * included in the build.
 *
 * To add a sensor:
 *   1. Include its header here
 *   2. Register it in sensor_reader_init() under its HW_HAS_<n> guard
 *   3. Add its read() call to the appropriate group (or call update() if interrupt-driven)
 *   4. Add its Sensor_value_t to sensor_catalog.cpp
 */

#include "sensor_reader.h"

#include "profiles/active_profile.h"

#include "../sensor_adxl345.h"
#include "../sensor_bmp180.h"
#include "../sensor_bmp280.h"
#include "../sensor_guva_s12s.h"
#include "../sensor_hcsr04.h"
#include "../sensor_pir.h"
#include "../sensor_push_button.h"
#include "../sensor_sht31.h"
#include "../sensor_sound.h"
#include "../sensor_sw420.h"
#include "logger/sys_logger.h"
#include "sensor_interrupt_queue.h"

static uint32_t last_fast = 0;
static uint32_t last_medium = 0;
static uint32_t last_slow = 0;

// ==========================================
// GROUPS
// Edit these functions to move sensors between groups.
// ==========================================
static void read_group_fast() {
#ifdef HW_HAS_SOUND
    sound_read();
#endif
}

static void read_group_medium() {
#ifdef HW_HAS_HCSR04
    hcsr04_read();
#endif
#ifdef HW_HAS_GUVA_S12S
    guva_s12s_read();
#endif
}

static void read_group_slow() {
#ifdef HW_HAS_SHT31
    sht31_read();
#endif
#ifdef HW_HAS_BMP180
    bmp180_read();
#endif
#ifdef HW_HAS_BMP280
    bmp280_read();
#endif
}

// ==========================================
// PUBLIC API
// ==========================================
void sensor_reader_init() {
    sys_log(LOG_INFO, "Sensors", "Initializing...");

#ifdef HW_HAS_SHT31
    sht31_register(0x44);
#endif
#ifdef HW_HAS_BMP180
    bmp180_register(0x77);
#endif
#ifdef HW_HAS_BMP280
    bmp280_register(0x76);
#endif
#ifdef HW_HAS_HCSR04
    hcsr04_register(12, 13);
#endif
#ifdef HW_HAS_SOUND
    sound_register(34);
#endif
#ifdef HW_HAS_SW420
    sw420_register(15);
#endif
#ifdef HW_HAS_PIR
    pir_register(14);
#endif
#ifdef HW_HAS_GUVA_S12S
    guva_s12s_register(35);
#endif
#ifdef HW_HAS_ADXL345
    adxl345_register(/* INT1 pin */ 32, /* I2C addr */ 0x53);
#endif
#ifdef HW_HAS_PUSH_BUTTON
    push_button_register(12, "config");
    push_button_register(13, "reset");
#endif

    sys_log(LOG_INFO, "Sensors", "Initialized");
}

void sensor_reader_update() {
    uint32_t now = millis();

    // Process interrupt-driven events first (ISR → iqueue → callback)
    sensor_iqueue_process();

    // Interrupt-driven drivers — each update() is a no-op on real hardware
#ifdef HW_HAS_PIR
    pir_update();
#endif
#ifdef HW_HAS_SW420
    sw420_update();
#endif
#ifdef HW_HAS_ADXL345
    adxl345_update();
#endif
#ifdef HW_HAS_PUSH_BUTTON
    push_button_update();
#endif

    if (now - last_fast >= INTERVAL_FAST_MS) {
        last_fast = now;
        read_group_fast();
    }

    if (now - last_medium >= INTERVAL_MEDIUM_MS) {
        last_medium = now;
        read_group_medium();
    }

    if (now - last_slow >= INTERVAL_SLOW_MS) {
        last_slow = now;
        read_group_slow();
    }
}

void sensor_reader_read_all() {
    uint32_t now = millis();
    sensor_iqueue_process();
    read_group_fast();
    read_group_medium();
    read_group_slow();
    last_fast = last_medium = last_slow = now;
}