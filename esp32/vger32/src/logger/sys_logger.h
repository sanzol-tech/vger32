/*
 * sys_logger.h
 *
 * System trace logger. Records tagged debug entries to Serial and to an
 * in-memory circular buffer. Entries are not persisted to LittleFS.
 */

#ifndef SYS_LOGGER_H
#define SYS_LOGGER_H

#include <Arduino.h>

// ==========================================
// LOG LEVEL
// ==========================================

typedef enum {
    LOG_NONE  = -1,
    LOG_FATAL =  0,
    LOG_ERROR =  1,
    LOG_WARN  =  2,
    LOG_INFO  =  3,
    LOG_DEBUG =  4,
} log_level_t;

// Runtime threshold — updated via preferences through log_set_level().
// Default is LOG_WARN; set to LOG_NONE to silence all output.
inline int log_level_runtime = LOG_WARN;

void log_set_level(int level);

// ==========================================
// PUBLIC API
// ==========================================

void        sys_logger_init();
void        sys_log(log_level_t level, const char *module, const char *fmt, ...);
const char *sys_logger_get_history();
void        sys_logger_clear();

#endif // SYS_LOGGER_H