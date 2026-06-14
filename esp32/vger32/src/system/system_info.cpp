/*
 * system_info.cpp
 *
 * Responsibility: Runtime system state serialization for the dashboard API.
 *
 * All functions return const char* to internal static buffers.
 * Valid until the next call to the same function.
 */

#include <LittleFS.h>

#include "system_info.h"

#include "config/build_info.h"
#include "config/constants.h"
#include "config/preferences.h"
#include "network/time_manager.h"
#include "network/wifi_manager.h"
#include "profiles/mission_manager.h"

// ==========================================
// IDENTITY
// ==========================================

const char *get_identity() {
    static char buf[256];

    const char *sts;
    if (wifi_manager_is_connected()) sts = "connected STA";
    else if (wifi_manager_is_ap_active()) sts = "connected AP";
    else sts = "connecting";

    snprintf(buf, sizeof(buf),
             "mid%c%s\n"
             "chip%c%s\n"
             "brd%c%s\n"
             "pid%c" PROFILE_NAME "\n"
             "ip%c%s\n"
             "ver%c" BUILD_VERSION "\n"
             "sts%c%s\n",
             KV_SEP, cfg_module_id.c_str(),
             KV_SEP, ESP.getChipModel(),
             KV_SEP, ARDUINO_BOARD,
             KV_SEP,
             KV_SEP, wifi_manager_get_ip(),
             KV_SEP,
             KV_SEP, sts);

    return buf;
}

// ==========================================
// TIME
// ==========================================

const char *get_time() {
    static char buf[24]; // "ts=4294967295\n" = 15 chars

    snprintf(buf, sizeof(buf), "ts%c%lu\n",
             KV_SEP, (unsigned long) time_get_timestamp_fallback());

    return buf;
}

// ==========================================
// METRICS
// ==========================================

static const char *get_uptime() {
    static char buf[24]; // "99d 23h 59m 59s" = 15 chars

    unsigned long ms = millis();
    int days = ms / 86400000UL;
    int hours = (ms % 86400000UL) / 3600000UL;
    int minutes = (ms % 3600000UL) / 60000UL;
    int seconds = (ms % 60000UL) / 1000UL;

    snprintf(buf, sizeof(buf), "%dd %02dh %02dm %02ds",
             days, hours, minutes, seconds);

    return buf;
}

const char *get_metrics() {
    static char buf[160];

    float heapTotalKB = ESP.getHeapSize() / 1024.0f;
    float heapFreeKB = ESP.getFreeHeap() / 1024.0f;
    float heapUsedKB = heapTotalKB - heapFreeKB;
    float heapMaxUsedKB = heapTotalKB - (ESP.getMinFreeHeap() / 1024.0f);

    float sketchUsedKB = ESP.getSketchSize() / 1024.0f;
    float sketchTotalKB = (ESP.getSketchSize() + ESP.getFreeSketchSpace()) / 1024.0f;
    float lfsUsedKB = LittleFS.usedBytes() / 1024.0f;
    float lfsTotalKB = LittleFS.totalBytes() / 1024.0f;

    snprintf(buf, sizeof(buf),
             "heap%c%.0f/%.0fKB\n"
             "hmax%c%.0fKB\n"
             "fls%c%.0f/%.0fKB\n"
             "lfs%c%.0f/%.0fKB\n"
             "cpu%c%dMHz\n"
             "tmp%c%.1fC\n"
             "upt%c%s\n",
             KV_SEP, heapUsedKB, heapTotalKB,
             KV_SEP, heapMaxUsedKB,
             KV_SEP, sketchUsedKB, sketchTotalKB,
             KV_SEP, lfsUsedKB, lfsTotalKB,
             KV_SEP, ESP.getCpuFreqMHz(),
             KV_SEP, temperatureRead(),
             KV_SEP, get_uptime());

    return buf;
}
