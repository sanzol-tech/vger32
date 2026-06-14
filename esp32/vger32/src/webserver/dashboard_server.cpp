/*
 * dashboard_server.cpp
 *
 * Responsibility: Lifecycle management for the HTTP web server.
 *                 Handles initialization in AP or STA mode, request dispatching,
 *                 activity tracking for the sleep manager, and API key authentication.
 *
 * Auth: every endpoint registered via register_authenticated_endpoint() requires
 *       a valid X-API-Key header. Static files (HTML/CSS/JS) are public.
 *
 * Scrambling: if the authenticated request carries X-Scramble: 1, the flag
 *             request_scrambled is set before calling the handler and cleared
 *             after. dashboard_request_is_scrambled() exposes it to api.cpp.
 *             Static-file endpoints never set this flag.
 */

#include <LittleFS.h>
#include <WebServer.h>

#include "dashboard_server.h"

#include "config/keys.h"
#include "logger/sys_logger.h"
#include "dashboard_api.h"
#include "network/wifi_manager.h"

// ==========================================
// PRIVATE STATE
// ==========================================

static bool dashboard_initialized = false;
static bool server_running = false;
static uint32_t last_activity_ms = 0;
static bool request_scrambled = false; // set per-request, cleared after handler

static WebServer server(80);

// ==========================================
// FORWARD DECLARATIONS
// ==========================================

static void dashboard_server_start();

static void register_endpoints();

static void serve_file(const char *path, const char *content_type);

// ==========================================
// PRIVATE
// ==========================================

static void dashboard_record_activity() {
    last_activity_ms = millis();
    if (last_activity_ms == 0) last_activity_ms = 1;
}

// Authenticated wrapper — checks X-API-Key, then reads X-Scramble.
// request_scrambled is always cleared before auth so a rejected request
// never leaks a previous request's flag.
static void register_authenticated_endpoint(const char *path, HTTPMethod method,
                                            WebServer::THandlerFunction handler) {
    server.on(path, method, [handler]() {
        dashboard_record_activity();
        request_scrambled = false;
        if (server.header("X-API-Key") != cfg_api_key.c_str()) {
            server.send(401, "text/plain", "err=Unauthorized\n");
            return;
        }
        request_scrambled = (server.header("X-Scramble") == "1");
        handler();
        request_scrambled = false;
    });
}

// Public wrapper — no authentication. Never sets request_scrambled.
static void register_public_endpoint(const char *path, HTTPMethod method,
                                     WebServer::THandlerFunction handler) {
    server.on(path, method, [handler]() {
        dashboard_record_activity();
        handler();
    });
}

static void dashboard_server_start() {
    // X-Scramble added alongside X-API-Key so WebServer exposes it via header().
    const char *headers[] = {"X-API-Key", "X-Scramble"};
    server.collectHeaders(headers, 2);

    server.begin();
    server_running = true;
    sys_log(LOG_INFO, "DASH", "%s ready at %s",
             wifi_manager_is_ap_active() ? "AP" : "STA", wifi_manager_get_ip());
}

// ==========================================
// PUBLIC API
// ==========================================

bool dashboard_request_is_scrambled() {
    return request_scrambled;
}

void dashboard_set_enabled(bool enabled) { dashboard_enabled = enabled; }

void dashboard_init() {
    if (!dashboard_enabled) {
        sys_log(LOG_INFO, "DASH", "Disabled by preference");
        return;
    }
    if (!dashboard_initialized) {
        register_endpoints();
        dashboard_initialized = true;
    }
    sys_log(LOG_INFO, "DASH", "Initialized — waiting for network");
}

void dashboard_disable() {
    if (!dashboard_enabled) return;
    server.stop();
    server_running = false;
    dashboard_enabled = false;
    sys_log(LOG_INFO, "DASH", "Disabled");
}

void dashboard_handle() {
    if (!dashboard_enabled) return;
    if (!server_running) {
        if (wifi_manager_is_connected() || wifi_manager_is_ap_active())
            dashboard_server_start();
        return;
    }
    server.handleClient();
}

uint32_t dashboard_last_activity_ms() {
    if (last_activity_ms == 0) return UINT32_MAX;
    return millis() - last_activity_ms;
}

// ==========================================
// PRIVATE — ROUTING
// ==========================================

static void register_endpoints() {
    // Static files — public, never scrambled
    register_public_endpoint("/", HTTP_GET, []() {
        serve_file("/dashboard.html", "text/html");
    });
    register_public_endpoint("/dashboard.css", HTTP_GET, []() {
        serve_file("/dashboard.css", "text/css");
    });
    register_public_endpoint("/dashboard.js", HTTP_GET, []() {
        serve_file("/dashboard.js", "application/javascript");
    });

    // System
    register_authenticated_endpoint("/api/system-identity", HTTP_GET, []() {
        dashboard_system_identity(server);
    });
    register_authenticated_endpoint("/api/system-metrics", HTTP_GET, []() {
        dashboard_system_metrics(server);
    });
    register_authenticated_endpoint("/api/boot-history", HTTP_GET, []() {
        dashboard_boot_history(server);
    });
    register_authenticated_endpoint("/api/logs", HTTP_GET, []() {
        dashboard_logs_get(server);
    });
    register_authenticated_endpoint("/api/logs", HTTP_DELETE, []() {
        dashboard_logs_clear(server);
    });

    // Configuration
    register_authenticated_endpoint("/api/preferences", HTTP_GET, []() {
        dashboard_preferences_get(server);
    });
    register_authenticated_endpoint("/api/preferences", HTTP_POST, []() {
        String body = server.arg("plain");
        dashboard_preferences_save(server, body);
    });
    register_authenticated_endpoint("/api/known-networks", HTTP_GET, []() {
        dashboard_known_networks_get(server);
    });
    register_authenticated_endpoint("/api/known-networks", HTTP_POST, []() {
        String body = server.arg("plain");
        dashboard_known_networks_save(server, body);
    });

    // Sensors
    register_authenticated_endpoint("/api/sensors", HTTP_GET, []() {
        dashboard_sensors(server);
    });
    register_authenticated_endpoint("/api/sensor-history", HTTP_GET, []() {
        dashboard_sensor_history(server);
    });

    // Network
    register_authenticated_endpoint("/api/wifi-scan", HTTP_GET, []() {
        dashboard_wifi_scan(server);
    });
    register_authenticated_endpoint("/api/wifi-fingerprints", HTTP_GET, []() {
        serve_file("/wifi_fingerprints.dat", "text/plain");
    });
    register_authenticated_endpoint("/api/wifi-fingerprints", HTTP_POST, []() {
        String body = server.arg("plain");
        dashboard_wifi_fingerprints_save(server, body);
    });
    register_authenticated_endpoint("/api/wifi-fingerprints", HTTP_PUT, []() {
        String body = server.arg("plain");
        dashboard_wifi_fingerprints_replace(server, body);
    });

    // Location
    register_authenticated_endpoint("/api/location", HTTP_GET, []() {
        dashboard_location(server);
    });

    // Time
    register_authenticated_endpoint("/api/time", HTTP_GET, []() {
        dashboard_time_get(server);
    });
    register_authenticated_endpoint("/api/time", HTTP_POST, []() {
        String body = server.arg("plain");
        dashboard_time_post(server, body);
    });

    // Control
    register_authenticated_endpoint("/api/force-ap", HTTP_POST, []() {
        server.send(200, "text/plain", "");
        wifi_manager_force_ap(); // persists NVS flag and reboots
    });
    register_authenticated_endpoint("/api/reboot", HTTP_POST, []() {
        server.send(200, "text/plain", "");
        delay(300);
        ESP.restart();
    });

    server.onNotFound([]() {
        server.send(404, "text/plain", "err=Not Found\n");
    });
}

// ==========================================
// PRIVATE — FILE SERVING
// ==========================================

static void serve_file(const char *path, const char *content_type) {
    if (!LittleFS.exists(path)) {
        sys_log(LOG_WARN, "DASH", "File not found: %s", path);
        server.send(404, "text/plain", "err=File Not Found\n");
        return;
    }
    File file = LittleFS.open(path, "r");
    if (!file) {
        sys_log(LOG_ERROR, "DASH", "Could not open: %s", path);
        server.send(500, "text/plain", "err=Internal Server Error\n");
        return;
    }
    server.streamFile(file, content_type);
    file.close();
}