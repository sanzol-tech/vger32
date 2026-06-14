package ar.vger32app.scrambler;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/*
 * Lightweight data protection for resource-constrained environments.
 * Designed for contexts where the overhead of full protocol stacks is
 * prohibitive — keeps non-sensitive data opaque while minimizing CPU
 * and RAM usage.
 *
 * A 4-byte random salt seeds an FNV-1a/xorshift32 stream cipher. Each byte
 * is XORed and additively mixed with the running cipher state and a
 * position-keyed byte. Output = input length + SALT_LEN bytes; salt prepended.
 */

public final class Scrambler {

    public static final int SALT_LEN = 4; // matches SCRAMBLER_SALT_LEN in firmware

    private static final SecureRandom RNG = new SecureRandom();

    private Scrambler() {
    }

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    public static byte[] encode(byte[] payload, String key) {
        if (payload == null || payload.length == 0) return payload;

        byte[] keyBytes = (key != null ? key : "").getBytes(StandardCharsets.UTF_8);
        byte[] saltBytes = new byte[SALT_LEN];
        RNG.nextBytes(saltBytes);

        int salt = ((saltBytes[0] & 0xFF) << 24)
                | ((saltBytes[1] & 0xFF) << 16)
                | ((saltBytes[2] & 0xFF) << 8)
                | (saltBytes[3] & 0xFF);

        int len = payload.length;
        byte[] result = new byte[len + SALT_LEN];
        System.arraycopy(saltBytes, 0, result, 0, SALT_LEN);

        int[] prngState = {(int) (fnv1a(keyBytes) ^ ((salt & 0xFFFFFFFFL) * 0x9E3779B9L))};
        int prev = salt & 0xFF;

        for (int i = 0; i < len; i++) {
            int keystreamByte = nextKeystreamByte(prngState) & 0xFF;
            int encodedByte = (Byte.toUnsignedInt(payload[i]) + prev + keystreamByte) & 0xFF;
            if (keyBytes.length > 0)
                encodedByte ^= Byte.toUnsignedInt(keyBytes[(i + (keystreamByte & 0x0F)) % keyBytes.length]);
            result[i + SALT_LEN] = (byte) encodedByte;
            prev = encodedByte ^ (keystreamByte & 0xFF);
        }
        return result;
    }

    public static byte[] decode(byte[] payload, String key) {
        if (payload == null || payload.length <= SALT_LEN) return payload;

        byte[] keyBytes = (key != null ? key : "").getBytes(StandardCharsets.UTF_8);

        int salt = ((payload[0] & 0xFF) << 24)
                | ((payload[1] & 0xFF) << 16)
                | ((payload[2] & 0xFF) << 8)
                | (payload[3] & 0xFF);

        int outLen = payload.length - SALT_LEN;
        byte[] result = new byte[outLen];

        int[] prngState = {(int) (fnv1a(keyBytes) ^ ((salt & 0xFFFFFFFFL) * 0x9E3779B9L))};
        int prev = salt & 0xFF;

        for (int i = 0; i < outLen; i++) {
            int encodedByte = Byte.toUnsignedInt(payload[i + SALT_LEN]);
            int keystreamByte = nextKeystreamByte(prngState) & 0xFF;
            int temp = encodedByte;
            if (keyBytes.length > 0)
                temp ^= Byte.toUnsignedInt(keyBytes[(i + (keystreamByte & 0x0F)) % keyBytes.length]);
            result[i] = (byte) ((temp - prev - keystreamByte) & 0xFF);
            prev = encodedByte ^ (keystreamByte & 0xFF);
        }
        return result;
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private static long fnv1a(byte[] key) {
        long hash = 2166136261L;
        for (byte b : key) {
            hash ^= (b & 0xFF);
            hash *= 16777619L;
            hash &= 0xFFFFFFFFL;
        }
        return hash;
    }

    // xorshift32. Uses >>> (unsigned right shift) to match C++ uint32_t >> 17.
    private static int nextKeystreamByte(int[] prngState) {
        int x = prngState[0];
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        prngState[0] = x;
        return (x >>> 8) & 0xFFFFFF;
    }
}