package com.sdvxhelper.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * HTTP client for the Maya2 score-upload server.
 *
 * <p>Provides health-check, music-list download, and score-upload operations.
 * Upload requests are authenticated with an HMAC-SHA256 signature appended as
 * the final CSV row, matching the Python implementation in
 * {@code ManageMaya2} / {@code connect_maya2.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class Maya2Client {

    private static final Logger log = LoggerFactory.getLogger(Maya2Client.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final String hmacKey;
    private final HttpClient http;

    /**
     * Constructs a Maya2 client.
     *
     * @param baseUrl base URL of the Maya2 server (e.g. {@code "http://192.168.1.100:8080"})
     * @param hmacKey HMAC key for signing upload payloads (from {@code SecretConfig})
     */
    public Maya2Client(String baseUrl, String hmacKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.hmacKey = hmacKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Checks whether the Maya2 server is reachable.
     *
     * @return {@code true} if the server responds with HTTP 200
     */
    public boolean isAlive() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            boolean alive = resp.statusCode() == 200;
            log.debug("Maya2 health check: {}", alive ? "alive" : "unreachable (status=" + resp.statusCode() + ")");
            return alive;
        } catch (Exception e) {
            log.debug("Maya2 not reachable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Downloads the music list CSV from the Maya2 server.
     *
     * @param endpoint relative endpoint path (e.g. {@code "/musiclist"})
     * @return response body as a string, or {@code null} on failure
     */
    public String downloadMusicList(String endpoint) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                log.info("Downloaded music list from Maya2 ({} bytes)", resp.body().length());
                return resp.body();
            }
            log.warn("Maya2 music list download failed: HTTP {}", resp.statusCode());
            return null;
        } catch (Exception e) {
            log.error("Maya2 music list download error", e);
            return null;
        }
    }

    /**
     * Uploads a play-history CSV payload to the Maya2 server.
     *
     * <p>Appends an HMAC-SHA256 checksum row to the payload before sending.</p>
     *
     * @param endpoint    relative endpoint path (e.g. {@code "/upload"})
     * @param csvPayload  the CSV content to upload
     * @return {@code true} if the server accepted the upload (HTTP 200 or 201)
     * @throws IOException if the upload request fails
     */
    public boolean upload(String endpoint, String csvPayload) throws IOException {
        String signed = sign(csvPayload);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(signed, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = resp.statusCode() == 200 || resp.statusCode() == 201;
            log.info("Maya2 upload: HTTP {}", resp.statusCode());
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Upload interrupted", e);
        }
    }

    /**
     * Appends an HMAC-SHA256 signature row to the CSV payload.
     *
     * @param csvPayload raw CSV content
     * @return CSV content with appended checksum row
     * @throws IOException if HMAC computation fails
     */
    String sign(String csvPayload) throws IOException {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(csvPayload.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return csvPayload + "\n" + hex;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IOException("HMAC signing failed", e);
        }
    }
}
