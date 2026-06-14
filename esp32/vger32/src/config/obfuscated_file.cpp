/*
 * obfuscated_file.cpp
 *
 * Per-line scrambler + base64 obfuscation for .vger config files.
 * Key derived from eFuseMac — unique and immutable per chip.
 *
 * obfuscated_file_load() reads line by line. For each line:
 *   - obfuscated file: base64-decode → scrambler-decode → deliver plaintext to callback
 *   - plain file:      deliver as-is → call save callback to re-save obfuscated
 *
 * obfuscated_file_save() scrambles + base64-encodes each line, prepends magic header.
 */

#include <LittleFS.h>
#include <mbedtls/base64.h>

#include "obfuscated_file.h"

#include "logger/sys_logger.h"
#include "scrambler/scrambler.h"

// ==========================================
// PRIVATE
// ==========================================

static String derive_key() {
    uint64_t mac = ESP.getEfuseMac();
    char buf[17];
    snprintf(buf, sizeof(buf), "%016llX", (unsigned long long) mac);
    return String(buf);
}

static bool encode_line(const char *plain, char *b64_out, size_t b64_out_size) {
    uint8_t cipher[OBFUSCATED_LINE_MAX + SCRAMBLER_SALT_LEN];
    String key = derive_key();
    int cipher_len = scrambler_encode((const uint8_t *) plain, (int) strlen(plain),
                                      cipher, key.c_str());
    size_t b64_len = 0;
    int ret = mbedtls_base64_encode((unsigned char *) b64_out, b64_out_size,
                                    &b64_len, cipher, (size_t) cipher_len);
    if (ret != 0) return false;
    b64_out[b64_len] = '\0';
    return true;
}

static bool decode_line(const char *b64_in, char *plain_out, size_t plain_out_size) {
    uint8_t cipher[OBFUSCATED_LINE_MAX + SCRAMBLER_SALT_LEN];
    size_t cipher_len = 0;

    int ret = mbedtls_base64_decode(cipher, sizeof(cipher), &cipher_len,
                                    (const unsigned char *) b64_in, strlen(b64_in));
    if (ret != 0) return false;

    String key = derive_key();
    uint8_t plain[OBFUSCATED_LINE_MAX + 1];
    int out_len = scrambler_decode(cipher, (int) cipher_len, plain, key.c_str());

    if (out_len <= 0 || (size_t) out_len >= plain_out_size) return false;
    memcpy(plain_out, plain, out_len);
    plain_out[out_len] = '\0';
    return true;
}

// ==========================================
// PUBLIC API
// ==========================================

bool obfuscated_file_load(const char *path,
                          obfuscated_line_cb on_line,
                          obfuscated_save_cb on_save,
                          void *ctx) {
    if (!LittleFS.exists(path)) return false;

    File f = LittleFS.open(path, "r");
    if (!f) {
        sys_log(LOG_ERROR, "ObfFile", "Could not open %s", path);
        return false;
    }

    bool is_obfuscated = false;
    bool first_line = true;
    char line[OBFUSCATED_B64_MAX + 2]; // base64 line + \r\0

    while (f.available()) {
        uint8_t len = f.readBytesUntil('\n', line, sizeof(line) - 1);
        if (len == 0) continue;
        line[len] = '\0';
        if (len > 0 && line[len - 1] == '\r') line[--len] = '\0';
        if (len == 0) continue;
        if (line[0] == '#' && strcmp(line, OBFUSCATED_MAGIC) != 0) continue;

        if (first_line) {
            first_line = false;
            if (strcmp(line, OBFUSCATED_MAGIC) == 0) {
                is_obfuscated = true;
                continue;
            }
        }

        if (is_obfuscated) {
            char plain[OBFUSCATED_LINE_MAX + 1];
            if (!decode_line(line, plain, sizeof(plain))) {
                sys_log(LOG_WARN, "ObfFile", "Failed to decode line in %s", path);
                continue;
            }
            on_line(plain, ctx);
        } else {
            on_line(line, ctx);
        }
    }

    f.close();

    if (!is_obfuscated && on_save) {
        sys_log(LOG_INFO, "ObfFile", "Plain file detected — obfuscating %s", path);
        on_save(ctx);
    }

    return true;
}

bool obfuscated_file_save(const char *path,
                          const char **lines,
                          uint8_t count) {
    File f = LittleFS.open(path, "w");
    if (!f) {
        sys_log(LOG_ERROR, "ObfFile", "Could not open %s for writing", path);
        return false;
    }

    f.printf("%s\n", OBFUSCATED_MAGIC);

    char b64[OBFUSCATED_B64_MAX];

    for (uint8_t i = 0; i < count; i++) {
        if (!encode_line(lines[i], b64, sizeof(b64))) {
            sys_log(LOG_ERROR, "ObfFile", "Failed to encode line %u in %s", i, path);
            f.close();
            return false;
        }
        f.printf("%s\n", b64);
    }

    f.close();
    sys_log(LOG_INFO, "ObfFile", "Saved %u line(s) to %s", count, path);
    return true;
}