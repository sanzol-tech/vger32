package ar.vger32app.ui.viewmodel;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ar.vger32app.R;
import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.module.DiscoverySource;
import ar.vger32app.module.Module;
import ar.vger32app.module.ModuleDiscovered;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.network.discovery.ApNetworkDetector;
import ar.vger32app.network.discovery.LanScanner;
import ar.vger32app.network.discovery.MdnsDiscoverer;
import ar.vger32app.network.discovery.UdpDiscoverer;
import ar.vger32app.network.mqtt.MqttManager;
import ar.vger32app.utils.NetworkUtils;

/*
 * Manages module discovery state: LAN scan, manual IP probe, MQTT ping,
 * mDNS discovery, UDP discovery, registry loading, and module deletion.
 * Survives tab switches and configuration changes.
 */

public class ModulesVM extends AndroidViewModel
        implements ModulesStore.OnModuleUpdateListener {

    private static final String LOG_TAG = "ModulesVM";
    private static final String DEFAULT_AP_IP = "192.168.4.1";

    private final MutableLiveData<List<Module>> modules = new MutableLiveData<>();
    private final MutableLiveData<String> status = new MutableLiveData<>();
    private final MutableLiveData<Boolean> discovering = new MutableLiveData<>(false);
    private final MutableLiveData<String> selectedMid = new MutableLiveData<>();
    private final MutableLiveData<Boolean> apNetworkDetected = new MutableLiveData<>(false);

    private final LanScanner scanner;
    private final MdnsDiscoverer mdnsDiscoverer;
    private final UdpDiscoverer udpDiscoverer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ModulesVM(@NonNull Application application) {
        super(application);
        scanner = new LanScanner(application);
        mdnsDiscoverer = new MdnsDiscoverer(application);
        udpDiscoverer = new UdpDiscoverer();
        ModulesStore.getInstance(application).addListener(this);
        selectedMid.setValue(ModulesStore.getInstance().getSelectedMid());

        List<Module> current = ModulesStore.getInstance().getAll();
        if (!current.isEmpty()) {
            modules.setValue(current);
            status.setValue(str(R.string.status_modules_known, current.size()));
        }
    }

    // --------------------------------------------------------
    // --- EXPOSED STATE --------------------------------------

    public LiveData<List<Module>> getModules() {
        return modules;
    }

    public LiveData<String> getStatus() {
        return status;
    }

    public LiveData<Boolean> isDiscovering() {
        return discovering;
    }

    public LiveData<String> getSelectedMid() {
        return selectedMid;
    }

    public LiveData<Boolean> isApNetworkDetected() {
        return apNetworkDetected;
    }

    // --------------------------------------------------------
    // --- OnModuleUpdateListener -----------------------------

    @Override
    public void onModuleUpdated() {
        List<Module> all = ModulesStore.getInstance().getAll();
        modules.postValue(all);
        selectedMid.postValue(ModulesStore.getInstance().getSelectedMid());
        if (!all.isEmpty()) {
            status.postValue(str(R.string.status_modules_known, all.size()));
        }
    }

    // --------------------------------------------------------
    // --- ACTIONS — discovery --------------------------------

    public void loadFromRegistry() {
        executor.execute(() -> {
            List<Module> known = ModulesStore.getInstance(getApplication()).loadAll();
            if (!known.isEmpty()) {
                modules.postValue(ModulesStore.getInstance().getAll());
                selectedMid.postValue(ModulesStore.getInstance().getSelectedMid());
                status.postValue(str(R.string.status_modules_loaded, known.size()));
            }
        });
    }

    public void refreshApNetwork() {
        apNetworkDetected.setValue(ApNetworkDetector.isEsp32ApNetwork(getApplication()));
    }

    public void sendMqttPing() {
        MqttManager.getInstance().ping();
        status.postValue(str(R.string.status_mqtt_ping_sent));
    }

    public void startMdnsDiscovery() {
        if (Boolean.TRUE.equals(discovering.getValue())) return;
        if (mdnsDiscoverer.isRunning()) return;
        discovering.postValue(true);
        status.postValue(str(R.string.status_mdns_discovering));
        mdnsDiscoverer.start(
                discovered -> ModulesStore.getInstance().register(discovered, DiscoverySource.MDNS),
                found -> {
                    discovering.postValue(false);
                    status.postValue(found == 0
                            ? str(R.string.status_mdns_none)
                            : str(R.string.status_mdns_found, found));
                }
        );
    }

    public void startUdpDiscovery() {
        if (Boolean.TRUE.equals(discovering.getValue())) return;
        if (udpDiscoverer.isRunning()) return;
        discovering.postValue(true);
        status.postValue(str(R.string.status_udp_discovering));
        udpDiscoverer.start(
                discovered -> ModulesStore.getInstance().register(discovered, DiscoverySource.UDP_DISCOVERY),
                found -> {
                    discovering.postValue(false);
                    status.postValue(found == 0
                            ? str(R.string.status_udp_none)
                            : str(R.string.status_udp_found, found));
                }
        );
    }

    public void startLanScan() {
        if (Boolean.TRUE.equals(discovering.getValue())) return;
        String subnet = NetworkUtils.getWifiSubnet(getApplication());
        if (subnet == null) {
            status.postValue(str(R.string.status_no_wifi));
            return;
        }
        discovering.postValue(true);
        status.postValue(str(R.string.status_scanning_subnet, subnet));
        scanner.scan(subnet, SettingsManager.getScanTimeout(), new LanScanner.Callback() {
            @Override
            public void onModuleFound(ModuleDiscovered discovered) {
                ModulesStore.getInstance().register(discovered, DiscoverySource.LAN_SCAN);
                modules.postValue(ModulesStore.getInstance().getAll());
            }

            @Override
            public void onProgress(int scanned, int found) {
                status.postValue(str(R.string.status_scanning_progress, scanned, found));
            }

            @Override
            public void onFinished(int found, String sn) {
                discovering.postValue(false);
                status.postValue(found == 0
                        ? str(R.string.status_no_modules_found, sn)
                        : str(R.string.status_modules_found, found, sn));
            }
        });
    }

    public void stopAllDiscovery() {
        scanner.cancel();
        mdnsDiscoverer.stop();
        udpDiscoverer.stop();
        discovering.postValue(false);
        status.postValue(str(R.string.status_scan_stopped));
    }

    public void connectToIp(String ip) {
        status.postValue(str(R.string.status_connecting_to_ip, ip));
        executor.execute(() -> {
            ConnectivityManager cm = (ConnectivityManager)
                    getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean bound = false;
            if (DEFAULT_AP_IP.equals(ip)) {
                Network wifiNetwork = ApNetworkDetector.getWifiNetwork(getApplication());
                if (wifiNetwork != null) {
                    cm.bindProcessToNetwork(wifiNetwork);
                    bound = true;
                }
            }
            try {
                ModuleDiscovered discovered = scanner.probeIp(ip);
                if (discovered != null) {
                    ModulesStore.getInstance(getApplication()).register(discovered, DiscoverySource.MANUAL_IP);
                    ModulesStore.getInstance().setSelectedMid(discovered.moduleId);
                    modules.postValue(ModulesStore.getInstance().getAll());
                    selectedMid.postValue(discovered.moduleId);
                    status.postValue(str(R.string.status_module_found, discovered.moduleId));
                } else {
                    status.postValue(str(R.string.status_no_module_at_ip, ip));
                }
            } finally {
                if (bound) cm.bindProcessToNetwork(null);
            }
        });
    }

    public void selectModule(Module module) {
        if (module == null) return;
        ModulesStore.getInstance(getApplication()).setSelectedMid(module.getModuleId());
        selectedMid.postValue(module.getModuleId());
        status.postValue(str(R.string.status_module_selected, module.getModuleId()));
    }

    // --------------------------------------------------------
    // --- ACTIONS — module keys ------------------------------

    public boolean hasApiKey(String mid) {
        return SecurePreferencesManager.getInstance(getApplication()).getApiKey(mid) != null;
    }

    public boolean hasScramblerKey(String mid) {
        return SecurePreferencesManager.getInstance(getApplication()).getModuleScramblerKey(mid) != null;
    }

    public void saveKeys(String mid, String apiKey, String scramblerKey) {
        SecurePreferencesManager spm = SecurePreferencesManager.getInstance(getApplication());
        if (!apiKey.isEmpty()) spm.saveApiKey(mid, apiKey);
        if (!scramblerKey.isEmpty()) spm.saveModuleScramblerKey(mid, scramblerKey);
    }

    public void clearKeys(String mid) {
        SecurePreferencesManager spm = SecurePreferencesManager.getInstance(getApplication());
        spm.removeApiKey(mid);
        spm.removeModuleScramblerKey(mid);
    }

    // --------------------------------------------------------
    // --- ACTIONS — deletion ---------------------------------

    public void deleteModule(String mid) {
        ModulesStore.getInstance(getApplication()).remove(mid);
    }

    public void deleteAll() {
        ModulesStore.getInstance(getApplication()).clear();
        status.postValue("");
    }

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    protected void onCleared() {
        super.onCleared();
        scanner.destroy();
        mdnsDiscoverer.stop();
        udpDiscoverer.stop();
        executor.shutdownNow();
        ModulesStore.getInstance().removeListener(this);
    }

    // --------------------------------------------------------

    private String str(int resId, Object... args) {
        return getApplication().getString(resId, args);
    }

    private String str(int resId) {
        return getApplication().getString(resId);
    }
}