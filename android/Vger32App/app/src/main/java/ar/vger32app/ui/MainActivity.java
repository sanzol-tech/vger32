package ar.vger32app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import ar.vger32app.R;
import ar.vger32app.config.DevicePermissionManager;
import ar.vger32app.databinding.ActivityMainBinding;
import ar.vger32app.logger.LogManager;
import ar.vger32app.utils.DeviceUtils;
import ar.vger32app.utils.LocationHelper;

/*
 * Single activity container. Bottom navigation bar with 5 tabs:
 *   Discovery → ModuleDiscoveryFragment  (always enabled)
 *   MQTT      → ModuleMqttFragment       (always enabled — module selection via spinner)
 *   HTTP      → ModuleHttpFragment       (always enabled — module selection via spinner)
 *   Localizer → LocalizerFragment        (always enabled)
 *   System    → SystemMenuFragment       (always enabled)
 *
 * All tabs are always accessible. Module selection is managed internally
 * via ModuleStore and selectors in each fragment.
 *
 * ModuleStore is initialized in MyApplication.onCreate() before this Activity starts.
 *
 * Ping button in toolbar sends a global ping to all modules via MQTT.
 */

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfig;
    private LinearLayout activeTab;

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setOrientation();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        navController = Navigation.findNavController(
                this, R.id.nav_host_fragment_content_main);

        appBarConfig = new AppBarConfiguration.Builder(
                R.id.moduleFragment,
                R.id.moduleMqttFragment,
                R.id.moduleHttpFragment,
                R.id.localizerFragment,
                R.id.systemMenuFragment)
                .build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig);

        setupBottomNav();

        DevicePermissionManager.checkPermissions(this);

        setActiveTab(binding.tabModules);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocationHelper.getInstance(getApplicationContext()).requestSingleUpdate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogManager.APP_LOGGER.flush();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfig)
                || super.onSupportNavigateUp();
    }

    // --------------------------------------------------------
    // --- SETUP ----------------------------------------------

    private void setupBottomNav() {
        binding.tabModules.setOnClickListener(v -> navigateTo(R.id.moduleFragment, null));
        binding.tabMqtt.setOnClickListener(v -> navigateTo(R.id.moduleMqttFragment, null));
        binding.tabHttp.setOnClickListener(v -> navigateTo(R.id.moduleHttpFragment, null));
        binding.tabLocalizer.setOnClickListener(v -> navigateTo(R.id.localizerFragment, null));
        binding.tabSettings.setOnClickListener(v -> navigateTo(R.id.systemMenuFragment, null));
    }

    // --------------------------------------------------------
    // --- NAVIGATION -----------------------------------------

    private void navigateTo(int destId, Bundle args) {
        NavOptions opts = new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, false)
                .setLaunchSingleTop(true)
                .build();

        navController.navigate(destId, args, opts);

        if (destId == R.id.moduleFragment) setActiveTab(binding.tabModules);
        else if (destId == R.id.moduleMqttFragment) setActiveTab(binding.tabMqtt);
        else if (destId == R.id.moduleHttpFragment) setActiveTab(binding.tabHttp);
        else if (destId == R.id.localizerFragment) setActiveTab(binding.tabLocalizer);
        else if (destId == R.id.systemMenuFragment) setActiveTab(binding.tabSettings);
    }

    // --------------------------------------------------------
    // --- TAB STATE ------------------------------------------

    private void setActiveTab(LinearLayout tab) {
        if (activeTab != null) applyTabActive(activeTab, false);
        activeTab = tab;
        applyTabActive(tab, true);
    }

    private void applyTabActive(LinearLayout tab, boolean active) {
        int color = active
                ? ContextCompat.getColor(this, R.color.color900)
                : ContextCompat.getColor(this, R.color.color400);
        for (int i = 0; i < tab.getChildCount(); i++) {
            View child = tab.getChildAt(i);
            if (child instanceof ImageView) ((ImageView) child).setColorFilter(color);
            else if (child instanceof TextView) ((TextView) child).setTextColor(color);
        }
    }

    // --------------------------------------------------------
    // --- ORIENTATION ----------------------------------------

    private void setOrientation() {
        DeviceUtils.applyOrientation(this);
    }
}