/*
 * dashboard_api.h
 *
 * HTTP API handler declarations for the dashboard.
 *
 * All responses are text/plain. Format:
 *   <data>\n         — success (GET), plain or Base64-scrambled
 *   (empty body)     — success (POST / DELETE)
 *   err=message\n   — error, always plain text
 *
 * System
 *   GET    /api/system-identity
 *   GET    /api/system-metrics
 *   GET    /api/boot-history
 *   GET    /api/logs
 *   DELETE /api/logs
 *
 * Configuration
 *   GET  /api/preferences
 *   POST /api/preferences
 *   GET  /api/known-networks
 *   POST /api/known-networks
 *
 * Sensors
 *   GET  /api/sensors
 *   GET  /api/sensor-history    ?h=<hw>&m=<metric>
 *
 * Network
 *   GET  /api/wifi-scan
 *   GET  /api/wifi-fingerprints   (raw file — not scrambled)
 *   POST /api/wifi-fingerprints
 *   PUT  /api/wifi-fingerprints
 *
 * Location
 *   GET  /api/location
 *
 * Time
 *   GET  /api/time
 *   POST /api/time
 *
 * Control
 *   POST /api/force-ap
 *   POST /api/reboot
 */

#ifndef DASHBOARD_API_H
#define DASHBOARD_API_H

#include <WebServer.h>

// System
void dashboard_system_identity(WebServer &server);

void dashboard_system_metrics(WebServer &server);

void dashboard_boot_history(WebServer &server);

void dashboard_logs_get(WebServer &server);

void dashboard_logs_clear(WebServer &server);

// Configuration
void dashboard_preferences_get(WebServer &server);

void dashboard_preferences_save(WebServer &server, const String &body);

void dashboard_known_networks_get(WebServer &server);

void dashboard_known_networks_save(WebServer &server, const String &body);

// Sensors
void dashboard_sensors(WebServer &server);

void dashboard_sensor_history(WebServer &server);

// Network
void dashboard_wifi_scan(WebServer &server);

void dashboard_wifi_fingerprints_save(WebServer &server, const String &body);

void dashboard_wifi_fingerprints_replace(WebServer &server, const String &body);

// Location
void dashboard_location(WebServer &server);

// Time
void dashboard_time_get(WebServer &server);

void dashboard_time_post(WebServer &server, const String &body);

#endif