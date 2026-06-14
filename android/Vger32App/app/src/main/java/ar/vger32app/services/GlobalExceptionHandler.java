package ar.vger32app.services;

import ar.vger32app.logger.LogManager;

/*
 * Catches uncaught exceptions on any thread, flushes the log to disk,
 * then delegates to the previously registered handler (e.g. Crashlytics).
 */

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final String LOG_TAG = "GlobalExceptionHandler";

    private final Thread.UncaughtExceptionHandler previousHandler;

    public GlobalExceptionHandler(Thread.UncaughtExceptionHandler previousHandler) {
        this.previousHandler = previousHandler;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable t) {
        LogManager.APP_LOGGER.fatal(LOG_TAG, t);
        LogManager.APP_LOGGER.flush();
        // Delegate to the previously registered handler (e.g. Firebase Crashlytics).
        if (previousHandler != null) previousHandler.uncaughtException(thread, t);
    }
}