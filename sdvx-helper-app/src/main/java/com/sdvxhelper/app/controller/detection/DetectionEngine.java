package com.sdvxhelper.app.controller.detection;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javax.imageio.ImageIO;

import com.sdvxhelper.app.controller.factories.ObsReconnectThreadFactory;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.model.enums.PlayState;
import com.sdvxhelper.network.DiscordPresenceClient;
import com.sdvxhelper.network.JacketUploadClient;
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

    /**
     * Single-thread executor for jacket uploads. Runs off the detection loop so
     * that network I/O never delays frame processing.
     */
    private final ExecutorService jacketUploadExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jacket-upload");
        t.setDaemon(true);
        return t;
    });
    private ObsWebSocketClient obsClient;

    // Detection loop state
    private volatile BufferedImage currentFrame;
    private DetectMode currentMode = DetectMode.INIT;
    private volatile boolean detectionRunning = false;
    private int consecutiveCaptureFailures = 0;
    private static final int CAPTURE_FAILURE_RESET_THRESHOLD = 10;

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

    // Playlist timer state — set when OBS recording or streaming starts
    private Instant outputStartTime;
    private Duration pendingSongTimestamp;

    // Last known song for Discord presence updates
    private String lastKnownTitle = "";
    private String lastKnownDiff = "";

    // Discord enhanced-presence state
    private JacketUploadClient jacketUploadClient;
    private volatile String lastJacketUrl = null;
    private String lastDiscordTitle = null;

    /**
     * Returns a new builder for constructing a {@link DetectionEngine}.
     *
     * @return a fresh {@link DetectionEngineBuilder}
     */
    public static DetectionEngineBuilder builder() {
        return new DetectionEngineBuilder();
    }

    /**
     * Package-private no-arg constructor — use {@link #builder()} to obtain an
     * instance. Field population is performed by
     * {@link DetectionEngineBuilder#build()}.
     */
    DetectionEngine() {
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
                Thread.sleep(100);
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
        jacketUploadExecutor.shutdown();
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

    /**
     * Returns the {@link ScreenHandler} used by this engine to process captured
     * frames, allowing callers to query accumulated play data (e.g. for
     * end-of-session archive generation).
     *
     * @return the current {@link ScreenHandler}, or {@code null} before the engine
     *         is fully initialised
     */
    public ScreenHandler getScreenHandler() {
        return screenHandler;
    }

    public boolean isRtaMode() {
        return rtaMode;
    }

    // -------------------------------------------------------------------------
    // Trigger methods (called by FXML action handlers via MainController)
    // -------------------------------------------------------------------------

    /**
     * Force-saves the Volforce and class images from the current frame (F4).
     *
     * <p>
     * Unlike the automatic detection path, this bypasses pHash change-detection and
     * brightness thresholds — the user explicitly requested a save.
     * </p>
     */
    public void triggerCaptureVolforce() {
        BufferedImage frame = currentFrame;
        if (frame != null) {
            screenHandler.forceCaptureVolforce(frame);
        }
    }

    /**
     * Regenerates {@code summary_full.png} and {@code summary_small.png} from all
     * current plays (preloaded + session) (F5).
     *
     * @return {@code true} if summary images were written successfully
     */
    public boolean triggerSaveSummary() {
        return screenHandler.regenerateSummary();
    }

    /** Triggers result-screen processing on the current frame (F6). */
    public void triggerResultScreen() {
        BufferedImage frame = currentFrame;
        if (frame != null) {
            boolean vfCaptured = screenHandler.captureVolforce(frame);
            processResultScreen(frame, vfCaptured);
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
        webhookDispatcher.sendPlaylistSummary(screenHandler.getSessionPlays(),
                screenHandler.getSessionPlayTimestamps());
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
            client.setOnRecordingStarted(this::handleRecordingStarted);
            client.setOnRecordingStopped(this::handleRecordingStopped);
            client.setOnStreamingStarted(this::handleStreamingStarted);
            client.setOnStreamingStopped(this::handleStreamingStopped);

            client.connect();

            obsClient = client;
            obsOverlayService.setObsClient(obsClient);
            Platform.runLater(() -> listener.onObsStatusChanged("connected"));
            log.info("OBS connected successfully");
            log.info("OBS connection properties are: messageSize={}", obsClient.getMaxMessageSize());

            // Events only fire on state *transitions*. If OBS is already recording or
            // streaming when we connect, no event is emitted, so we query the current
            // state explicitly and activate the playlist timer / UI indicators manually.
            try {
                if (client.isRecording()) {
                    log.info("OBS was already recording on connect - activating playlist timer");
                    handleRecordingStarted();
                }
                if (client.isStreaming()) {
                    log.info("OBS was already streaming on connect - activating playlist timer");
                    handleStreamingStarted();
                }
            } catch (IOException e) {
                log.debug("Could not query OBS output state on connect: {}", e.getMessage());
            }
        } catch (IOException e) {
            log.debug("OBS connect retry failed: {}", e.getMessage());
            Platform.runLater(() -> listener.onObsStatusChanged("disconnected"));
        }
    }

    private void handleRecordingStarted() {
        handleOutputStarted("Recording");
    }

    private void handleRecordingStopped() {
        handleOutputStopped("Recording");
    }

    private void handleStreamingStarted() {
        handleOutputStarted("Streaming");
    }

    private void handleStreamingStopped() {
        handleOutputStopped("Streaming");
    }

    private void handleOutputStarted(String outputType) {
        if (Objects.isNull(outputStartTime)) {
            outputStartTime = Instant.now();
            log.info("OBS {} started - playlist timer started", outputType);
        } else {
            log.debug("OBS {} started - playlist timer already running", outputType);
        }
        listener.onObsOutputStarted(outputType);
    }

    private void handleOutputStopped(String outputType) {
        log.info("OBS {} stopped", outputType);
        listener.onObsOutputStopped(outputType);
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
            log.warn("Cannot capture frame: obs_source setting is blank - set it in OBS Control Settings");
            return;
        }
        try {
            BufferedImage raw = obsClient.captureSource(source);
            if (consecutiveCaptureFailures >= CAPTURE_FAILURE_RESET_THRESHOLD) {
                log.info("OBS screenshot capture resumed — resetting detection state");
                currentMode = DetectMode.INIT;
                doneThisSong = false;
            } else if (consecutiveCaptureFailures > 0) {
                log.info("OBS screenshot capture resumed");
            }
            consecutiveCaptureFailures = 0;
            currentFrame = applyOrientation(raw);
            log.debug("Captured frame from source '{}' (raw={}x{}, oriented={}x{})", source, raw.getWidth(),
                    raw.getHeight(), currentFrame.getWidth(), currentFrame.getHeight());
            if ("true".equalsIgnoreCase(settings.getOrDefault("save_on_capture", "false"))) {
                saveDebugCapture(currentFrame);
            }
        } catch (IOException e) {
            consecutiveCaptureFailures++;
            if (consecutiveCaptureFailures == 1) {
                log.warn("captureSource('{}') failed: {} - suppressing further failures until capture recovers", source,
                        e.getMessage());
            } else {
                log.debug("captureSource('{}') failed ({} consecutive): {}", source, consecutiveCaptureFailures,
                        e.getMessage());
            }
            currentFrame = null;
        }
    }

    private void saveDebugCapture(BufferedImage frame) {
        try {
            File outDir = new File("out");
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            File captureFile = new File(outDir, "capture.png");
            log.debug("Saving debug capture to {}", captureFile.getAbsolutePath());
            ImageIO.write(frame, "PNG", captureFile);
        } catch (IOException e) {
            log.warn("Failed to save debug capture to out/capture.png: {}", e.getMessage());
        }
    }

    private void processCurrentFrame() {
        if (currentFrame == null) {
            log.debug("Skipping frame processing: no frame captured");
            return;
        }
        BufferedImage frame = currentFrame;

        // Priority mode detection — mirrors Python detect() in
        // sdvx_helper.pyw:1360-1365.
        // Evaluate each screen once to avoid redundant hash comparisons.
        boolean logoDetected = imageAnalysisService.isLogoScreen(frame, params);
        boolean resultDetected = !logoDetected && imageAnalysisService.isResultScreen(frame, params);
        boolean selectDetected = !logoDetected && !resultDetected && imageAnalysisService.isSelectScreen(frame, params);

        DetectMode newMode = currentMode;
        if (logoDetected) {
            newMode = DetectMode.INIT;
        } else if (resultDetected) {
            newMode = DetectMode.RESULT;
        } else if (selectDetected) {
            newMode = DetectMode.SELECT;
        } else if (newMode == DetectMode.SELECT) {
            // Select screen was active but is now gone — mirrors Python line 1425:
            // "if not self.is_onselect(): self.detect_mode = detect_mode.init"
            // This is required to open the INIT block below, which is the only place
            // that checks isPlayScreen() and transitions to PLAY.
            newMode = DetectMode.INIT;
        } else if (newMode == DetectMode.RESULT) {
            // Result screen was active but is now gone — return to INIT.
            // Required for Skill Analyzer (RESULT -> DETECT -> PLAY without an
            // intervening SELECT) and retry-from-result (RESULT -> PLAY without a
            // logo screen). Without this, the engine stays stuck in RESULT and the
            // INIT block that detects isPlayScreen() is never reached.
            newMode = DetectMode.INIT;
        }

        // PLAY validation — mirrors Python detect() lines 1376-1455.
        // If currently PLAY but play screen is gone, fall back to INIT.
        // If INIT and play screen is detected, enter PLAY only after the
        // play0_interval guard to avoid false triggers from end-of-song animation.
        if (newMode == DetectMode.PLAY && !imageAnalysisService.isPlayScreen(frame, params)) {
            newMode = DetectMode.INIT;
        }
        if (newMode == DetectMode.INIT && imageAnalysisService.isPlayScreen(frame, params)) {
            long secondsSinceLastPlay1 = Duration.between(lastPlay1Time, Instant.now()).getSeconds();
            int play0Interval = ParamUtils.parseIntParam(settings.get("play0_interval"), 10);
            if (secondsSinceLastPlay1 >= play0Interval) {
                newMode = DetectMode.PLAY;
            }
        }

        if (newMode != currentMode) {
            log.info("Mode transition: {} -> {}", currentMode, newMode);
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

        if (currentMode == DetectMode.INIT && !doneThisSong && imageAnalysisService.isDetectScreen(frame, params)) {
            processDetectMode(frame);
        }
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
            discordPresenceClient.updatePresence(PlayState.PLAYING, lastDiscordTitle, lastKnownDiff,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), lastJacketUrl);
        }
    }

    private void handleTransitionToResult(BufferedImage frame) {
        obsOverlayService.controlSources("result0");
        applyAutosavePrewait();
        // Always crop and save the VF/class badge PNGs regardless of the autosave
        // interval. Mirrors Python save_playerinfo() which runs on every result frame.
        boolean vfCaptured = screenHandler.captureVolforce(frame);
        if ("true".equalsIgnoreCase(settings.get("autosave_always"))) {
            long intervalSeconds = ParamUtils.parseIntParam(settings.get("autosave_interval"), 60);
            long elapsed = Duration.between(lastAutosaveTime, Instant.now()).getSeconds();
            if (elapsed > intervalSeconds) {
                processResultScreen(frame, vfCaptured);
                lastAutosaveTime = Instant.now();
            } else {
                // Interval not elapsed — still fire indicator so VF icon shows
                final boolean finalVfCaptured = vfCaptured;
                Platform.runLater(() -> listener.onResultCaptured(false, false, finalVfCaptured));
            }
        } else {
            processResultScreen(frame, vfCaptured);
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

    private void processResultScreen(BufferedImage frame, boolean vfCaptured) {
        OnePlayData play = screenHandler.handleResultScreen(frame, pendingSongTimestamp, lastKnownDiff);
        pendingSongTimestamp = null;
        if (play == null) {
            log.debug("Result screen processing returned no play (frame may have been unreadable)");
            final boolean finalVf = vfCaptured;
            Platform.runLater(() -> listener.onResultCaptured(false, false, finalVf));
            return;
        }
        obsOverlayService.updateVfText(screenHandler.getCurrentTotalVf(), screenHandler.getPreviousTotalVf());
        handleRtaUpdate(screenHandler.getCurrentTotalVf());
        if (discordPresenceClient != null) {
            int level = screenHandler.getLevelFor(play.getTitle(), play.getDifficulty());
            int scoreDiff = play.getCurScore() - play.getPreScore();
            String discordTitle = lastDiscordTitle != null ? lastDiscordTitle : play.getTitle();
            discordPresenceClient.updatePresenceResult(discordTitle, play.getDifficulty(), level, play.getCurScore(),
                    scoreDiff, play.getLamp(), lastJacketUrl);
        }
        boolean screenshotSaved = screenHandler.wasLastScreenshotSaved();
        boolean summaryGenerated = screenHandler.wasLastSummaryGenerated();
        final OnePlayData finalPlay = play;
        final boolean finalVfCaptured = vfCaptured;
        final boolean finalScreenshot = screenshotSaved;
        final boolean finalSummary = summaryGenerated;
        Platform.runLater(() -> {
            listener.onPlayRecorded(finalPlay);
            listener.onResultCaptured(finalScreenshot, finalSummary, finalVfCaptured);
        });
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
        if (result != null) {
            lastKnownTitle = result.getTitle();
            lastKnownDiff = result.getDiff();
        } else {
            log.debug("Select screen processing returned no result (jacket not identified)");
        }
        if (discordPresenceClient != null) {
            discordPresenceClient.updatePresence(PlayState.SELECTING, result != null ? lastKnownTitle : null,
                    result != null ? lastKnownDiff : null,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), lastJacketUrl);
        }
        if (result == null) {
            return;
        }
        if (result.getImportedPlay() != null) {
            final OnePlayData importedPlay = result.getImportedPlay();
            Platform.runLater(() -> listener.onPlayRecorded(importedPlay));
        }
    }

    private void processDetectMode(BufferedImage frame) {
        double detectWait = ParamUtils.parseDoubleParam(params.get("detect_wait"), 1.5);
        int sampleCount = ParamUtils.parseIntParam(settings.get("detect_sample_count"), 3);
        if (sampleCount < 1) {
            sampleCount = 1;
        }
        long intervalMs = (long) ((detectWait * 1000) / sampleCount);
        BufferedImage bestFrame = null;
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            captureCurrentFrame();
            if (currentFrame != null && imageAnalysisService.isDetectScreen(currentFrame, params)) {
                bestFrame = currentFrame;
                log.debug("processDetectMode: sample {}/{} passed isDetectScreen - bestFrame updated", sampleIndex + 1,
                        sampleCount);
            } else {
                log.debug("processDetectMode: sample {}/{} did not pass isDetectScreen - skipped", sampleIndex + 1,
                        sampleCount);
            }
        }
        if (bestFrame == null) {
            log.error(
                    "processDetectMode: no sample passed isDetectScreen (all {} samples rejected) — falling back to original frame",
                    sampleCount);
        }
        BufferedImage freshFrame = bestFrame != null ? bestFrame : frame;
        String[] titleDiff = screenHandler.handleDetectMode(freshFrame);
        doneThisSong = true;
        if (Objects.nonNull(outputStartTime)) {
            pendingSongTimestamp = Duration.between(outputStartTime, Instant.now());
            log.debug("Playlist timestamp captured at detect screen: {}", pendingSongTimestamp);
        }
        if (titleDiff == null) {
            log.debug("Detect mode: frame unreadable, skipping title/diff update");
            return;
        }
        lastKnownTitle = titleDiff[0];
        lastKnownDiff = titleDiff[1];
        log.debug("Detect mode: title='{}', diff='{}'", lastKnownTitle, lastKnownDiff);

        resolveDiscordTitle();
        resolveDiscordJacket();

        if (obsClient != null && obsClient.isConnected()) {
            // Refresh the browser source so OBS reloads the updated select_*.png files.
            // Mirrors Python: obs.refresh_source('nowplaying.html') /
            // obs.refresh_source('nowplaying')
            obsClient.refreshBrowserSource("nowplaying.html");
            obsClient.refreshBrowserSource("nowplaying");
        }
        if (discordPresenceClient != null) {
            discordPresenceClient.updatePresence(PlayState.PLAYING, lastDiscordTitle, lastKnownDiff,
                    ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000)), lastJacketUrl);
        }
        log.info("Detect mode processed for: {}", lastKnownTitle);
    }

    private void processBlasterMax(BufferedImage frame) {
        boolean isMax = screenHandler.checkBlasterMax(frame);
        String txtSource = settings.getOrDefault("obs_txt_blastermax", "sdvx_helper_blastermax");
        obsOverlayService.updateBlasterMax(isMax, txtSource);
    }

    /**
     * Resolves the Discord display title for the current song following a
     * three-step priority chain:
     *
     * <ol>
     * <li>If the jacket hash lookup identified the song ({@link #lastKnownTitle} is
     * not {@code "Unknown"}), that matched title is used directly.</li>
     * <li>If the jacket was <em>not</em> matched ({@code lastKnownTitle} is
     * {@code "Unknown"}) <em>and</em> the {@code discord_presence_ocr_titles}
     * setting is enabled, {@link ScreenHandler#ocrSelectTitle()} is called to
     * extract the title from the saved {@code out/select_title.png} crop via
     * Tesseract OCR.</li>
     * <li>If OCR also fails or produces no usable text, the title falls back to
     * {@code "Unknown"}.</li>
     * </ol>
     */
    private void resolveDiscordTitle() {
        if (!"Unknown".equals(lastKnownTitle)) {
            lastDiscordTitle = lastKnownTitle;
            return;
        }
        if ("true".equalsIgnoreCase(settings.get("discord_presence_ocr_titles"))) {
            String ocrTitle = screenHandler.ocrSelectTitle();
            if (ocrTitle != null && !ocrTitle.isBlank()) {
                log.debug("resolveDiscordTitle: jacket not matched, OCR title '{}'", ocrTitle);
                lastDiscordTitle = ocrTitle;
            } else {
                log.warn(
                        "resolveDiscordTitle: jacket not matched and OCR returned blank, Discord title will show as Unknown");
                lastDiscordTitle = "Unknown";
            }
        } else {
            lastDiscordTitle = "Unknown";
        }
    }

    /**
     * Resolves the Discord jacket URL for the current song. When
     * {@code discord_presence_upload_jacket} is enabled and a
     * {@link LitterboxClient} is available, reads the saved
     * {@code out/select_jacket.png} bytes and uploads them to Litterbox. On success
     * the returned URL is stored in {@link #lastJacketUrl}; on any failure
     * {@code lastJacketUrl} is cleared so the default Discord asset is shown.
     */
    private void resolveDiscordJacket() {
        if (!"true".equalsIgnoreCase(settings.get("discord_presence_upload_jacket"))) {
            log.debug("resolveDiscordJacket: jacket upload disabled by setting, skipping");
            return;
        }
        // Snapshot both clients so the lambda below is not affected by concurrent
        // setLitterboxClient() / close() calls from the JavaFX thread.
        JacketUploadClient client = jacketUploadClient;
        if (client == null) {
            log.warn("resolveDiscordJacket: upload enabled but JacketUploadClient is null - jacket will not appear");
            return;
        }
        File jacketFile = new File("out", "select_jacket.png");
        if (!jacketFile.exists()) {
            log.warn("resolveDiscordJacket: out/select_jacket.png not found - jacket will not appear in Discord");
            lastJacketUrl = null;
            return;
        }

        // Hand off the blocking file-read + HTTP upload to a background thread so the
        // detection loop is never stalled waiting for network I/O.
        jacketUploadExecutor.submit(() -> {
            try {
                byte[] jacketBytes = Files.readAllBytes(jacketFile.toPath());
                log.info("resolveDiscordJacket: uploading select_jacket.png ({} bytes) [async]", jacketBytes.length);
                String url = client.upload(jacketBytes, "select_jacket.png");
                if (url != null && !url.isBlank()) {
                    log.info("resolveDiscordJacket: jacket uploaded -> '{}'", url);
                    lastJacketUrl = url;
                    // Re-push presence so Discord picks up the jacket without waiting for
                    // the next natural state transition.
                    DiscordPresenceClient dpc = discordPresenceClient;
                    if (dpc != null) {
                        PlayState state = currentMode == DetectMode.PLAY
                                ? PlayState.PLAYING
                                : currentMode == DetectMode.SELECT ? PlayState.SELECTING : PlayState.IDLE;
                        String vf = ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000));
                        dpc.updatePresence(state, lastDiscordTitle, lastKnownDiff, vf, url);
                    }
                } else {
                    log.warn("resolveDiscordJacket: upload returned empty URL - Discord will use default asset");
                    lastJacketUrl = null;
                }
            } catch (IOException e) {
                log.warn("resolveDiscordJacket: jacket upload failed - {}", e.getMessage());
                lastJacketUrl = null;
            }
        });
    }

    // -------------------------------------------------------------------------
    // Frame orientation
    // -------------------------------------------------------------------------

    /**
     * Mirrors Python {@code get_capture_after_rotate()} in
     * {@code sdvx_helper.pyw:617}.
     *
     * <p>
     * The value describes which direction the physical screen top is pointing
     * inside the raw OBS capture:
     * <ul>
     * <li>{@code "right"} — top points right → rotate 90° CCW (PIL rotate 90)</li>
     * <li>{@code "left"} — top points left → rotate 90° CW (PIL rotate 270)</li>
     * <li>anything else — already portrait → resize to 1080×1920</li>
     * </ul>
     * The Java key {@code "orientation"} takes precedence; the Python legacy key
     * {@code "orientation_top"} is used as a fallback so that Python-only settings
     * files are still handled correctly on first launch.
     * </p>
     */
    private BufferedImage applyOrientation(BufferedImage frame) {
        if (frame == null) {
            log.debug("applyOrientation: frame is null, returning null");
            return null;
        }
        // Java "orientation" key takes priority; fall back to Python "orientation_top".
        String orientationTop = settings.getOrDefault("orientation", settings.getOrDefault("orientation_top", "top"));
        BufferedImage oriented = switch (orientationTop) {
            case "bottom" -> rotateImage(frame, Math.PI);
            // Python rotate(90 CCW) — top points right in raw capture
            case "right" -> rotateImage(frame, -Math.PI / 2);
            // Python rotate(270 CCW = 90 CW) — top points left in raw capture
            case "left" -> rotateImage(frame, Math.PI / 2);
            // Python img.resize((1080,1920)) — already portrait orientation
            default -> resizeImage(frame, 1080, 1920);
        };
        log.debug("applyOrientation: orientation_top='{}' {}x{} -> {}x{}", orientationTop, frame.getWidth(),
                frame.getHeight(), oriented.getWidth(), oriented.getHeight());
        return oriented;
    }

    private static BufferedImage resizeImage(BufferedImage src, int targetW, int targetH) {
        if (src.getWidth() == targetW && src.getHeight() == targetH) {
            return src;
        }
        BufferedImage dest = new BufferedImage(targetW, targetH, src.getType());
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return dest;
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

    /**
     * @param listener
     *            the listener to set
     */
    public void setListener(DetectionListener listener) {
        this.listener = listener;
    }

    /**
     * @param imageAnalysisService
     *            the imageAnalysisService to set
     */
    public void setImageAnalysisService(ImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
    }

    /**
     * @param discordPresenceClient
     *            the discordPresenceClient to set
     */
    public void setDiscordPresenceClient(DiscordPresenceClient discordPresenceClient) {
        this.discordPresenceClient = discordPresenceClient;
    }

    /**
     * Sets the optional {@link JacketUploadClient} used to upload jacket images for
     * Discord Rich Presence. When {@code null} (default) jacket upload is skipped.
     *
     * @param jacketUploadClient
     *            the client, or {@code null} to disable jacket uploads
     */
    public void setLitterboxClient(JacketUploadClient jacketUploadClient) {
        this.jacketUploadClient = jacketUploadClient;
    }

    /**
     * Clears the cached jacket URL and immediately re-pushes the current Discord
     * Rich Presence activity without a jacket image.
     *
     * <p>
     * Called when the user disables jacket upload mid-session so that Discord
     * reverts to the default logo asset right away rather than waiting for the next
     * detection cycle.
     * </p>
     */
    public void clearJacketUrl() {
        lastJacketUrl = null;
        if (discordPresenceClient == null) {
            return;
        }
        PlayState state;
        if (currentMode == DetectMode.PLAY) {
            state = PlayState.PLAYING;
        } else if (currentMode == DetectMode.SELECT) {
            state = PlayState.SELECTING;
        } else {
            state = PlayState.IDLE;
        }
        String vf = ScoreFormatter.formatTotalVf((int) (screenHandler.getCurrentTotalVf() * 1000));
        discordPresenceClient.updatePresence(state, lastDiscordTitle, lastKnownDiff, vf, null);
        log.debug("clearJacketUrl: presence re-pushed with null jacket (state={})", state);
    }

    /**
     * @param screenHandler
     *            the screenHandler to set
     */
    public void setScreenHandler(ScreenHandler screenHandler) {
        this.screenHandler = screenHandler;
    }

    /**
     * @param obsOverlayService
     *            the obsOverlayService to set
     */
    public void setObsOverlayService(ObsOverlayService obsOverlayService) {
        this.obsOverlayService = obsOverlayService;
    }

    /**
     * @param webhookDispatcher
     *            the webhookDispatcher to set
     */
    public void setWebhookDispatcher(WebhookDispatcher webhookDispatcher) {
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * @param params
     *            the params to set
     */
    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    /**
     * @param settings
     *            the settings to set
     */
    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    /**
     * Sets the initial detection mode. Used by {@link DetectionEngineBuilder} to
     * restore the previous engine's mode when rebuilding after a locale switch,
     * preventing a false re-trigger of the transition handler for a screen that was
     * already processed before the switch.
     *
     * @param currentMode
     *            the mode the engine should start in
     */
    public void setCurrentMode(DetectMode currentMode) {
        this.currentMode = currentMode;
    }

}
