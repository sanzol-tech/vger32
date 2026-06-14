/*
* wifi_manager.h
 *
 * WiFi connection manager. Handles STA and AP modes via an internal FSM.
 * Call wifi_manager_init() from setup(), wifi_manager_handle() each loop().
 *
 * Normal operation: scans for visible APs, matches against known_networks[],
 * and connects in RSSI order. If no known network is reachable, retries
 * indefinitely — there is no automatic fallback to AP mode.
 *
 * AP mode is entered only on boot when the force-AP flag is set in NVS.
 * Use wifi_manager_force_ap() to set the flag and reboot into AP mode.
 * To exit AP mode, reboot without setting the flag (e.g. via /api/reboot).
 *
 * TX power:
 *   Applied once in wifi_manager_init() via wifi_set_tx_power().
 *   The value comes from preferences_get().wifi_tx_power (set per-mission,
 *   overridable at runtime via the dashboard). Board quirks
 *   (ARDUINO_LOLIN_C3_MINI) and hardware profile ceilings (HW_WIFI_TX_POWER)
 *   are clamped inside wifi_set_tx_power() — they always take precedence.
 */

#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include <Arduino.h>
#include <WiFi.h>

inline bool wifi_manager_enabled = false;

// When false, retries are immediate after each failed scan cycle.
// Disable for mobile devices where coverage changes frequently.
inline bool wifi_manager_backoff_enabled = true;

void wifi_manager_set_enabled(bool enabled);

void wifi_manager_init();

void wifi_manager_handle();

// Persists the force-AP flag to NVS and reboots immediately.
// On next boot the FSM starts in AP mode.
void wifi_manager_force_ap();

bool wifi_manager_is_connected();

bool wifi_manager_is_ap_active();

// Returns the current IP address (STA if connected, softAP otherwise).
// Points to an internal static buffer — valid until next call.
const char *wifi_manager_get_ip();

#endif