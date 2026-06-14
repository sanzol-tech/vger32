/*
* udp_discovery.h
 *
 * Responsibility: Passive UDP discovery responder.
 *                 Listens for broadcast discovery requests and replies with
 *                 the device identity payload (same as /api/system-identity).
 *
 * Call udp_discovery_handle() from each mission loop().
 * No init function — the handle bootstraps itself when WiFi STA is connected.
 */

#ifndef UDP_DISCOVERY_H
#define UDP_DISCOVERY_H

#include <Arduino.h>

inline bool udp_discovery_enabled = true;

static constexpr uint16_t UDP_DISCOVERY_PORT    = 4210;
static constexpr char     UDP_DISCOVERY_MAGIC[] = "vger32:discover";

void udp_discovery_set_enabled(bool enabled);
void udp_discovery_handle();

#endif