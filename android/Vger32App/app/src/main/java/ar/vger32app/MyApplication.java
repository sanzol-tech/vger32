package ar.vger32app;

import android.app.Application;
import android.content.Context;

import androidx.preference.PreferenceManager;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.DiscoverySource;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.network.mqtt.MqttManager;
import ar.vger32app.services.GlobalExceptionHandler;

/*
 * Application entry point. Initializes all singletons in dependency order:
 *   1. SettingsManager  — preferences, no dependencies
 *   2. LogManager       — needs context
 *   3. ModulesStore     — needs context, reads SettingsManager
 *   4. MqttManager      — needs context, reads SettingsManager
 */

public class MyApplication extends Application {

    private static final String LOG_TAG = MyApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();

        // --- Preferences --------------------------------------------
        PreferenceManager.setDefaultValues(context, R.xml.root_preferences, false);

        // --- ExceptionHandler ---------------------------------------
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler(prev));

        // --- SettingsManager ----------------------------------------
        SettingsManager.init(context);

        // --- Logger -------------------------------------------------
        LogManager.init(context);
        LogManager.APP_LOGGER.debug(LOG_TAG, "onCreate()");

        // --- ModuleStore — restore known modules, purge stale ones --
        new Thread(() -> {
            ModulesStore store = ModulesStore.getInstance(context);
            store.loadAll();
            int purged = store.purgeExpired();
            if (purged > 0) {
                LogManager.APP_LOGGER.info(LOG_TAG, "Purged " + purged + " expired module(s)");
            }
        }, "modulestore-init").start();

        // --- MqttManager --------------------------------------------
        MqttManager.getInstance(context).connect();
        MqttManager.getInstance().setPongListener(
                discovered -> ModulesStore.getInstance().register(discovered, DiscoverySource.MQTT_PONG));
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        MqttManager.getInstance().disconnect();
        LogManager.APP_LOGGER.debug(LOG_TAG, "onTerminate()");
        LogManager.APP_LOGGER.flush();
    }
}