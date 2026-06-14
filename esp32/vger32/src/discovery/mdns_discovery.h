/*
 * mdns_discovery.h
 *
 * Responsibility: mDNS service advertisement for passive device discovery.
 *                 Announces _vger32._tcp on port 80 with TXT records that
 *                 mirror get_identity() — all discovery channels are symmetric.
 *
 * Call mdns_discovery_handle() from each mission loop().
 * No init function — the handle bootstraps itself when WiFi STA is connected.
 */

#ifndef MDNS_DISCOVERY_H
#define MDNS_DISCOVERY_H

#include <Arduino.h>

inline bool mdns_discovery_enabled = true;

static constexpr char     MDNS_SERVICE_TYPE[]  = "vger32";
static constexpr uint16_t MDNS_SERVICE_PORT    = 80;

void mdns_discovery_set_enabled(bool enabled);
void mdns_discovery_handle();

#endif