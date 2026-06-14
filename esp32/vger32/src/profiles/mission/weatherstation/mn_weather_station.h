/*
 * mn_weather_station.h
 *
 * Weather station mission logic.
 * Handles LED state tracking, periodic MQTT sensor publish, and command dispatch.
 *
 * Call mn_weather_station_init() from setup().
 * Call mn_weather_station_update() every loop() cycle.
 * mn_weather_station_on_cmd() is the mission observer for cmd_dispatcher_init().
 */

#ifndef MN_WEATHER_STATION_H
#define MN_WEATHER_STATION_H

void mn_weather_station_init();

void mn_weather_station_update();

void mn_weather_station_on_cmd(const char *topic, const char *payload);

#endif
