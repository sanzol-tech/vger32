package ar.vger32app.network.discovery;

import android.content.Context;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.ModuleDiscovered;
import ar.vger32app.network.http.ApiKeyHelper;
import ar.vger32app.network.http.Vger32ApiClient;

/*
 * Scans a /24 subnet for vger32 modules by probing port 80 and calling
 * /api/system-identity on responsive hosts. Also handles single-IP probing.
 * Thread-safe and cancellable.
 */

public class LanScanner {

    private static final String LOG_TAG = "LanScanner";
    private static final int SCAN_THREADS = 10;

    public interface Callback {
        void onModuleFound(ModuleDiscovered discovered);

        void onProgress(int scanned, int found);

        void onFinished(int found, String subnet);
    }

    private final Context appContext;
    private final ExecutorService pool = Executors.newFixedThreadPool(SCAN_THREADS);
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    public LanScanner(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    public void scan(String subnet, int timeoutMs, Callback callback) {
        LogManager.APP_LOGGER.info(LOG_TAG, "LAN scan started on " + subnet + ".0/24");
        scanning.set(true);

        AtomicInteger remaining = new AtomicInteger(254);
        AtomicInteger found = new AtomicInteger(0);

        for (int i = 1; i <= 254; i++) {
            if (!scanning.get()) break;
            final String ip = subnet + "." + i;
            pool.execute(() -> {
                if (!scanning.get()) {
                    tick(remaining, found, subnet, callback);
                    return;
                }
                if (isPortOpen(ip, SettingsManager.getModuleHttpPort(), timeoutMs)) {
                    ModuleDiscovered discovered = fetchBestPayload(ip);
                    if (discovered != null) {
                        found.incrementAndGet();
                        callback.onModuleFound(discovered);
                    }
                }
                tick(remaining, found, subnet, callback);
            });
        }
    }

    public ModuleDiscovered probeIp(String ip) {
        LogManager.APP_LOGGER.info(LOG_TAG, "Manual IP probe: " + ip);
        return fetchBestPayload(ip);
    }

    public void cancel() {
        scanning.set(false);
    }

    public void destroy() {
        scanning.set(false);
        pool.shutdownNow();
    }

    public boolean isScanning() {
        return scanning.get();
    }

    // --------------------------------------------------------
    // --- PROBE ----------------------------------------------

    // Returns a ModuleDiscovered for a host, or null if unreachable or not a vger32 module.
    // Retries with the mid-specific API key if it differs from the default.
    private ModuleDiscovered fetchBestPayload(String ip) {
        try {
            String apiKey = ApiKeyHelper.resolveKey(appContext, null);
            String raw = fetchIdentity(ip, apiKey);
            if (raw == null) return null;

            ModuleDiscovered discovered = ModuleDiscovered.fromPayload(raw);
            if (discovered == null) return null;

            // Retry with the mid-specific key if it differs from the default
            String midKey = ApiKeyHelper.resolveKey(appContext, discovered.moduleId);
            if (!java.util.Objects.equals(midKey, apiKey)) {
                String retried = fetchIdentity(ip, midKey);
                if (retried != null) discovered = ModuleDiscovered.fromPayload(retried);
            }

            return discovered;

        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "probe " + ip + ": " + e.getMessage());
            return null;
        }
    }

    private String fetchIdentity(String ip, String apiKey) {
        try {
            Vger32ApiClient client = new Vger32ApiClient(ip, apiKey);
            // Discovery always uses plain text — the scanned device may not have
            // the HTTP scrambler enabled, and system-identity must be readable
            // regardless of global scrambler settings.
            String identity = client.getUnscrambled("/api/system-identity");
            return ModuleDiscovered.fromPayload(identity) != null ? identity : null;
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------
    // --- HELPERS --------------------------------------------

    private boolean isPortOpen(String ip, int port, int timeoutMs) {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.close();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void tick(AtomicInteger remaining, AtomicInteger found,
                      String subnet, Callback callback) {
        int left = remaining.decrementAndGet();
        int scanned = 254 - left;
        if (left == 0) {
            scanning.set(false);
            LogManager.APP_LOGGER.info(LOG_TAG,
                    "LAN scan finished: " + found.get() + " module(s) on " + subnet + ".0/24");
            callback.onFinished(found.get(), subnet);
        } else if (scanned % 10 == 0) {
            callback.onProgress(scanned, found.get());
        }
    }
}