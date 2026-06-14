package ar.vger32app.ui.viewmodel;

import android.app.Application;
import android.net.wifi.ScanResult;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ar.vger32app.R;
import ar.vger32app.localizer.FingerprintFilter;
import ar.vger32app.localizer.FingerprintFormat;
import ar.vger32app.localizer.Waypoint;
import ar.vger32app.localizer.WaypointStore;
import ar.vger32app.localizer.WifiScanParser;
import ar.vger32app.localizer.WifiScanner;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.Module;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.network.http.ApiKeyHelper;
import ar.vger32app.network.http.Vger32Api;
import ar.vger32app.network.http.Vger32ApiClient;
import ar.vger32app.ui.localizer.LocalizerWifiAdapter;
import ar.vger32app.utils.DateTimeUtils;

/*
 * Shared state for the three Localizer tabs, scoped to LocalizerFragment.
 *
 * Domain 1 — PHONE SCAN   live WiFi scan from the Android device
 * Domain 2 — MODULE SCAN  WiFi scan + fingerprint import from the selected ESP32
 * Domain 3 — WAYPOINTS    local CRUD and upload to the module
 */

public class LocalizerVM extends AndroidViewModel {

    private static final String LOG_TAG = "LocalizerVM";

    // --- Domain 1: phone scan ---
    private final MutableLiveData<List<ScanResult>> phoneScanResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> phoneScanLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> phoneScanStatus = new MutableLiveData<>();
    private List<ScanResult> lastPhoneScan = new ArrayList<>();
    private Set<String> phoneScanIncludedBssids; // "BSSID:frequency" keys; drives adapter greying

    // --- Domain 2: module scan ---
    private final MutableLiveData<List<LocalizerWifiAdapter.WifiItem>> moduleScanResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> moduleScanLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> moduleScanStatus = new MutableLiveData<>();
    private List<LocalizerWifiAdapter.WifiItem> lastModuleScan = new ArrayList<>();
    private Set<String> moduleScanIncludedMacs; // "MAC:channel" keys; drives adapter greying

    // --- Domain 3: waypoints ---
    private final MutableLiveData<List<Waypoint>> waypoints = new MutableLiveData<>();
    private final MutableLiveData<String> uploadStatus = new MutableLiveData<>();
    private final MutableLiveData<String> importStatus = new MutableLiveData<>();

    private final WifiScanner wifiScanner;
    private final WaypointStore waypointStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LocalizerVM(@NonNull Application application) {
        super(application);
        wifiScanner = new WifiScanner(application);
        waypointStore = WaypointStore.getInstance(application);
    }

    public LiveData<List<ScanResult>> getPhoneScanResults() {
        return phoneScanResults;
    }

    public LiveData<String> getPhoneScanStatus() {
        return phoneScanStatus;
    }

    public LiveData<Boolean> isPhoneScanLoading() {
        return phoneScanLoading;
    }

    public boolean hasPhoneScanResults() {
        return !lastPhoneScan.isEmpty();
    }

    public int getPhoneScanCount() {
        return lastPhoneScan.size();
    }

    public Set<String> getPhoneScanIncludedBssids() {
        return phoneScanIncludedBssids;
    }

    public LiveData<List<LocalizerWifiAdapter.WifiItem>> getModuleScanResults() {
        return moduleScanResults;
    }

    public LiveData<String> getModuleScanStatus() {
        return moduleScanStatus;
    }

    public LiveData<Boolean> isModuleScanLoading() {
        return moduleScanLoading;
    }

    public boolean hasModuleScanResults() {
        return !lastModuleScan.isEmpty();
    }

    public int getModuleScanCount() {
        return lastModuleScan.size();
    }

    public Set<String> getModuleScanIncludedMacs() {
        return moduleScanIncludedMacs;
    }

    public LiveData<List<Waypoint>> getWaypoints() {
        return waypoints;
    }

    public LiveData<String> getUploadStatus() {
        return uploadStatus;
    }

    public LiveData<String> getImportStatus() {
        return importStatus;
    }

    // --------------------------------------------------------
    // --- PHONE SCAN -----------------------------------------

    public void scanPhone() {
        phoneScanLoading.postValue(true);
        phoneScanStatus.postValue(str(R.string.status_scanning));
        phoneScanResults.postValue(new ArrayList<>());
        wifiScanner.scan(new WifiScanner.Callback() {
            @Override
            public void onResult(List<ScanResult> results) {
                lastPhoneScan = results;
                phoneScanIncludedBssids = FingerprintFilter.phoneIncludedKeys(results);
                phoneScanResults.postValue(results);
                String time = results.isEmpty() ? ""
                        : DateTimeUtils.formatUptimeMicros(results.get(0).timestamp);
                phoneScanStatus.postValue(
                        str(R.string.status_networks_count, results.size())
                                + "\n" + str(R.string.status_scan_cached, time));
                phoneScanLoading.postValue(false);
            }

            @Override
            public void onError(String message) {
                lastPhoneScan = new ArrayList<>();
                phoneScanIncludedBssids = null;
                phoneScanResults.postValue(new ArrayList<>());
                phoneScanStatus.postValue(message);
                phoneScanLoading.postValue(false);
            }
        });
    }

    // --------------------------------------------------------
    // --- MODULE SCAN -----------------------------------------

    public void scanFromModule(String mid) {
        if (mid == null) {
            moduleScanStatus.postValue(str(R.string.status_no_module_selected));
            return;
        }
        Module module = ModulesStore.getInstance().getSelectedModule();
        if (module == null || module.getIp() == null || module.getIp().isEmpty()) {
            moduleScanStatus.postValue(str(R.string.status_module_no_ip));
            return;
        }
        moduleScanLoading.postValue(true);
        moduleScanStatus.postValue(str(R.string.status_fetching));
        moduleScanResults.postValue(new ArrayList<>());

        String ip = module.getIp();
        String apiKey = ApiKeyHelper.resolveKey(getApplication(), mid);
        Vger32Api api = new Vger32Api(new Vger32ApiClient(ip, apiKey));

        executor.execute(() -> {
            try {
                List<LocalizerWifiAdapter.WifiItem> items = WifiScanParser.parse(api.getWifiScan());
                lastModuleScan = items;
                moduleScanIncludedMacs = FingerprintFilter.moduleIncludedKeys(items);
                moduleScanResults.postValue(items);
                moduleScanStatus.postValue(items.isEmpty()
                        ? str(R.string.status_no_networks_found)
                        : str(R.string.status_networks_count, items.size()));
            } catch (Exception e) {
                LogManager.APP_LOGGER.warn(LOG_TAG, "Module WiFi scan failed: " + e.getMessage());
                lastModuleScan = new ArrayList<>();
                moduleScanIncludedMacs = null;
                moduleScanResults.postValue(new ArrayList<>());
                moduleScanStatus.postValue(str(R.string.status_error_fmt, e.getMessage()));
            } finally {
                moduleScanLoading.postValue(false);
            }
        });
    }

    // Merges fingerprints from GET /api/wifi-fingerprints into local WaypointStore.
    // Each import creates a new entry; existing waypoints are not overwritten.
    public void importFromModule() {
        String mid = ModulesStore.getInstance().getSelectedMid();
        Module module = ModulesStore.getInstance().getSelectedModule();
        if (module == null || module.getIp() == null || module.getIp().isEmpty()) {
            importStatus.postValue(str(R.string.status_module_no_ip));
            return;
        }
        String ip = module.getIp();
        String apiKey = ApiKeyHelper.resolveKey(getApplication(), mid);
        Vger32Api api = new Vger32Api(new Vger32ApiClient(ip, apiKey));
        moduleScanLoading.postValue(true);
        executor.execute(() -> {
            try {
                String raw = api.getWifiFingerprints();
                List<Waypoint> parsed = FingerprintFormat.parse(raw);
                for (Waypoint wp : parsed) {
                    waypointStore.save(wp.getName(), wp.getTimestamp(), wp.getNetworks(), mid);
                }
                waypoints.postValue(waypointStore.getAll());
                LogManager.APP_LOGGER.info(LOG_TAG, "Imported " + parsed.size() + " waypoints from " + mid);
                importStatus.postValue(str(R.string.status_import_ok, parsed.size()));
            } catch (Exception e) {
                LogManager.APP_LOGGER.error(LOG_TAG, "importFromModule failed: " + e.getMessage());
                importStatus.postValue(str(R.string.status_error_fmt, e.getMessage()));
            } finally {
                moduleScanLoading.postValue(false);
            }
        });
    }

    // --------------------------------------------------------
    // --- WAYPOINTS ------------------------------------------

    public void loadWaypoints() {
        waypoints.postValue(waypointStore.getAll());
    }

    public void saveWaypointFromScan(String name) {
        waypointStore.save(name, System.currentTimeMillis(),
                FingerprintFilter.fromPhoneScan(lastPhoneScan),
                str(R.string.waypoint_source_phone));
        LogManager.APP_LOGGER.info(LOG_TAG, "Waypoint saved from phone scan: " + name);
        waypoints.postValue(waypointStore.getAll());
    }

    public void saveWaypointFromModule(String name) {
        waypointStore.save(name, System.currentTimeMillis(),
                FingerprintFilter.fromModuleScan(lastModuleScan),
                ModulesStore.getInstance().getSelectedMid());
        LogManager.APP_LOGGER.info(LOG_TAG, "Waypoint saved from module scan: " + name);
        waypoints.postValue(waypointStore.getAll());
    }

    public void deleteWaypoints(Set<String> ids) {
        for (String id : ids) waypointStore.delete(id);
        LogManager.APP_LOGGER.info(LOG_TAG, "Waypoints deleted: " + ids.size());
        waypoints.postValue(waypointStore.getAll());
    }

    public String exportWaypoints(Set<String> ids) {
        return waypointStore.exportToFirmwareFormat(new ArrayList<>(ids));
    }

    // Replaces the module's stored fingerprints via PUT /api/wifi-fingerprints.
    public void uploadWaypoints(Set<String> ids) {
        if (ids.isEmpty()) {
            uploadStatus.postValue(str(R.string.status_no_data));
            return;
        }
        String mid = ModulesStore.getInstance().getSelectedMid();
        Module module = ModulesStore.getInstance().getSelectedModule();
        if (module == null || module.getIp() == null || module.getIp().isEmpty()) {
            uploadStatus.postValue(str(R.string.status_no_module_selected));
            return;
        }
        String ip = module.getIp();
        String apiKey = ApiKeyHelper.resolveKey(getApplication(), mid);
        String payload = waypointStore.exportToFirmwareFormat(new ArrayList<>(ids));
        Vger32Api api = new Vger32Api(new Vger32ApiClient(ip, apiKey));
        uploadStatus.postValue(str(R.string.status_uploading));
        executor.execute(() -> {
            try {
                api.replaceWifiFingerprints(payload);
                uploadStatus.postValue(str(R.string.status_upload_ok, ids.size()));
                LogManager.APP_LOGGER.info(LOG_TAG, "Uploaded " + ids.size() + " waypoints to " + ip);
            } catch (Exception e) {
                LogManager.APP_LOGGER.error(LOG_TAG, "uploadWaypoints failed: " + e.getMessage());
                uploadStatus.postValue(str(R.string.status_error_fmt, e.getMessage()));
            }
        });
    }

    // --------------------------------------------------------

    @Override
    protected void onCleared() {
        super.onCleared();
        wifiScanner.release();
        executor.shutdownNow();
    }

    // --------------------------------------------------------

    private String str(int resId, Object... args) {
        return getApplication().getString(resId, args);
    }

    private String str(int resId) {
        return getApplication().getString(resId);
    }
}