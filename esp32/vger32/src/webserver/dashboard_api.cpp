/*
 * dashboard_api.cpp
 *
 * Responsibility: HTTP API handler implementations for the dashboard.
 *
 * Response format:
 *   <data>\n         — success (GET)
 *   (empty body)     — success (POST / DELETE)
 *   err=message\n   — error, always plain text
 *
 * Private implementation is split into three layers:
 *
 *   LAYER 1 — BASE64
 *     Pure ASCII-safe encoding/decoding. No external dependencies.
 *     Lives here (not in scrambler) — Base64 is an HTTP transport concern.
 *
 *   LAYER 2 — HTTP PAYLOAD ENCODING
 *     http_encode(): plain text → scramble → Base64 → ASCII string
 *     http_decode(): ASCII string → Base64 → raw bytes → unscramble
 *     Base64 is mandatory: the scrambler emits bytes 0x00–0xFF and
 *     Arduino String truncates at 0x00.
 *
 *   LAYER 3 — HTTP RESPONSE HELPERS
 *     sendOk()             — empty 200, never encoded.
 *     sendErr()            — plain text error, never encoded.
 *     sendApiResponse()    — plain text normally; encoded when X-Scramble: 1.
 *     decode_request_body()— plain text normally; decoded when X-Scramble: 1.
 *
 * The X-Scramble: 1 header is set only by the Android app. Browser clients
 * never send it, so they always receive and send plain text — no behaviour change.
 */

#include <WebServer.h>

#include "dashboard_api.h"

#include "config/keys.h"
#include "config/known_networks.h"
#include "logger/sys_logger.h"
#include "config/preferences.h"
#include "dashboard_server.h"
#include "drivers/sensors/core/sensor_serializer.h"
#include "localizer/wifi_localizer.h"
#include "localizer/wifi_storage.h"
#include "mqtt/mqtt_client.h"
#include "network/time_manager.h"
#include "network/wifi_manager.h"
#include "network/wifi_scanner.h"
#include "scrambler/scrambler.h"
#include "logger/boot_logger.h"
#include "system/system_info.h"

// ==========================================
// LAYER 1 — BASE64
//
// Pure encoding/decoding. No dependency on scrambler or WebServer.
// Lives here (not in scrambler module) because Base64 is an HTTP
// transport concern, not a cipher concern.
// ==========================================

static const char B64_TABLE[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

// Raw bytes → Base64 ASCII string.
// out must hold at least ((in_len + 2) / 3) * 4 + 1 bytes.
static int b64_encode(const uint8_t *in, int in_len, char *out) {
    int i = 0, j = 0;
    for (; i + 2 < in_len; i += 3) {
        out[j++] = B64_TABLE[in[i] >> 2];
        out[j++] = B64_TABLE[(in[i] & 0x3) << 4 | in[i + 1] >> 4];
        out[j++] = B64_TABLE[(in[i + 1] & 0xF) << 2 | in[i + 2] >> 6];
        out[j++] = B64_TABLE[in[i + 2] & 0x3F];
    }
    if (i < in_len) {
        out[j++] = B64_TABLE[in[i] >> 2];
        if (i + 1 < in_len) {
            out[j++] = B64_TABLE[(in[i] & 0x3) << 4 | in[i + 1] >> 4];
            out[j++] = B64_TABLE[(in[i + 1] & 0xF) << 2];
        } else {
            out[j++] = B64_TABLE[(in[i] & 0x3) << 4];
            out[j++] = '=';
        }
        out[j++] = '=';
    }
    out[j] = '\0';
    return j;
}

static int8_t b64_val(char c) {
    if (c >= 'A' && c <= 'Z') return (int8_t) (c - 'A');
    if (c >= 'a' && c <= 'z') return (int8_t) (c - 'a' + 26);
    if (c >= '0' && c <= '9') return (int8_t) (c - '0' + 52);
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1; // '=' padding and invalid chars
}

// Base64 ASCII string → raw bytes.
// out must hold at least (in_len / 4) * 3 bytes.
static int b64_decode(const char *in, int in_len, uint8_t *out) {
    int j = 0;
    for (int i = 0; i + 3 < in_len; i += 4) {
        int8_t a = b64_val(in[i]), b = b64_val(in[i + 1]);
        int8_t c = b64_val(in[i + 2]), d = b64_val(in[i + 3]);
        if (a < 0 || b < 0) break;
        out[j++] = (uint8_t) ((a << 2) | (b >> 4));
        if (c >= 0) out[j++] = (uint8_t) ((b << 4) | (c >> 2));
        if (d >= 0) out[j++] = (uint8_t) ((c << 6) | d);
    }
    return j;
}

// ==========================================
// LAYER 2 — HTTP PAYLOAD ENCODING
//
// Combines Layer 1 (Base64) + scrambler module (raw bytes).
// Pipeline for outbound (response):  plain text → scramble → Base64 → ASCII
// Pipeline for inbound  (body):      ASCII → Base64 → raw bytes → unscramble
//
// Base64 is mandatory here: the scrambler produces arbitrary bytes (0x00–0xFF),
// and Arduino String truncates at 0x00. Base64 output is ASCII-safe.
// ==========================================

// Encodes plain text for sending as a scrambled HTTP response body.
// Returns Base64 ASCII string, or an empty String on allocation failure.
static String http_encode(const char *plain, int plain_len) {
    int enc_len = plain_len + SCRAMBLER_SALT_LEN;
    int b64_size = ((enc_len + 2) / 3) * 4 + 1;

    uint8_t *enc_buf = new uint8_t[enc_len];
    char *b64_buf = new char[b64_size];

    if (!enc_buf || !b64_buf) {
        delete[] enc_buf;
        delete[] b64_buf;
        return String();
    }

    scrambler_encode((const uint8_t *) plain, plain_len, enc_buf,
                     cfg_scrambler_key.c_str());
    b64_encode(enc_buf, enc_len, b64_buf);

    String result(b64_buf);
    delete[] enc_buf;
    delete[] b64_buf;
    return result;
}

// Decodes a scrambled HTTP request body back to plain text.
// Returns plain text String, or an empty String on allocation failure.
static String http_decode(const char *b64_body, int b64_len) {
    int raw_max = (b64_len / 4 + 1) * 3;
    uint8_t *raw_buf = new uint8_t[raw_max];
    uint8_t *plain_buf = new uint8_t[raw_max + 1];

    if (!raw_buf || !plain_buf) {
        delete[] raw_buf;
        delete[] plain_buf;
        return String();
    }

    int raw_len = b64_decode(b64_body, b64_len, raw_buf);
    int plain_len = scrambler_decode(raw_buf, raw_len, plain_buf,
                                     cfg_scrambler_key.c_str());
    plain_buf[plain_len] = '\0';

    String result((char *) plain_buf);
    delete[] raw_buf;
    delete[] plain_buf;
    return result;
}

// ==========================================
// LAYER 3 — HTTP RESPONSE HELPERS
//
// sendOk / sendErr  — never encoded (empty body / error before decode).
// sendApiResponse   — plain text normally; encoded only when the request
//                     carried X-Scramble: 1 (app clients only).
//
// decode_request_body — plain text normally; decoded only when scrambled.
//                       Always safe to call regardless of the flag.
// ==========================================

static void sendOk(WebServer &server) {
    server.send(200, "text/plain", "");
}

static void sendErr(WebServer &server, int code, const char *msg) {
    char buf[64];
    snprintf(buf, sizeof(buf), "err=%s\n", msg);
    server.send(code, "text/plain", buf);
}

static void sendApiResponse(WebServer &server, const char *content) {
    if (!content || content[0] == '\0' || !dashboard_request_is_scrambled()) {
        server.send(200, "text/plain", content ? content : "");
        return;
    }

    String encoded = http_encode(content, (int) strlen(content));
    if (encoded.isEmpty()) {
        sendErr(server, 500, "Encoding failed");
        return;
    }
    server.send(200, "text/plain", encoded);
}

static String decode_request_body(const String &body) {
    if (!dashboard_request_is_scrambled() || body.isEmpty())
        return body;

    return http_decode(body.c_str(), (int) body.length());
}

// ==========================================
// SYSTEM
// ==========================================

void dashboard_system_identity(WebServer &server) {
    sendApiResponse(server, get_identity());
}

void dashboard_system_metrics(WebServer &server) {
    sendApiResponse(server, get_metrics());
}

void dashboard_boot_history(WebServer &server) {
    sendApiResponse(server, boot_logger_get_history());
}

void dashboard_logs_get(WebServer &server) {
    sendApiResponse(server, sys_logger_get_history());
}

void dashboard_logs_clear(WebServer &server) {
    sys_logger_clear();
    sendOk(server);
}

// ==========================================
// CONFIGURATION
// ==========================================

void dashboard_preferences_get(WebServer &server) {
    sendApiResponse(server, preferences_to_text());
}

/*
 * POST /api/preferences
 *
 * Runtime effects after persisting:
 *   mqtt      false→true   mqtt_reconnect()
 *   dash      true→false   dashboard_disable()
 *   dash      false→true   dashboard_init()
 *   wifi                   persist only — requires reboot
 *   txpwr                  persist only — takes effect on next boot
 *   locl/slep/time/blog/log  no explicit action
 */
void dashboard_preferences_save(WebServer &server, const String &body) {
    const bool prev_mqtt      = mqtt_enabled;
    const bool prev_webserver = dashboard_enabled;
    const bool prev_wifi      = wifi_manager_enabled;

    String plain = decode_request_body(body);
    if (!preferences_from_text(plain.c_str())) {
        sendErr(server, 400, "Invalid payload");
        return;
    }

    sendOk(server);

    if (wifi_manager_enabled != prev_wifi) return;
    if (mqtt_enabled && !prev_mqtt)        mqtt_reconnect();
    if (!dashboard_enabled && prev_webserver) dashboard_disable();
    if (dashboard_enabled && !prev_webserver) dashboard_init();
}

void dashboard_known_networks_get(WebServer &server) {
    sendApiResponse(server, known_networks_to_text());
}

void dashboard_known_networks_save(WebServer &server, const String &body) {
    String plain = decode_request_body(body);
    if (!known_networks_from_text(plain.c_str())) {
        sendErr(server, 400, "Invalid payload");
        return;
    }
    if (!known_networks_save()) {
        sendErr(server, 500, "Storage failed");
        return;
    }
    sendOk(server);
}

// ==========================================
// SENSORS
// ==========================================

void dashboard_sensors(WebServer &server) {
    sendApiResponse(server, sensor_latest_text());
}

void dashboard_sensor_history(WebServer &server) {
    String hardware = server.arg("h");
    String metric = server.arg("m");
    if (hardware.length() == 0 || metric.length() == 0) {
        sendErr(server, 400, "Missing parameters");
        return;
    }
    sendApiResponse(server, sensor_history_text(hardware.c_str(), metric.c_str()));
}

// ==========================================
// NETWORK
// ==========================================

void dashboard_wifi_scan(WebServer &server) {
    wifi_scanner_request(); // no-op if scan is running or cache is still valid
    sendApiResponse(server, wifi_scanner_as_text());
}

void dashboard_wifi_fingerprints_save(WebServer &server, const String &body) {
    String plain = decode_request_body(body);
    if (plain.length() < 20) {
        sendErr(server, 400, "Payload too short");
        return;
    }
    if (!wstore_save(plain)) {
        sendErr(server, 500, "Storage failed");
        return;
    }
    sendOk(server);
}

void dashboard_wifi_fingerprints_replace(WebServer &server, const String &body) {
    String plain = decode_request_body(body);
    if (plain.length() == 0) {
        sendErr(server, 400, "Empty payload");
        return;
    }
    if (!wstore_replace(plain)) {
        sendErr(server, 500, "Storage failed");
        return;
    }
    sendOk(server);
}

// ==========================================
// LOCATION
// ==========================================

void dashboard_location(WebServer &server) {
    sendApiResponse(server, localizer_history());
}

// ==========================================
// TIME
// ==========================================

void dashboard_time_get(WebServer &server) {
    sendApiResponse(server, get_time());
}

void dashboard_time_post(WebServer &server, const String &body) {
    String plain = decode_request_body(body);
    int idx = plain.indexOf("ts=");
    if (idx < 0) {
        sendErr(server, 400, "Missing ts field");
        return;
    }
    uint32_t unix_ts = (uint32_t) plain.substring(idx + 3).toInt();
    if (unix_ts == 0) {
        sendErr(server, 400, "Invalid timestamp");
        return;
    }
    time_set(unix_ts);
    sendOk(server);
}