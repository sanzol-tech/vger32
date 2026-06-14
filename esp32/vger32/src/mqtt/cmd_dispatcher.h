/*
* cmd_dispatcher.h
 *
 * Infrastructure command dispatcher. Handles built-in system commands and
 * forwards every command — built-in or unknown — to a mission-supplied observer.
 *
 * Command source is transport-agnostic — callers may be MQTT, serial, HTTP, etc.
 * The dispatcher parses the last segment of a slash-delimited topic as the
 * command name, and uses the payload as optional arguments.
 *
 * Built-in commands (infrastructure only):
 *   reboot           — restarts the device immediately
 *   ping             — publishes a pong with the device identity
 *   force_ap         — persists AP flag to NVS and reboots into AP mode
 *   sleep <seconds>  — enters deep sleep for <seconds> seconds (one-shot)
 *
 * Mission commands (handled by the observer, not the dispatcher):
 *   msg              — display or log a text message
 *   publish_now      — trigger an immediate MQTT publish
 *   (any other)      — forwarded to the mission observer as-is
 *
 * Usage:
 *   cmd_dispatcher_init(my_mission_observer);  // call from setup(); pass nullptr if unused
 *   cmd_dispatcher_dispatch(topic, payload);   // call from any command source
 *
 * The observer receives every command — built-ins and unknowns alike — with
 * the original topic and payload. If no observer is registered, unknown
 * commands are logged as warnings.
 */

#ifndef CMD_DISPATCHER_H
#define CMD_DISPATCHER_H

#include <Arduino.h>

#include "mqtt/mqtt_client.h"

void cmd_dispatcher_init(cmd_callback_t observer);

void cmd_dispatcher_dispatch(const char *topic, const char *payload);

#endif