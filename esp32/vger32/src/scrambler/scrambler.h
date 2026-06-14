/*
 * scrambler.h
 *
 * Streaming cipher obfuscation — not cryptography.
 * Obfuscates against casual passive observers.
 *
 * Algorithm v1.2 (NOT wire-compatible with v1.1 — deploy firmware + app together):
 *   PRNG:     xorshift32 replaces LCG — better statistical properties, same RAM.
 *   Key use:  index offset by (k & 0x0F) — breaks key-length periodicity.
 *   Feedback: prev = enc ^ k — stronger cascade effect.
 *
 * Pure bytes in / bytes out — no Base64, no String, no Arduino dependencies.
 * Caller is responsible for output buffer sizing.
 * Caller supplies the key explicitly on every call.
 *
 * Empty-payload contract (mirrors Scrambler.java):
 *   scrambler_encode with len == 0 → returns 0, writes nothing to out.
 *   scrambler_decode with len == 0 → returns 0 (len < SCRAMBLER_SALT_LEN guard).
 *   MQTT clients pass empty payloads through unchanged without calling these functions.
 */

#ifndef SCRAMBLER_H
#define SCRAMBLER_H

#include <stdint.h>

// Salt bytes prepended to every encoded payload (hardware RNG).
// 4 bytes — collision probability negligible over the device lifetime.
static constexpr int SCRAMBLER_SALT_LEN = 4;

// Encode in[0..len-1] with key.
// out must be at least (len + SCRAMBLER_SALT_LEN) bytes when len > 0.
// Returns output length = len + SCRAMBLER_SALT_LEN, or 0 if len == 0.
int scrambler_encode(const uint8_t *in, int len, uint8_t *out, const char *key);

// Decode in[0..len-1] with key.
// in must begin with SCRAMBLER_SALT_LEN salt bytes.
// out must be at least (len - SCRAMBLER_SALT_LEN) bytes.
// Returns decoded length, or 0 if len < SCRAMBLER_SALT_LEN.
int scrambler_decode(const uint8_t *in, int len, uint8_t *out, const char *key);

#endif // SCRAMBLER_H
