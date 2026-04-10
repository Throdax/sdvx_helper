package com.sdvxhelper.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Downloads CSV files from a Google Drive shared link.
 *
 * <p>Google Drive redirects through a confirmation page for large files.
 * This client follows the redirect chain and handles the confirmation token
 * automatically, matching the behaviour of the Python implementation in
 * {@code sdvxh_classes.py} (the {@code dl_googledrive_csv()} method).</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class GoogleDriveClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String CONFIRM_PATTERN = "confirm=";

    private final HttpClient http;

    /**
     * Constructs a Google Drive client with cookie support for the confirmation flow.
     */
    public GoogleDriveClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager())
                .build();
    }

    /**
     * Downloads a CSV file from a Google Drive file ID.
     *
     * @param fileId Google Drive file ID (from the share URL)
     * @return CSV content as a string, or {@code null} on failure
     * @throws IOException if the download fails
     */
    public String downloadCsv(String fileId) throws IOException {
        String url = "https://drive.google.com/uc?export=download&id=" + fileId;
        return fetch(url);
    }

    /**
     * Downloads content from the given URL, handling the Google Drive
     * virus-scan confirmation redirect.
     *
     * @param url initial download URL
     * @return downloaded content as a string, or {@code null} on failure
     * @throws IOException if the request fails
     */
    private String fetch(String url) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // Check if we need to follow the confirmation URL
            if (resp.body().contains(CONFIRM_PATTERN)) {
                String confirmUrl = extractConfirmUrl(resp.body(), url);
                if (confirmUrl != null) {
                    log.debug("Following Google Drive confirmation URL");
                    return fetch(confirmUrl);
                }
            }

            if (resp.statusCode() == 200) {
                log.info("Downloaded {} bytes from Google Drive", resp.body().length());
                return resp.body();
            }
            log.warn("Google Drive download failed: HTTP {}", resp.statusCode());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Drive download interrupted", e);
        }
    }

    private static String extractConfirmUrl(String body, String originalUrl) {
        // Look for the confirmation URL in the page body
        int idx = body.indexOf("confirm=");
        if (idx == -1) return null;
        // Extract the confirm token value
        int end = body.indexOf("&", idx);
        if (end == -1) end = body.indexOf("\"", idx);
        if (end == -1) return null;
        String confirmParam = body.substring(idx, end);
        return originalUrl + "&" + confirmParam;
    }
}
