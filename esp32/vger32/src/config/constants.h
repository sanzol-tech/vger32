/*
 * constants.h
 *
 * Responsibility: Compile-time constants and time utilities shared across
 *                 all modules. Mission and hardware selection live in
 *                 profiles/active_profile.h — not here.
 */

#ifndef CONSTANTS_H
#define CONSTANTS_H

#include <Arduino.h>

// ==========================================
// COMPILE-TIME FEATURE GATES
// ==========================================

// Enables the scrambler module (scrambler.cpp).
// When not defined, mqtt_scrambled is always forced to false at load/save,
// preventing the user from enabling a feature the binary does not contain.
//
// The scrambler is always compiled in the current build — define the gate.
// To exclude the scrambler from a build, comment out this line AND remove
// scrambler.cpp from the build (platformio.ini src_filter or similar).
#define SCRAMBLER_ENABLED

// ==========================================
// MODULE IDENTITY
// ==========================================

// Prefix used when generating a module ID on first boot.
// The dashboard derives the editable suffix by splitting on the first '_'.
static constexpr char MODULE_ID_PREFIX[] = "VGER_";

// ==========================================
// FIELD SEPARATORS
// ==========================================

// Simple key=value separator.
// Use when value cannot contain '=' or ':' (IDs, IPs, numbers, flags, enums).
static constexpr char KV_SEP = '=';

// ASCII Unit Separator (0x1F) — separator for arbitrary values.
// Use when value may contain '=' or ':': passwords, URLs, SSIDs, free text.
static constexpr char FIELD_SEP = 0x1F;

// ==========================================
// TIME LITERALS (Zero-cost abstractions)
// ==========================================

// Suffix for seconds
constexpr uint32_t operator"" _seconds(unsigned long long s) {
    return static_cast<uint32_t>(s * 1000ULL);
}

// Suffix for minutes
constexpr uint32_t operator"" _minutes(unsigned long long m) {
    return static_cast<uint32_t>(m * 60ULL * 1000ULL);
}

// Suffix for hours
constexpr uint32_t operator"" _hours(unsigned long long h) {
    return static_cast<uint32_t>(h * 60ULL * 60ULL * 1000ULL);
}

#endif