/*
 * keys.cpp
 *
 * Loads sensitive keys from LittleFS /keys.vger at boot.
 * Falls back to hardcoded defaults for any missing key.
 *
 * File format (plaintext, before first load):
 *   api_key=value
 *   ap_pass=value
 *   scrambler_key=value
 *
 * On first load the file is automatically re-saved in obfuscated form
 * by obfuscated_file_load(). Subsequent loads are transparent.
 */

#include "keys.h"

#include "logger/sys_logger.h"
#include "config/obfuscated_file.h"

// ==========================================
// DEFAULTS
// ==========================================

static constexpr char DEFAULT_AP_PASS[] = "0102030405";
static constexpr char DEFAULT_API_KEY[] = "a1b2c3d4e5";
static constexpr char DEFAULT_SCRAMBLER_KEY[] = "vger32xk";

static constexpr char KEYS_PATH[] = "/keys.vger";

// ==========================================
// GLOBALS
// ==========================================

String cfg_ap_pass = DEFAULT_AP_PASS;
String cfg_api_key = DEFAULT_API_KEY;
String cfg_scrambler_key = DEFAULT_SCRAMBLER_KEY;

// ==========================================
// PRIVATE
// ==========================================

// Parse "key=value" or "key:value" and apply to globals.
static void parse_line(const char *line) {
    const char *sep = strchr(line, '=');
    if (!sep) sep = strchr(line, ':');
    if (!sep || sep == line) return;

    char key[32];
    size_t key_len = (size_t) (sep - line);
    if (key_len >= sizeof(key)) return;
    memcpy(key, line, key_len);
    key[key_len] = '\0';

    const char *val = sep + 1;

    if (strcmp(key, "api_key") == 0) cfg_api_key = val;
    else if (strcmp(key, "ap_pass") == 0) cfg_ap_pass = val;
    else if (strcmp(key, "scrambler_key") == 0) cfg_scrambler_key = val;
}

// obfuscated_line_cb — called once per decoded line
static void on_line(const char *line, void *ctx) {
    parse_line(line);
}

// obfuscated_save_cb — called after plaintext load to re-save obfuscated
static bool on_save(void *ctx) {
    char line0[64], line1[64], line2[64];
    snprintf(line0, sizeof(line0), "api_key=%s", cfg_api_key.c_str());
    snprintf(line1, sizeof(line1), "ap_pass=%s", cfg_ap_pass.c_str());
    snprintf(line2, sizeof(line2), "scrambler_key=%s", cfg_scrambler_key.c_str());

    const char *lines[] = {line0, line1, line2};
    return obfuscated_file_save(KEYS_PATH, lines, 3);
}

// ==========================================
// PUBLIC API
// ==========================================

void keys_load() {
    if (!obfuscated_file_load(KEYS_PATH, on_line, on_save, nullptr)) {
        sys_log(LOG_WARN, "Keys", "%s not found — using defaults", KEYS_PATH);
    } else {
        sys_log(LOG_INFO, "Keys", "Loaded");
    }
}