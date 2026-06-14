/*
 * sensor_serializer.cpp
 *
 * Responsibility: Serialize sensor data (latest values and history) into
 *                 plain-text strings for the dashboard API.
 *
 * Output format (latest):  one line per sensor: h|m|value|timestamp|unit\n
 * Output format (history): header line h|m|unit\n, then one value|timestamp\n per sample
 *                           Samples are in chronological order (oldest → newest).
 *
 * Note: history samples correctly account for the circular buffer layout
 *       in Sensor_history_t — when full, oldest entry is at [cursor], not [0].
 */

#include "sensor_serializer.h"

#include "sensor_catalog.h"

static char latest_buf[SENSOR_SERIALIZER_LATEST_SIZE];
static char history_buf[SENSOR_SERIALIZER_HISTORY_SIZE];

// ==========================================
// PUBLIC API
// ==========================================

/*
 * Returns one line per registered sensor: h|m|value|timestamp|unit\n
 * Uses a static buffer — do not store the pointer across calls.
 */
const char *sensor_latest_text() {
    size_t offset = 0;
    Sensor_value_t **sensors = sensor_catalog_get_all();
    uint8_t count = sensor_catalog_get_count();

    for (uint8_t i = 0; i < count; i++) {
        Sensor_value_t *tel = sensors[i];
        offset += snprintf(latest_buf + offset,
                           SENSOR_SERIALIZER_LATEST_SIZE - offset,
                           "%s|%s|%.2f|%lu|%s\n",
                           tel->hardware->code,
                           tel->metric->code,
                           tel->latest.value,
                           tel->latest.timestamp,
                           tel->metric->unit);
    }

    latest_buf[offset] = '\0';
    return latest_buf;
}

/*
 * Returns the history for a specific sensor, chronologically ordered
 * (oldest first). First line is the header: h|m|unit\n
 * Subsequent lines: value|timestamp\n
 *
 * On error returns: err=Sensor not found\n
 *
 * Uses a static buffer — do not store the pointer across calls.
 */
const char *sensor_history_text(const char *hardware_code, const char *metric_code) {
    Sensor_value_t *tel = sensor_catalog_find(hardware_code, metric_code);

    if (tel == NULL) {
        snprintf(history_buf, SENSOR_SERIALIZER_HISTORY_SIZE,
                 "err=Sensor not found\n");
        return history_buf;
    }

    size_t offset = 0;

    // Header line: h|m|unit
    offset += snprintf(history_buf + offset,
                       SENSOR_SERIALIZER_HISTORY_SIZE - offset,
                       "%s|%s|%s\n",
                       tel->hardware->code,
                       tel->metric->code,
                       tel->metric->unit);

    // When the buffer has not yet wrapped, oldest entry is at index 0.
    // When the buffer is full (count == SENSOR_HISTORY_SIZE), oldest
    // entry is at cursor (the next slot to be overwritten).
    uint8_t start = (tel->history.count < SENSOR_HISTORY_SIZE)
                        ? 0
                        : tel->history.cursor;

    for (uint8_t i = 0; i < tel->history.count; i++) {
        uint8_t idx = (start + i) % SENSOR_HISTORY_SIZE;
        offset += snprintf(history_buf + offset,
                           SENSOR_SERIALIZER_HISTORY_SIZE - offset,
                           "%.2f|%lu\n",
                           tel->history.samples[idx].value,
                           tel->history.samples[idx].timestamp);
    }

    return history_buf;
}