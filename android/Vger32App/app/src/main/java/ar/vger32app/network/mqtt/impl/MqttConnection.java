package ar.vger32app.network.mqtt.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import ar.vger32app.logger.LogManager;

/*
 * Blocking TCP transport layer for the MQTT broker connection.
 * Does not interpret frame contents — that is MqttProtocol's responsibility.
 * Forces IPv4 on connect: Android may resolve the broker host to an IPv6 address
 * that refuses connections on port 1883, causing ECONNREFUSED.
 */

public class MqttConnection {

    private static final String LOG_TAG = "MqttConnection";

    // Used for both the initial connect call and the pre-handshake SO_TIMEOUT.
    // After CONNACK, SO_TIMEOUT is reset to 20s to drive keepalive from the reader thread.
    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private Socket socket;
    private InputStream input;
    private OutputStream output;

    // --------------------------------------------------------
    // --- LIFECYCLE ------------------------------------------

    public void connect(String host, int port) throws IOException {
        InetAddress ipv4 = resolveIPv4(host);

        socket = new Socket();
        socket.setSoTimeout(CONNECT_TIMEOUT_MS);
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(ipv4, port), CONNECT_TIMEOUT_MS);

        input = socket.getInputStream();
        output = socket.getOutputStream();

        socket.setSoTimeout(40_000);

        LogManager.APP_LOGGER.info(LOG_TAG,
                "TCP connected to " + ipv4.getHostAddress() + ":" + port);
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        input = null;
        output = null;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    // --------------------------------------------------------
    // --- WRITE ----------------------------------------------

    public synchronized void writeFrame(byte[] frame) throws IOException {
        if (output == null) throw new IOException("Not connected");
        output.write(frame);
        output.flush();
    }

    // --------------------------------------------------------
    // --- READ -----------------------------------------------

    public byte[] readFrame() throws IOException {
        if (input == null) throw new IOException("Not connected");

        int headerByte = input.read();
        if (headerByte == -1) throw new IOException("Stream closed");

        int remaining = 0;
        int multiplier = 1;
        int vleBytes = 0;
        int[] vleRaw = new int[4];
        do {
            int vleByte = input.read();
            if (vleByte == -1) throw new IOException("Stream closed reading VLE");
            vleRaw[vleBytes] = vleByte;
            remaining += (vleByte & 0x7F) * multiplier;
            multiplier *= 128;
            vleBytes++;
        } while ((vleRaw[vleBytes - 1] & 0x80) != 0 && vleBytes < 4);

        byte[] payload = new byte[remaining];
        int totalRead = 0;
        while (totalRead < remaining) {
            int bytesRead = input.read(payload, totalRead, remaining - totalRead);
            if (bytesRead == -1) throw new IOException("Stream closed reading payload");
            totalRead += bytesRead;
        }

        byte[] frame = new byte[1 + vleBytes + remaining];
        frame[0] = (byte) headerByte;
        for (int i = 0; i < vleBytes; i++) frame[1 + i] = (byte) vleRaw[i];
        System.arraycopy(payload, 0, frame, 1 + vleBytes, remaining);

        return frame;
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private InetAddress resolveIPv4(String host) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress addr : addresses) {
            if (addr instanceof Inet4Address) {
                LogManager.APP_LOGGER.debug(LOG_TAG,
                        "Resolved " + host + " → " + addr.getHostAddress() + " (IPv4)");
                return addr;
            }
        }
        throw new IOException("No IPv4 address found for " + host);
    }
}