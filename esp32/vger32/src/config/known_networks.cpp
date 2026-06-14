/*
 * known_networks.cpp
 *
 * Load, save, serialize and deserialize known WiFi networks
 * from/to LittleFS (/known_networks.vger).
 *
 * File format (plaintext, before first load):
 *   TYPE\x1FIDENTIFIER\x1FPASS\n   (one network per line)
 *
 * On first load the file is automatically re-saved in obfuscated form.
 * Obfuscation is handled entirely by obfuscated_file — this file only
 * parses and serializes its own plaintext format.
 *
 * Password handling (HTTP API):
 *   known_networks_to_text() masks passwords — "********" if set, "" if open.
 *   known_networks_from_text() treats "********" as "keep stored password".
 *   Passwords are never sent in cleartext to the frontend.
 */

#include <LittleFS.h>

#include "known_networks.h"

#include "logger/sys_logger.h"
#include "config/obfuscated_file.h"
#include "constants.h"

// ==========================================
// RUNTIME STATE
// ==========================================

KnownNetwork known_networks[KNOWN_NET_MAX];
uint8_t known_network_count = 0;

// ==========================================
// PRIVATE HELPERS
// ==========================================

static void safe_copy(char *dst, const char *src, size_t max_len) {
    strncpy(dst, src, max_len);
    dst[max_len] = '\0';
}

static bool is_valid_mac(const char *mac) {
    if (strlen(mac) != KNOWN_NET_MAC_LEN) return false;
    for (uint8_t i = 0; i < KNOWN_NET_MAC_LEN; i++) {
        char c = mac[i];
        if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
    }
    return true;
}

static const char *find_stored_pass(known_net_type_t type, const char *identifier) {
    for (uint8_t i = 0; i < known_network_count; i++) {
        if (known_networks[i].type != type) continue;
        if (strncmp(known_networks[i].identifier, identifier, KNOWN_NET_SSID_LEN) == 0)
            return known_networks[i].pass;
    }
    return "";
}

/*
 * Parse one plaintext line: TYPE\x1FIDENTIFIER\x1FPASS
 * Line must be null-terminated, no trailing newline.
 */
static bool parse_line(char *line, KnownNetwork &out) {
    char sep[2] = {FIELD_SEP, '\0'};
    char *ctx = nullptr;

    char *type_str = strtok_r(line, sep, &ctx);
    if (!type_str || strlen(type_str) != 1) return false;
    if (type_str[0] != 'S' && type_str[0] != 'M') return false;
    out.type = (known_net_type_t) type_str[0];

    char *identifier = strtok_r(nullptr, sep, &ctx);
    if (!identifier || strlen(identifier) == 0) return false;
    if (out.type == KNOWN_NET_BY_SSID) {
        if (strlen(identifier) > KNOWN_NET_SSID_LEN) return false;
    } else {
        if (!is_valid_mac(identifier)) return false;
    }
    safe_copy(out.identifier, identifier, KNOWN_NET_SSID_LEN);

    char *pass = strtok_r(nullptr, sep, &ctx);
    if (pass && strlen(pass) > KNOWN_NET_PASS_LEN) return false;
    safe_copy(out.pass, pass ? pass : "", KNOWN_NET_PASS_LEN);

    return true;
}

// obfuscated_line_cb — called once per decoded line during load
static void on_line(const char *line, void *ctx) {
    if (known_network_count >= KNOWN_NET_MAX) return;

    char buf[OBFUSCATED_LINE_MAX + 1];
    strncpy(buf, line, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    KnownNetwork entry;
    if (!parse_line(buf, entry)) {
        sys_log(LOG_WARN, "KnownNet", "Skipping malformed line");
        return;
    }
    known_networks[known_network_count++] = entry;
}

// obfuscated_save_cb — called after plaintext load to re-save obfuscated
static bool on_save(void *ctx) {
    return known_networks_save();
}

// ==========================================
// PUBLIC API
// ==========================================

void known_networks_load() {
    known_network_count = 0;

    if (!obfuscated_file_load(KNOWN_NET_PATH, on_line, on_save, nullptr)) {
        sys_log(LOG_INFO, "KnownNet", "No config file — starting empty");
        return;
    }

    sys_log(LOG_INFO, "KnownNet", "Loaded %u network(s)", known_network_count);
}

bool known_networks_save() {
    // Build plaintext lines: TYPE\x1FIDENTIFIER\x1FPASS
    char lines_buf[KNOWN_NET_MAX][OBFUSCATED_LINE_MAX + 1];
    const char *ptrs[KNOWN_NET_MAX];

    for (uint8_t i = 0; i < known_network_count; i++) {
        snprintf(lines_buf[i], sizeof(lines_buf[i]), "%c%c%s%c%s",
                 (char) known_networks[i].type,
                 FIELD_SEP,
                 known_networks[i].identifier,
                 FIELD_SEP,
                 known_networks[i].pass);
        ptrs[i] = lines_buf[i];
    }

    return obfuscated_file_save(KNOWN_NET_PATH, ptrs, known_network_count);
}

bool known_networks_from_text(const char *payload) {
    if (!payload || strlen(payload) == 0) return false;

    static char buf[KNOWN_NET_MAX * 100 + 1];
    strncpy(buf, payload, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    KnownNetwork tmp[KNOWN_NET_MAX];
    uint8_t tmp_count = 0;

    char *outer_ctx = nullptr;
    char *line = strtok_r(buf, "\n", &outer_ctx);

    while (line && tmp_count < KNOWN_NET_MAX) {
        size_t len = strlen(line);
        if (len > 0 && line[len - 1] == '\r') line[--len] = '\0';
        if (len == 0) {
            line = strtok_r(nullptr, "\n", &outer_ctx);
            continue;
        }

        KnownNetwork entry;
        if (!parse_line(line, entry)) {
            sys_log(LOG_WARN, "KnownNet", "from_text: malformed line — rejecting payload");
            return false;
        }

        if (strcmp(entry.pass, "********") == 0) {
            safe_copy(entry.pass,
                      find_stored_pass(entry.type, entry.identifier),
                      KNOWN_NET_PASS_LEN);
        }

        tmp[tmp_count++] = entry;
        line = strtok_r(nullptr, "\n", &outer_ctx);
    }

    if (tmp_count == 0) return false;

    for (uint8_t i = 0; i < tmp_count; i++) known_networks[i] = tmp[i];
    known_network_count = tmp_count;
    return true;
}

const char *known_networks_to_text() {
    static char buf[KNOWN_NET_MAX * 100 + 1];

    char *p = buf;
    char *end = buf + sizeof(buf) - 1;

    for (uint8_t i = 0; i < known_network_count && p < end; i++) {
        const char *masked = known_networks[i].pass[0] ? "********" : "";
        p += snprintf(p, end - p, "%c%c%s%c%s\n",
                      (char) known_networks[i].type,
                      FIELD_SEP,
                      known_networks[i].identifier,
                      FIELD_SEP,
                      masked);
    }

    *p = '\0';
    return buf;
}