package ar.vger32app.module;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.config.safe.SecurePreferencesManager;
import ar.vger32app.logger.LogManager;

/*
 * Singleton. Source of truth for all known vger32 modules.
 *
 * Memory  — ConcurrentHashMap, fast access during the session.
 * Disk    — modules.dat in the app's internal files directory.
 * Format: mid|ip|profileId|chip|board|version|lastSeenAt|lastDiscoverySource
 *
 * Init: call getInstance(context) once from Application.onCreate() or
 * MainActivity.onCreate() before any other use.
 *
 * Thread safety: ConcurrentHashMap for the in-memory map; disk writes are
 * synchronised. Listeners are notified from whatever thread triggers the
 * change — consumers must post to the UI thread if needed.
 */

public class ModulesStore {

    private static final String LOG_TAG = "ModulesStore";
    private static final String FILENAME = "modules.dat";

    private static volatile ModulesStore instance;

    private final Context appContext;
    private final Map<String, Module> modules = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<OnModuleUpdateListener> listeners = new CopyOnWriteArrayList<>();
    private volatile String selectedMid;
    private final java.util.concurrent.atomic.AtomicLong clearSeq = new java.util.concurrent.atomic.AtomicLong(0);

    // --------------------------------------------------------
    // --- LISTENER -------------------------------------------

    public interface OnModuleUpdateListener {
        void onModuleUpdated();
    }

    public void addListener(OnModuleUpdateListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(OnModuleUpdateListener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        for (OnModuleUpdateListener l : listeners) l.onModuleUpdated();
    }

    // --------------------------------------------------------
    // --- SINGLETON ------------------------------------------

    private ModulesStore(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static ModulesStore getInstance(Context context) {
        if (instance == null) {
            synchronized (ModulesStore.class) {
                if (instance == null) instance = new ModulesStore(context);
            }
        }
        return instance;
    }

    public static ModulesStore getInstance() {
        if (instance == null) throw new IllegalStateException(
                "ModuleStore not initialized — call getInstance(context) first");
        return instance;
    }

    // --------------------------------------------------------
    // --- SELECTION ------------------------------------------

    public void setSelectedMid(String mid) {
        if (Objects.equals(this.selectedMid, mid)) return;
        this.selectedMid = mid;
        LogManager.APP_LOGGER.info(LOG_TAG, "Selected: " + mid);
        notifyListeners();
    }

    public String getSelectedMid() {
        return selectedMid;
    }

    public Module getSelectedModule() {
        return selectedMid != null ? modules.get(selectedMid) : null;
    }

    // --------------------------------------------------------
    // --- DISCOVERY ------------------------------------------

    public void register(ModuleDiscovered discovered, DiscoverySource source) {
        if (discovered == null) return;

        Module module = modules.get(discovered.moduleId);
        if (module == null) {
            module = new Module(discovered, source);
            modules.put(discovered.moduleId, module);
            LogManager.APP_LOGGER.info(LOG_TAG, "New module: " + discovered.moduleId + " via " + source);
        } else {
            module.update(discovered, source);
            LogManager.APP_LOGGER.debug(LOG_TAG, "Updated: " + discovered.moduleId);
        }

        persistAll();
        notifyListeners();
    }

    // --------------------------------------------------------
    // --- CRUD -----------------------------------------------

    public void save(Module module) {
        modules.put(module.getModuleId(), module);
        persistAll();
        LogManager.APP_LOGGER.debug(LOG_TAG, "Saved: " + module.getModuleId());
        notifyListeners();
    }

    private void removeCredentials(String mid) {
        SecurePreferencesManager spm = SecurePreferencesManager.getInstance(appContext);
        spm.removeApiKey(mid);
        spm.removeModuleScramblerKey(mid);
    }

    public void remove(String mid) {
        modules.remove(mid);
        if (Objects.equals(selectedMid, mid)) setSelectedMid(null);
        removeCredentials(mid);
        persistAll();
        LogManager.APP_LOGGER.info(LOG_TAG, "Removed: " + mid);
        notifyListeners();
    }

    public void clear() {
        modules.clear();
        selectedMid = null;
        clearSeq.incrementAndGet();
        persistAll();
        notifyListeners();
    }

    // --------------------------------------------------------
    // --- PURGE ----------------------------------------------

    public int purgeExpired() {
        List<String> toRemove = new ArrayList<>();
        for (Module module : modules.values()) {
            long purgeAgeMs = TimeUnit.DAYS.toMillis(SettingsManager.getModulePurgeDays());
            if (module.isExpired(purgeAgeMs)) toRemove.add(module.getModuleId());
        }
        for (String mid : toRemove) {
            modules.remove(mid);
            removeCredentials(mid);
            LogManager.APP_LOGGER.info(LOG_TAG, "Purged expired: " + mid);
        }
        if (!toRemove.isEmpty()) {
            if (toRemove.contains(selectedMid)) selectedMid = null;
            persistAll();
            notifyListeners();
        }
        return toRemove.size();
    }

    // --------------------------------------------------------
    // --- QUERIES --------------------------------------------

    public Module get(String mid) {
        return modules.get(mid);
    }

    public List<Module> getAll() {
        List<Module> list = new ArrayList<>(modules.values());
        list.sort((a, b) -> Long.compare(b.getLastSeenAt(), a.getLastSeenAt()));
        return Collections.unmodifiableList(list);
    }

    public List<Module> getOnline() {
        List<Module> online = new ArrayList<>();
        for (Module module : modules.values()) if (module.isOnline()) online.add(module);
        return Collections.unmodifiableList(online);
    }

    public List<Module> getOffline() {
        List<Module> offline = new ArrayList<>();
        for (Module module : modules.values()) if (!module.isOnline()) offline.add(module);
        return Collections.unmodifiableList(offline);
    }

    // --------------------------------------------------------
    // --- LOAD FROM DISK -------------------------------------

    public List<Module> loadAll() {
        List<Module> result = new ArrayList<>();
        File file = new File(appContext.getFilesDir(), FILENAME);
        if (!file.exists()) return result;

        long seq = clearSeq.get(); // capture before reading — detects concurrent clear()

        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(
                        new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Module module = ModulesSerializer.fromLine(line);
                if (module != null) result.add(module);
            }
            LogManager.APP_LOGGER.info(LOG_TAG, "Loaded " + result.size() + " module(s)");
        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "loadAll failed: " + e.getMessage());
        }

        // Only merge if clear() was not called while we were reading from disk
        if (!result.isEmpty() && clearSeq.get() == seq) {
            for (Module module : result) modules.putIfAbsent(module.getModuleId(), module);
            if (selectedMid == null && modules.size() == 1) {
                selectedMid = modules.keySet().iterator().next();
                LogManager.APP_LOGGER.info(LOG_TAG, "Auto-selected: " + selectedMid);
            }
            notifyListeners();
        }
        return result;
    }

    // --------------------------------------------------------
    // --- PERSIST --------------------------------------------

    private synchronized void persistAll() {
        try {
            File file = new File(appContext.getFilesDir(), FILENAME);
            try (PrintWriter writer = new PrintWriter(
                    new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (Module module : modules.values())
                    writer.println(ModulesSerializer.toLine(module));
            }
        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "persistAll failed: " + e.getMessage());
        }
    }
}