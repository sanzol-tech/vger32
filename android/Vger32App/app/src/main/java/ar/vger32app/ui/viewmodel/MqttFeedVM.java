package ar.vger32app.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ar.vger32app.R;
import ar.vger32app.config.AppConfig;
import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.module.ModulesStore;
import ar.vger32app.network.mqtt.MqttManager;
import ar.vger32app.ui.mqtt.MqttFeedEvent;

/*
 * Activity-scoped. Manages the MQTT feed across tab switches and configuration
 * changes. Registers directly as MqttManager and ModuleRegistry listener so
 * the Fragment does not need to.
 *
 * Usage: new ViewModelProvider(requireActivity()).get(MqttFeedViewModel.class)
 */

public class MqttFeedVM extends AndroidViewModel
        implements MqttManager.OnMessageListener,
        ModulesStore.OnModuleUpdateListener {

    private final List<MqttFeedEvent> events = new ArrayList<>();
    private final MutableLiveData<MqttFeedEvent> latestEvent = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasSelectedModule = new MutableLiveData<>(false);

    private String selectedMid = null;
    private boolean sessionStartedPosted = false;

    public MqttFeedVM(@NonNull Application application) {
        super(application);
        ModulesStore.getInstance(application).addListener(this);
        MqttManager.getInstance().setMessageListener(this);
        selectedMid = ModulesStore.getInstance().getSelectedMid();
        hasSelectedModule.setValue(selectedMid != null);
    }

    // --------------------------------------------------------
    // --- EXPOSED STATE --------------------------------------

    public LiveData<MqttFeedEvent> getLatestEvent() {
        return latestEvent;
    }

    public LiveData<Boolean> hasSelectedModule() {
        return hasSelectedModule;
    }

    public List<MqttFeedEvent> getEvents() {
        return new ArrayList<>(events);
    }

    public String getSelectedMid() {
        return selectedMid;
    }

    public int size() {
        return events.size();
    }

    // --------------------------------------------------------
    // --- OnModuleUpdateListener -----------------------------

    @Override
    public void onModuleUpdated() {
        selectedMid = ModulesStore.getInstance().getSelectedMid();
        hasSelectedModule.postValue(selectedMid != null);
    }

    // --------------------------------------------------------
    // --- OnMessageListener ----------------------------------

    @Override
    public void onMessage(String topic, String message) {
        if (MqttManager.TOPIC_SYS.equals(topic)) {
            post(MqttFeedEvent.sys(message));
            return;
        }
        String sourceMid = midFromTopic(topic);
        if (topic.endsWith("/pong")) {
            processPong(message, sourceMid);
            return;
        }
        if (selectedMid == null) {
            post(MqttFeedEvent.mqtt(sourceMid, subtopicFrom(topic) + ": " + message));
        } else {
            String prefix = SettingsManager.getMqttTopicBase() + "/" + selectedMid + "/";
            if (topic.startsWith(prefix)) {
                post(MqttFeedEvent.mqtt(selectedMid, topic.substring(prefix.length()) + ": " + message));
            }
        }
    }

    // --------------------------------------------------------
    // --- COMMANDS -------------------------------------------

    public void ping() {
        MqttManager.getInstance().ping();
        post(MqttFeedEvent.publish(str(R.string.cmd_ping)));
    }

    public void sendCommand(String command, String payload) {
        if (selectedMid == null) {
            post(MqttFeedEvent.sys(str(R.string.mqtt_event_no_module_selected)));
            return;
        }
        MqttManager.getInstance().publish(selectedMid, command, payload);
        post(MqttFeedEvent.publish(command + (payload.isEmpty() ? "" : ": " + payload)));
    }

    public void sendMessage(String message) {
        if (message.isEmpty()) return;
        if (selectedMid == null) {
            MqttManager.getInstance().publish(SettingsManager.getMqttTopicBase() + "/msg", message);
            post(MqttFeedEvent.publish(str(R.string.mqtt_event_msg_broadcast, message)));
        } else {
            MqttManager.getInstance().publish(selectedMid, "msg", message);
            post(MqttFeedEvent.publish(str(R.string.mqtt_event_msg_to_module, selectedMid, message)));
        }
    }

    public void postSessionStarted() {
        if (sessionStartedPosted) return;
        sessionStartedPosted = true;
        post(MqttFeedEvent.sys(str(R.string.mqtt_event_session_started)));
    }

    public void clearFeed() {
        events.clear();
        sessionStartedPosted = false;
        postSessionStarted();
    }

    public String getFeedAsText() {
        if (events.isEmpty()) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (MqttFeedEvent e : events) {
            sb.append(sdf.format(new Date(e.getTimestamp())));
            sb.append("  [").append(e.getType().name().toLowerCase(Locale.ROOT)).append("]");
            if (e.getModuleId() != null) sb.append("  ").append(e.getModuleId());
            sb.append("  ").append(e.getText()).append('\n');
        }
        return sb.toString().trim();
    }

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    @Override
    protected void onCleared() {
        super.onCleared();
        MqttManager.getInstance().removeMessageListener();
        ModulesStore.getInstance().removeListener(this);
    }

    // --------------------------------------------------------
    // --- INTERNAL -------------------------------------------

    private void processPong(String message, String sourceMid) {
        String pongMid = "", chip = "", pid = "", ip = "";
        for (String line : message.split("\n")) {
            String[] parts = line.split("=", 2);
            if (parts.length < 2) continue;
            switch (parts[0].trim()) {
                case "mid":
                    pongMid = parts[1].trim();
                    break;
                case "chip":
                    chip = parts[1].trim();
                    break;
                case "pid":
                    pid = parts[1].trim();
                    break;
                case "ip":
                    ip = parts[1].trim();
                    break;
            }
        }
        if (selectedMid != null && !selectedMid.equals(pongMid)) return;
        String info = (chip.isEmpty() ? "" : chip + " · ")
                + (pid.isEmpty() ? "" : pid + " · ")
                + ip;
        post(MqttFeedEvent.pong(pongMid.isEmpty() ? sourceMid : pongMid, info));
    }

    public void post(MqttFeedEvent event) {
        events.add(event);
        if (events.size() > AppConfig.MQTT_FEED_MAX_EVENTS) events.remove(0);
        latestEvent.postValue(event);
    }

    private static String midFromTopic(String topic) {
        String[] parts = topic.split("/", 3);
        return parts.length >= 3 ? parts[1] : null;
    }

    private static String subtopicFrom(String topic) {
        String[] parts = topic.split("/", 3);
        return parts.length >= 3 ? parts[2] : (parts.length == 2 ? parts[1] : topic);
    }

    // --------------------------------------------------------

    private String str(int resId, Object... args) {
        return getApplication().getString(resId, args);
    }

    private String str(int resId) {
        return getApplication().getString(resId);
    }
}