package ar.vger32app.logger;

import android.content.Context;

import java.time.LocalDateTime;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.utils.DateTimeUtils;

/*
 * Concrete logger that filters entries by the user-configured log level
 * and formats each line with timestamp, level, tag, and message.
 */

public class AppLogger extends Logger {

    public AppLogger(Context context, String fileName, int maxLines) {
        super(context, fileName, maxLines);
    }

    private void log(LogLevel level, String tag, String msg) {
        LogLevel current = LogLevel.valueOf(SettingsManager.getLogLevel());
        if (level.getSeverity() < current.getSeverity()) return;
        addLineEntry(formatLine(level, tag, msg));
    }

    private String formatLine(LogLevel level, String tag, String msg) {
        String datetime = LocalDateTime.now().format(DateTimeUtils.DATE_TIME_FORMATTER);
        return String.format("%-19s : %s : %s : %s", datetime, level.name(), tag, msg);
    }

    public void debug(String tag, String msg) {
        log(LogLevel.DEBUG, tag, msg);
    }

    public void info(String tag, String msg) {
        log(LogLevel.INFO, tag, msg);
    }

    public void warn(String tag, String msg) {
        log(LogLevel.WARN, tag, msg);
    }

    public void error(String tag, String msg) {
        log(LogLevel.ERROR, tag, msg);
    }

    public void fatal(String tag, String msg) {
        log(LogLevel.FATAL, tag, msg);
    }

    public void fatal(String tag, String msg, Throwable e) {
        log(LogLevel.FATAL, tag, msg + " : " + e.getMessage());
    }

    public void fatal(String tag, Throwable ex) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
        String simpleClassName = caller.getClassName().substring(caller.getClassName().lastIndexOf('.') + 1);
        String callerPath = simpleClassName + "." + caller.getMethodName();
        log(LogLevel.FATAL, tag, callerPath + " : " + ex.getMessage());
    }
}