package ar.vger32app.scrambler;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ScramblerTest {

    // --------------------------------------------------------
    // --- ROUNDTRIP TESTS ------------------------------------

    @Test
    public void encodeDecode_shortPlainText_returnsOriginal() {
        String original = "Hello ESP32";
        String key = "test123";

        byte[] encoded = Scrambler.encode(original.getBytes(StandardCharsets.UTF_8), key);
        byte[] decoded = Scrambler.decode(encoded, key);
        String result = new String(decoded, StandardCharsets.UTF_8);

        assertEquals(original, result);
    }

    @Test
    public void encodeDecode_emptyPayload_returnsEmpty() {
        byte[] original = new byte[0];
        String key = "anyKey";

        byte[] encoded = Scrambler.encode(original, key);
        byte[] decoded = Scrambler.decode(encoded, key);

        assertNotNull(encoded);
        assertEquals(0, encoded.length);
        assertNotNull(decoded);
        assertEquals(0, decoded.length);
    }

    @Test
    public void encodeDecode_nullPayload_returnsNull() {
        String key = "anyKey";

        byte[] encoded = Scrambler.encode(null, key);
        byte[] decoded = Scrambler.decode(encoded, key);

        assertNull(encoded);
        assertNull(decoded);
    }

    @Test
    public void encodeDecode_unicodeCharacters_returnsOriginal() {
        String original = "¡Hola! こんにちは 🌍";
        String key = "unicodeKey";

        byte[] encoded = Scrambler.encode(original.getBytes(StandardCharsets.UTF_8), key);
        byte[] decoded = Scrambler.decode(encoded, key);
        String result = new String(decoded, StandardCharsets.UTF_8);

        assertEquals(original, result);
    }

    @Test
    public void encodeDecode_longKey_returnsOriginal() {
        String original = "Sensitive payload data here";
        String key = "a-very-long-scrambler-key-1234567890!@#$%";

        byte[] encoded = Scrambler.encode(original.getBytes(StandardCharsets.UTF_8), key);
        byte[] decoded = Scrambler.decode(encoded, key);
        String result = new String(decoded, StandardCharsets.UTF_8);

        assertEquals(original, result);
    }

    @Test
    public void encodeDecode_emptyKey_returnsOriginal() {
        String original = "Payload with no encryption key";
        String key = "";

        byte[] encoded = Scrambler.encode(original.getBytes(StandardCharsets.UTF_8), key);
        byte[] decoded = Scrambler.decode(encoded, key);
        String result = new String(decoded, StandardCharsets.UTF_8);

        assertEquals(original, result);
    }

    @Test
    public void encodeDecode_nullKey_returnsOriginal() {
        String original = "Payload with null key";

        byte[] encoded = Scrambler.encode(original.getBytes(StandardCharsets.UTF_8), null);
        byte[] decoded = Scrambler.decode(encoded, null);
        String result = new String(decoded, StandardCharsets.UTF_8);

        assertEquals(original, result);
    }

    // --------------------------------------------------------
    // --- OUTPUT FORMAT TESTS --------------------------------

    @Test
    public void encode_addsSaltLength_returnsLongerOutput() {
        byte[] original = "test".getBytes(StandardCharsets.UTF_8);
        String key = "key";

        byte[] encoded = Scrambler.encode(original, key);

        assertEquals(original.length + Scrambler.SALT_LEN, encoded.length);
    }

    @Test
    public void encode_saltPresentAtBeginning_extractsCorrectly() {
        byte[] original = "data".getBytes(StandardCharsets.UTF_8);
        String key = "key";

        byte[] encoded = Scrambler.encode(original, key);

        // First SALT_LEN bytes should be the salt (non-zero, random)
        for (int i = 0; i < Scrambler.SALT_LEN; i++) {
            assertNotEquals(0, encoded[i]);
        }
    }

    // --------------------------------------------------------
    // --- COMPATIBILITY TESTS (firmware v1.2) ----------------

    @Test
    public void encode_numericKey_shiftsBytesCorrectly() {
        byte[] original = "ABC".getBytes(StandardCharsets.UTF_8);
        String key = "12345";

        byte[] encoded = Scrambler.encode(original, key);
        byte[] decoded = Scrambler.decode(encoded, key);

        assertArrayEquals(original, decoded);
    }

    @Test
    public void encode_keyWithSpaces_worksCorrectly() {
        byte[] original = "Test payload".getBytes(StandardCharsets.UTF_8);
        String key = "my secret key with spaces";

        byte[] encoded = Scrambler.encode(original, key);
        byte[] decoded = Scrambler.decode(encoded, key);

        assertArrayEquals(original, decoded);
    }

    // --------------------------------------------------------
    // --- DETERMINISTIC BEHAVIOR TESTS -----------------------

    @Test
    public void encode_sameInputDifferentSalt_producesDifferentOutput() {
        byte[] original = "deterministic test".getBytes(StandardCharsets.UTF_8);
        String key = "key";

        byte[] encoded1 = Scrambler.encode(original, key);
        byte[] encoded2 = Scrambler.encode(original, key);

        // Different salt should produce different encoded bytes
        assertFalse(java.util.Arrays.equals(encoded1, encoded2));
    }

    @Test
    public void decode_differentSaltWithSameOriginal_bothDecodeToOriginal() {
        byte[] original = "different paths same destination".getBytes(StandardCharsets.UTF_8);
        String key = "key";

        byte[] encoded1 = Scrambler.encode(original, key);
        byte[] encoded2 = Scrambler.encode(original, key);

        byte[] decoded1 = Scrambler.decode(encoded1, key);
        byte[] decoded2 = Scrambler.decode(encoded2, key);

        assertArrayEquals(original, decoded1);
        assertArrayEquals(original, decoded2);
    }

    // --------------------------------------------------------
    // --- EDGE CASES -----------------------------------------

    @Test
    public void decode_corruptedPayload_doesNotCrash() {
        byte[] original = "valid payload".getBytes(StandardCharsets.UTF_8);
        String key = "key";
        byte[] encoded = Scrambler.encode(original, key);

        // Corrupt the middle of the payload
        if (encoded.length > Scrambler.SALT_LEN + 2) {
            encoded[Scrambler.SALT_LEN + 1] = 0x00;
        }

        // Should decode without crashing (garbage out is acceptable)
        byte[] decoded = Scrambler.decode(encoded, key);
        assertNotNull(decoded);
    }

    @Test
    public void decode_payloadOnlySalt_returnsInputAsIs() {
        byte[] saltOnly = new byte[Scrambler.SALT_LEN];
        java.util.Random r = new java.util.Random();
        r.nextBytes(saltOnly);

        byte[] decoded = Scrambler.decode(saltOnly, "key");

        // payload == SALT_LEN: no content bytes — returns input reference unchanged
        assertSame(saltOnly, decoded);
    }

    @Test
    public void decode_payloadShorterThanSalt_returnsInput() {
        byte[] shortPayload = new byte[Scrambler.SALT_LEN - 1];

        byte[] decoded = Scrambler.decode(shortPayload, "key");

        assertSame(shortPayload, decoded);
    }

    // --------------------------------------------------------
    // --- SALT_LEN CONSTANT VERIFICATION ---------------------

    @Test
    public void saltLen_isExactly4Bytes() {
        assertEquals(4, Scrambler.SALT_LEN);
    }

    // --------------------------------------------------------
    // --- KEY ISOLATION AND BINARY COVERAGE ------------------

    @Test
    public void encodeDecode_threeDistinctKeys_allRoundtrip() {
        byte[] payload = "shared payload".getBytes(StandardCharsets.UTF_8);
        String[] keys = {"key_alpha", "key_beta", "key_gamma"};

        for (String key : keys) {
            byte[] encoded = Scrambler.encode(payload, key);
            byte[] decoded = Scrambler.decode(encoded, key);
            assertArrayEquals("roundtrip failed for key: " + key, payload, decoded);
        }
    }

    @Test
    public void decode_withWrongKey_doesNotMatchOriginal() {
        byte[] payload = "test payload".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Scrambler.encode(payload, "correct_key");
        byte[] decodedWrong = Scrambler.decode(encoded, "wrong_key");

        // Wrong key produces different output — confirms key is actually used
        assertFalse(java.util.Arrays.equals(payload, decodedWrong));
    }

    @Test
    public void encode_singleBytePayload_roundtrips() {
        byte[] payload = new byte[]{0x42};

        byte[] encoded = Scrambler.encode(payload, "key");
        byte[] decoded = Scrambler.decode(encoded, "key");

        assertEquals(Scrambler.SALT_LEN + 1, encoded.length);
        assertArrayEquals(payload, decoded);
    }

    @Test
    public void decode_twoBytePayload_returnsInput() {
        byte[] twoBytes = new byte[]{0x01, 0x02};

        byte[] decoded = Scrambler.decode(twoBytes, "key");

        assertSame(twoBytes, decoded);
    }

    @Test
    public void encodeDecode_binaryPayload_roundtrips() {
        // All 256 byte values — exercises every code path in the XOR/add logic
        byte[] payload = new byte[256];
        for (int i = 0; i < 256; i++) payload[i] = (byte) i;

        byte[] encoded = Scrambler.encode(payload, "binaryKey");
        byte[] decoded = Scrambler.decode(encoded, "binaryKey");

        assertArrayEquals(payload, decoded);
    }
}