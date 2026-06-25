package com.sdvxhelper.network;

import java.io.IOException;

/**
 * Strategy interface for temporary jacket image hosting used by Discord Rich
 * Presence.
 *
 * <p>
 * Different hosting services have different expiry mechanisms (explicit TTL
 * parameters, access-based automatic purge, etc.). Hiding those details behind
 * this interface lets
 * {@link com.sdvxhelper.app.controller.detection.DetectionEngine} remain
 * decoupled from any specific provider. Implementations are responsible for
 * choosing and applying the appropriate expiry policy internally.
 * </p>
 *
 * <p>
 * Implementations must be thread-safe: {@code upload} may be called from the
 * detection-loop background thread at any time.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 * @see LitterboxClient
 */
public interface JacketUploadClient {

    /**
     * Uploads raw image bytes to a temporary hosting service and returns the public
     * URL under which the image can be accessed.
     *
     * <p>
     * The returned URL must be directly reachable by Discord's image proxy so that
     * it can be displayed as the Rich Presence large image. The URL should remain
     * valid for at least the duration of the active game session.
     * </p>
     *
     * @param imageBytes
     *            raw image bytes (PNG, JPEG, etc.); must not be {@code null}
     * @param filename
     *            original filename including extension (e.g. {@code "jacket.png"});
     *            used by the hosting service to infer the MIME type
     * @return the public URL of the uploaded image, or {@code null} if the upload
     *         failed
     * @throws IOException
     *             if the upload request cannot be sent or the response cannot be
     *             read
     */
    String upload(byte[] imageBytes, String filename) throws IOException;
}
