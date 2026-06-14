/*
 * mn_full.h
 *
 * Full mission logic.
 * Handles LED state tracking and mission-specific commands.
 *
 * Call mn_full_init() from setup().
 * Call mn_full_update() every loop() cycle.
 * mn_full_on_cmd() is the mission observer for cmd_dispatcher_init().
 */

#ifndef MN_FULL_H
#define MN_FULL_H

void mn_full_init();

void mn_full_update();

void mn_full_on_cmd(const char *topic, const char *payload);

#endif
