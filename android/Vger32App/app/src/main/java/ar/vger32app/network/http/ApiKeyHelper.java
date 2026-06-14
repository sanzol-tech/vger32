package ar.vger32app.network.http;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.config.safe.SecurePreferencesManager;

/*
 * Resolves and prompts for HTTP API keys used to authenticate against ESP32 modules.
 *
 * Resolution chain (in priority order):
 * 1. Per-module key  — stored in EncryptedSharedPreferences per mid
 * 2. Global default  — set once in Settings, covers all modules without a specific key
 * 3. null            — no key available; caller should invoke promptAndSave()
 *
 * On HTTP 401, HttpViewModel retries with the next step in the chain before
 * falling through to promptAndSave().
 */

public final class ApiKeyHelper {

    // Utility class — not instantiable.
    private ApiKeyHelper() {
    }

    // --------------------------------------------------------
    // --- CALLBACK -------------------------------------------

    public interface OnKeyReady {
        void onKey(String key);

        void onCancelled();
    }

    // --------------------------------------------------------
    // --- RESOLUTION -----------------------------------------

    public static String getModuleKey(Context context, String mid) {
        if (mid == null) return null;
        String key = SecurePreferencesManager.getInstance(context).getApiKey(mid);
        return (key != null && !key.isEmpty()) ? key : null;
    }

    public static String getDefaultKey() {
        String key = SettingsManager.getDefaultApiKey();
        return (key != null && !key.isEmpty()) ? key : null;
    }

    public static String resolveKey(Context context, String mid) {
        String specific = getModuleKey(context, mid);
        if (specific != null) return specific;
        return getDefaultKey();
    }

    // --------------------------------------------------------
    // --- PROMPT ---------------------------------------------

    public static void promptAndSave(Fragment fragment, String mid, OnKeyReady callback) {
        Context context = fragment.requireContext();

        View dialogView = LayoutInflater.from(context).inflate(ar.vger32app.R.layout.dialog_input, null);
        EditText input = dialogView.findViewById(ar.vger32app.R.id.edit_input);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(context.getString(ar.vger32app.R.string.api_key_title));
        input.setSingleLine(true);

        new AlertDialog.Builder(context)
                .setTitle(mid != null
                        ? context.getString(ar.vger32app.R.string.api_key_title_module, mid)
                        : context.getString(ar.vger32app.R.string.api_key_title))
                .setMessage(context.getString(ar.vger32app.R.string.api_key_message))
                .setView(dialogView)
                .setPositiveButton(ar.vger32app.R.string.btn_save, (d, w) -> {
                    String key = input.getText().toString().trim();
                    if (!key.isEmpty() && mid != null) {
                        SecurePreferencesManager.getInstance(context).saveApiKey(mid, key);
                    }
                    callback.onKey(key.isEmpty() ? null : key);
                })
                .setNegativeButton(ar.vger32app.R.string.cancel, (d, w) -> callback.onCancelled())
                .setCancelable(false)
                .show();
    }

    public static void resolveOrPrompt(Fragment fragment, String mid, OnKeyReady callback) {
        String key = resolveKey(fragment.requireContext(), mid);
        if (key != null) {
            callback.onKey(key);
        } else {
            promptAndSave(fragment, mid, callback);
        }
    }
}