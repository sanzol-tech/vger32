/*
 * scrambler.cpp
 *
 * Algorithm v1.2 — per byte:
 *
 *   encode: k    = (uint8_t)(xorshift32(seed) >> 8)
 *           enc  = (in[i] + prev + k) ^ key[(i + (k & 0x0F)) % key_len]
 *           prev = enc ^ k
 *
 *   decode: k    = (uint8_t)(xorshift32(seed) >> 8)   // same PRNG state as encode
 *           temp = enc ^ key[(i + (k & 0x0F)) % key_len]
 *           out  = temp - prev - k
 *           prev = enc ^ k                             // same feedback as encode
 *
 * Salt: 4 random bytes from hardware RNG, prepended to the output.
 * Output length = input length + SCRAMBLER_SALT_LEN, or 0 for empty input.
 * Operates on raw bytes (0x00–0xFF).
 *
 * Changes from v1.1:
 *   - LCG replaced by xorshift32 (passes statistical tests LCG fails)
 *   - Key index shifted by (k & 0x0F) — eliminates key-period attack surface
 *   - prev = enc ^ k — stronger dependency chain between bytes
 */

#include <cstring>
#include <esp_random.h>

#include "scrambler.h"

// ==========================================
// PRIVATE
// ==========================================

static uint32_t hash_key(const char *key) {
    uint32_t h = 2166136261u; // FNV-1a offset basis
    while (*key) {
        h ^= (uint8_t) *key++;
        h *= 16777619u;
    }
    return h;
}

// xorshift32 — better than LCG for all bit positions.
// Returns upper 24 bits (lowest byte is weakest in xorshift32).
static uint32_t prng(uint32_t *s) {
    *s ^= *s << 13;
    *s ^= *s >> 17;
    *s ^= *s << 5;
    return *s >> 8;
}

// ==========================================
// PUBLIC API
// ==========================================

int scrambler_encode(const uint8_t *in, int len, uint8_t *out, const char *key) {
    // Empty payload — no content to scramble, no salt written.
    // Mirrors Scrambler.java behaviour: empty in → empty out.
    if (len == 0) return 0;

    const int key_len = key ? (int) strlen(key) : 0;

    uint32_t salt = esp_random();
    out[0] = (uint8_t) ((salt >> 24) & 0xFF);
    out[1] = (uint8_t) ((salt >> 16) & 0xFF);
    out[2] = (uint8_t) ((salt >> 8) & 0xFF);
    out[3] = (uint8_t) (salt & 0xFF);

    uint32_t seed = hash_key(key ? key : "") ^ (salt * 0x9E3779B9u);
    uint8_t prev = (uint8_t) (salt & 0xFF);

    for (int i = 0; i < len; i++) {
        uint8_t k = (uint8_t) prng(&seed);
        uint8_t enc = (uint8_t) (in[i] + prev + k);
        if (key_len > 0)
            enc ^= (uint8_t) key[(i + (k & 0x0F)) % key_len];
        out[i + SCRAMBLER_SALT_LEN] = enc;
        prev = enc ^ k;
    }
    return len + SCRAMBLER_SALT_LEN;
}

int scrambler_decode(const uint8_t *in, int len, uint8_t *out, const char *key) {
    if (len <= SCRAMBLER_SALT_LEN) return 0;

    const int key_len = key ? (int) strlen(key) : 0;

    uint32_t salt = ((uint32_t) in[0] << 24)
                    | ((uint32_t) in[1] << 16)
                    | ((uint32_t) in[2] << 8)
                    | (uint32_t) in[3];

    uint32_t seed = hash_key(key ? key : "") ^ (salt * 0x9E3779B9u);
    uint8_t prev = (uint8_t) (salt & 0xFF);
    int out_len = len - SCRAMBLER_SALT_LEN;

    for (int i = 0; i < out_len; i++) {
        uint8_t enc = in[i + SCRAMBLER_SALT_LEN];
        uint8_t k = (uint8_t) prng(&seed);
        uint8_t temp = enc;
        if (key_len > 0)
            temp ^= (uint8_t) key[(i + (k & 0x0F)) % key_len];
        out[i] = (uint8_t) (temp - prev - k);
        prev = enc ^ k;
    }
    return out_len;
}
