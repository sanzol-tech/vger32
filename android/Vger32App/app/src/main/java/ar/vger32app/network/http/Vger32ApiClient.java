package ar.vger32app.network.http;

import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import ar.vger32app.config.preferences.SettingsManager;
import ar.vger32app.logger.LogManager;
import ar.vger32app.scrambler.Scrambler;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/*
 * OkHttp client scoped to a single device (IP + API key).
 * All methods are blocking — call from a background thread.
 * Throws IOException on network errors or non-2xx responses.
 * SCRAMBLING
 *
 * When enabled:
 * Header:        X-Scramble: 1 added to every request.
 * POST/PUT body: Scrambler.encode(payload bytes) → Base64(NO_WRAP) → text/plain
 * GET response:  Base64.decode(NO_WRAP) → Scrambler.decode(bytes) → UTF-8 String
 * Scrambler key resolution (2-arg constructor):
 * SettingsManager.getDefaultScramblerKey() — global default from Preferences.
 * Scrambler key resolution (3-arg constructor):
 * Uses the supplied scramblerKeyOverride if non-null and non-empty,
 * otherwise falls back to the global default. Use this for per-module
 * scrambler keys stored in SecurePreferencesManager.
 * FINGERPRINTS EXCEPTION
 *
 * GET /api/wifi-fingerprints returns raw binary — never scrambled by the firmware.
 * Use getWifiFingerprints() → get() without the scramble flag.
 * NULL API KEY
 *
 * When apiKey is null or empty the X-API-Key header is omitted entirely,
 * allowing calls to unprotected devices (e.g. during LAN scan discovery).
 */

public class Vger32ApiClient {

    private static final String LOG_TAG = "Vger32ApiClient";
    private static final int CONNECT_TIMEOUT_SEC = 10;
    private static final int READ_TIMEOUT_SEC = 15;
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String SCRAMBLE_HEADER = "X-Scramble";
    private static final String SCRAMBLE_VALUE = "1";

    private static final MediaType MEDIA_TYPE_TEXT =
            MediaType.parse("text/plain; charset=utf-8");

    // Shared across all instances — OkHttpClient is thread-safe and designed
    // to be reused. A single instance avoids redundant connection pools and
    // thread pools being created per API call or per discovered module.
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    private final String baseUrl;
    private final String apiKey;
    private final boolean scramblerEnabled;
    private final String scramblerKey;

    // --------------------------------------------------------
    // --- CONSTRUCTORS ---------------------------------------

    public Vger32ApiClient(String ip, String apiKey) {
        this(ip, apiKey, null);
    }

    public Vger32ApiClient(String ip, String apiKey, String scramblerKeyOverride) {
        this.baseUrl = "http://" + ip;
        this.apiKey = apiKey;

        String key = (scramblerKeyOverride != null && !scramblerKeyOverride.isEmpty())
                ? scramblerKeyOverride
                : SettingsManager.getDefaultScramblerKey();

        this.scramblerEnabled = SettingsManager.isHttpScramblerEnabled()
                && key != null && !key.isEmpty();
        this.scramblerKey = scramblerEnabled ? key : "";

        if (scramblerEnabled)
            LogManager.APP_LOGGER.debug(LOG_TAG, "Scrambler enabled for " + ip);
    }

    // --------------------------------------------------------
    // --- PUBLIC HTTP METHODS --------------------------------

    public String get(String endpoint) throws IOException {
        return get(endpoint, scramblerEnabled);
    }

    public String getUnscrambled(String endpoint) throws IOException {
        return get(endpoint, false);
    }

    public String post(String endpoint, String payload) throws IOException {
        RequestBody body = buildBody(payload);
        Request.Builder rb = new Request.Builder().url(baseUrl + endpoint).post(body);
        addApiKeyHeader(rb);
        if (scramblerEnabled) rb.header(SCRAMBLE_HEADER, SCRAMBLE_VALUE);
        try (Response response = HTTP_CLIENT.newCall(rb.build()).execute()) {
            assertSuccess(response, endpoint);
            return readBody(response);
        }
    }

    public String put(String endpoint, String payload) throws IOException {
        RequestBody body = buildBody(payload);
        Request.Builder rb = new Request.Builder().url(baseUrl + endpoint).put(body);
        addApiKeyHeader(rb);
        if (scramblerEnabled) rb.header(SCRAMBLE_HEADER, SCRAMBLE_VALUE);
        try (Response response = HTTP_CLIENT.newCall(rb.build()).execute()) {
            assertSuccess(response, endpoint);
            return readBody(response);
        }
    }

    public String delete(String endpoint) throws IOException {
        Request.Builder rb = new Request.Builder().url(baseUrl + endpoint).delete();
        addApiKeyHeader(rb);
        if (scramblerEnabled) rb.header(SCRAMBLE_HEADER, SCRAMBLE_VALUE);
        try (Response response = HTTP_CLIENT.newCall(rb.build()).execute()) {
            assertSuccess(response, endpoint);
            return readBody(response);
        }
    }

    // --------------------------------------------------------
    // --- ACCESSORS ------------------------------------------

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean isScramblerEnabled() {
        return scramblerEnabled;
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private String get(String endpoint, boolean scramble) throws IOException {
        Request.Builder rb = new Request.Builder().url(baseUrl + endpoint).get();
        addApiKeyHeader(rb);
        if (scramble) rb.header(SCRAMBLE_HEADER, SCRAMBLE_VALUE);
        try (Response response = HTTP_CLIENT.newCall(rb.build()).execute()) {
            assertSuccess(response, endpoint);
            return readBody(response, scramble);
        }
    }

    private void addApiKeyHeader(Request.Builder rb) {
        if (apiKey != null && !apiKey.isEmpty()) rb.header(API_KEY_HEADER, apiKey);
    }

    private RequestBody buildBody(String payload) {
        if (!scramblerEnabled || payload == null || payload.isEmpty())
            return RequestBody.create(payload != null ? payload : "", MEDIA_TYPE_TEXT);
        byte[] encoded = Scrambler.encode(payload.getBytes(StandardCharsets.UTF_8), scramblerKey);
        return RequestBody.create(Base64.encodeToString(encoded, Base64.NO_WRAP), MEDIA_TYPE_TEXT);
    }

    private String readBody(Response response) throws IOException {
        return readBody(response, scramblerEnabled);
    }

    private String readBody(Response response, boolean scramble) throws IOException {
        ResponseBody body = response.body();
        if (body == null) return "";
        if (!scramble) return body.string();
        String b64 = body.string();
        if (b64.isEmpty()) return "";
        byte[] raw = Base64.decode(b64, Base64.NO_WRAP);
        byte[] decoded = Scrambler.decode(raw, scramblerKey);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static void assertSuccess(Response response, String endpoint) throws IOException {
        if (!response.isSuccessful())
            throw new IOException("HTTP " + response.code() + " on " + endpoint);
    }
}