/*
* dashboard_server.h
 *
 * Lifecycle management for the HTTP web server. Handles initialization in
 * AP or STA mode, request dispatching, and activity tracking for the
 * sleep manager.
 */

#ifndef DASHBOARD_SERVER_H
#define DASHBOARD_SERVER_H

#include <Arduino.h>

inline bool dashboard_enabled = false;

void dashboard_set_enabled(bool enabled);

void dashboard_init();

void dashboard_disable();

void dashboard_handle();

// Returns milliseconds elapsed since the last HTTP request was served.
// Returns UINT32_MAX if no request has been served since boot.
uint32_t dashboard_last_activity_ms();

// Returns true if the current request carried X-Scramble: 1.
bool dashboard_request_is_scrambled();

#endif