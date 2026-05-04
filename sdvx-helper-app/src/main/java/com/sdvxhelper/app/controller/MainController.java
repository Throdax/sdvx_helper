package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.sdvxhelper.app.controller.detection.DetectionEngine;
import com.sdvxhelper.app.controller.detection.DetectionListener;
import com.sdvxhelper.app.controller.detection.ObsOverlayService;
import com.sdvxhelper.app.controller.detection.ScreenHandler;
import com.sdvxhelper.app.controller.detection.WebhookDispatcher;
import com.sdvxhelper.app.controller.factories.DetectionThreadFactory;
import com.sdvxhelper.app.controller.listeners.SdvxNativeKeyListener;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.network.DiscordPresenceClient;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.network.GoogleDriveClient;
import com.sdvxhelper.network.Maya2Client;
import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.service.CsvExportService;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.SdvxLoggerService;
import com.sdvxhelper.service.SummaryImageService;
import com.sdvxhelper.service.XmlExportService;
import com.sdvxhelper.util.ScoreFormatter;
import com.sdvxhelper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the main application window ({@code main.fxml}).
 *
 * <p>
 * Acts as a UI coordinator: wires services together during initialisation,
 * handles FXML action events, and implements {@link DetectionListener} to
 * receive background-thread callbacks from {@link DetectionEngine}.
 * </p>
 *
 * <p>
 * Replaces the Python {@code SDVXHelper} class in {@code sdvx_helper.pyw}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class MainController implements Initializable, DetectionListener {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // -------------------------------------------------------------------------
    // FXML-injected controls
    // -------------------------------------------------------------------------

    @FXML
    private Label obsStatusLabel;
    @FXML
    private Label totalVfLabel;
    @FXML
    private Label playCountLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label diffLabel;
    @FXML
    private Label levelLabel;
    @FXML
    private Label bestLabel;
    @FXML
    private Label lampLabel;
    @FXML
    private Label vfLabel;
    @FXML
    private Label detectModeLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button startStopButton;
    @FXML
    private Button popPlayButton;
    @FXML
    private Button f9Button;
    @FXML
    private TableView<OnePlayData> sessionLogTable;
    @FXML
    private TableColumn<OnePlayData, String> logTitleColumn;
    @FXML
    private TableColumn<OnePlayData, String> logDiffColumn;
    @FXML
    private TableColumn<OnePlayData, Integer> logScoreColumn;
    @FXML
    private TableColumn<OnePlayData, String> logLampColumn;
    @FXML
    private TableColumn<OnePlayData, String> logDateColumn;
    @FXML
    private TextArea outputArea;
    @FXML
    private ComboBox<String> languageCombo;

    // -------------------------------------------------------------------------
    // Core services (owned by this controller)
    // -------------------------------------------------------------------------

    private SdvxLoggerService loggerService;
    private SummaryImageService summaryImageService;
    private CsvExportService csvExportService;
    private XmlExportService xmlExportService;
    private DiscordPresenceClient discordPresenceClient;

    // -------------------------------------------------------------------------
    // Detection sub-system
    // -------------------------------------------------------------------------

    private DetectionEngine detectionEngine;

    // -------------------------------------------------------------------------
    // JavaFX state
    // -------------------------------------------------------------------------

    private ObservableList<OnePlayData> sessionLogData = FXCollections.observableArrayList();
    private ExecutorService executor;
    private NativeKeyListener hotkeyListener;
    private Map<String, String> settings = Collections.emptyMap();

    // -------------------------------------------------------------------------
    // Initializable
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        logDiffColumn.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        logScoreColumn.setCellValueFactory(new PropertyValueFactory<>("curScore"));
        logLampColumn.setCellValueFactory(new PropertyValueFactory<>("lamp"));
        logDateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        sessionLogTable.setItems(sessionLogData);
        sessionLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        languageCombo.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        languageCombo.setValue(LocaleManager.getInstance().getCurrentCode());
        languageCombo.setOnAction(_ -> LocaleManager.getInstance().setLocale(languageCombo.getValue()));

        executor = Executors.newSingleThreadExecutor(new DetectionThreadFactory());
        registerHotkeys();
        executor.submit(this::initialise);
    }

    // -------------------------------------------------------------------------
    // Initialisation (runs on background thread)
    // -------------------------------------------------------------------------

    private void initialise() {
        log.info("Initialising repositories and services…");

        settings = new SettingsRepository().load();
        String paramsPath = settings.getOrDefault("params_json", "resources/params.json");
        Map<String, String> params = new ParamsRepository().load(paramsPath);

        MusicListRepository musicListRepo = new MusicListRepository();
        PlayLogRepository playLogRepo = new PlayLogRepository();
        loggerService = new SdvxLoggerService(playLogRepo, musicListRepo);
        ImageAnalysisService imageAnalysisService = new ImageAnalysisService(musicListRepo);
        PerceptualHasher perceptualHasher = new PerceptualHasher();
        summaryImageService = new SummaryImageService();
        DiscordWebhookClient discordWebhookClient = new DiscordWebhookClient();
        csvExportService = new CsvExportService();
        xmlExportService = new XmlExportService();

        discordPresenceClient = buildDiscordPresenceClient();

        if ("true".equalsIgnoreCase(settings.get("get_rival_score"))) {
            downloadRivalsOnStartup();
        }

        ScreenHandler screenHandler = new ScreenHandler(imageAnalysisService, loggerService, xmlExportService,
                csvExportService, perceptualHasher, params, settings);
        ObsOverlayService obsOverlayService = new ObsOverlayService(settings);
        WebhookDispatcher webhookDispatcher = new WebhookDispatcher(discordWebhookClient, loggerService, settings);

        detectionEngine = new DetectionEngine(this, imageAnalysisService, discordPresenceClient, screenHandler,
                obsOverlayService, webhookDispatcher, params, settings);

        Platform.runLater(() -> {
            refreshVfDisplay();
            setStatus("Ready — " + loggerService.getPlayLog().getPlays().size() + " plays loaded");
            startDetection();
        });

        detectionEngine.startObsConnectRetry();
        log.info("Initialisation complete");
    }

    private DiscordPresenceClient buildDiscordPresenceClient() {
        if (!"true".equalsIgnoreCase(settings.get("discord_presence_enable"))) {
            log.debug("Discord Rich Presence disabled by setting");
            return null;
        }
        String appId = settings.getOrDefault("discord_client_id", "");
        if (appId.isBlank()) {
            log.warn("Discord Rich Presence enabled but discord_client_id is blank");
            return null;
        }
        try {
            DiscordPresenceClient client = new DiscordPresenceClient(appId);
            client.connect();
            log.info("Discord Rich Presence connected");
            return client;
        } catch (IOException e) {
            log.warn("Discord Rich Presence unavailable: {}", e.getMessage());
            return null;
        }
    }

    private void downloadRivalsOnStartup() {
        List<String> rivalNames = StringUtils.parseListSetting(settings.getOrDefault("rival_names", "[]"));
        List<String> rivalDrives = StringUtils.parseListSetting(settings.getOrDefault("rival_googledrive", "[]"));
        for (int i = 0; i < Math.min(rivalNames.size(), rivalDrives.size()); i++) {
            String driveId = rivalDrives.get(i).trim();
            if (driveId.isBlank()) {
                continue;
            }
            try {
                String csv = new GoogleDriveClient().downloadCsv(driveId);
                if (csv != null) {
                    loggerService.applyRivalCsv(csv);
                    log.info("Downloaded rival data for {}", rivalNames.get(i));
                }
            } catch (IOException e) {
                log.warn("Failed to download rival data for {}: {}", rivalNames.get(i), e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // DetectionListener — UI callbacks (called on background thread)
    // -------------------------------------------------------------------------

    @Override
    public void onPlayRecorded(OnePlayData play) {
        Platform.runLater(() -> {
            sessionLogData.add(play);
            sessionLogTable.scrollTo(play);
            refreshVfDisplay();
        });
    }

    @Override
    public void onTitleAndDiffChanged(String title, String diff) {
        Platform.runLater(() -> {
            titleLabel.setText(title);
            diffLabel.setText(diff.toUpperCase());
        });
    }

    @Override
    public void onModeChanged(DetectMode mode) {
        Platform.runLater(() -> detectModeLabel.setText(mode.name()));
    }

    @Override
    public void onObsStatusChanged(String status) {
        Platform.runLater(() -> obsStatusLabel.setText(status));
    }

    // -------------------------------------------------------------------------
    // Detection start / stop
    // -------------------------------------------------------------------------

    private void startDetection() {
        if (detectionEngine == null || detectionEngine.isRunning()) {
            log.debug("startDetection: skipped (engine={}, running={})", detectionEngine == null ? "null" : "present",
                    detectionEngine != null && detectionEngine.isRunning());
            return;
        }
        detectionEngine.start();
        startStopButton.setText("Stop Detection");
        startStopButton.getStyleClass().removeAll("button-success");
        startStopButton.getStyleClass().add("button-danger");
        setStatus("Detection running…");
        executor.submit(detectionEngine::runDetectionLoop);
    }

    private void stopDetection() {
        if (detectionEngine != null) {
            detectionEngine.stop();
        }
        startStopButton.setText("Start Detection");
        startStopButton.getStyleClass().removeAll("button-danger");
        startStopButton.getStyleClass().add("button-success");
        setStatus("Detection stopped");
    }

    // -------------------------------------------------------------------------
    // FXML action handlers
    // -------------------------------------------------------------------------

    /**
     * Toggles the detection loop on or off.
     *
     * @param event
     *            action event from the start/stop button
     */
    @FXML
    public void onStartStop(ActionEvent event) {
        if (detectionEngine != null && detectionEngine.isRunning()) {
            stopDetection();
        } else {
            startDetection();
        }
    }

    /**
     * Removes the most recently recorded play (undo).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onPopPlay(ActionEvent event) {
        if (loggerService == null) {
            log.warn("onPopPlay: loggerService not initialised, ignoring action");
            return;
        }
        executor.submit(() -> {
            try {
                OnePlayData removed = loggerService.popLastPlay();
                Platform.runLater(() -> {
                    if (removed != null) {
                        sessionLogData.remove(removed);
                        refreshVfDisplay();
                        setStatus("Removed last play: " + removed.getTitle());
                    }
                });
            } catch (IOException e) {
                log.error("Failed to undo last play", e);
                Platform.runLater(() -> setStatus("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Saves the Volforce and class-badge images to disk (F4).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSaveVolforce(ActionEvent event) {
        if (detectionEngine == null || detectionEngine.getCurrentMode() != DetectMode.RESULT
                || detectionEngine.getCurrentFrame() == null) {
            log.debug("onSaveVolforce: not on result screen (engine={}, mode={}, frame={})",
                    detectionEngine == null ? "null" : "present",
                    detectionEngine != null ? detectionEngine.getCurrentMode() : "n/a",
                    detectionEngine != null ? detectionEngine.getCurrentFrame() : "null");
            setStatus("F4: not on result screen");
            return;
        }
        executor.submit(() -> {
            detectionEngine.triggerCaptureVolforce();
            Platform.runLater(() -> setStatus("F4: Volforce saved"));
        });
    }

    /**
     * Saves a composite summary PNG of today's session plays (F5).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSaveSummary(ActionEvent event) {
        if (loggerService == null) {
            setStatus("F5: not ready");
            return;
        }
        List<OnePlayData> plays = loggerService.getTodayLog();
        String autosaveDir = settings.getOrDefault("autosave_dir", "out");
        executor.submit(() -> {
            summaryImageService.generateAndSave(plays, Path.of(autosaveDir));
            Platform.runLater(() -> setStatus("F5: Summary saved"));
        });
    }

    /**
     * Manually triggers result-screen processing on the current frame (F6).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSaveResult(ActionEvent event) {
        if (detectionEngine != null) {
            executor.submit(detectionEngine::triggerResultScreen);
        }
        setStatus("F6: Save Result triggered");
    }

    /**
     * Imports the score from the music-select screen (F7).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onImportScore(ActionEvent event) {
        if (detectionEngine == null) {
            setStatus("F7: not ready");
            return;
        }
        if (detectionEngine.getCurrentMode() == DetectMode.SELECT && detectionEngine.getCurrentFrame() != null) {
            executor.submit(detectionEngine::triggerSelectScreen);
        } else {
            setStatus("F7: not on select screen");
        }
    }

    /**
     * Updates rival score data and writes the battle XML (F8).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onUpdateRival(ActionEvent event) {
        executor.submit(() -> {
            loggerService.refreshBestAndVf();
            ObsWebSocketClient obsClient = detectionEngine != null ? detectionEngine.getObsClient() : null;
            if (obsClient != null && obsClient.isConnected()) {
                Maya2Client maya2 = buildMaya2Client();
                if (maya2 != null && maya2.isAlive()) {
                    log.debug("Maya2 alive — rival cross-reference would go here");
                }
            }
            try {
                xmlExportService.writeSdvxBattle(loggerService.getTodayLog(), new File("out/sdvx_battle.xml"));
            } catch (IOException e) {
                log.warn("Failed to write sdvx_battle.xml: {}", e.getMessage());
            }
            Platform.runLater(() -> {
                refreshVfDisplay();
                setStatus("F8: Rival updated");
            });
        });
    }

    /**
     * Toggles RTA (Real-Time Attack) mode (F9).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onStartRta(ActionEvent event) {
        if (detectionEngine == null) {
            log.warn("onStartRta: detectionEngine not initialised, ignoring action");
            return;
        }
        if (detectionEngine.isRtaMode()) {
            detectionEngine.stopRta();
            setStatus("F9: RTA stopped");
            log.info("RTA mode stopped");
            f9Button.setText("Start RTA");
        } else {
            double targetVf;
            try {
                targetVf = Double.parseDouble(settings.getOrDefault("rta_target_vf", "20.0"));
            } catch (NumberFormatException e) {
                targetVf = 20.0;
            }
            detectionEngine.startRta(targetVf);
            setStatus("F9: RTA started, target VF=" + targetVf);
            log.info("RTA mode started, target VF={}", targetVf);
            f9Button.setText("Stop RTA");
        }
    }

    /**
     * Opens the Settings dialog.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSettings(ActionEvent event) {
        try {
            URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/settings.fxml");
            if (fxmlUrl == null) {
                setStatus("settings.fxml not found");
                return;
            }
            ResourceBundle bundle = LocaleManager.getInstance().getBundle();
            FXMLLoader loader = new FXMLLoader(fxmlUrl, bundle);
            Parent root = loader.load();
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle(bundle.getString("label.settings.title"));
            dlg.getDialogPane().setContent(root);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (statusLabel.getScene() != null) {
                dlg.initOwner(statusLabel.getScene().getWindow());
            }
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(_ -> {
                ((SettingsController) loader.getController()).save();
                settings = new SettingsRepository().load();
            });
        } catch (IOException e) {
            log.error("Failed to open Settings dialog", e);
            setStatus("Error opening Settings: " + e.getMessage());
        }
    }

    /**
     * Opens the OBS Control dialog.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onObsControl(ActionEvent event) {
        try {
            URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/obs_control.fxml");
            if (fxmlUrl == null) {
                setStatus("obs_control.fxml not found");
                return;
            }
            ResourceBundle bundle = LocaleManager.getInstance().getBundle();
            FXMLLoader loader = new FXMLLoader(fxmlUrl, bundle);
            Parent root = loader.load();
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle(bundle.getString("label.obs.control.title"));
            dlg.getDialogPane().setContent(root);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (statusLabel.getScene() != null) {
                dlg.initOwner(statusLabel.getScene().getWindow());
            }
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK)
                    .ifPresent(_ -> ((ObsControlController) loader.getController()).save());
        } catch (IOException e) {
            log.error("Failed to open OBS Control dialog", e);
            setStatus("Error opening OBS Control: " + e.getMessage());
        }
    }

    /**
     * Downloads rival CSV from Google Drive and cross-references it.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onGoogleDrive(ActionEvent event) {
        executor.submit(() -> {
            Map<String, String> currentSettings = new SettingsRepository().load();
            String fileId = currentSettings.getOrDefault("my_googledrive", "").trim();
            if (fileId.isEmpty()) {
                Platform.runLater(() -> setStatus("Google Drive file ID not configured in settings"));
                return;
            }
            try {
                String csv = new GoogleDriveClient().downloadCsv(fileId);
                if (csv != null) {
                    loggerService.applyRivalCsv(csv);
                    Platform.runLater(() -> {
                        refreshVfDisplay();
                        setStatus("Rival data updated from Google Drive");
                    });
                } else {
                    Platform.runLater(() -> setStatus("Google Drive: download returned no data"));
                }
            } catch (IOException e) {
                log.warn("Google Drive download failed: {}", e.getMessage());
                Platform.runLater(() -> setStatus("Google Drive sync failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Opens the Webhooks configuration dialog.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onWebhooks(ActionEvent event) {
        try {
            URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/webhooks.fxml");
            if (fxmlUrl == null) {
                setStatus("webhooks.fxml not found");
                return;
            }
            ResourceBundle bundle = LocaleManager.getInstance().getBundle();
            FXMLLoader loader = new FXMLLoader(fxmlUrl, bundle);
            Parent root = loader.load();
            Dialog<ButtonType> dlg = new Dialog<>();
            String title = bundle.containsKey("window.webhook.title")
                    ? bundle.getString("window.webhook.title")
                    : "Webhooks";
            dlg.setTitle(title);
            dlg.getDialogPane().setContent(root);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (statusLabel.getScene() != null) {
                dlg.initOwner(statusLabel.getScene().getWindow());
            }
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK)
                    .ifPresent(_ -> ((WebhooksController) loader.getController()).save());
        } catch (IOException e) {
            log.error("Failed to open Webhooks dialog", e);
            setStatus("Error opening Webhooks: " + e.getMessage());
        }
    }

    /**
     * Exports all play log entries to a CSV file.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onExportAllCsv(ActionEvent event) {
        if (loggerService == null) {
            setStatus("Not ready");
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export All Plays CSV");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(statusLabel.getScene().getWindow());
        if (file != null) {
            final File target = file;
            executor.submit(() -> {
                try {
                    csvExportService.writeAllLogCsv(loggerService.getPlayLog().getPlays(), target);
                    Platform.runLater(() -> setStatus("Exported all plays CSV to " + target.getName()));
                } catch (IOException e) {
                    log.error("Failed to export all plays CSV", e);
                    Platform.runLater(() -> setStatus("Export failed: " + e.getMessage()));
                }
            });
        }
    }

    /**
     * Exports personal-best scores to a CSV file.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onExportBestCsv(ActionEvent event) {
        if (loggerService == null) {
            setStatus("Not ready");
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Best Scores CSV");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(statusLabel.getScene().getWindow());
        if (file != null) {
            final File target = file;
            executor.submit(() -> {
                try {
                    csvExportService.writeBestCsv(loggerService.getBestAllFumen(), target);
                    Platform.runLater(() -> setStatus("Exported best scores CSV to " + target.getName()));
                } catch (IOException e) {
                    log.error("Failed to export best scores CSV", e);
                    Platform.runLater(() -> setStatus("Export failed: " + e.getMessage()));
                }
            });
        }
    }

    /**
     * Exits the application.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onExit(ActionEvent event) {
        onWindowClose();
        Platform.exit();
    }

    // -------------------------------------------------------------------------
    // Public API (called by the app on window close / locale change)
    // -------------------------------------------------------------------------

    /**
     * Performs on-close actions: generate today summary, OBS quit event, Maya2
     * upload, play-count CSV, send playlist.
     */
    public void onWindowClose() {
        log.info("Performing on-close actions");
        if (loggerService == null) {
            log.warn("onWindowClose: loggerService not initialised, skipping on-close actions");
            return;
        }
        String autosaveDir = settings.getOrDefault("autosave_dir", "out");
        List<OnePlayData> todayPlays = loggerService.getTodayLog();
        Map<String, String> params = detectionEngine != null ? detectionEngine.getParams() : Collections.emptyMap();
        summaryImageService.generateAndSave(todayPlays, Path.of(autosaveDir), params);

        if (detectionEngine != null) {
            detectionEngine.triggerQuitSources();
        }

        saveCsvToGoogleDriveOnClose();
        uploadToMaya2OnClose();

        if (detectionEngine != null) {
            detectionEngine.sendPlaylistSummary();
        }
        if (discordPresenceClient != null) {
            discordPresenceClient.close();
        }
    }

    private void saveCsvToGoogleDriveOnClose() {
        String myGdrive = settings.getOrDefault("my_googledrive", "").trim();
        if (myGdrive.isBlank()) {
            log.debug("saveCsvToGoogleDriveOnClose: my_googledrive not configured, skipping");
            return;
        }
        try {
            csvExportService.writeBestCsv(loggerService.getBestAllFumen(), new File(myGdrive, "sdvx_helper_best.csv"));
            log.info("Best CSV saved to Google Drive path");
        } catch (IOException e) {
            log.warn("Failed to save best CSV on close: {}", e.getMessage());
        }
        try {
            csvExportService.writeAllLogCsv(loggerService.getPlayLog().getPlays(), new File(myGdrive, "playcount.csv"));
            log.info("Playcount CSV saved to Google Drive path");
        } catch (IOException e) {
            log.warn("Failed to save playcount CSV on close: {}", e.getMessage());
        }
    }

    private void uploadToMaya2OnClose() {
        Maya2Client maya2 = buildMaya2Client();
        if (maya2 == null || !maya2.isAlive()) {
            log.debug("uploadToMaya2OnClose: Maya2 not configured or unreachable, skipping upload");
            return;
        }
        try {
            File tempCsv = File.createTempFile("sdvx_best_", ".csv");
            csvExportService.writeBestCsv(loggerService.getBestAllFumen(), tempCsv);
            String csvContent = java.nio.file.Files.readString(tempCsv.toPath());
            maya2.upload("/upload_best", csvContent);
            tempCsv.delete();
            log.info("Best scores uploaded to Maya2");
        } catch (IOException e) {
            log.debug("Maya2 upload failed: {}", e.getMessage());
        }
    }

    /**
     * Stops the detection loop and releases the JNativeHook global hotkey listener.
     * Called by the app before a locale-triggered scene rebuild.
     */
    public void cleanup() {
        if (detectionEngine != null) {
            detectionEngine.shutdown();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        unregisterHotkeys();
    }

    /**
     * Appends a line to the output text area (thread-safe).
     *
     * @param line
     *            log line to append
     */
    public void appendOutput(String line) {
        Platform.runLater(() -> outputArea.appendText(line + "\n"));
    }

    // -------------------------------------------------------------------------
    // Private UI helpers
    // -------------------------------------------------------------------------

    private void refreshVfDisplay() {
        if (loggerService == null) {
            log.debug("refreshVfDisplay: loggerService not yet initialised, skipping display refresh");
            return;
        }
        totalVfLabel.setText(ScoreFormatter.formatTotalVf(loggerService.getTotalVfInt()));
        playCountLabel.setText(String.valueOf(loggerService.getPlayLog().getPlays().size()));
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }

    // -------------------------------------------------------------------------
    // Hotkeys
    // -------------------------------------------------------------------------

    private void registerHotkeys() {
        try {
            // JNativeHook's default event-dispatch thread is a non-daemon thread.
            // Replacing it with a daemon-thread executor ensures the JVM can exit
            // naturally when the window is closed, without waiting for JNativeHook
            // to fully finish its internal teardown.
            ThreadFactory daemonFactory = r -> {
                Thread t = new Thread(r, "jnativehook-dispatch");
                t.setDaemon(true);
                return t;
            };
            GlobalScreen.setEventDispatcher(Executors.newSingleThreadExecutor(daemonFactory));
            GlobalScreen.registerNativeHook();
            Map<Integer, Runnable> keyActions = new HashMap<>();
            keyActions.put(NativeKeyEvent.VC_F4, () -> Platform.runLater(() -> onSaveVolforce(null)));
            keyActions.put(NativeKeyEvent.VC_F5, () -> Platform.runLater(() -> onSaveSummary(null)));
            keyActions.put(NativeKeyEvent.VC_F6, () -> Platform.runLater(() -> onSaveResult(null)));
            keyActions.put(NativeKeyEvent.VC_F7, () -> Platform.runLater(() -> onImportScore(null)));
            keyActions.put(NativeKeyEvent.VC_F8, () -> Platform.runLater(() -> onUpdateRival(null)));
            keyActions.put(NativeKeyEvent.VC_F9, () -> Platform.runLater(() -> onStartRta(null)));
            hotkeyListener = new SdvxNativeKeyListener(keyActions);
            GlobalScreen.addNativeKeyListener(hotkeyListener);
            log.info("Global hotkeys F4-F9 registered");
        } catch (NativeHookException e) {
            log.warn("Could not register global hotkeys (JNativeHook): {}", e.getMessage());
        }
    }

    private void unregisterHotkeys() {
        if (hotkeyListener != null) {
            GlobalScreen.removeNativeKeyListener(hotkeyListener);
            hotkeyListener = null;
        }
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            log.debug("Error unregistering native hook", e);
        }
    }

    // -------------------------------------------------------------------------
    // Miscellaneous private helpers
    // -------------------------------------------------------------------------

    private Maya2Client buildMaya2Client() {
        String url = settings.getOrDefault("maya2_url", "");
        String key = settings.getOrDefault("maya2_key", "");
        if (url.isBlank() || key.isBlank()) {
            log.debug("buildMaya2Client: maya2_url or maya2_key not configured (url blank={}, key blank={})",
                    url.isBlank(), key.isBlank());
            return null;
        }
        return new Maya2Client(url, key);
    }
}
