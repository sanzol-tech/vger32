package ar.vger32app.network.mqtt;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.ModuleDiscovered;
import ar.vger32app.network.mqtt.impl.MqttConnection;
import ar.vger32app.network.mqtt.impl.MqttProtocol;
import ar.vger32app.scrambler.Scrambler;

/*
 * Singleton. Manages the full MQTT session: connect, subscribe, publish,
 * keepalive, and automatic reconnection.
 * Subscribes to "vger32/#" — receives all traffic under the vger32 namespace.
 * Inbound messages are dispatched: pong → pongListener (Consumer<ModuleDiscovered>), everything → messageListener.
 * Thread model:
 * mqttExecutor    — single thread for connect/reconnect loop
 * publishExecutor — single thread for outbound writes
 * readerThread    — blocking frame reader
 * mainHandler     — dispatches callbacks to the main (UI) thread
 * Broker host and port are read from SettingsManager on each connection attempt,
 * so a change in Preferences takes effect on the next reconnect.
 */

public class MqttManager {

    private static final String LOG_TAG = "MqttManager";
    private static final String CLIENT_ID_PREFIX = "vger32app_";
    private static final int KEEPALIVE_SEC = 30;
    private static final int RECONNECT_DELAY_MS = 5_000;

    // ISO_8859_1 maps each byte 0x00-0xFF to a char losslessly —
    // required to preserve scrambled bytes through MqttProtocol's String API.
    private static final Charset ISO = StandardCharsets.ISO_8859_1;

    // Synthetic topic for protocol events (PINGREQ, PINGRESP) surfaced to the UI.
    public static final String TOPIC_SYS = "__sys__";

    private static volatile MqttManager instance;

    private final Context appContext;
    private final MqttConnection connection = new MqttConnection();
    private ExecutorService mqttExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private Thread readerThread;
    private int packetIdCounter = 1;

    // --------------------------------------------------------
    // --- LISTENERS ------------------------------------------

    public interface OnConnectionListener {
        void onConnected();

        void onDisconnected();
    }

    public interface OnMessageListener {
        void onMessage(String topic, String message);
    }

    private volatile OnConnectionListener connectionListener;
    private volatile OnMessageListener messageListener;

    public void setConnectionListener(OnConnectionListener l) {
        this.connectionListener = l;
    }

    public void setMessageListener(OnMessageListener l) {
        this.messageListener = l;
    }

    public void removeMessageListener() {
        this.messageListener = null;
    }

    private volatile Consumer<ModuleDiscovered> pongListener;

    public void setPongListener(Consumer<ModuleDiscovered> l) {
        this.pongListener = l;
    }

    // --------------------------------------------------------
    // --- SINGLETON ------------------------------------------

    private MqttManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // Primera llamada — desde MyApplication.onCreate()
    public static MqttManager getInstance(Context context) {
        if (instance == null) {
            synchronized (MqttManager.class) {
                if (instance == null) instance = new MqttManager(context);
            }
        }
        return instance;
    }

    // Llamadas posteriores — desde cualquier otro punto
    public static MqttManager getInstance() {
        if (instance == null)
            throw new IllegalStateException(
                    "MqttManager not initialized — call getInstance(context) first");
        return instance;
    }

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    public void start() {
        if (running.getAndSet(true)) return;
        if (mqttExecutor.isShutdown()) mqttExecutor = Executors.newSingleThreadExecutor();
        if (publishExecutor.isShutdown()) publishExecutor = Executors.newSingleThreadExecutor();
        mqttExecutor.execute(this::connectionLoop);
    }

    public void connect() {
        start();
    }

    public void stop() {
        running.set(false);
        sendDisconnect();
        connection.disconnect();
        if (readerThread != null) readerThread.interrupt();
        mqttExecutor.shutdownNow();
        publishExecutor.shutdownNow();
    }

    public void disconnect() {
        stop();
    }

    public boolean isConnected() {
        return connected.get();
    }

    // --------------------------------------------------------
    // --- PUBLISH --------------------------------------------

    public void ping() {
        String topicPing = SettingsManager.getMqttTopicBase() + "/ping";
        publish(topicPing, "");
    }

    public void publish(String mid, String command, String payload) {
        String topic = SettingsManager.getMqttTopicBase() + "/" + mid + "/cmd/" + command;
        publish(topic, payload);
    }

    public void publish(String topic, String message) {
        if (!connected.get()) {
            LogManager.APP_LOGGER.warn(LOG_TAG, "Discarded (not connected): " + topic);
            return;
        }
        publishExecutor.execute(() -> {
            try {
                byte[] frame = MqttProtocol.buildPublish(topic, prepareOutbound(message));
                connection.writeFrame(frame);
                LogManager.APP_LOGGER.debug(LOG_TAG, "→ " + topic + " [" + message + "]");
            } catch (IOException e) {
                LogManager.APP_LOGGER.error(LOG_TAG, "publish failed: " + e.getMessage());
            }
        });
    }

    // --------------------------------------------------------
    // --- PRIVATE — CONNECTION -------------------------------

    private void connectionLoop() {
        while (running.get()) {
            if (!isNetworkAvailable()) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ignored) {
                }
                continue;
            }

            try {
                doConnect();
                startReaderThread();
                readerThread.join();
            } catch (Exception e) {
                LogManager.APP_LOGGER.warn(LOG_TAG, "Connection error: " + e.getMessage());
            }

            if (!running.get()) break;

            connected.set(false);
            connection.disconnect();
            notifyDisconnected();

            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void doConnect() throws IOException {
        String clientId = CLIENT_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        String host = SettingsManager.getMqttBrokerHost();
        int port = SettingsManager.getMqttBrokerPort();

        connection.connect(host, port);
        connection.writeFrame(MqttProtocol.buildConnect(clientId, KEEPALIVE_SEC));

        byte[] connack = connection.readFrame();
        int returnCode = MqttProtocol.parseConnAck(connack);
        if (returnCode != MqttProtocol.CONNACK_ACCEPTED) {
            throw new IOException("CONNACK refused: code " + returnCode);
        }

        String topicSubscribe = SettingsManager.getMqttTopicBase() + "/#";
        connection.writeFrame(MqttProtocol.buildSubscribe(nextPacketId(), topicSubscribe));

        connected.set(true);
        notifyConnected();

        LogManager.APP_LOGGER.info(LOG_TAG,
                "MQTT connected as " + clientId + " to " + host + ":" + port);
    }

    private void startReaderThread() {
        readerThread = new Thread(() -> {
            while (running.get() && connection.isConnected()) {
                try {
                    byte[] frame = connection.readFrame();
                    handleFrame(frame);
                } catch (SocketTimeoutException e) {
                    try {
                        connection.writeFrame(MqttProtocol.buildPingReq());
                        LogManager.APP_LOGGER.debug(LOG_TAG, "PINGREQ");
                        notifySys("pingreq");
                    } catch (IOException ex) {
                        LogManager.APP_LOGGER.warn(LOG_TAG, "PINGREQ failed: " + ex.getMessage());
                        break;
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        LogManager.APP_LOGGER.warn(LOG_TAG, "Reader error: " + e.getMessage());
                    }
                    break;
                }
            }
        }, "mqtt-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void handleFrame(byte[] frame) {
        int type = MqttProtocol.parseType(frame[0] & 0xFF);
        switch (type) {
            case MqttProtocol.TYPE_PUBLISH:
                handlePublish(frame);
                break;
            case MqttProtocol.TYPE_PINGRESP:
                LogManager.APP_LOGGER.debug(LOG_TAG, "PINGRESP");
                notifySys("pingresp");
                break;
            case MqttProtocol.TYPE_SUBACK:
                LogManager.APP_LOGGER.debug(LOG_TAG, "SUBACK");
                break;
            default:
                LogManager.APP_LOGGER.debug(LOG_TAG, "Unknown frame type: 0x"
                        + Integer.toHexString(frame[0] & 0xFF));
        }
    }

    private void handlePublish(byte[] frame) {
        String[] parsed = MqttProtocol.parsePublish(frame);
        if (parsed == null) return;

        String topic = parsed[0];
        String message = prepareInbound(parsed[1]);

        LogManager.APP_LOGGER.debug(LOG_TAG, "← " + topic + " [" + message + "]");

        dispatch(topic, message);
    }

    // --------------------------------------------------------
    // --- PRIVATE — DISPATCH ---------------------------------

    private void dispatch(String topic, String message) {
        if (topic.endsWith("/pong")) {
            Consumer<ModuleDiscovered> pl = pongListener;
            if (pl != null) {
                ModuleDiscovered discovered = ModuleDiscovered.fromPayload(message);
                if (discovered != null) pl.accept(discovered);
            }
        }

        // Capture listener locally to avoid NPE if removeMessageListener() races.
        OnMessageListener listener = messageListener;
        if (listener != null) {
            mainHandler.post(() -> listener.onMessage(topic, message));
        }
    }

    private void notifySys(String event) {
        OnMessageListener listener = messageListener;
        if (listener != null) {
            mainHandler.post(() -> listener.onMessage(TOPIC_SYS, event));
        }
    }

    // --------------------------------------------------------
    // --- PRIVATE — SCRAMBLER --------------------------------

    private String prepareOutbound(String message) {
        if (!SettingsManager.isMqttScrambled()) return message;
        String key = SettingsManager.getDefaultScramblerKey();
        if (key == null || key.isEmpty()) return message;
        byte[] encoded = Scrambler.encode(message.getBytes(StandardCharsets.UTF_8), key);
        return new String(encoded, ISO);
    }

    private String prepareInbound(String raw) {
        if (!SettingsManager.isMqttScrambled()) return raw;
        String key = SettingsManager.getDefaultScramblerKey();
        if (key == null || key.isEmpty()) return raw;
        byte[] decoded = Scrambler.decode(raw.getBytes(ISO), key);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    // --------------------------------------------------------
    // --- PRIVATE — HELPERS ----------------------------------

    private void sendDisconnect() {
        try {
            if (connection.isConnected()) connection.writeFrame(MqttProtocol.buildDisconnect());
        } catch (IOException ignored) {
        }
    }

    private synchronized int nextPacketId() {
        if (packetIdCounter > 65535) packetIdCounter = 1;
        return packetIdCounter++;
    }

    private void notifyConnected() {
        OnConnectionListener l = connectionListener;
        if (l != null) mainHandler.post(() -> l.onConnected());
    }

    private void notifyDisconnected() {
        OnConnectionListener l = connectionListener;
        if (l != null) mainHandler.post(() -> l.onDisconnected());
    }
}