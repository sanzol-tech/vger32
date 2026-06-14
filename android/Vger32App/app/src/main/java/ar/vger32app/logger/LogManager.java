package ar.vger32app.logger;

import android.content.Context;

/*
 * Single access point for the application logger.
 * Call LogManager.init(context) once from MyApplication.onCreate()
 * before any other use. APP_LOGGER is available immediately after.
 */

public class LogManager {

    public static AppLogger APP_LOGGER;

    public static void init(Context context) {
        APP_LOGGER = new AppLogger(context, "app_logger", 1000);
    }

    private LogManager() {
    }
}