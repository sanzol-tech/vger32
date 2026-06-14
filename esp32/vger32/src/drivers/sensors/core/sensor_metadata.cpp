/*
 * sensor_metadata.cpp
 *
 * Responsibility: Constant definitions for all known sensor hardware and metrics.
 *                 Referenced by Sensor_value_t instances in each driver.
 */

#include "sensor_metadata.h"

// ==========================================
// METRICS
// ==========================================

const Metric_t METRIC_TEMPERATURE = {
    .code = "temp",
    .label = "Temperature",
    .unit = "°C"
};

const Metric_t METRIC_HUMIDITY = {
    .code = "hum",
    .label = "Humidity",
    .unit = "%"
};

const Metric_t METRIC_PRESSURE = {
    .code = "pressure",
    .label = "Pressure",
    .unit = "hPa"
};

const Metric_t METRIC_MOTION = {
    .code = "motion",
    .label = "Motion",
    .unit = ""
};

const Metric_t METRIC_DISTANCE = {
    .code = "distance",
    .label = "Distance",
    .unit = "cm"
};

const Metric_t METRIC_SOUND_LEVEL = {
    .code = "sound",
    .label = "Sound Level",
    .unit = "dB"
};

const Metric_t METRIC_VIBRATION = {
    .code = "vibration",
    .label = "Vibration",
    .unit = ""
};

const Metric_t METRIC_EVENT = {
    .code = "event",
    .label = "Event",
    .unit = ""
};

const Metric_t METRIC_UV_INDEX = {
    .code = "uv",
    .label = "UV Index",
    .unit = "UV"
};

const Metric_t METRIC_ACCEL_X = {
    .code = "ax",
    .label = "Accel X",
    .unit = "g"
};

const Metric_t METRIC_ACCEL_Y = {
    .code = "ay",
    .label = "Accel Y",
    .unit = "g"
};

const Metric_t METRIC_ACCEL_Z = {
    .code = "az",
    .label = "Accel Z",
    .unit = "g"
};

// ==========================================
// HARDWARE
// ==========================================

const Hardware_t HW_SHT31 = {
    .code = "sht31",
    .model = "SHT31",
    .brand = "Sensirion"
};

const Hardware_t HW_BMP180 = {
    .code = "bmp180",
    .model = "BMP180",
    .brand = "Bosch"
};

const Hardware_t HW_BMP280 = {
    .code = "bmp280",
    .model = "BMP280",
    .brand = "Bosch"
};

const Hardware_t HW_PIR = {
    .code = "pir",
    .model = "HC-SR501",
    .brand = "Generic"
};

const Hardware_t HW_HCSR04 = {
    .code = "hcsr04",
    .model = "HC-SR04",
    .brand = "Generic"
};

const Hardware_t HW_SOUND = {
    .code = "sound",
    .model = "Sound Sensor",
    .brand = "Generic"
};

const Hardware_t HW_SW420 = {
    .code = "sw420",
    .model = "SW-420",
    .brand = "Generic"
};

const Hardware_t HW_PUSH_BUTTON = {
    .code = "push_button",
    .model = "Push Button",
    .brand = "Generic"
};

const Hardware_t HW_GUVA_S12S = {
    .code = "guva",
    .model = "GUVA-S12S",
    .brand = "Generic"
};

const Hardware_t HW_ADXL345 = {
    .code = "adxl345",
    .model = "ADXL345",
    .brand = "Analog Devices"
};