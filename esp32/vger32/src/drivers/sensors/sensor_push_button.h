/*
 * sensor_push_button.h
 *
 * Responsibility: Driver for generic push buttons.
 *                 Supports up to PUSH_BUTTON_MAX buttons registered dynamically.
 *                 Interrupt-driven with debounce cooldown.
 *                 push_button_update() drives the simulation cycle in demo mode.
 */

#ifndef SENSOR_PUSH_BUTTON_H
#define SENSOR_PUSH_BUTTON_H

#ifdef HW_HAS_PUSH_BUTTON

#include "core/sensor_types.h"
static constexpr uint8_t PUSH_BUTTON_MAX = 8;
static constexpr uint32_t BUTTON_COOLDOWN_MS = 500;

// Register a button. Returns the assigned index, or -1 if full.
int push_button_register(uint8_t pin, const char *name);

void push_button_update();

#endif // HW_HAS_PUSH_BUTTON

#endif // SENSOR_PUSH_BUTTON_H