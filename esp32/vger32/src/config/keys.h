/*
 * keys.h
 *
 * Loads sensitive keys from LittleFS /keys.vger at boot.
 * Format: key = value (one per line, accepts = or :)
 * Falls back to hardcoded defaults if file is missing.
 *
 * Call keys_load() from setup() after LittleFS.begin().
 */

#ifndef KEYS_H
#define KEYS_H

#include <Arduino.h>

extern String cfg_ap_pass;
extern String cfg_api_key;
extern String cfg_scrambler_key;

// Load keys from /keys.vger into globals.
// Falls back to defaults if file is missing or a key is absent.
void keys_load();

#endif