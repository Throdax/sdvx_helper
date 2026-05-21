package com.sdvxhelper.network;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends messages (and optionally images) to a Discord webhook URL.
 *
 * <p>
 * Replaces the Python {@code DiscordWebhook} usage in {@code gen_summary.py}.
 * Uses the JDK {@code java.net.http.HttpClient} for multipart POST requests.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class DiscordWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final HttpService http;

    /**
     * Constructs a webhook client with a shared {@link HttpService}.
     */
    public DiscordWebhookClient() {
        this.http = new HttpService(TIMEOUT);
    }

    /**
     * Sends a text message to the specified webhook URL.
     *
     * @param webhookUrl
     *            Discord webhook URL
     * @param content
     *            message text (max 2000 characters)
     * @return {@code true} if the server accepted the request (HTTP 2xx)
     */
    public boolean sendMessage(String webhookUrl, String content) {
        String json = toJsonString(buildContentPayload(content));
        try {
            HttpResponse<Void> resp = http.postJson(URI.create(webhookUrl), json);
            boolean ok = HttpService.isSuccess(resp.statusCode());
            log.debug("Discord webhook JSON: HTTP {}", resp.statusCode());
            return ok;
        } catch (IOException e) {
            log.error("Discord webhook failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sends a text message with an embedded image via multipart form data.
     *
     * @param webhookUrl
     *            Discord webhook URL
     * @param content
     *            message text (max 2000 characters)
     * @param imageBytes
     *            raw image bytes (e.g. PNG)
     * @param filename
     *            filename to use in the attachment (e.g. {@code "summary.png"})
     * @return {@code true} if accepted
     * @throws IOException
     *             if the request fails
     */
    public boolean sendMessageWithImage(String webhookUrl, String content, byte[] imageBytes, String filename)
            throws IOException {
        HttpService.MultipartBody body = new HttpService.MultipartBody()
                .addField("payload_json", toJsonString(buildContentPayload(content)))
                .addFile("files[0]", filename, "image/png", imageBytes);

        HttpResponse<String> resp = http.postMultipart(URI.create(webhookUrl), body);
        boolean ok = HttpService.isSuccess(resp.statusCode());
        log.info("Discord webhook (with image): HTTP {}", resp.statusCode());
        return ok;
    }

    /**
     * Sends a text message with a generic file attachment via multipart form data.
     *
     * @param webhookUrl
     *            Discord webhook URL
     * @param content
     *            message text (max 2000 characters)
     * @param fileBytes
     *            raw file bytes
     * @param filename
     *            filename to use in the attachment (e.g. {@code "musiclist.xml"})
     * @param mimeType
     *            MIME type of the attachment (e.g. {@code "application/xml"})
     * @return {@code true} if accepted
     * @throws IOException
     *             if the request fails
     */
    public boolean sendMessageWithFile(String webhookUrl, String content, byte[] fileBytes, String filename,
            String mimeType) throws IOException {
        HttpService.MultipartBody body = new HttpService.MultipartBody()
                .addField("payload_json", toJsonString(buildContentPayload(content)))
                .addFile("files[0]", filename, mimeType, fileBytes);

        HttpResponse<String> resp = http.postMultipart(URI.create(webhookUrl), body);
        boolean ok = HttpService.isSuccess(resp.statusCode());
        log.info("Discord webhook (with file): HTTP {}", resp.statusCode());
        return ok;
    }

    /**
     * Sends a text message with multiple image attachments via multipart form data.
     *
     * <p>
     * Used by the OCR-reporter registration webhook to attach both {@code info.png}
     * (the title/info crop) and {@code difficulty.png} (the difficulty-band crop),
     * mirroring the two {@code webhook.add_file()} calls in Python
     * {@code ocr_reporter.py:302–306}.
     * </p>
     *
     * @param webhookUrl
     *            Discord webhook URL
     * @param content
     *            message text (max 2000 characters)
     * @param files
     *            ordered map of {@code filename → raw PNG bytes}; entries are
     *            attached as {@code files[0]}, {@code files[1]}, … in iteration
     *            order
     * @return {@code true} if accepted
     * @throws IOException
     *             if the request fails
     */
    public boolean sendMessageWithMultipleImages(String webhookUrl, String content, Map<String, byte[]> files)
            throws IOException {
        HttpService.MultipartBody body = new HttpService.MultipartBody().addField("payload_json",
                toJsonString(buildContentPayload(content)));
        int idx = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            body.addFile("files[" + idx + "]", entry.getKey(), "image/png", entry.getValue());
            idx++;
        }
        HttpResponse<String> resp = http.postMultipart(URI.create(webhookUrl), body);
        boolean ok = HttpService.isSuccess(resp.statusCode());
        log.info("Discord webhook ({} images): HTTP {}", files.size(), resp.statusCode());
        return ok;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the simple {@code {"content":"..."}} JSON object used by both
     * plain-text and multipart webhook requests.
     *
     * @param content
     *            the message text (may be {@code null})
     * @return the {@link JsonObject} payload
     */
    private static JsonObject buildContentPayload(String content) {
        return Json.createObjectBuilder().add("content", content != null ? content : "").build();
    }

    /**
     * Serialises a {@link JsonObject} to a compact JSON string.
     *
     * @param obj
     *            the object to serialise
     * @return the JSON string representation
     */
    private static String toJsonString(JsonObject obj) {
        StringWriter sw = new StringWriter();
        Json.createWriter(sw).writeObject(obj);
        return sw.toString();
    }
}