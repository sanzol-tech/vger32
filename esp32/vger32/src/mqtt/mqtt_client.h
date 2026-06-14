/*
 * mqtt_client.h
 *
 * Responsibility: MQTT transport. Manages connection to the broker,
 *                 outbound publish with automatic topic prefix,
 *                 and inbound message delivery to a caller-provided dispatcher.
 *
 * Topic prefix: "vger32/<mid>/" is prepended automatically on publish.
 *   mqtt_publish("sensors/latest", payload) → "vger32/<mid>/sensors/latest"
 *
 * Inbound: messages on vger32/<mid>/#, vger32/ping, and vger32/msg are
 *   delivered to the dispatcher registered in mqtt_init().
 */

#ifndef MQTT_CLIENT_H
#define MQTT_CLIENT_H

#include <Arduino.h>

#include "config/constants.h"

inline bool mqtt_enabled = false;

// Set to true by cmd/publish_now. Mission profiles check and consume this flag
// in their mqtt_publish_sensors_if_due() — forces an immediate publish
// regardless of the configured interval.
inline bool mqtt_publish_now = false;

static constexpr uint32_t MQTT_RECONNECT_INTERVAL_MS = 5_seconds;
static constexpr uint16_t MQTT_BUFFER_SIZE = 1024;

typedef void (*cmd_callback_t)(const char *topic, const char *payload);

void mqtt_set_enabled(bool enabled);

void mqtt_init(cmd_callback_t dispatcher);

void mqtt_handle();

void mqtt_reconnect();

void mqtt_publish(const char *subtopic, const char *payload);

bool mqtt_is_connected();

#endif
