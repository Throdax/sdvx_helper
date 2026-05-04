package com.sdvxhelper.app.controller.detection;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

import com.sdvxhelper.app.controller.factories.ObsReconnectThreadFactory;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.model.enums.PlayState;
import com.sdvxhelper.network.DiscordPresenceClient;
import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.util.ParamUtils;
import com.sdvxhelper.util.ScoreFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the detection loop, manages the OBS connection, and orchestrates
 * screen-handler results into OBS, Discord, and webhook side-effects.
 *
 * <p>
 * All background-thread results are delivered to the UI via
 * {@link DetectionListener} callbacks. The listener implementation is
 * responsible for dispatching to the JavaFX application thread.
 * </p>
 *
 * <p>
 * Replaces the detection-related methods of the Python {@code SDVXHelper} class
 * in {@code sdvx_helper.pyw}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);

    private DetectionListener listener;
    private ImageAnalysisService imageAnalysisService;
    private DiscordPresenceClient discordPresenceClient;
    private ScreenHandler screenHandler;
    private ObsOverlayService obsOverlayService;
    private WebhookDispatcher webhookDispatcher;
    private Map<String, String> params;
    private Map<String, String> settings;

    // OBS connection
    private ScheduledExecutorService obsReconnectScheduler;
    private ObsWebSocketClient obsClient;

    // Detection loop state
    private volatile BufferedImage currentFrame;
    private DetectMode currentMode = DetectMode.INIT;
    private Map<DetectMode, String> stateHashes = new EnumMap<>(DetectMode.class);
    private volatile boolean detectionRunning = false;

    // PLAY-mode timing
    private Instant lastPlay0Time = Instant.EPOCH;
    private Instant lastPlay1Time = Instant.EPOCH;
    private Duration playtime = Duration.ZERO;

    // Misc session state
    private volatile boolean doneThisSong = false;
    private int playCount = 0;
    private Instant lastAutosaveTime = Instant.EPOCH;

    // RTA state
    private boolean rtaMode = false;
    private Instant rtaStartTime;
    private double rtaTargetVf = 0;

    // Last known song for Discord presence updates
    private String lastKnownTitle = "";
    private String lastKnownDiff = "";

    /**
     * @param listener
     *            UI callback target
     * @param imageAnalysisService
     *            image analysis service (also passed to ScreenHandler)
     * @param discordPresenceClient
     *            Discord Rich Presence client, may be {@code null}
     * @param screenHandler
     *            per-screen image processing service
     * @param obsOverlayService
     *            OBS text-source and scene service
     * @param webhookDispatcher
     *            Discord webhook dispatcher
     * @param params
     *            detection parameters map
     * @param settings
     *            application settings map
     */
    public DetectionEngine(DetectionListener listener, ImageAnalysisService imageAnalysisService,
            DiscordPresenceClient discordPresenceClient, ScreenHandler screenHandler,
            ObsOverlayService obsOverlayService, WebhookDispatcher webhookDispatcher, Map<String, String> params,
            Map<String, String> settings) {
        this.listener = listener;
        this.imageAnalysisService = imageAnalysisService;
        this.discordPresenceClient = discordPresenceClient;
        this.screenHandler = screenHandler;
        this.obsOverlayService = obsOverlayService;
        this.webhookDispatcher = webhookDispatcher;
        this.params = params;
        this.settings = settings;
        this.stateHashes = buildStateHashes(params);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Entry point for the detection loop. Call via an executor; blocks until
     * {@link #stop()} is called.
     */
    public void runDetectionLoop() {
        log.info("Detection loop started");
        while (detectionRunning) {
            try {
                captureCurrentFrame();
                processCurrentFrame();
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Detection loop stopped");
    }

    /** Signals the detection loop to start accepting frames. */
    public void start() {
        detectionRunning = true;
    }

    /** Signals the detection loop to exit after the current iteration. */
    public void stop() {
        detectionRunning = false;
    }

    /** Shuts down the OBS reconnect scheduler and closes the OBS connection. */
    public void shutdown() {
        detectionRunning = false;
        if (obsReconnectScheduler != null) {
            obsReconnectScheduler.shutdownNow();
            obsReconnectScheduler = null;
        }
        if (obsClient != null) {
            obsClient.close();
            obsClient = null;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isRunning() {
        return detectionRunning;
    }

    public BufferedImage getCurrentFrame() {
        return currentFrame;
    }

    public DetectMode getCurrentMode() {
        return currentMode;
    }

    public ObsWebSocketClient getObsClient() {
        return obsClient;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public boolean isRtaMode() {
        return rtaMode;
    }

    // -------------------------------------------------------------------------
    // Trigger methods (called by FXML action handlers via MainController)
    // -------------------------------------------------------------------------

    /** Triggers a Volforce capture on the current frame (F4). */
    public void triggerCaptureVolforce() {
        BufferedImage frame = currentFrame;
        if (frame != null) {
            screenHandler.captureVolforce(frame);
        }
    }

    /** Triggers result-screen processing on the current frame (F6). */
    public void triggerResultScreen() {
        BufferedImage frame = currentFrame;
        if (frame != null) {
            processResultScreen(frame);
        }
    }

    /** Triggers select-screen processing when in SELECT mode (F7). */
    public void triggerSelectScreen() {
        if (currentMode == DetectMode.SELECT && currentFrame != null) {
            processSelectScreen(currentFrame);
        }
    }

    /**
     * Starts RTA mode with the given VF target.
     *
     * @param targetVf
     *            target Volforce value
     */
    public void startRta(double targetVf) {
        rtaMode = true;
        rtaStartTime = Instant.now();
        rtaTargetVf = targetVf;
        obsOverlayService.resetRtaVf();
    }

    /** Stops RTA mode. */
    public void stopRta() {
        rtaMode = false;
    }

    /** Fires the OBS "quit" source-control event. */
    public void triggerQuitSources() {
        obsOverlayService.controlSources("quit");
    }

    /** Sends the session playlist summary via webhook. */
    public void sendPlaylistSummary() {
        webhookDispatcher.sendPlaylistSummary(screenHandler.getSessionPlays());
    }

    // -------------------------------------------------------------------------
    // OBS auto-connect
    // -------------------------------------------------------------------------

    /**
     * Starts a background scheduler that attempts to connect to OBS every 5 seconds
     * until a connection is established.
     */
    public void startObsConnectRetry() {
        if (obsReconnectScheduler != null) {
            log.debug("OBS reconnect scheduler already running, skipping start");
            return;
        }
        obsReconnectScheduler = Executors.newSingleThreadScheduledExecutor(new ObsReconnectThreadFactory());
        obsReconnectScheduler.scheduleWithFixedDelay(this::tryConnectObs, 0, 5, TimeUnit.SECONDS);
    }

    private void tryConnectObs() {
        if (obsClient != null && obsClient.isConnected()) {
            log.debug("OBS already connected, skipping retry");
            return;
        }
        try {
            Map<String, String> currentSettings = new SettingsRepository().load();
            String host = currentSettings.getOrDefault("obs_host", currentSettings.getOrDefault("host", "localhost"));
            int port = ParamUtils.parseIntParam(currentSettings.get("obs_port"),
                    ParamUtils.parseIntParam(currentSettings.get("port"), 4455));
            String pass = currentSettings.getOrDefault("obs_password", currentSettings.getOrDefault("passwd", ""));

            ObsWebSocketClient client = new ObsWebSocketClient(host, port, pass);

            client.connect();

            obsClient = client;
            obsOverlayService.setObsClient(obsClient);
            Platform.runLater(() -> listener.onObsStatusChanged("OBS: connected"));
            log.info("OBS connected successfully");
        } catch (IOException e) {
            log.debug("OBS connect retry failed: {}", e.getMessage());
            Platform.runLater(() -> listener.onObsStatusChanged("OBS: disconnected"));
        }
    }

    // -------------------------------------------------------------------------
    // Detection loop internals
    // -------------------------------------------------------------------------

    private void captureCurrentFrame() {
        if (obsClient == null || !obsClient.isConnected()) {
            log.debug("Cannot capture frame: OBS not connected");
            return;
        }
        String source = settings.getOrDefault("obs_source", "");
        if (source.isBlank()) {
            log.debug("Cannot capture frame: obs_source setting is blank");
            return;
        }
        try {
            BufferedImage raw = obsClient.captureSource(source);
            currentFrame = applyOrientation(raw);
        } catch (IOException e) {
            log.debug("captureSource failed: {}", e.getMessage());
            currentFrame = null;
        }
    }

    private void processCurrentFrame() {
        if (currentFrame == null || stateHashes.isEmpty()) {
            log.debug("Skipping frame processing: frame={}, stateHashesEmpty={}",
                    currentFrame == null ? "null" : "present", stateHashes.isEmpty());
            return;
        }
        BufferedImage frame = currentFrame;
        DetectMode newMode = imageAnalysisService.detectMode(frame, stateHashes, params);
        newMode = filterPlayModeFlicker(newMode);

        if (newMode != currentMode) {
            log.info("Mode transition: {} → {}", currentMode, newMode);
            DetectMode previousMode = currentMode;
            currentMode = newMode;
            handleModeTransition(previousMode, newMode, frame);
            final DetectMode modeForUi = currentMode;
            Platform.runLater(() -> listener.onModeChanged(modeForUi));
        }

        if (currentMode == DetectMode.PLAY) {
            Duration elapsed = Duration.between(lastPlay0Time, Instant.now());
            obsOverlayService.updatePlaytime(playtime.plus(elapsed));
        }

        if (currentMode == DetectMode.INIT && !doneThisSong && screenHandler.isOnDetect(frame)) {
            processDetectMode(frame);
        }
    }

    private DetectMode filterPlayModeFlicker(DetectMode newMode) {
        if (newMode != DetectMode.PLAY || currentMode == DetectMode.PLAY) {
            return newMode;
        }
        long secondsSinceLastPlay1 = Duration.between(lastPlay1Time, Instant.now()).getSeconds();
        int play0Interval = ParamUtils.parseIntParam(settings.get("play0_interval"), 10);
        if (secondsSinceLastPlay1 < play0Interval) {
            return DetectMode.INIT;
        }
        return newMode;
    }

    private void handleModeTransition(DetectMode from, DetectMode to, BufferedImage frame) {
        switchObsScene(to);
        if (to == DetectMode.PLAY) {
            handleTransitionToPlay();
        } else if (to == DetectMode.RESULT) {
            handleTransitionToResult(frame);
        } else if (to == DetectMode.SELECT) {
            handleTransitionToSelect(frame);
        }
        if (from == DetectMode.PLAY) {
            lastPlay1Time = Instant.now();
            playtime = playtime.plus(Duration.between(lastPlay0Time, lastPlay1Time));
            obsOverlayService.updatePlaytime(playtime);
            obsOverlayService.controlSources("play1");
        } else if (from == DetectMode.RESULT) {
            obsOverlayService.controlSources("result1");
        } else if (from == DetectMode.SELECT) {
            obsOverlayService.controlSources("select1");
        }
    }

    private void switchObsScene(DetectMode to) {
        String scene = settings.get("obs_scene_" + to.name().toLowerCase());
        if (scene != null && !scene.isBlank() && obsClient != null && obsClient.isConnected()) {
            try {
                obsClient.setCurrentScene(scene);
            } catch (IOException e) {
                log.debug("switchObsScene failed: {}", e.getMessage());
            }
        }
    }

    private void handleTransitionToPlay() {
        lastPlay0Time = Instant.now();
        playCount++;
        doneThisSong = false;
        obsOverlayService.controlSources("play0");
        obsOverlayService.updatePlaysText(playCount);
        if (discordPresenceClient != null) {
            discordPresenceClient.updatePresence(PlayState.PLAYING, lastKnownTitle, lastKnownDiff,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), null);
        }
    }

    private void handleTransitionToResult(BufferedImage frame) {
        obsOverlayService.controlSources("result0");
        applyAutosavePrewait();
        if ("true".equalsIgnoreCase(settings.get("autosave_always"))) {
            long intervalSeconds = ParamUtils.parseIntParam(settings.get("autosave_interval"), 60);
            long elapsed = Duration.between(lastAutosaveTime, Instant.now()).getSeconds();
            if (elapsed > intervalSeconds) {
                processResultScreen(frame);
                lastAutosaveTime = Instant.now();
            }
        } else {
            processResultScreen(frame);
        }
    }

    private void applyAutosavePrewait() {
        double prewait = ParamUtils.parseDoubleParam(settings.get("autosave_prewait"), 0.0);
        if (prewait > 0) {
            try {
                Thread.sleep((long) (prewait * 1000));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleTransitionToSelect(BufferedImage frame) {
        obsOverlayService.controlSources("select0");
        processSelectScreen(frame);
        processBlasterMax(frame);
    }

    // -------------------------------------------------------------------------
    // Screen processing
    // -------------------------------------------------------------------------

    private void processResultScreen(BufferedImage frame) {
        OnePlayData play = screenHandler.handleResultScreen(frame);
        if (play == null) {
            log.debug("Result screen processing returned no play (frame may have been unreadable)");
            return;
        }
        obsOverlayService.updateVfText(screenHandler.getCurrentTotalVf(), screenHandler.getPreviousTotalVf());
        handleRtaUpdate(screenHandler.getCurrentTotalVf());
        if (discordPresenceClient != null) {
            discordPresenceClient.updatePresence(PlayState.RESULT, lastKnownTitle, lastKnownDiff,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), null);
        }
        final OnePlayData finalPlay = play;
        Platform.runLater(() -> listener.onPlayRecorded(finalPlay));
        webhookDispatcher.send(play, frame);
    }

    private void handleRtaUpdate(double totalVf) {
        if (!rtaMode) {
            log.debug("RTA update skipped: RTA mode is off");
            return;
        }
        long elapsedSeconds = rtaStartTime != null ? Duration.between(rtaStartTime, Instant.now()).getSeconds() : 0;
        if (totalVf >= rtaTargetVf) {
            log.info("RTA target VF {} reached! Elapsed: {}s", rtaTargetVf, elapsedSeconds);
        }
        obsOverlayService.updateRtaVf(totalVf);
    }

    private void processSelectScreen(BufferedImage frame) {
        SelectScreenResult result = screenHandler.handleSelectScreen(frame);
        if (result == null) {
            log.debug("Select screen processing returned no result (jacket not identified)");
            return;
        }
        lastKnownTitle = result.getTitle();
        lastKnownDiff = result.getDiff();
        if (discordPresenceClient != null) {
            discordPresenceClient.updatePresence(PlayState.SELECTING, lastKnownTitle, lastKnownDiff,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), null);
        }
        Platform.runLater(() -> listener.onTitleAndDiffChanged(result.getTitle(), result.getDiff()));
        if (result.getImportedPlay() != null) {
            final OnePlayData importedPlay = result.getImportedPlay();
            Platform.runLater(() -> listener.onPlayRecorded(importedPlay));
        }
    }

    private void processDetectMode(BufferedImage frame) {
        String[] titleDiff = screenHandler.handleDetectMode(frame, currentFrame);
        doneThisSong = true;
        if (titleDiff == null) {
            log.debug("Detect mode: jacket not identified, marking song as done");
            return;
        }
        lastKnownTitle = titleDiff[0];
        lastKnownDiff = titleDiff[1];
        if (obsClient != null && obsClient.isConnected()) {
            try {
                obsClient.setTextSourceValue("nowplaying.html", "");
            } catch (IOException e) {
                log.debug("Could not refresh nowplaying.html: {}", e.getMessage());
            }
        }
        if (discordPresenceClient != null) {
            discordPresenceClient.updatePresence(PlayState.PLAYING, lastKnownTitle, lastKnownDiff,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), null);
        }
        final String ftitle = titleDiff[0];
        final String fdiff = titleDiff[1];
        Platform.runLater(() -> listener.onTitleAndDiffChanged(ftitle, fdiff));
        log.info("Detect mode processed for: {}", lastKnownTitle);
    }

    private void processBlasterMax(BufferedImage frame) {
        boolean isMax = screenHandler.checkBlasterMax(frame);
        String txtSource = settings.getOrDefault("obs_txt_blastermax", "sdvx_helper_blastermax");
        obsOverlayService.updateBlasterMax(isMax, txtSource);
    }

    // -------------------------------------------------------------------------
    // Frame orientation
    // -------------------------------------------------------------------------

    private BufferedImage applyOrientation(BufferedImage frame) {
        if (frame == null) {
            log.debug("applyOrientation: frame is null, returning null");
            return null;
        }
        String orientation = settings.getOrDefault("orientation", settings.getOrDefault("orientation_top", "top"));
        return switch (orientation) {
            case "bottom" -> rotateImage(frame, Math.PI);
            case "left" -> rotateImage(frame, -Math.PI / 2);
            case "right" -> rotateImage(frame, Math.PI / 2);
            default -> frame;
        };
    }

    private static BufferedImage rotateImage(BufferedImage src, double radians) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean isOdd = (Math.abs(radians) == Math.PI / 2);
        int newW = isOdd ? h : w;
        int newH = isOdd ? w : h;
        BufferedImage dest = new BufferedImage(newW, newH, src.getType());
        AffineTransform at = new AffineTransform();
        at.translate(newW / 2.0, newH / 2.0);
        at.rotate(radians);
        at.translate(-w / 2.0, -h / 2.0);
        new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR).filter(src, dest);
        return dest;
    }

    private static Map<DetectMode, String> buildStateHashes(Map<String, String> params) {
        Map<DetectMode, String> map = new EnumMap<>(DetectMode.class);
        String selectHash = params.get("hash_select");
        String resultHash = params.get("hash_result");
        String playHash = params.get("hash_play");
        if (selectHash != null && !selectHash.isBlank()) {
            map.put(DetectMode.SELECT, selectHash);
        }
        if (resultHash != null && !resultHash.isBlank()) {
            map.put(DetectMode.RESULT, resultHash);
        }
        if (playHash != null && !playHash.isBlank()) {
            map.put(DetectMode.PLAY, playHash);
        }
        return map;
    }
}
