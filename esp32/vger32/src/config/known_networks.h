/*
 * known_networks.h
 *
 * Persistent storage for known WiFi networks.
 * Networks are loaded from LittleFS at boot and saved on configuration change.
 *
 * File format (/known_networks.vger), one network per line:
 *   TYPE\x1FIDENTIFIER\x1FPASS\n
 *   TYPE       — 'S' (match by SSID) or 'M' (match by MAC)
 *   IDENTIFIER — SSID (up to 32 chars) or MAC (12 hex uppercase, no colons)
 *   PASS       — password, empty for open networks
 *
 * 0x1F (ASCII Unit Separator) is used as field separator — never appears
 * in SSIDs, MACs, or passwords, and is not treated as whitespace by editors.
 *
 * The dashboard represents this format as verbose text for human editing:
 *   S US MyHome US secret
 *   M US AABBCCDDEEFF US
 * The JS layer converts between verbose and compact on GET/POST.
 */

#ifndef KNOWN_NETWORKS_H
#define KNOWN_NETWORKS_H

#include <Arduino.h>

// ==========================================
// CONFIG
// ==========================================

static constexpr uint8_t KNOWN_NET_MAX = 10;
static constexpr uint8_t KNOWN_NET_SSID_LEN = 32;
static constexpr uint8_t KNOWN_NET_MAC_LEN = 12;
static constexpr uint8_t KNOWN_NET_PASS_LEN = 63;

static constexpr char KNOWN_NET_PATH[] = "/known_networks.vger";

// ==========================================
// MODEL
// ==========================================

typedef enum {
    KNOWN_NET_BY_SSID = 'S',
    KNOWN_NET_BY_MAC = 'M'
} known_net_type_t;

struct KnownNetwork {
    known_net_type_t type;
    char identifier[KNOWN_NET_SSID_LEN + 1]; // SSID or MAC depending on type
    char pass[KNOWN_NET_PASS_LEN + 1]; // empty = open network
};

// ==========================================
// RUNTIME STATE
// ==========================================

extern KnownNetwork known_networks[KNOWN_NET_MAX];
extern uint8_t known_network_count;

// ==========================================
// API
// ==========================================

// Load networks from LittleFS into known_networks[].
// Call once from setup() after LittleFS.begin().
void known_networks_load();

// Save known_networks[] to LittleFS.
// Returns false if the file could not be written.
bool known_networks_save();

// Deserialize a compact payload into known_networks[].
// Updates known_network_count. Returns false if the payload is invalid.
// Rejects the entire payload on any malformed line — no partial updates.
bool known_networks_from_text(const char *payload);

// Serialize known_networks[] to compact format.
// Returns a pointer to an internal static buffer — valid until next call.
const char *known_networks_to_text();

#endif