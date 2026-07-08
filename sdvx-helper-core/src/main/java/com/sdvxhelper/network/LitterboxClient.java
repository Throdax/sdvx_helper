package com.sdvxhelper.network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JacketUploadClient} implementation that uploads files to
 * <a href="https://litterbox.catbox.moe">litterbox.catbox.moe</a> for temporary
 * image hosting.
 *
 * <p>
 * All uploads use a fixed 1-hour TTL, which is sufficient for a single SDVX
 * session. The TTL is an implementation detail of this provider and is not
 * exposed through the {@link JacketUploadClient} interface.
 * </p>
 *
 * <p>
 * Used by the Discord Rich Presence feature to host jacket images. Replaces the
 * Python litterbox upload in {@code discord_presence.py}.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 * @see JacketUploadClient
 */
public class LitterboxClient implements JacketUploadClient {

    private static final Logger log = LoggerFactory.getLogger(LitterboxClient.class);
    private static final URI ENDPOINT = URI.create("https://litterbox.catbox.moe/resources/internals/api.php");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Expiry TTL applied to every upload. */
    private static final String TTL = "1h";

    private final HttpService http;

    /**
     * Constructs a Litterbox client with default settings.
     */
    public LitterboxClient() {
        this.http = new HttpService(TIMEOUT);
    }

    /**
     * Uploads raw image bytes to Litterbox with a 1-hour TTL and returns the public
     * URL.
     *
     * <p>
     * The multipart POST sends {@code reqtype=fileupload} and {@code time=1h} to
     * the Litterbox API endpoint. A successful response body contains the full CDN
     * URL of the uploaded file.
     * </p>
     *
     * @param imageBytes
     *            raw image bytes (PNG, JPEG, etc.); must not be {@code null}
     * @param filename
     *            original filename including extension (e.g. {@code "jacket.png"})
     * @return the public CDN URL, or {@code null} if the server returned a non-2xx
     *         status
     * @throws IOException
     *             if the HTTP request cannot be sent or the response cannot be read
     */
    @Override
    public String upload(byte[] imageBytes, String filename) throws IOException {
        log.info("Litterbox upload: sending '{}' ({} bytes, ttl={}) to {}", filename, imageBytes.length, TTL, ENDPOINT);
        HttpService.MultipartBody body = new HttpService.MultipartBody().addField("reqtype", "fileupload")
                .addField("time", TTL).addFile("fileToUpload", filename, "application/octet-stream", imageBytes);

        HttpResponse<String> resp = http.postMultipart(ENDPOINT, body);
        if (HttpService.isSuccess(resp.statusCode())) {
            String url = resp.body().trim();
            log.info("Litterbox upload succeeded: {} -> {}", filename, url);
            return url;
        }
        String errorSummary = resp.body().trim().replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
        if (errorSummary.length() > 120) {
            errorSummary = errorSummary.substring(0, 120) + "…";
        }
        log.warn("Litterbox upload failed: HTTP {} - {}", resp.statusCode(), errorSummary);
        return null;
    }
}
