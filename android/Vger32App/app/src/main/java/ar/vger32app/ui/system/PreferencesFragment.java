package ar.vger32app.ui.system;

import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import ar.vger32app.R;
import ar.vger32app.config.safe.SecurePreferencesManager;

/*
 * App preferences screen.
 *
 * default_api_key and default_scrambler_key are intercepted so their values
 * go to EncryptedSharedPreferences instead of the default plain store.
 * Returning false from the listener prevents the framework from persisting
 * the value in plain storage.
 *
 * Because EditTextPreference reads its displayed value from plain SharedPreferences
 * (where we never write), the input field is always blank on reopen — correct
 * behaviour for password-style fields. The summary is updated to show whether
 * a value is currently stored, so the user can tell at a glance.
 */

public class PreferencesFragment extends PreferenceFragmentCompat {

    private static final String PREF_DEFAULT_API_KEY = "default_api_key";
    private static final String PREF_DEFAULT_SCRAMBLER_KEY = "default_scrambler_key";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);
        interceptApiKey();
        interceptScramblerKey();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh summaries every time the screen becomes visible
        refreshSummaries();
    }

    // --------------------------------------------------------
    // --- INTERCEPT — redirect to EncryptedSharedPreferences ---

    private void interceptApiKey() {
        EditTextPreference pref = findPreference(PREF_DEFAULT_API_KEY);
        if (pref == null) return;
        pref.setOnPreferenceChangeListener((p, newValue) -> {
            String value = newValue != null ? newValue.toString().trim() : "";
            SecurePreferencesManager.getInstance(requireContext()).saveDefaultApiKey(value);
            updateSummary(pref, value);
            return false;
        });
    }

    private void interceptScramblerKey() {
        EditTextPreference pref = findPreference(PREF_DEFAULT_SCRAMBLER_KEY);
        if (pref == null) return;
        pref.setOnPreferenceChangeListener((p, newValue) -> {
            String value = newValue != null ? newValue.toString().trim() : "";
            SecurePreferencesManager.getInstance(requireContext()).saveDefaultScramblerKey(value);
            updateSummary(pref, value);
            return false;
        });
    }

    // --------------------------------------------------------
    // --- SUMMARY — "Set" / "Not set" indicator --------------

    private void refreshSummaries() {
        SecurePreferencesManager spm = SecurePreferencesManager.getInstance(requireContext());

        EditTextPreference apiKeyPref = findPreference(PREF_DEFAULT_API_KEY);
        if (apiKeyPref != null) {
            String stored = spm.getDefaultApiKey();
            updateSummary(apiKeyPref, stored);
        }

        EditTextPreference scramblerKeyPref = findPreference(PREF_DEFAULT_SCRAMBLER_KEY);
        if (scramblerKeyPref != null) {
            String stored = spm.getDefaultScramblerKey();
            updateSummary(scramblerKeyPref, stored);
        }
    }

    private void updateSummary(EditTextPreference pref, String value) {
        boolean isSet = value != null && !value.trim().isEmpty();
        pref.setSummary(isSet
                ? getString(R.string.preference_value_masked)
                : getString(R.string.preference_value_not_set));
    }
}