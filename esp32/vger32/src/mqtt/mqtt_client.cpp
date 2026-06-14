/*
 * mqtt_client.cpp
 *
 * Responsibility: MQTT transport.
 *                 Owns the PubSubClient instance, manages broker connection,
 *                 prepends vger32/<mid> prefix on publish, and delivers inbound
 *                 messages to the registered dispatcher.
 *
 * Scrambler integration:
 *   When cfg_mqtt_scrambled is true, outbound payloads go through
 *   scrambler_encode() and inbound payloads through scrambler_decode().
 *   Both paths check against MQTT_SCRAMBLER_BUF_SIZE to avoid buffer
 *   overflow — oversized payloads are dropped with LOG_ERROR.
 *   Empty payloads (length == 0) bypass the scrambler entirely on both
 *   paths — scrambler_encode returns 0 for len == 0, and on_message
 *   short-circuits before calling scrambler_decode.
 */

#include <PubSubClient.h>
#include <WiFi.h>

#include "mqtt_client.h"

#include "config/keys.h"
#include "logger/sys_logger.h"
#include "config/preferences.h"
#include "network/wifi_manager.h"
#include "scrambler/scrambler.h"

// ==========================================
// CONFIG
// ==========================================

// Scratch buffer size for scrambler encode/decode.
// Must be >= max expected MQTT payload + SCRAMBLER_SALT_LEN.
// PubSubClient's own buffer is MQTT_BUFFER_SIZE (1024) — we stay below
// that because scrambled payload = plaintext + SCRAMBLER_SALT_LEN bytes.
static constexpr int MQTT_SCRAMBLER_BUF_SIZE = 512;

// ==========================================
// STATE
// ==========================================

static WiFiClient wifi_client;
static PubSubClient broker(wifi_client);
static cmd_callback_t dispatcher = nullptr;
static unsigned long last_connect_attempt = 0;

// ==========================================
// PRIVATE — INBOUND
// ==========================================

static void on_message(const char *topic, byte *payload, unsigned int length) {
    if (!dispatcher) return;

    static uint8_t buf[MQTT_SCRAMBLER_BUF_SIZE];
    int buf_len;

    if (cfg_mqtt_scrambled) {
        if (length == 0) {
            // Empty payload — scrambler is bypassed (scrambler_encode also
            // returns 0 for empty input). Pass an empty string to dispatcher.
            buf_len = 0;
        } else if ((int) length > (int) sizeof(buf) + SCRAMBLER_SALT_LEN - 1) {
            sys_log(LOG_ERROR, "MQTT", "Scrambled inbound too large: %u bytes", length);
            return;
        } else if ((int) length < SCRAMBLER_SALT_LEN) {
            sys_log(LOG_ERROR, "MQTT", "Scrambled inbound too short: %u bytes", length);
            return;
        } else {
            buf_len = scrambler_decode(payload, (int) length, buf, cfg_scrambler_key.c_str());
        }
    } else {
        buf_len = (int) length < (int) sizeof(buf) - 1
                      ? (int) length
                      : (int) sizeof(buf) - 1;
        memcpy(buf, payload, buf_len);
    }
    buf[buf_len] = '\0';

    dispatcher(topic, (const char *) buf);
}

// ==========================================
// PRIVATE — CONNECTION
// ==========================================

static void try_connect() {
    if (!wifi_manager_is_connected()) return;

    unsigned long now = millis();
    if (now - last_connect_attempt < MQTT_RECONNECT_INTERVAL_MS) return;
    last_connect_attempt = now;

    sys_log(LOG_INFO, "MQTT", "Connecting to %s:%d",
             cfg_mqtt_server.c_str(), cfg_mqtt_port);

    String client_id = cfg_module_id + "_" + String(random(0xffff), HEX);

    if (broker.connect(client_id.c_str())) {
        sys_log(LOG_INFO, "MQTT", "✓ Connected");
        if (dispatcher) {
            String mid_topic = "vger32/" + cfg_module_id + "/cmd/#";
            broker.subscribe(mid_topic.c_str());
            sys_log(LOG_INFO, "MQTT", "✓ Subscribed to %s", mid_topic.c_str());
            broker.subscribe("vger32/ping");
            sys_log(LOG_INFO, "MQTT", "✓ Subscribed to vger32/ping");
            broker.subscribe("vger32/msg");
            sys_log(LOG_INFO, "MQTT", "✓ Subscribed to vger32/msg");
        }
    } else {
        sys_log(LOG_ERROR, "MQTT", "✗ Failed, rc=%d", broker.state());
    }
}

// ==========================================
// PRIVATE — OUTBOUND
// ==========================================

static void publish_raw(const char *topic, const char *payload) {
    static uint8_t encoded[MQTT_SCRAMBLER_BUF_SIZE];
    const uint8_t *data = (const uint8_t *) payload;
    unsigned int len = (unsigned int) strlen(payload);

    if (cfg_mqtt_scrambled && len > 0) {
        // Scrambled outbound: encoded length = len + SCRAMBLER_SALT_LEN.
        // Drop the message if it doesn't fit, rather than producing
        // truncated ciphertext that the receiver can't decode.
        // Empty payloads bypass the scrambler (scrambler_encode returns 0
        // for len == 0, consistent with the inbound and Java behaviour).
        if ((int) len + SCRAMBLER_SALT_LEN > (int) sizeof(encoded)) {
            sys_log(LOG_ERROR, "MQTT", "Payload too large for scrambler: %u bytes (max %d)",
                      len, (int)sizeof(encoded) - SCRAMBLER_SALT_LEN);
            return;
        }
        len = (unsigned int) scrambler_encode(data, (int) len, encoded, cfg_scrambler_key.c_str());
        data = encoded;
    }

    if (broker.publish(topic, data, len)) {
        sys_log(LOG_DEBUG, "MQTT", "✓ %s", topic);
    } else {
        sys_log(LOG_ERROR, "MQTT", "✗ Failed: %s", topic);
    }
}

// ==========================================
// PUBLIC API
// ==========================================

void mqtt_set_enabled(bool enabled) { mqtt_enabled = enabled; }

void mqtt_init(cmd_callback_t dispatcher_fn) {
    if (!mqtt_enabled) {
        sys_log(LOG_INFO, "MQTT", "Disabled by capability");
        return;
    }
    dispatcher = dispatcher_fn;

    broker.setServer(cfg_mqtt_server.c_str(), cfg_mqtt_port);
    broker.setBufferSize(MQTT_BUFFER_SIZE);
    if (dispatcher) broker.setCallback(on_message);

    sys_log(LOG_INFO, "MQTT", "Initialized");
}

void mqtt_handle() {
    if (!mqtt_enabled) return;

    if (broker.connected()) {
        broker.loop();
    } else {
        try_connect();
    }
}

void mqtt_reconnect() {
    if (broker.connected()) broker.disconnect();
    broker.setServer(cfg_mqtt_server.c_str(), cfg_mqtt_port);
    last_connect_attempt = 0;
    sys_log(LOG_INFO, "MQTT", "Reconnecting with new config...");
}

void mqtt_publish(const char *subtopic, const char *payload) {
    if (!mqtt_enabled || !broker.connected()) {
        sys_log(LOG_DEBUG, "MQTT", "Discarded (not connected): %s", subtopic);
        return;
    }
    char topic[128];
    snprintf(topic, sizeof(topic), "vger32/%s/%s",
             cfg_module_id.c_str(), subtopic);
    publish_raw(topic, payload);
}

bool mqtt_is_connected() {
    return mqtt_enabled && broker.connected();
}
