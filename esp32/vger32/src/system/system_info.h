/*
 * system_info.h
 *
 * Responsibility: Runtime system state serialization for the dashboard API.
 *
 * All functions return const char* pointing to internal static buffers.
 * The returned pointer is valid until the next call to the same function.
 */

#ifndef SYSTEM_INFO_H
#define SYSTEM_INFO_H

#include <Arduino.h>

// Device identity and current network state (KV_SEP-separated, one field per line).
// mid=VGER_29858\nchip=ESP32-S3\nbrd=ESP32S3_DEV\npid=full\nip=192.168.0.8\nver=1.2.3\nsts=connected STA\n
//   mid  = module ID
//   chip = chip model
//   brd  = board identifier
//   pid  = active mission profile ID
//   ip   = local IP address
//   ver  = build version
//   sts  = connected STA | connected AP | connecting
// Internal buffer: 256 bytes. Valid until next call.
const char *get_identity();

// Current device time (KV_SEP-separated).
// ts=1745678901\n
//   ts = Unix timestamp if synced, seconds since boot otherwise.
// Internal buffer: 24 bytes. Valid until next call.
const char *get_time();

// Runtime system metrics — changes slowly (slow refresh).
// heap=320/520KB\nhmax=400KB\nfls=1024/4096KB\nlfs=128/512KB\ncpu=240MHz\ntmp=52.3C\nupt=0d 01h 23m 45s\n
//   heap = heap used/total KB
//   hmax = heap peak used (lifetime) KB
//   fls  = sketch used/total KB
//   lfs  = LittleFS used/total KB
//   cpu  = CPU frequency MHz
//   tmp  = internal temperature C
//   upt  = uptime string
// Internal buffer: 160 bytes. Valid until next call.
const char *get_metrics();

#endif
