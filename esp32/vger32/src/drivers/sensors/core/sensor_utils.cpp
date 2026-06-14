/*
 * sensor_utils.cpp
 *
 * Responsibility: Helper functions for recording a new reading into a
 *                 Sensor_value_t -- updates the latest value and advances
 *                 the circular history buffer.
 */

#include "sensor_utils.h"

void sensor_add_sample(Sensor_value_t *tel, float value, uint32_t timestamp) {
    tel->latest.value = value;
    tel->latest.timestamp = timestamp;

    tel->history.samples[tel->history.cursor] = tel->latest;
    tel->history.cursor = (tel->history.cursor + 1) % SENSOR_HISTORY_SIZE;

    if (tel->history.count < SENSOR_HISTORY_SIZE) {
        tel->history.count++;
    }
}

void sensor_clear_history(Sensor_value_t *tel) {
    tel->history.cursor = 0;
    tel->history.count = 0;
}