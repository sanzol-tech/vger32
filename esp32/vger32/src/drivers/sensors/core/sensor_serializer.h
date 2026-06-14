#ifndef SENSOR_SERIALIZER_H
#define SENSOR_SERIALIZER_H

// Latest: ~12 sensors × 50 chars/line worst case (push_button|vibration|...)
#define SENSOR_SERIALIZER_LATEST_SIZE  640

// History: header(~30) + 10 samples × 25 chars/line worst case
#define SENSOR_SERIALIZER_HISTORY_SIZE 320

// One line per sensor: h|m|value|timestamp|unit\n
// Uses a static buffer — valid until next call.
const char *sensor_latest_text();

// Header line h|m|unit\n followed by one value|timestamp\n per sample (oldest first).
// On error: err=Sensor not found\n
// Uses a static buffer — valid until next call.
const char *sensor_history_text(const char *hardware_code, const char *metric_code);

#endif