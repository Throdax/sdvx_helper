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
 * Uploads files to <a href="https://litterbox.catbox.moe">litterbox.catbox.moe</a>
 * for temporary image hosting.
 *
 * <p>Used by the Discord Rich Presence feature to host jacket images.
 * Replaces the Python litterbox upload in {@code discord_presence.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class LitterboxClient {

    private static final Logger log = LoggerFactory.getLogger(LitterboxClient.class);
    private static final String ENDPOINT = "https://litterbox.catbox.moe/resources/internals/api.php";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Default expiry time for uploaded files. */
    public static final String EXPIRY_1H = "1h";

    private final HttpClient http;

    /**
     * Constructs a Litterbox client.
     */
    public LitterboxClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Uploads a file to Litterbox and returns its public URL.
     *
     * @param fileBytes  raw file bytes (PNG, JPEG, etc.)
     * @param filename   original filename including extension
     * @param timeToLive expiry duration string (e.g. {@code "1h"}, {@code "12h"}, {@code "24h"}, {@code "72h"})
     * @return public URL of the uploaded file, or {@code null} on failure
     * @throws IOException if the upload request fails
     */
    public String upload(byte[] fileBytes, String filename, String timeToLive) throws IOException {
        String boundary = UUID.randomUUID().toString();
        String nl = "\r\n";

        StringBuilder head = new StringBuilder();
        // reqtype field
        head.append("--").append(boundary).append(nl);
        head.append("Content-Disposition: form-data; name=\"reqtype\"").append(nl).append(nl);
        head.append("fileupload").append(nl);
        // time field
        head.append("--").append(boundary).append(nl);
        head.append("Content-Disposition: form-data; name=\"time\"").append(nl).append(nl);
        head.append(timeToLive).append(nl);
        // fileToUpload field
        head.append("--").append(boundary).append(nl);
        head.append("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"").append(filename).append("\"").append(nl);
        head.append("Content-Type: application/octet-stream").append(nl).append(nl);

        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = (nl + "--" + boundary + "--" + nl).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headBytes.length + fileBytes.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(fileBytes, 0, body, headBytes.length, fileBytes.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + fileBytes.length, tailBytes.length);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String url = resp.body().trim();
                log.info("Uploaded to Litterbox: {}", url);
                return url;
            }
            log.warn("Litterbox upload failed: HTTP {}", resp.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Litterbox upload interrupted", e);
        }
    }
}
