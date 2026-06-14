/*
 * udp_discovery.cpp
 *
 * Responsibility: Passive UDP discovery responder.
 *
 * Protocol:
 *   Request  — UDP broadcast to any:UDP_DISCOVERY_PORT, payload "vger32:discover"
 *   Response — UDP unicast to sender, payload identical to get_identity()
 *
 * Active in both STA and AP modes: WiFiUDP.begin() binds to all interfaces,
 * so broadcasts on either the router subnet or the ESP32's own AP subnet
 * (typically 192.168.4.x) are received and answered correctly.
 *
 * Jitter:
 *   Responses are delayed by random(0, UDP_JITTER_MAX_MS) milliseconds.
 *   This staggers simultaneous replies from many devices responding to the
 *   same broadcast, preventing packet storms at the receiver (relevant for
 *   deployments of 50+ units on the same subnet).
 *
 * The delay is non-blocking: pending state is stored in module-level statics
 *   and checked on every call to udp_discovery_handle().
 *
 * Lifecycle:
 *   - Starts when network is available (STA connected or AP active).
 *   - Stops and resets if both interfaces go down, or when
 *     udp_discovery_enabled is set to false.
 */

#include <WiFiUdp.h>

#include "udp_discovery.h"
#include "network/wifi_manager.h"
#include "system/system_info.h"
#include "logger/sys_logger.h"

// ==========================================
// CONFIG
// ==========================================

static constexpr uint32_t UDP_JITTER_MAX_MS     = 200;
static constexpr uint8_t  UDP_MAGIC_LEN         = sizeof(UDP_DISCOVERY_MAGIC) - 1;
static constexpr int      UDP_READ_BUF          = 32;

// ==========================================
// STATE
// ==========================================

static WiFiUDP   udp;
static bool      started  = false;

static bool      pending      = false;
static IPAddress pending_ip;
static uint16_t  pending_port = 0;
static uint32_t  respond_at   = 0;

// ==========================================
// PRIVATE
// ==========================================

static void start() {
    udp.begin(UDP_DISCOVERY_PORT);
    started = true;
    sys_log(LOG_INFO, "UDP-DISC", "Listening on :%u", UDP_DISCOVERY_PORT);
}

static void stop() {
    udp.stop();
    started = false;
    pending     = false;
    sys_log(LOG_INFO, "UDP-DISC", "Stopped");
}

static void send_pending() {
    const char *identity = get_identity();
    udp.beginPacket(pending_ip, pending_port);
    udp.write((const uint8_t *)identity, strlen(identity));
    udp.endPacket();
    sys_log(LOG_DEBUG, "UDP-DISC", "Replied to :%u", pending_port);
    pending = false;
}

static void check_pending() {
    if (!pending) return;
    if (millis() >= respond_at) send_pending();
}

static void check_incoming() {
    if (pending) return;

    int len = udp.parsePacket();
    if (len <= 0) return;

    char buf[UDP_READ_BUF];
    int  n = udp.read(buf, (int)sizeof(buf) - 1);
    if (n < 0) return;
    buf[n] = '\0';

    if (n != (int)UDP_MAGIC_LEN || memcmp(buf, UDP_DISCOVERY_MAGIC, UDP_MAGIC_LEN) != 0) {
        sys_log(LOG_DEBUG, "UDP-DISC", "Ignored unknown payload (len=%d)", n);
        return;
    }

    uint32_t jitter  = (uint32_t)random(0, UDP_JITTER_MAX_MS);
    pending_ip       = udp.remoteIP();
    pending_port     = udp.remotePort();
    respond_at       = millis() + jitter;
    pending          = true;
    sys_log(LOG_DEBUG, "UDP-DISC", "Request received, jitter=%ums", jitter);
}

// ==========================================
// PUBLIC API
// ==========================================

void udp_discovery_set_enabled(bool enabled) {
    udp_discovery_enabled = enabled;
    if (!enabled && started) stop();
}

void udp_discovery_handle() {
    const bool should_run = udp_discovery_enabled && wifi_manager_is_connected();

    if (!should_run) {
        if (started) stop();
        return;
    }

    if (!started) start();

    check_pending();
    check_incoming();
}