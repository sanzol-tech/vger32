package ar.vger32app.network.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.module.ModuleDiscovered;

/*
 * Sends a single UDP broadcast ("vger32:discover") to port 4210 and collects
 * responses for RECEIVE_TIMEOUT_MS milliseconds. Each responding device sends
 * back its system-identity payload (same key=value format as get_identity()).
 *
 * Runs entirely on a background thread — Callback methods are called from
 * that thread. Callers must post to the UI thread if needed.
 */

public class UdpDiscoverer {

    private static final String LOG_TAG = "UdpDiscoverer";
    private static final int RECEIVE_TIMEOUT_MS = 2000;
    private static final int RESPONSE_BUF_SIZE = 512;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private volatile Consumer<ModuleDiscovered> onFound = null;
    private volatile IntConsumer onFinished = null;

    // --------------------------------------------------------
    // --- DISCOVERY -----------------------------------------

    public synchronized void start(Consumer<ModuleDiscovered> onFound, IntConsumer onFinished) {
        if (running) return;
        this.onFound = onFound;
        this.onFinished = onFinished;
        this.running = true;
        executor.execute(this::run);
    }

    public synchronized void stop() {
        running = false;
        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private void run() {
        int found = 0;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(RECEIVE_TIMEOUT_MS);

            byte[] magic = SettingsManager.getUdpDiscoveryMagic().getBytes(StandardCharsets.UTF_8);
            DatagramPacket send = new DatagramPacket(
                    magic, magic.length,
                    InetAddress.getByName("255.255.255.255"), SettingsManager.getUdpDiscoveryPort());
            socket.send(send);
            LogManager.APP_LOGGER.info(LOG_TAG, "UDP discovery started, broadcast → 255.255.255.255:" + SettingsManager.getUdpDiscoveryPort());

            byte[] buf = new byte[RESPONSE_BUF_SIZE];
            long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;

            while (running && System.currentTimeMillis() < deadline) {
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(recv);
                    String raw = new String(
                            recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                    ModuleDiscovered discovered = ModuleDiscovered.fromPayload(raw);
                    LogManager.APP_LOGGER.info(LOG_TAG,
                            "UDP response from " + recv.getAddress().getHostAddress());
                    if (discovered != null && onFound != null) {
                        found++;
                        onFound.accept(discovered);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }

        } catch (Exception e) {
            LogManager.APP_LOGGER.error(LOG_TAG, "run() failed: " + e.getMessage());
        } finally {
            running = false;
            LogManager.APP_LOGGER.info(LOG_TAG, "UDP discovery finished: " + found + " module(s)");
            if (onFinished != null) onFinished.accept(found);
        }
    }
}