/*
 * obfuscated_file.h
 *
 * Generic per-line obfuscation for .vger config files.
 * Key derived from eFuseMac — unique and immutable per chip.
 *
 * File format (obfuscated):
 *   Line 1: "##SCRM##\n"
 *   Remaining lines: base64(scramble(plaintext_line, efuseMac))\n
 *
 * Callers (keys.cpp, known_networks.cpp) only deal with plaintext lines.
 * They never see the scrambler or base64 — obfuscation is fully transparent.
 *
 * On first load from a plaintext file, the file is immediately re-saved
 * in obfuscated form via the provided save callback.
 *
 * Max line length: OBFUSCATED_LINE_MAX bytes (plaintext).
 * Stack-safe — no heap allocation.
 */

#ifndef OBFUSCATED_FILE_H
#define OBFUSCATED_FILE_H

#include <Arduino.h>

static constexpr char OBFUSCATED_MAGIC[] = "##SCRM##";
static constexpr size_t OBFUSCATED_LINE_MAX = 100; // max plaintext line
static constexpr size_t OBFUSCATED_B64_MAX = 152; // max base64(scramble(100 bytes))

// Called once per plaintext line during load.
// line: null-terminated, no trailing \n. ctx: caller-provided context.
typedef void (*obfuscated_line_cb)(const char *line, void *ctx);

// Called to re-save the file in obfuscated form after a plaintext load.
// Implemented by the caller (keys.cpp / known_networks.cpp).
typedef bool (*obfuscated_save_cb)(void *ctx);

// Load a .vger file and deliver each line (already decoded) to on_line().
// If the file is plaintext (no magic header), delivers lines as-is,
// then calls on_save() to re-save in obfuscated form.
// Returns false if the file cannot be opened.
bool obfuscated_file_load(const char *path,
                          obfuscated_line_cb on_line,
                          obfuscated_save_cb on_save,
                          void *ctx);

// Save lines in obfuscated form.
// lines[]: array of plaintext strings (no \n needed).
// count: number of lines.
// Returns false if the file cannot be written.
bool obfuscated_file_save(const char *path,
                          const char **lines,
                          uint8_t count);

#endif