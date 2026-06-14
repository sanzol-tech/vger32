package ar.vger32app.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
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
 * Manages the firmware log for the selected module via HTTP.
 * Follows the same patterns as HttpViewModel: reads module and keys from
 * ModuleStore and SecurePreferencesManager, exposes LiveData for content,
 * title, and loading state, and re-syncs if the selected module changes.
 *
 * Log format from firmware (sys_logger): oldest-first, one entry per line:
 *   "<unix_ts> <level_char> <module> <message>"
 *   e.g. "1718000000 I WiFi Connected, IP: 192.168.1.1"
 *
 * processLog() reverses the lines (newest first) and formats each entry as:
 *   "HH:MM:SS [I] [WiFi] Connected, IP: 192.168.1.1"
 * Lines that cannot be parsed are shown as-is.
 */

public class ModuleLogVM extends AndroidViewModel
        implements ModulesStore.OnModuleUpdateListener {

    private static final String LOG_TAG = "ModuleLogVM";

    private final MutableLiveData<String>  content   = new MutableLiveData<>();
    private final MutableLiveData<String>  title     = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public LiveData<String>  getContent()   { return content; }
    public LiveData<String>  getTitle()     { return title; }
    public LiveData<Boolean> isLoading()    { return isLoading; }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Vger32Api api;
    private String    mid;

    public ModuleLogVM(@NonNull Application application) {
        super(application);
        ModulesStore.getInstance(application).addListener(this);
        syncFromRegistry();
    }

    @Override
    public void onModuleUpdated() {
        syncFromRegistry();
        if (api != null) load();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        ModulesStore.getInstance().removeListener(this);
    }

    // --------------------------------------------------------
    // --- OPERATIONS -----------------------------------------

    public void load() {
        if (api == null) {
            title.postValue(str(R.string.http_item_logs));
            content.postValue(str(R.string.status_no_module_selected));
            return;
        }
        isLoading.postValue(true);
        executor.execute(() -> {
            try {
                String raw = api.getLogs().trim();
                content.postValue(raw.isEmpty()
                        ? str(R.string.status_no_data)
                        : processLog(raw));
                updateTitle();
            } catch (Exception e) {
                LogManager.APP_LOGGER.error(LOG_TAG, "load: " + e.getMessage());
                content.postValue(str(R.string.status_error_fmt, e.getMessage()));
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void clear() {
        if (api == null) return;
        isLoading.postValue(true);
        executor.execute(() -> {
            try {
                api.clearLogs();
                String raw = api.getLogs().trim();
                content.postValue(raw.isEmpty()
                        ? str(R.string.status_no_data)
                        : processLog(raw));
                updateTitle();
            } catch (Exception e) {
                LogManager.APP_LOGGER.error(LOG_TAG, "clear: " + e.getMessage());
                content.postValue(str(R.string.status_error_fmt, e.getMessage()));
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public String getMid() { return mid; }

    // --------------------------------------------------------
    // --- INTERNAL SETUP -------------------------------------

    private void syncFromRegistry() {
        mid = ModulesStore.getInstance(getApplication()).getSelectedMid();
        if (mid == null) {
            api = null;
            title.postValue(str(R.string.http_item_logs));
            return;
        }
        Module module = ModulesStore.getInstance().get(mid);
        if (module == null || module.getIp() == null || module.getIp().isEmpty()) {
            api = null;
            return;
        }
        String ip          = module.getIp();
        String moduleKey   = ApiKeyHelper.getModuleKey(getApplication(), mid);
        String defaultKey  = ApiKeyHelper.getDefaultKey();
        String apiKey      = moduleKey != null ? moduleKey : defaultKey;
        String scramblerKey = SecurePreferencesManager
                .getInstance(getApplication()).getModuleScramblerKey(mid);
        api = new Vger32Api(new Vger32ApiClient(ip, apiKey != null ? apiKey : "", scramblerKey));
        updateTitle();
    }

    private void updateTitle() {
        title.postValue(mid != null
                ? getApplication().getString(R.string.title_module_log_fmt, mid)
                : str(R.string.http_item_logs));
    }

    // --------------------------------------------------------
    // --- LOG PROCESSING -------------------------------------

    /*
     * Reverses the firmware log (oldest-first → newest-first) and formats
     * each entry for display.
     *
     * Firmware entry format: "<unix_ts> <level_char> <module> <message>"
     *   e.g. "1718000000 I WiFi Connected, IP: 192.168.1.1"
     *
     * Display format: "HH:MM:SS [I] [WiFi] Connected, IP: 192.168.1.1"
     *
     * Lines that cannot be parsed are shown as-is.
     */
    private String processLog(String raw) {
        String[] lines = raw.split("\n");

        // Reverse in-place: oldest-first → newest-first
        for (int i = 0, j = lines.length - 1; i < j; i++, j--) {
            String tmp = lines[i]; lines[i] = lines[j]; lines[j] = tmp;
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.isEmpty()) continue;
            sb.append(formatEntry(line)).append('\n');
        }
        return sb.toString();
    }

    /*
     * Parses one firmware log entry and returns a formatted display string.
     * Format in:  "<unix_ts> <level_char> <module> <message>"
     * Format out: "HH:MM:SS [I] [WiFi] message"
     * Returns the raw line if parsing fails.
     */
    private String formatEntry(String line) {
        // Expected: at least 4 space-separated tokens
        String[] parts = line.split(" ", 4);
        if (parts.length < 4) return line;

        try {
            long unixTs = Long.parseLong(parts[0]);
            String level   = parts[1];
            String module  = parts[2];
            String message = parts[3];

            String time = formatTime(unixTs);
            return time + " [" + level + "] [" + module + "] " + message;
        } catch (NumberFormatException e) {
            return line;
        }
    }

    private static final SimpleDateFormat TIME_FMT;
    static {
        TIME_FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);
        TIME_FMT.setTimeZone(TimeZone.getDefault());
    }

    private String formatTime(long unixSeconds) {
        return TIME_FMT.format(new Date(unixSeconds * 1000L));
    }

    // --------------------------------------------------------

    private String str(int resId, Object... args) {
        return getApplication().getString(resId, args);
    }

    private String str(int resId) {
        return getApplication().getString(resId);
    }
}