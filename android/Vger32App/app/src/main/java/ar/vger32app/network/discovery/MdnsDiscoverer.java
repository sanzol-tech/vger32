package ar.vger32app.network.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.ModuleDiscovered;

/*
 * Discovers vger32 devices advertising _vger32._tcp via mDNS.
 * For each service found, resolves it and reconstructs the identity payload
 * from TXT records — same key=value format as get_identity() on the firmware.
 *
 * Stops automatically after DISCOVERY_TIMEOUT_MS. Call stop() to abort early.
 * Callback methods are called from background threads — callers must post to
 * the UI thread if needed.
 *
 * Note: NsdManager.resolveService() serialises internally on some Android
 * versions. A new ResolveListener instance is created per service to avoid
 * blocking concurrent resolutions.
 */

public class MdnsDiscoverer {

    private static final String LOG_TAG = "MdnsDiscoverer";
    //private static final String SERVICE_TYPE         = "_vger32._tcp";
    private static final long DISCOVERY_TIMEOUT_MS = 5000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private Consumer<ModuleDiscovered> onFound;
    private IntConsumer onFinished;
    private volatile boolean running = false;
    private final AtomicInteger found = new AtomicInteger(0);

    private final Runnable stopRunnable = this::stopInternal;

    public MdnsDiscoverer(Context context) {
        this.context = context.getApplicationContext();
    }

    // --------------------------------------------------------
    // --- DISCOVERY -----------------------------------------

    public synchronized void start(Consumer<ModuleDiscovered> onFound, IntConsumer onFinished) {
        if (running) return;
        this.onFound = onFound;
        this.onFinished = onFinished;
        this.found.set(0);
        this.running = true;

        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        discoveryListener = buildDiscoveryListener();

        nsdManager.discoverServices(SettingsManager.getMdnsServiceType(), NsdManager.PROTOCOL_DNS_SD, discoveryListener);

        handler.postDelayed(stopRunnable, DISCOVERY_TIMEOUT_MS);
        LogManager.APP_LOGGER.info(LOG_TAG, "mDNS discovery started (" + SettingsManager.getMdnsServiceType() + ")");
    }

    public synchronized void stop() {
        handler.removeCallbacks(stopRunnable);
        stopInternal();
    }

    public boolean isRunning() {
        return running;
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private synchronized void stopInternal() {
        if (!running) return;
        running = false;
        try {
            if (nsdManager != null && discoveryListener != null) {
                nsdManager.stopServiceDiscovery(discoveryListener);
            }
        } catch (Exception e) {
            LogManager.APP_LOGGER.warn(LOG_TAG,
                    "stopServiceDiscovery: " + e.getMessage());
        }
        int n = found.get();
        LogManager.APP_LOGGER.info(LOG_TAG, "mDNS discovery finished: " + n + " module(s)");
        if (onFinished != null) onFinished.accept(n);
    }

    private NsdManager.DiscoveryListener buildDiscoveryListener() {
        return new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String serviceType) {
                LogManager.APP_LOGGER.debug(LOG_TAG, "onDiscoveryStarted");
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!running) return;
                // New ResolveListener per service — avoids serialisation issues
                // on older Android versions.
                nsdManager.resolveService(serviceInfo, buildResolveListener());
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                LogManager.APP_LOGGER.debug(LOG_TAG, "onDiscoveryStopped");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                LogManager.APP_LOGGER.error(LOG_TAG,
                        "onStartDiscoveryFailed: " + errorCode);
                running = false;
                if (onFinished != null) onFinished.accept(0);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                LogManager.APP_LOGGER.warn(LOG_TAG,
                        "onStopDiscoveryFailed: " + errorCode);
            }
        };
    }

    private NsdManager.ResolveListener buildResolveListener() {
        return new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                LogManager.APP_LOGGER.warn(LOG_TAG,
                        "onResolveFailed: " + serviceInfo.getServiceName()
                                + " err=" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                if (!running) return;
                String resolvedIp = serviceInfo.getHost() != null
                        ? serviceInfo.getHost().getHostAddress() : "";
                ModuleDiscovered discovered = ModuleDiscovered.fromTxtRecords(
                        serviceInfo.getAttributes(), resolvedIp);
                if (discovered == null) {
                    LogManager.APP_LOGGER.warn(LOG_TAG,
                            "Empty TXT records: " + serviceInfo.getServiceName());
                    return;
                }
                LogManager.APP_LOGGER.info(LOG_TAG,
                        "mDNS resolved: " + serviceInfo.getServiceName());
                found.incrementAndGet();
                if (onFound != null) onFound.accept(discovered);
            }
        };
    }

}