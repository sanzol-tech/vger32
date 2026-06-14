package ar.vger32app.localizer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;

/*
 * Singleton. Local persistence of Waypoints in SharedPreferences as JSON.
 * Each waypoint is an independent JSON object; the full list is serialized
 * as a JSONArray under PREFS_KEY.
 *
 * Responsibilities:
 *   - CRUD: save, getAll, delete(id), deleteAll
 *   - Export selected waypoints to firmware format (via FingerprintFormat)
 *
 * Not using Room to avoid extra dependency — data volume is small
 * (max ~32 waypoints × 20 networks). Migrate to Room if it grows.
 */

public class WaypointStore {

    private static final String LOG_TAG = "WaypointStore";
    private static final String PREFS_NAME = "waypoint_store";
    private static final String PREFS_KEY = "waypoints";

    private static volatile WaypointStore instance;

    private final SharedPreferences prefs;

    private WaypointStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static WaypointStore getInstance(Context context) {
        if (instance == null) {
            synchronized (WaypointStore.class) {
                if (instance == null) instance = new WaypointStore(context);
            }
        }
        return instance;
    }

    // --------------------------------------------------------
    // --- CRUD -----------------------------------------------

    public void save(String name, long timestamp, List<WifiNetwork> networks, String source) {
        int limit = SettingsManager.getFingerprintMaxNetworks();
        List<WifiNetwork> capped = networks.subList(0, Math.min(networks.size(), limit));

        Waypoint wp = new Waypoint(UUID.randomUUID().toString(), name, timestamp, capped, source);

        try {
            JSONArray all = loadJson();
            all.put(toJson(wp));
            prefs.edit().putString(PREFS_KEY, all.toString()).apply();
            LogManager.APP_LOGGER.info(LOG_TAG,
                    "Saved waypoint " + name + " (" + capped.size() + " networks)");
        } catch (JSONException e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "save failed: " + e.getMessage());
        }
    }

    public List<Waypoint> getAll() {
        List<Waypoint> result = new ArrayList<>();
        try {
            JSONArray all = loadJson();
            for (int i = all.length() - 1; i >= 0; i--) {
                result.add(fromJson(all.getJSONObject(i)));
            }
        } catch (JSONException e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "getAll failed: " + e.getMessage());
        }
        return result;
    }

    public void delete(String id) {
        try {
            JSONArray all = loadJson();
            JSONArray filtered = new JSONArray();
            for (int i = 0; i < all.length(); i++) {
                JSONObject obj = all.getJSONObject(i);
                if (!id.equals(obj.getString("id"))) filtered.put(obj);
            }
            prefs.edit().putString(PREFS_KEY, filtered.toString()).apply();
        } catch (JSONException e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "delete failed: " + e.getMessage());
        }
    }

    public void deleteAll() {
        prefs.edit().remove(PREFS_KEY).apply();
        LogManager.APP_LOGGER.info(LOG_TAG, "All waypoints deleted");
    }

    // --------------------------------------------------------
    // --- EXPORT ---------------------------------------------

    public String exportToFirmwareFormat(List<String> ids) {
        List<Waypoint> all = getAll();
        List<Waypoint> selected = new ArrayList<>();
        for (String id : ids) {
            for (Waypoint wp : all) {
                if (wp.getId().equals(id)) {
                    selected.add(wp);
                    break;
                }
            }
        }
        return FingerprintFormat.serialize(selected);
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private JSONArray loadJson() throws JSONException {
        String raw = prefs.getString(PREFS_KEY, null);
        return raw != null ? new JSONArray(raw) : new JSONArray();
    }

    private JSONObject toJson(Waypoint waypoint) throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray networksArray = new JSONArray();

        for (WifiNetwork network : waypoint.getNetworks()) {
            JSONObject networkJson = new JSONObject();
            networkJson.put("mac", network.getMac());
            networkJson.put("channel", network.getChannel());
            networkJson.put("rssi", network.getRssi());
            networksArray.put(networkJson);
        }

        json.put("id", waypoint.getId());
        json.put("name", waypoint.getName());
        json.put("timestamp", waypoint.getTimestamp());
        json.put("source", waypoint.getSource());
        json.put("networks", networksArray);

        return json;
    }

    private Waypoint fromJson(JSONObject json) throws JSONException {
        String id = json.getString("id");
        String name = json.getString("name");
        long timestamp = json.getLong("timestamp");
        String source = json.optString("source", "");

        JSONArray networksArray = json.getJSONArray("networks");
        List<WifiNetwork> networks = new ArrayList<>();

        for (int i = 0; i < networksArray.length(); i++) {
            JSONObject networkJson = networksArray.getJSONObject(i);
            networks.add(new WifiNetwork(
                    networkJson.getString("mac"),
                    networkJson.getInt("channel"),
                    networkJson.getInt("rssi")));
        }

        return new Waypoint(id, name, timestamp, networks, source);
    }
}