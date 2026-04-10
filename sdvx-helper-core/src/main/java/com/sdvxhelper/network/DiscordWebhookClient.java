package com.sdvxhelper.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Sends messages (and optionally images) to a Discord webhook URL.
 *
 * <p>Replaces the Python {@code DiscordWebhook} usage in {@code gen_summary.py}.
 * Uses the JDK {@code java.net.http.HttpClient} for multipart POST requests.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class DiscordWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient http;

    /**
     * Constructs a webhook client with a shared {@link HttpClient}.
     */
    public DiscordWebhookClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Sends a text message to the specified webhook URL.
     *
     * @param webhookUrl  Discord webhook URL
     * @param content     message text (max 2000 characters)
     * @return {@code true} if the server accepted the request (HTTP 2xx)
     */
    public boolean sendMessage(String webhookUrl, String content) {
        String json = "{\"content\": " + jsonStr(content) + "}";
        return postJson(webhookUrl, json);
    }

    /**
     * Sends a text message with an embedded image via multipart form data.
     *
     * @param webhookUrl Discord webhook URL
     * @param content    message text
     * @param imageBytes raw image bytes (e.g. PNG)
     * @param filename   filename to use in the attachment (e.g. {@code "summary.png"})
     * @return {@code true} if accepted
     * @throws IOException if the request fails
     */
    public boolean sendMessageWithImage(String webhookUrl, String content, byte[] imageBytes, String filename) throws IOException {
        String boundary = UUID.randomUUID().toString();
        String nl = "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append(nl);
        sb.append("Content-Disposition: form-data; name=\"payload_json\"").append(nl);
        sb.append("Content-Type: application/json").append(nl).append(nl);
        sb.append("{\"content\": ").append(jsonStr(content)).append("}").append(nl);
        sb.append("--").append(boundary).append(nl);
        sb.append("Content-Disposition: form-data; name=\"files[0]\"; filename=\"").append(filename).append("\"").append(nl);
        sb.append("Content-Type: image/png").append(nl).append(nl);

        byte[] prefix = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] suffix = (nl + "--" + boundary + "--" + nl).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + imageBytes.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(imageBytes, 0, body, prefix.length, imageBytes.length);
        System.arraycopy(suffix, 0, body, prefix.length + imageBytes.length, suffix.length);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            boolean ok = resp.statusCode() / 100 == 2;
            log.info("Discord webhook (with image): HTTP {}", resp.statusCode());
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Discord webhook interrupted", e);
        }
    }

    private boolean postJson(String url, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            boolean ok = resp.statusCode() / 100 == 2;
            log.debug("Discord webhook JSON: HTTP {}", resp.statusCode());
            return ok;
        } catch (Exception e) {
            log.error("Discord webhook failed: {}", e.getMessage());
            return false;
        }
    }

    /** Minimal JSON string escape. */
    private static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
