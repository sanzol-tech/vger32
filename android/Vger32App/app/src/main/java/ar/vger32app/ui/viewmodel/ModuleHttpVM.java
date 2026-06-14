package ar.vger32app.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ar.vger32app.R;
import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.Module;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.network.http.ApiKeyHelper;
import ar.vger32app.network.http.Vger32Api;
import ar.vger32app.network.http.Vger32ApiClient;

/*
 * Manages HTTP endpoint calls for the selected module.
 * Survives tab switches so in-flight requests are not cancelled on navigation.
 *
 * API key resolution on 401:
 * 1. If the current key is the module-specific key and a default exists → retry with default.
 * 2. If we already used the default, or no fallback exists → fire needsApiKey so the
 * fragment can prompt the user and save the result as a module-specific override.
 *
 * Scrambler key resolution (via Vger32ApiClient 3-arg constructor):
 * Per-module key (SecurePreferencesManager.getModuleScramblerKey) overrides the
 * global default (SettingsManager.getDefaultScramblerKey). Null = use global default.
 */

public class ModuleHttpVM extends AndroidViewModel
        implements ModulesStore.OnModuleUpdateListener {

    private static final String LOG_TAG = "ModuleHttpVM";

    public static class HttpResult {
        public final String title;
        public final String body;

        HttpResult(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    private final MutableLiveData<HttpResult> result = new MutableLiveData<>();
    private final MutableLiveData<String> needsApiKey = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private String mid;
    private String ip;
    private String apiKey;
    private String moduleKey;
    private String defaultKey;
    private Vger32Api api;

    public ModuleHttpVM(@NonNull Application application) {
        super(application);
        ModulesStore.getInstance(application).addListener(this);
        syncFromRegistry();
    }

    public LiveData<HttpResult> getResult() {
        return result;
    }

    public LiveData<String> getNeedsApiKey() {
        return needsApiKey;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public String getSelectedMid() {
        return mid;
    }

    public String getSelectedIp() {
        return ip;
    }

    public boolean hasApi() {
        return api != null;
    }

    @Override
    public void onModuleUpdated() {
        syncFromRegistry();
    }

    public void fetchSystemIdentity() {
        fetch(str(R.string.http_item_identity), () -> formatKv(api.getSystemIdentity()));
    }

    public void fetchSystemMetrics() {
        fetch(str(R.string.http_item_metrics), () -> formatKv(api.getSystemMetrics()));
    }

    public void fetchSensors() {
        fetch(str(R.string.http_item_sensors), () -> formatKv(api.getSensors()));
    }

    public void fetchBootHistory() {
        fetch(str(R.string.http_item_boot_history), () -> formatBootHistory(api.getBootHistory()));
    }

    public void fetchLogs() {
        fetch(str(R.string.http_item_logs), () -> {
            String raw = api.getLogs().trim();
            return raw.isEmpty() ? str(R.string.status_no_data) : raw;
        });
    }

    public void reboot() {
        fetch(str(R.string.http_item_reboot), () -> {
            String raw = api.reboot().trim();
            return raw.isEmpty() ? str(R.string.dialog_ok) : raw;
        });
    }

    public void forceAp() {
        fetch(str(R.string.http_item_force_ap), () -> {
            api.forceAp();
            return str(R.string.dialog_ok);
        });
    }

    public void updateApiKey(String key) {
        this.apiKey = key;
        this.moduleKey = key;
        this.defaultKey = ApiKeyHelper.getDefaultKey();
        api = buildApi();
    }

    private void fetch(String title, FetchCall call) {
        if (api == null) {
            result.postValue(new HttpResult(str(R.string.dialog_error_title),
                    str(R.string.status_no_module_selected)));
            return;
        }
        isLoading.postValue(true);
        executor.execute(() -> {
            try {
                result.postValue(new HttpResult(title, call.execute()));
            } catch (Exception e) {
                if (isAuthError(e)) handle401(title, call);
                else {
                    LogManager.APP_LOGGER.error(LOG_TAG, title + ": " + e.getMessage());
                    result.postValue(new HttpResult(str(R.string.dialog_error_title), e.getMessage()));
                }
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private void handle401(String title, FetchCall call) {
        boolean usedModuleKey = moduleKey != null && moduleKey.equals(apiKey);
        boolean hasOtherKey = defaultKey != null && !defaultKey.equals(apiKey);
        if (usedModuleKey && hasOtherKey) {
            LogManager.APP_LOGGER.info(LOG_TAG, "401 on " + title + " — retrying with default key");
            apiKey = defaultKey;
            api = buildApi();
            try {
                result.postValue(new HttpResult(title, call.execute()));
            } catch (Exception e2) {
                if (isAuthError(e2)) {
                    LogManager.APP_LOGGER.warn(LOG_TAG, "401 on " + title + " — no valid key available");
                    needsApiKey.postValue(mid);
                } else {
                    LogManager.APP_LOGGER.error(LOG_TAG, title + ": " + e2.getMessage());
                    result.postValue(new HttpResult(str(R.string.dialog_error_title), e2.getMessage()));
                }
            }
        } else {
            LogManager.APP_LOGGER.warn(LOG_TAG, "401 on " + title + " — no fallback key");
            needsApiKey.postValue(mid);
        }
    }

    @FunctionalInterface
    private interface FetchCall {
        String execute() throws Exception;
    }

    private static boolean isAuthError(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("401") || msg.contains("Unauthorized"));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        ModulesStore.getInstance().removeListener(this);
    }

    private void syncFromRegistry() {
        mid = ModulesStore.getInstance(getApplication()).getSelectedMid();
        if (mid == null) {
            ip = null;
            apiKey = null;
            moduleKey = null;
            defaultKey = null;
            api = null;
            return;
        }
        Module module = ModulesStore.getInstance().get(mid);
        if (module != null) {
            ip = module.getIp();
            moduleKey = ApiKeyHelper.getModuleKey(getApplication(), mid);
            defaultKey = ApiKeyHelper.getDefaultKey();
            apiKey = moduleKey != null ? moduleKey : defaultKey;
            api = buildApi();
        }
    }

    private Vger32Api buildApi() {
        if (mid == null || ip == null || ip.isEmpty()) return null;
        String moduleScramblerKey = SecurePreferencesManager
                .getInstance(getApplication()).getModuleScramblerKey(mid);
        return new Vger32Api(new Vger32ApiClient(ip, apiKey != null ? apiKey : "", moduleScramblerKey));
    }

    private String str(int resId) {
        return getApplication().getString(resId);
    }

    private static String formatKv(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : raw.trim().split("\n")) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2)
                sb.append(parts[0].trim()).append(": ").append(parts[1].trim()).append("\n");
            else if (!line.trim().isEmpty()) sb.append(line.trim()).append("\n");
        }
        return sb.toString();
    }

    private static String formatBootHistory(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String[] lines = raw.trim().split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(" ", 2);
            if (parts.length < 2) {
                sb.append(line).append("\n");
                continue;
            }
            try {
                long ts = Long.parseLong(parts[0]) * 1000L;
                sb.append(new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault()).format(new Date(ts)))
                        .append("  ").append(parts[1]).append("\n");
            } catch (NumberFormatException e) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}