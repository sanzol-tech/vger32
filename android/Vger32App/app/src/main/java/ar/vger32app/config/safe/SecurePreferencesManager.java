package ar.vger32app.config.safe;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import ar.vger32app.logger.LogManager;
import ar.vger32app.ui.auth.StartDestination;

/*
 * Thread-safe singleton for encrypted storage of credentials,
 * per-device API keys, and scrambler tokens.
 */

public class SecurePreferencesManager {

    private static final String LOG_TAG = "SecurePreferencesManager";
    private static final String PREFERENCES_NAME = "secure_preferences";

    private static volatile SecurePreferencesManager instance;

    private static final String KEY_START_DESTINATION = "start_destination";
    private static final String KEY_USER_UNLOCK_CODE = "user_unlock_code";
    private static final String KEY_DEFAULT_SCRAMBLER = "scrambler_key";
    private static final String KEY_DEFAULT_API_KEY = "default_api_key";

    private static final String PREFIX_API_KEY = "api_key_";
    private static final String PREFIX_MODULE_SCRAMBLER = "module_scrambler_";

    private SharedPreferences securePreferences;
    private String initError = null; // null = ready, non-null = failed with this message

    // --------------------------------------------------------
    // --- INIT -----------------------------------------------

    private SecurePreferencesManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            securePreferences = EncryptedSharedPreferences.create(
                    context,
                    PREFERENCES_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            initError = e.getClass().getSimpleName() + ": " + e.getMessage();
            LogManager.APP_LOGGER.fatal(LOG_TAG, e);
        }
    }

    public static SecurePreferencesManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SecurePreferencesManager.class) {
                if (instance == null) {
                    instance = new SecurePreferencesManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public String getInitError() { return initError; }

    // --------------------------------------------------------
    // --- START DESTINATION ----------------------------------

    public SecurePreferencesManager saveStartDestination(StartDestination dest) {
        securePreferences.edit().putInt(KEY_START_DESTINATION, dest.getValue()).apply();
        return this;
    }

    public StartDestination getStartDestination() {
        int value = securePreferences.getInt(KEY_START_DESTINATION, StartDestination.SET_CODE.getValue());
        for (StartDestination d : StartDestination.values()) {
            if (d.getValue() == value) return d;
        }
        return StartDestination.SET_CODE;
    }

    // --------------------------------------------------------
    // --- USER UNLOCK CODE (PIN) -----------------------------

    public SecurePreferencesManager saveUserUnlockCode(String code) {
        securePreferences.edit().putString(KEY_USER_UNLOCK_CODE, code).apply();
        return this;
    }

    public String getUserUnlockCode() {
        return securePreferences.getString(KEY_USER_UNLOCK_CODE, null);
    }

    public void removeUserUnlockCode() {
        securePreferences.edit().remove(KEY_USER_UNLOCK_CODE).apply();
    }

    // --------------------------------------------------------
    // --- PER-DEVICE API KEYS --------------------------------

    public void saveApiKey(String mid, String apiKey) {
        securePreferences.edit().putString(PREFIX_API_KEY + mid, apiKey).apply();
        LogManager.APP_LOGGER.debug(LOG_TAG, "API key saved for: " + mid);
    }

    public String getApiKey(String mid) {
        return securePreferences.getString(PREFIX_API_KEY + mid, null);
    }

    public void removeApiKey(String mid) {
        securePreferences.edit().remove(PREFIX_API_KEY + mid).apply();
        LogManager.APP_LOGGER.debug(LOG_TAG, "API key removed for: " + mid);
    }

    // --------------------------------------------------------
    // --- PER-DEVICE SCRAMBLER KEYS --------------------------

    public void saveModuleScramblerKey(String mid, String key) {
        if (key == null || key.isEmpty()) {
            removeModuleScramblerKey(mid);
            return;
        }
        securePreferences.edit().putString(PREFIX_MODULE_SCRAMBLER + mid, key).apply();
        LogManager.APP_LOGGER.debug(LOG_TAG, "Scrambler key saved for: " + mid);
    }

    public String getModuleScramblerKey(String mid) {
        if (mid == null) return null;
        String key = securePreferences.getString(PREFIX_MODULE_SCRAMBLER + mid, null);
        return (key != null && !key.isEmpty()) ? key : null;
    }

    public void removeModuleScramblerKey(String mid) {
        securePreferences.edit().remove(PREFIX_MODULE_SCRAMBLER + mid).apply();
        LogManager.APP_LOGGER.debug(LOG_TAG, "Scrambler key removed for: " + mid);
    }

    // --------------------------------------------------------
    // --- GLOBAL DEFAULT SCRAMBLER KEY -----------------------

    public void saveDefaultScramblerKey(String key) {
        if (key == null || key.isEmpty()) {
            securePreferences.edit().remove(KEY_DEFAULT_SCRAMBLER).apply();
        } else {
            securePreferences.edit().putString(KEY_DEFAULT_SCRAMBLER, key).apply();
        }
        LogManager.APP_LOGGER.debug(LOG_TAG, "Global default scrambler key updated");
    }

    public String getDefaultScramblerKey() {
        return securePreferences.getString(KEY_DEFAULT_SCRAMBLER, null);
    }

    // --------------------------------------------------------
    // --- GLOBAL DEFAULT API KEY -----------------------------

    public void saveDefaultApiKey(String key) {
        if (key == null || key.isEmpty()) {
            securePreferences.edit().remove(KEY_DEFAULT_API_KEY).apply();
        } else {
            securePreferences.edit().putString(KEY_DEFAULT_API_KEY, key).apply();
        }
        LogManager.APP_LOGGER.debug(LOG_TAG, "Global default API key updated");
    }

    public String getDefaultApiKey() {
        return securePreferences.getString(KEY_DEFAULT_API_KEY, null);
    }

    public void clearTokens() {
        securePreferences.edit().remove(KEY_USER_UNLOCK_CODE).apply();
    }
}