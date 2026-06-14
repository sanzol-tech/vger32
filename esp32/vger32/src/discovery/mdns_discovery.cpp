/*
 * mdns_discovery.cpp
 *
 * Responsibility: mDNS service advertisement.
 *
 * Advertises _vger32._tcp (port 80) with TXT records matching the fields
 * produced by get_identity(). The Android app resolves this service via
 * NsdManager and reads identity from TXT records — no HTTP round-trip needed.
 *
 * TXT record fields (mirrors system_info.h get_identity()):
 *   mid   module ID
 *   chip  chip model string
 *   brd   board identifier
 *   pid   active mission profile ID
 *   ip    current STA IP address
 *   ver   build version
 *   sts   always "connected STA" — mDNS only runs in STA mode
 *
 * Transport: ESPmDNS / IDF mDNS component owns 224.0.0.251:5353 internally.
 * Our code never touches that address or port directly.
 *
 * Lifecycle:
 *   - Starts when wifi_manager_is_connected() first returns true.
 *   - Stops on disconnect or when mdns_discovery_enabled is set to false.
 *   - Restarts on reconnect if still enabled. This ensures the ip TXT record
 *     always reflects the address that was current when last announced.
 *
 * Note: ESP32 Arduino Core 3.x runs mDNS on a dedicated IDF task — no
 *       update() call is needed in the loop.
 */

#include <ESPmDNS.h>

#include "mdns_discovery.h"
#include "network/wifi_manager.h"
#include "config/preferences.h"
#include "config/build_info.h"
#include "logger/sys_logger.h"
#include "profiles/mission_manager.h"

// ==========================================
// CONFIG
// ==========================================

static constexpr char     MDNS_SERVICE_PROTO[] = "tcp";

// ==========================================
// STATE
// ==========================================

static bool started = false;

// ==========================================
// PRIVATE
// ==========================================

static void register_txt_records() {
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, "mid",  cfg_module_id.c_str());
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, "chip", ESP.getChipModel());
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, "brd",  ARDUINO_BOARD);
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, "pid",  PROFILE_NAME);
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, "ip",   wifi_manager_get_ip());
    MDNS.addServiceTxt(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, "ver",  BUILD_VERSION);
}

static void start() {
    if (!MDNS.begin(cfg_module_id.c_str())) {
        sys_log(LOG_ERROR, "MDNS", "begin() failed");
        return;
    }
    MDNS.addService(MDNS_SERVICE_TYPE, MDNS_SERVICE_PROTO, MDNS_SERVICE_PORT);
    register_txt_records();
    started = true;
    sys_log(LOG_INFO, "MDNS", "Started %s @ %s", cfg_module_id.c_str(), wifi_manager_get_ip());
}

static void stop() {
    MDNS.end();
    started = false;
    sys_log(LOG_INFO, "MDNS", "Stopped");
}

// ==========================================
// PUBLIC API
// ==========================================

void mdns_discovery_set_enabled(bool enabled) {
    mdns_discovery_enabled = enabled;
    if (!enabled && started) stop();
}

void mdns_discovery_handle() {
    const bool should_run = mdns_discovery_enabled && wifi_manager_is_connected();

    if (!should_run) {
        if (started) stop();
        return;
    }

    if (!started) start();
}