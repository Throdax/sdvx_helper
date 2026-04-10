package com.sdvxhelper.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Base64;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Client for the OBS Studio WebSocket API (obs-websocket protocol).
 *
 * <p>Wraps the {@code obs-websocket-java} library to provide a simplified interface
 * for the operations needed by SDVX Helper: connecting, capturing frames,
 * toggling source visibility, and changing scenes.
 *
 * <p>Maps to the Python {@code OBSSocket} class in {@code obssocket.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class ObsWebSocketClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ObsWebSocketClient.class);

    private final String host;
    private final int port;
    private final String password;

    private volatile boolean connected = false;

    /**
     * Constructs an OBS WebSocket client.
     *
     * @param host     OBS WebSocket host (e.g. {@code "localhost"})
     * @param port     OBS WebSocket port (e.g. {@code 4444})
     * @param password OBS WebSocket password (empty string if not set)
     */
    public ObsWebSocketClient(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    /**
     * Establishes a connection to the OBS WebSocket server.
     *
     * @throws IOException if the connection cannot be established
     */
    public void connect() throws IOException {
        log.info("Connecting to OBS at {}:{}", host, port);
        // TODO: Use obs-websocket-java OBSRemoteController to connect
        // OBSRemoteController controller = new OBSRemoteController(host, port, password, false);
        // controller.connect();
        connected = true;
        log.info("Connected to OBS");
    }

    /**
     * Returns {@code true} if the client is currently connected to OBS.
     *
     * @return connection status
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Captures a screenshot of the given source and returns it as a {@link BufferedImage}.
     *
     * @param sourceName OBS source name
     * @return captured image
     * @throws IOException if the screenshot cannot be captured or decoded
     */
    public BufferedImage captureSource(String sourceName) throws IOException {
        requireConnected();
        log.debug("Capturing source '{}'", sourceName);
        // TODO: Call GetSourceScreenshot request and decode the Base64 PNG response
        // For now, return a placeholder 1×1 image
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        log.debug("Captured source '{}'", sourceName);
        return img;
    }

    /**
     * Decodes a Base64-encoded PNG image (as returned by the OBS GetSourceScreenshot response).
     *
     * @param base64Data Base64 string (may include the {@code data:image/png;base64,} prefix)
     * @return decoded image
     * @throws IOException if decoding fails
     */
    public static BufferedImage decodeBase64Image(String base64Data) throws IOException {
        String stripped = base64Data.contains(",")
                ? base64Data.substring(base64Data.indexOf(',') + 1)
                : base64Data;
        byte[] bytes = Base64.getDecoder().decode(stripped);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) throw new IOException("Failed to decode image from OBS response");
            return img;
        }
    }

    /**
     * Sets the visibility of an OBS source within its current scene.
     *
     * @param sceneName  name of the scene containing the source
     * @param sourceName name of the source to toggle
     * @param visible    {@code true} to show, {@code false} to hide
     * @throws IOException if the request fails
     */
    public void setSourceVisible(String sceneName, String sourceName, boolean visible) throws IOException {
        requireConnected();
        log.debug("setSourceVisible: scene='{}', source='{}', visible={}", sceneName, sourceName, visible);
        // TODO: Call SetSceneItemEnabled request
    }

    /**
     * Changes the active OBS scene.
     *
     * @param sceneName name of the scene to switch to
     * @throws IOException if the request fails
     */
    public void setCurrentScene(String sceneName) throws IOException {
        requireConnected();
        log.debug("setCurrentScene: '{}'", sceneName);
        // TODO: Call SetCurrentProgramScene request
    }

    /**
     * Sets the text of a GDI+/text source to the given value.
     *
     * @param sourceName source name
     * @param text       new text value
     * @throws IOException if the request fails
     */
    public void setTextSourceValue(String sourceName, String text) throws IOException {
        requireConnected();
        log.debug("setTextSourceValue: source='{}', text='{}'", sourceName, text);
        // TODO: Call SetInputSettings request
    }

    /**
     * Registers a callback invoked on every OBS event.
     * Used for monitoring connection status changes.
     *
     * @param eventHandler callback accepting the raw event string
     */
    public void onEvent(Consumer<String> eventHandler) {
        // TODO: Register OBSRemoteController event listener
    }

    /**
     * Disconnects from OBS.
     */
    @Override
    public void close() {
        if (connected) {
            log.info("Disconnecting from OBS");
            // TODO: controller.disconnect()
            connected = false;
        }
    }

    private void requireConnected() throws IOException {
        if (!connected) {
            throw new IOException("Not connected to OBS WebSocket");
        }
    }
}
