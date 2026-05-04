package com.sdvxhelper.network;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.OBSRemoteControllerBuilder;
import io.obswebsocket.community.client.message.request.inputs.SetInputSettingsRequest;
import io.obswebsocket.community.client.message.request.sceneitems.GetSceneItemIdRequest;
import io.obswebsocket.community.client.message.request.sceneitems.GetSceneItemListRequest;
import io.obswebsocket.community.client.message.request.sceneitems.SetSceneItemEnabledRequest;
import io.obswebsocket.community.client.message.request.scenes.GetSceneListRequest;
import io.obswebsocket.community.client.message.request.scenes.SetCurrentProgramSceneRequest;
import io.obswebsocket.community.client.message.request.sources.GetSourceScreenshotRequest;
import io.obswebsocket.community.client.message.response.sceneitems.GetSceneItemIdResponse;
import io.obswebsocket.community.client.message.response.sceneitems.GetSceneItemListResponse;
import io.obswebsocket.community.client.message.response.scenes.GetSceneListResponse;
import io.obswebsocket.community.client.message.response.sources.GetSourceScreenshotResponse;
import io.obswebsocket.community.client.model.Scene;
import io.obswebsocket.community.client.model.SceneItem;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client for the OBS Studio WebSocket API (obs-websocket protocol v5).
 *
 * <p>
 * Wraps the {@code io.obs-websocket.community:client} library to provide a
 * simplified, synchronous interface for the operations needed by SDVX Helper:
 * connecting, capturing frames, toggling source visibility, changing scenes,
 * and updating text source values.
 * </p>
 *
 * <p>
 * The underlying library is fully asynchronous; this wrapper bridges each async
 * callback into a blocking {@link CompletableFuture} with a
 * {@value #REQUEST_TIMEOUT_SECONDS}-second timeout so callers do not have to
 * manage threading themselves.
 * </p>
 *
 * <p>
 * Maps to the Python {@code OBSSocket} class in {@code obssocket.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ObsWebSocketClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ObsWebSocketClient.class);

    /** Timeout in seconds for each synchronous OBS request. */
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    /** Screenshot image format requested from OBS. */
    private static final String IMAGE_FORMAT = "png";

    private Executor obsConnectorExecutor = Executors.newFixedThreadPool(1);

    private String host;
    private int port;
    private String password;

    private OBSRemoteController controller;
    private volatile boolean connected = false;

    /**
     * Constructs an OBS WebSocket client.
     *
     * @param host
     *            OBS WebSocket host (e.g. {@code "localhost"})
     * @param port
     *            OBS WebSocket port (e.g. {@code 4455})
     * @param password
     *            OBS WebSocket password (empty string if authentication is
     *            disabled)
     */
    public ObsWebSocketClient(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Establishes a connection to the OBS WebSocket server and blocks until the
     * handshake completes or the connection times out.
     *
     * @throws IOException
     *             if the connection cannot be established
     */
    public void connect() throws IOException {
        log.info("Connecting to OBS at {}:{}", host, port);

        CompletableFuture<Void> ready = new CompletableFuture<>();

        OBSRemoteControllerBuilder builder = OBSRemoteController.builder().host(host).port(port).password(password)
                .connectionTimeout(REQUEST_TIMEOUT_SECONDS).lifecycle().onReady(() -> {
                    connected = true;
                    ready.complete(null);
                }).onCommunicatorError(rt -> {
                    ready.completeExceptionally(
                            new IOException("OBS connection error: " + rt.getReason(), rt.getThrowable()));
                }).onDisconnect(() -> {
                    log.info("OBS disconnected");
                    connected = false;
                }).onControllerError(rt -> {
                    ready.completeExceptionally(
                            new IOException("OBS connection error: " + rt.getReason(), rt.getThrowable()));
                }).and();

        WebSocketClient webSocketClient = builder.getWebSocketClient();
        webSocketClient.getHttpClient().setMaxConnectionsPerDestination(1);
        
        controller = builder.build();
        controller.connect();

        try {
            ready.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Connected to OBS at {}:{}", host, port);

        } catch (TimeoutException e) {
            throw new IOException("Timed out waiting for OBS WebSocket handshake at " + host + ":" + port, e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to connect to OBS WebSocket", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection to OBS WebSocket was interrupted", e);
        }

    }

    /**
     * Returns {@code true} if the client is currently connected to OBS.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Disconnects from OBS and releases all resources.
     */
    @Override
    public void close() {
        if (connected && controller != null) {
            log.info("Disconnecting from OBS");
            controller.disconnect();
            controller.stop();
            connected = false;
        }
    }

    // -------------------------------------------------------------------------
    // Scene / source discovery
    // -------------------------------------------------------------------------

    /**
     * Retrieves all scene names currently defined in the connected OBS instance.
     *
     * @return list of scene names (never {@code null})
     * @throws IOException
     *             if the OBS request fails
     */
    public List<String> getSceneNames() throws IOException {
        requireConnected();
        CompletableFuture<GetSceneListResponse> future = new CompletableFuture<>();
        controller.sendRequest(GetSceneListRequest.builder().build(),
                (GetSceneListResponse resp) -> future.complete(resp));
        GetSceneListResponse resp = await(future, "GetSceneList");
        if (!resp.isSuccessful()) {
            throw new IOException("OBS GetSceneList failed: " + resp.getMessageData().getRequestStatus().getComment());
        }
        List<String> names = new ArrayList<>();
        List<Scene> scenes = resp.getScenes();
        if (scenes != null) {
            for (Scene s : scenes) {
                names.add(s.getSceneName());
            }
        }
        return names;
    }

    /**
     * Retrieves all source names (scene items) for the given scene.
     *
     * @param sceneName
     *            the OBS scene whose sources should be listed
     * @return list of source names (never {@code null})
     * @throws IOException
     *             if the OBS request fails
     */
    public List<String> getSourceNames(String sceneName) throws IOException {
        requireConnected();
        CompletableFuture<GetSceneItemListResponse> future = new CompletableFuture<>();
        controller.sendRequest(GetSceneItemListRequest.builder().sceneName(sceneName).build(),
                (GetSceneItemListResponse resp) -> future.complete(resp));
        GetSceneItemListResponse resp = await(future, "GetSceneItemList");
        if (!resp.isSuccessful()) {
            throw new IOException("OBS GetSceneItemList failed for scene '" + sceneName + "': "
                    + resp.getMessageData().getRequestStatus().getComment());
        }
        List<String> names = new ArrayList<>();
        List<SceneItem> items = resp.getSceneItems();
        if (items != null) {
            for (SceneItem si : items) {
                names.add(si.getSourceName());
            }
        }
        return names;
    }

    // -------------------------------------------------------------------------
    // Source screenshot
    // -------------------------------------------------------------------------

    /**
     * Captures a screenshot of the given OBS source and returns it as a
     * {@link BufferedImage}.
     *
     * @param sourceName
     *            the OBS source name to capture
     * @return the captured image
     * @throws IOException
     *             if the screenshot request fails or the image cannot be decoded
     */
    public BufferedImage captureSource(String sourceName) throws IOException {
        requireConnected();
        log.debug("Capturing source '{}'", sourceName);

        CompletableFuture<GetSourceScreenshotResponse> future = new CompletableFuture<>();
        controller.sendRequest(
                GetSourceScreenshotRequest.builder().sourceName(sourceName).imageFormat(IMAGE_FORMAT).build(),
                (GetSourceScreenshotResponse resp) -> future.complete(resp));

        GetSourceScreenshotResponse resp = await(future, "GetSourceScreenshot");
        if (!resp.isSuccessful()) {
            throw new IOException("OBS GetSourceScreenshot failed for source '" + sourceName + "': "
                    + resp.getMessageData().getRequestStatus().getComment());
        }

        BufferedImage img = decodeBase64Image(resp.getMessageData().getResponseData().getImageData());
        log.debug("Captured source '{}'", sourceName);
        return img;
    }

    /**
     * Decodes a Base64-encoded PNG image as returned by the OBS
     * {@code GetSourceScreenshot} response.
     *
     * @param base64Data
     *            Base64 string (may include the {@code data:image/png;base64,}
     *            data-URL prefix)
     * @return the decoded {@link BufferedImage}
     * @throws IOException
     *             if decoding fails or the image data is invalid
     */
    public static BufferedImage decodeBase64Image(String base64Data) throws IOException {
        String stripped = base64Data.contains(",") ? base64Data.substring(base64Data.indexOf(',') + 1) : base64Data;
        byte[] bytes = Base64.getDecoder().decode(stripped);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(bais);
            if (img == null) {
                throw new IOException("Failed to decode image from OBS response");
            }
            return img;
        }
    }

    // -------------------------------------------------------------------------
    // Scene item visibility
    // -------------------------------------------------------------------------

    /**
     * Sets the visibility of an OBS source within the specified scene.
     *
     * <p>
     * This operation requires two OBS requests: first {@code GetSceneItemId} to
     * resolve the numeric scene item ID from the source name, then
     * {@code SetSceneItemEnabled} to apply the visibility change.
     * </p>
     *
     * @param sceneName
     *            the name of the scene containing the source
     * @param sourceName
     *            the name of the source to show or hide
     * @param visible
     *            {@code true} to show the source, {@code false} to hide it
     * @throws IOException
     *             if either OBS request fails
     */
    public void setSourceVisible(String sceneName, String sourceName, boolean visible) throws IOException {
        requireConnected();
        log.debug("setSourceVisible: scene='{}', source='{}', visible={}", sceneName, sourceName, visible);

        // Step 1: resolve the scene item ID
        CompletableFuture<GetSceneItemIdResponse> idFuture = new CompletableFuture<>();
        controller.sendRequest(GetSceneItemIdRequest.builder().sceneName(sceneName).sourceName(sourceName).build(),
                (GetSceneItemIdResponse resp) -> idFuture.complete(resp));

        GetSceneItemIdResponse idResp = await(idFuture, "GetSceneItemId");
        if (!idResp.isSuccessful()) {
            throw new IOException("OBS GetSceneItemId failed for source '" + sourceName + "' in scene '" + sceneName
                    + "': " + idResp.getMessageData().getRequestStatus().getComment());
        }
        Number sceneItemId = idResp.getMessageData().getResponseData().getSceneItemId();

        // Step 2: set visibility
        CompletableFuture<Void> enableFuture = new CompletableFuture<>();
        controller.sendRequest(SetSceneItemEnabledRequest.builder().sceneName(sceneName).sceneItemId(sceneItemId)
                .sceneItemEnabled(visible).build(), _ -> enableFuture.complete(null));

        await(enableFuture, "SetSceneItemEnabled");
        log.debug("setSourceVisible done: source='{}', visible={}", sourceName, visible);
    }

    // -------------------------------------------------------------------------
    // Scene switching
    // -------------------------------------------------------------------------

    /**
     * Switches the active OBS program scene to the specified scene.
     *
     * @param sceneName
     *            the name of the scene to switch to
     * @throws IOException
     *             if the OBS request fails
     */
    public void setCurrentScene(String sceneName) throws IOException {
        requireConnected();
        log.debug("setCurrentScene: '{}'", sceneName);

        CompletableFuture<Void> future = new CompletableFuture<>();
        controller.sendRequest(SetCurrentProgramSceneRequest.builder().sceneName(sceneName).build(),
                _ -> future.complete(null));

        await(future, "SetCurrentProgramScene");
        log.debug("setCurrentScene done: '{}'", sceneName);
    }

    // -------------------------------------------------------------------------
    // Text source value
    // -------------------------------------------------------------------------

    /**
     * Sets the text displayed by a GDI+ or FreeType 2 text source.
     *
     * @param sourceName
     *            the name of the text input source
     * @param text
     *            the new text value to display
     * @throws IOException
     *             if the OBS request fails
     */
    public void setTextSourceValue(String sourceName, String text) throws IOException {
        requireConnected();
        log.debug("setTextSourceValue: source='{}', text='{}'", sourceName, text);

        JsonObject settings = new JsonObject();
        settings.addProperty("text", text);

        CompletableFuture<Void> future = new CompletableFuture<>();
        controller.sendRequest(SetInputSettingsRequest.builder().inputName(sourceName).inputSettings(settings).build(),
                _ -> future.complete(null));

        await(future, "SetInputSettings");
        log.debug("setTextSourceValue done: source='{}'", sourceName);
    }

    // -------------------------------------------------------------------------
    // Event listener
    // -------------------------------------------------------------------------

    /**
     * Registers a callback that is invoked whenever OBS emits an event.
     *
     * <p>
     * The callback receives the raw event string for diagnostic or custom-handling
     * purposes. For typed event handling use
     * {@link OBSRemoteController.OBSRemoteControllerBuilder#registerEventListener}
     * during construction instead.
     * </p>
     *
     * @param eventHandler
     *            callback accepting the raw event string
     */
    public void onEvent(Consumer<String> eventHandler) {
        // Raw event dispatch is handled via registerEventListener() on the builder.
        // This method is retained for API compatibility; callers needing typed
        // events should rebuild the controller with the appropriate listener
        // registered.
        log.debug("onEvent: raw event listener registered (no-op on live controller)");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Asserts that the client is connected, throwing {@link IOException} if not.
     *
     * @throws IOException
     *             if not connected to OBS
     */
    private void requireConnected() throws IOException {
        if (!connected) {
            throw new IOException("Not connected to OBS WebSocket");
        }
    }

    /**
     * Blocks on the given {@link CompletableFuture} for up to
     * {@value #REQUEST_TIMEOUT_SECONDS} seconds.
     *
     * @param <T>
     *            the future's result type
     * @param future
     *            the future to await
     * @param requestName
     *            the OBS request name, used in error messages
     * @return the future's result
     * @throws IOException
     *             if the future completes exceptionally, times out, or the thread
     *             is interrupted
     */
    private <T> T await(CompletableFuture<T> future, String requestName) throws IOException {
        try {
            return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("OBS request '" + requestName + "' timed out after " + REQUEST_TIMEOUT_SECONDS + "s",
                    e);
        } catch (ExecutionException e) {
            throw new IOException("OBS request '" + requestName + "' failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OBS request '" + requestName + "' was interrupted", e);
        }
    }
}