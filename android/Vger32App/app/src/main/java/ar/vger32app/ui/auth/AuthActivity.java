package ar.vger32app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import ar.vger32app.R;
import ar.vger32app.config.DevicePermissionManager;
import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.ui.MainActivity;
import ar.vger32app.utils.DeviceUtils;
import ar.vger32app.utils.LocationHelper;

/*
 * Entry activity. Shows the PIN unlock or set-code screen based on
 * SecurePreferencesManager.getStartDestination(), then hands off to MainActivity.
 */

public class AuthActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_auth);

        setOrientation();

        inflateInitialFragment();

        DevicePermissionManager.checkPermissions(this);

        checkLocationEnabled();
    }


    // --------------------------------------------------------
    // --- LOCATION CHECK -------------------------------------

    private void checkLocationEnabled() {
        if (!LocationHelper.getInstance(this).isLocationEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.location_required_title)
                    .setMessage(R.string.location_required_msg)
                    .setPositiveButton(R.string.action_open_settings, (d, w) ->
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton(R.string.action_skip, null)
                    .setCancelable(false)
                    .show();
        }
    }

    // --------------------------------------------------------
    // --- FRAGMENTS ------------------------------------------

    private void inflateInitialFragment() {
        if (!SettingsManager.isUnlockCodeEnabled()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        String initError = SecurePreferencesManager.getInstance(this).getInitError();
        if (initError != null) {
            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.ic_cancel_red)
                    .setTitle(R.string.dialog_error_title)
                    .setMessage(R.string.err_secure_storage_init)
                    .setPositiveButton(R.string.dialog_ok, (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }

        StartDestination startDestination = SecurePreferencesManager
                .getInstance(this).getStartDestination();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        switch (startDestination) {
            case SET_CODE:
                transaction.replace(R.id.fragment_container_unlock,
                        getUnlockFragmentInstance(UnlockFragment.PARAM_ACTION_SETCODE));
                break;
            case REQ_CODE:
                transaction.replace(R.id.fragment_container_unlock,
                        getUnlockFragmentInstance(UnlockFragment.PARAM_ACTION_REQCODE));
                break;
            default:
        }

        transaction.commit();
    }

    private UnlockFragment getUnlockFragmentInstance(int mode) {
        UnlockFragment unlockFragment = new UnlockFragment();
        Bundle unlockArgs = new Bundle();
        unlockArgs.putInt("mode", mode);
        unlockFragment.setArguments(unlockArgs);
        return unlockFragment;
    }

    private void setOrientation() {
        DeviceUtils.applyOrientation(this);
    }
}