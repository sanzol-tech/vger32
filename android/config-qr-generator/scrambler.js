/*
 * scrambler.js — Algorithm v1.2
 *
 * Port of Scrambler.java / scrambler.cpp.
 * Wire-compatible: output from any implementation decodes correctly in the others.
 *
 * encode(payload: Uint8Array, key: string) → Uint8Array   (length + SALT_LEN)
 * decode(payload: Uint8Array, key: string) → Uint8Array | payload
 *
 * Empty-payload contract (mirrors C++ / Java):
 *   encode(empty) → empty (no salt written)
 *   decode(length ≤ SALT_LEN) → payload unchanged
 */

const SALT_LEN = 4;

// ── private ───────────────────────────────────────────────────────────────────

function _fnv1a(keyBytes) {
    let h = 2166136261; // 0x811C9DC5 — FNV offset basis (unsigned 32-bit)
    for (const b of keyBytes) {
        h = Math.imul(h ^ b, 16777619) >>> 0;
    }
    return h;
}

// xorshift32 — state is Uint32Array[1] (mutated in-place).
// Returns keystreamByte: bits [15:8] of the new state (matches C++ uint8_t cast).
function _xorStep(s) {
    s[0] ^= s[0] << 13;
    s[0] ^= s[0] >>> 17;
    s[0] ^= s[0] << 5;
    return (s[0] >>> 8) & 0xFF;
}

function _initState(key, salt) {
    const keyBytes = new TextEncoder().encode(key || '');
    const seed = (_fnv1a(keyBytes) ^ (Math.imul(salt >>> 0, 0x9E3779B9) >>> 0)) >>> 0;
    const state = new Uint32Array(1);
    state[0] = seed;
    return { state, keyBytes };
}

// ── public API ────────────────────────────────────────────────────────────────

function scramblerEncode(payload, key) {
    if (!payload || payload.length === 0) return payload;

    const saltBytes = new Uint8Array(SALT_LEN);
    crypto.getRandomValues(saltBytes);
    const salt = ((saltBytes[0] << 24) | (saltBytes[1] << 16) |
                  (saltBytes[2] <<  8) |  saltBytes[3]) >>> 0;

    const { state, keyBytes } = _initState(key, salt);
    let prev = salt & 0xFF;

    const out = new Uint8Array(payload.length + SALT_LEN);
    out.set(saltBytes, 0);

    for (let i = 0; i < payload.length; i++) {
        const k   = _xorStep(state);
        let   enc = (payload[i] + prev + k) & 0xFF;
        if (keyBytes.length > 0)
            enc ^= keyBytes[(i + (k & 0x0F)) % keyBytes.length];
        out[i + SALT_LEN] = enc;
        prev = (enc ^ k) & 0xFF;
    }
    return out;
}

function scramblerDecode(payload, key) {
    if (!payload || payload.length <= SALT_LEN) return payload;

    const salt = ((payload[0] << 24) | (payload[1] << 16) |
                  (payload[2] <<  8) |  payload[3]) >>> 0;

    const { state, keyBytes } = _initState(key, salt);
    let prev   = salt & 0xFF;
    const out  = new Uint8Array(payload.length - SALT_LEN);

    for (let i = 0; i < out.length; i++) {
        const enc = payload[i + SALT_LEN];
        const k   = _xorStep(state);
        let   tmp = enc;
        if (keyBytes.length > 0)
            tmp ^= keyBytes[(i + (k & 0x0F)) % keyBytes.length];
        out[i] = (tmp - prev - k) & 0xFF;
        prev   = (enc ^ k) & 0xFF;
    }
    return out;
}
