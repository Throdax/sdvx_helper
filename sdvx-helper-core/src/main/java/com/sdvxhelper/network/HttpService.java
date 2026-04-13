package com.sdvxhelper.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around {@link HttpClient} that eliminates boilerplate HTTP
 * request construction, interrupt handling, and response checking that was
 * previously duplicated across every network client.
 *
 * <p>Provides three request types used by the application's network layer:</p>
 * <ul>
 *   <li>{@link #get(URI, Map)} — plain GET with optional headers</li>
 *   <li>{@link #postJson(URI, String)} — POST with a JSON body</li>
 *   <li>{@link #postMultipart(URI, MultipartBody)} — POST with a
 *       builder-assembled multipart body</li>
 * </ul>
 *
 * <p>All methods restore the thread interrupt flag when an
 * {@link InterruptedException} is caught and re-throw as {@link IOException}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class HttpService {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    private final HttpClient client;
    private final Duration   timeout;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Constructs an {@code HttpService} with the given request timeout.
     *
     * @param timeout the per-request (and connect) timeout
     */
    public HttpService(Duration timeout) {
        this.timeout = timeout;
        this.client  = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a GET request to the given URI with optional extra headers and
     * returns the response with a {@code String} body.
     *
     * @param uri     the target URI
     * @param headers map of additional request headers (may be empty)
     * @return the {@link HttpResponse} with a {@code String} body
     * @throws IOException if the request fails or the thread is interrupted
     */
    public HttpResponse<String> get(URI uri, Map<String, String> headers) throws IOException {
        return get(uri, headers, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Sends a GET request to the given URI with optional extra headers, using
     * the supplied body handler.
     *
     * @param <T>     the response body type
     * @param uri     the target URI
     * @param headers map of additional request headers (may be empty)
     * @param handler the response body handler
     * @return the {@link HttpResponse}
     * @throws IOException if the request fails or the thread is interrupted
     */
    public <T> HttpResponse<T> get(URI uri, Map<String, String> headers,
                                   HttpResponse.BodyHandler<T> handler) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .GET();
        headers.forEach(builder::header);
        return send(builder.build(), handler);
    }

    /**
     * Sends a POST request with a plain-text body to the given URI and returns
     * the response with a {@code String} body.
     *
     * @param uri         the target URI
     * @param body        the text payload
     * @param contentType the {@code Content-Type} header value
     *                    (e.g. {@code "text/csv; charset=UTF-8"})
     * @return the {@link HttpResponse} with a {@code String} body
     * @throws IOException if the request fails or the thread is interrupted
     */
    public HttpResponse<String> postString(URI uri, String body, String contentType) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Sends a POST request with a JSON body to the given URI and returns
     * the response with a discarded body.
     *
     * @param uri  the target URI
     * @param json the JSON payload string
     * @return the {@link HttpResponse} (body discarded)
     * @throws IOException if the request fails or the thread is interrupted
     */
    public HttpResponse<Void> postJson(URI uri, String json) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return send(req, HttpResponse.BodyHandlers.discarding());
    }

    /**
     * Assembles and sends a {@code multipart/form-data} POST request from the
     * given {@link MultipartBody} and returns the response with a {@code String}
     * body.
     *
     * @param uri  the target URI
     * @param body the pre-built {@link MultipartBody}
     * @return the {@link HttpResponse} with a {@code String} body
     * @throws IOException if body assembly or the request fails
     */
    public HttpResponse<String> postMultipart(URI uri, MultipartBody body) throws IOException {
        byte[] rawBody = body.toBytes();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + body.getBoundary())
                .POST(HttpRequest.BodyPublishers.ofByteArray(rawBody))
                .build();
        return send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Returns {@code true} if the given HTTP status code indicates success
     * (i.e. is in the 2xx range).
     *
     * @param statusCode the HTTP status code to test
     * @return {@code true} for 2xx status codes
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode / 100 == 2;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sends the given request and returns the response, restoring the interrupt
     * flag and rethrowing as {@link IOException} if the thread is interrupted.
     *
     * @param <T>     the response body type
     * @param request the prepared {@link HttpRequest}
     * @param handler the response body handler
     * @return the {@link HttpResponse}
     * @throws IOException if sending fails or the thread is interrupted
     */
    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            log.debug("→ {} {}", request.method(), request.uri());
            HttpResponse<T> response = client.send(request, handler);
            log.debug("← HTTP {}", response.statusCode());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted: " + request.uri(), e);
        }
    }

    // =========================================================================
    // MultipartBody builder
    // =========================================================================

    /**
     * Fluent builder for {@code multipart/form-data} request bodies.
     *
     * <p>Supports two kinds of parts:</p>
     * <ul>
     *   <li>{@link #addField(String, String)} — a plain-text form field</li>
     *   <li>{@link #addFile(String, String, String, byte[])} — a binary file
     *       attachment</li>
     * </ul>
     *
     * <p>Usage:</p>
     * <pre>{@code
     * HttpService.MultipartBody body = new HttpService.MultipartBody()
     *         .addField("reqtype", "fileupload")
     *         .addField("time", "1h")
     *         .addFile("fileToUpload", "jacket.png", "image/png", pngBytes);
     *
     * http.postMultipart(endpoint, body);
     * }</pre>
     */
    public static final class MultipartBody {

        private static final String CRLF = "\r\n";

        private final String      boundary = UUID.randomUUID().toString();
        private final List<Part>  parts    = new ArrayList<>();

        // -------------------------------------------------------------------------
        // Public builder API
        // -------------------------------------------------------------------------

        /**
         * Adds a plain-text form field to this multipart body.
         *
         * @param name  the field name
         * @param value the field value
         * @return this builder
         */
        public MultipartBody addField(String name, String value) {
            parts.add(new TextPart(name, value));
            return this;
        }

        /**
         * Adds a binary file attachment to this multipart body.
         *
         * @param name        the form field name
         * @param filename    the filename reported to the server
         * @param contentType the MIME type of the file (e.g. {@code "image/png"})
         * @param data        the raw file bytes
         * @return this builder
         */
        public MultipartBody addFile(String name, String filename, String contentType, byte[] data) {
            parts.add(new FilePart(name, filename, contentType, data));
            return this;
        }

        /**
         * Returns the boundary string used to delimit parts.
         *
         * @return the boundary string
         */
        public String getBoundary() {
            return boundary;
        }

        /**
         * Assembles all parts into a single {@code byte[]} ready to be used as
         * the HTTP request body.
         *
         * @return the assembled multipart body bytes
         * @throws IOException if the byte stream cannot be written
         */
        public byte[] toBytes() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Part part : parts) {
                out.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
                part.writeTo(out);
            }
            out.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        }

        // -------------------------------------------------------------------------
        // Part hierarchy
        // -------------------------------------------------------------------------

        /** Common base for a single multipart part. */
        private interface Part {
            /**
             * Writes this part's headers and body to the given stream.
             *
             * @param out the output stream to write to
             * @throws IOException if the write fails
             */
            void writeTo(ByteArrayOutputStream out) throws IOException;
        }

        /** A plain-text form field part. */
        private record TextPart(String name, String value) implements Part {
            @Override
            public void writeTo(ByteArrayOutputStream out) throws IOException {
                out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write((value + CRLF).getBytes(StandardCharsets.UTF_8));
            }
        }

        /** A binary file attachment part. */
        private record FilePart(String name, String filename, String contentType, byte[] data) implements Part {
            @Override
            public void writeTo(ByteArrayOutputStream out) throws IOException {
                out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"" + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: " + contentType + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
                out.write(data);
                out.write(CRLF.getBytes(StandardCharsets.UTF_8));
            }

            
        }
    }
}

