package com.sdvxhelper.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;

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
    private static final URI    ENDPOINT = URI.create("https://litterbox.catbox.moe/resources/internals/api.php");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Default expiry time for uploaded files. */
    public static final String EXPIRY_1H = "1h";

    private final HttpService http;

    /**
     * Constructs a Litterbox client.
     */
    public LitterboxClient() {
        this.http = new HttpService(TIMEOUT);
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
        HttpService.MultipartBody body = new HttpService.MultipartBody()
                .addField("reqtype", "fileupload")
                .addField("time", timeToLive)
                .addFile("fileToUpload", filename, "application/octet-stream", fileBytes);

        HttpResponse<String> resp = http.postMultipart(ENDPOINT, body);
        if (HttpService.isSuccess(resp.statusCode())) {
            String url = resp.body().trim();
            log.info("Uploaded to Litterbox: {}", url);
            return url;
        }
        log.warn("Litterbox upload failed: HTTP {}", resp.statusCode());
        return null;
    }
}