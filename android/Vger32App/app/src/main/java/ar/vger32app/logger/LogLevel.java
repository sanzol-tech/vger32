package ar.vger32app.logger;

/*
 * Log severity levels in ascending order: DEBUG, INFO, WARN, ERROR, FATAL.
 * Each level carries a numeric severity used for threshold filtering in AppLogger.
 */

public enum LogLevel {
    DEBUG(1), INFO(2), WARN(3), ERROR(4), FATAL(5);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }
}