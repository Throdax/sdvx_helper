package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.sdvxhelper.app.controller.detection.DetectionEngine;
import com.sdvxhelper.app.controller.detection.DetectionListener;
import com.sdvxhelper.app.controller.detection.ObsOverlayService;
import com.sdvxhelper.app.controller.detection.ScreenHandler;
import com.sdvxhelper.app.controller.detection.WebhookDispatcher;
import com.sdvxhelper.app.controller.factories.DetectionThreadFactory;
import com.sdvxhelper.app.controller.listeners.GlobalHotkeyService;
import com.sdvxhelper.config.SecretConfig;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.WebhookConfig;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.network.DiscordPresenceClient;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.network.GoogleDriveClient;
import com.sdvxhelper.network.Maya2Client;
import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.TesseractLanguageInstaller;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.repository.WebhookConfigRepository;
import com.sdvxhelper.service.CsvExportService;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.SdvxPlayLogService;
import com.sdvxhelper.service.SummaryGeneratorService;
import com.sdvxhelper.service.XmlExportService;
import com.sdvxhelper.util.LampFormatter;
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
public class SDVXHelperController implements Initializable, DetectionListener {

    private static final Logger log = LoggerFactory.getLogger(SDVXHelperController.class);

    // -------------------------------------------------------------------------
    // FXML-injected controls
    // -------------------------------------------------------------------------

    @FXML
    private Label obsStatusLabel;
    @FXML
    private Label obsRecordingLabel;
    @FXML
    private Label obsStreamingLabel;
    @FXML
    private Label totalVfLabel;
    @FXML
    private Label playCountLabel;
    @FXML
    private Label detectModeLabel;
    @FXML
    private Label captureScreenshotIcon;
    @FXML
    private Label captureSummaryIcon;
    @FXML
    private Label captureVfIcon;
    @FXML
    private Label statusLabel;
    @FXML
    private Button f9Button;
    @FXML
    private Label sessionLogLabel;
    @FXML
    private TableView<OnePlayData> sessionLogTable;
    @FXML
    private TableColumn<OnePlayData, String> logTitleColumn;
    @FXML
    private TableColumn<OnePlayData, String> logDiffColumn;
    @FXML
    private TableColumn<OnePlayData, String> logScoreColumn;
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

    private SdvxPlayLogService loggerService;
    private SummaryGeneratorService summaryGeneratorService;
    private CsvExportService csvExportService;
    private XmlExportService xmlExportService;
    private DiscordPresenceClient discordPresenceClient;
    private SecretConfig secretConfig;

    // -------------------------------------------------------------------------
    // Detection sub-system
    // -------------------------------------------------------------------------

    private DetectionEngine detectionEngine;

    // -------------------------------------------------------------------------
    // JavaFX state
    // -------------------------------------------------------------------------

    private ObservableList<OnePlayData> sessionLogData = FXCollections.observableArrayList();
    private ExecutorService executor;
    private GlobalHotkeyService hotkeyService;
    private Map<String, String> settings = Collections.emptyMap();
    private boolean windowCloseDone = false;
    private volatile DetectMode initialDetectMode = DetectMode.INIT;

    // -------------------------------------------------------------------------
    // Initializable
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        DateTimeFormatter logDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        logTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        logDiffColumn.setCellValueFactory(cell -> {
            String diff = cell.getValue().getDifficulty();
            return new SimpleStringProperty(diff != null ? diff.toUpperCase() : "");
        });
        logScoreColumn.setCellValueFactory(
                cell -> new SimpleStringProperty(ScoreFormatter.formatScore(cell.getValue().getCurScore())));
        logLampColumn.setCellValueFactory(cell -> {
            String lamp = cell.getValue().getLamp();
            return new SimpleStringProperty(LampFormatter.formatDisplay(lamp));
        });
        logDateColumn.setCellValueFactory(cell -> {
            LocalDateTime date = cell.getValue().getDate();
            return new SimpleStringProperty(date != null ? date.format(logDateFormatter) : "");
        });
        sessionLogTable.setItems(sessionLogData);
        sessionLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionLogData
                .addListener((javafx.collections.ListChangeListener<OnePlayData>) change -> updateSessionLogLabel());
        updateSessionLogLabel();

        languageCombo.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        languageCombo.setValue(LocaleManager.getInstance().getCurrentCode());
        languageCombo.setOnAction(_ -> LocaleManager.getInstance().setLocale(languageCombo.getValue()));

        executor = Executors.newSingleThreadExecutor(new DetectionThreadFactory());

        hotkeyService = new GlobalHotkeyService();
        Map<Integer, Runnable> hotkeys = new LinkedHashMap<>();
        hotkeys.put(GlobalHotkeyService.VK_F4, () -> onSaveVolforce(null));
        hotkeys.put(GlobalHotkeyService.VK_F5, () -> onSaveSummary(null));
        hotkeys.put(GlobalHotkeyService.VK_F6, () -> onSaveResult(null));
        hotkeys.put(GlobalHotkeyService.VK_F7, () -> onImportScore(null));
        hotkeys.put(GlobalHotkeyService.VK_F8, () -> onUpdateRival(null));
        hotkeys.put(GlobalHotkeyService.VK_F9, () -> onStartRta(null));
        hotkeyService.start(hotkeys);

        executor.submit(this::initialise);
    }

    // -------------------------------------------------------------------------
    // Tessdata setup
    // -------------------------------------------------------------------------

    private void installTesseractLanguages() {
        String tessdataDir = System.getProperty("TESSDATA_PREFIX", "resources/tessdata");
        TesseractLanguageInstaller.ensureLanguages(List.of("jpn", "eng", "deu"), tessdataDir);
    }

    // -------------------------------------------------------------------------
    // Initialisation (runs on background thread)
    // -------------------------------------------------------------------------

    private void initialise() {
        log.info("Initialising repositories and services...");
        installTesseractLanguages();

        settings = new SettingsRepository().load();
        String paramsPath = settings.getOrDefault("params_json", "resources/params.json");
        Map<String, String> params = new ParamsRepository().load(paramsPath);

        MusicListRepository musicListRepo = new MusicListRepository();
        if ("true".equalsIgnoreCase(settings.get("autoload_musiclist"))) {
            downloadMusicListOnStartup(params, musicListRepo);
        }

        PlayLogRepository playLogRepo = new PlayLogRepository();
        loggerService = new SdvxPlayLogService(playLogRepo, musicListRepo);
        ImageAnalysisService imageAnalysisService = new ImageAnalysisService(musicListRepo);
        PerceptualHasher perceptualHasher = new PerceptualHasher();
        DiscordWebhookClient discordWebhookClient = new DiscordWebhookClient();
        csvExportService = new CsvExportService();
        xmlExportService = new XmlExportService();

        secretConfig = new SecretConfig();
        discordPresenceClient = buildDiscordPresenceClient();

        if ("true".equalsIgnoreCase(settings.get("get_rival_score"))) {
            downloadRivalsOnStartup();
        }

        summaryGeneratorService = new SummaryGeneratorService(imageAnalysisService);
        ScreenHandler screenHandler = new ScreenHandler(imageAnalysisService, loggerService, xmlExportService,
                csvExportService, summaryGeneratorService, perceptualHasher, params, settings);

        String autosaveDir = settings.getOrDefault("autosave_dir", "out");
        String resourcesDir = settings.getOrDefault("resources_dir", "resources");
        int logpicOffsetHours = Integer.parseInt(settings.getOrDefault("logpic_offset_time", "2"));
        log.info("Generating startup summary overlays from '{}' ({}h window)", autosaveDir, logpicOffsetHours);
        List<OnePlayData> preloadedPlays = summaryGeneratorService.generateFromResultsDir(autosaveDir,
                logpicOffsetHours, params, settings, resourcesDir);
        screenHandler.addPreloadedPlays(preloadedPlays);

        ObsOverlayService obsOverlayService = new ObsOverlayService(settings);

        WebhookConfigRepository webhookRepo = new WebhookConfigRepository();
        boolean migrated = webhookRepo.migrateFromLegacySettings(settings);
        if (migrated) {
            try {
                new SettingsRepository().save(settings);
                log.info("Legacy webhook keys removed from settings.json after migration");
            } catch (IOException e) {
                log.warn("Could not persist cleaned settings.json after webhook migration: {}", e.getMessage());
            }
        }
        List<WebhookConfig> webhookConfigs = webhookRepo.load();
        log.info("Loaded {} webhook configuration(s)", webhookConfigs.size());

        WebhookDispatcher webhookDispatcher = new WebhookDispatcher(discordWebhookClient, loggerService, settings,
                webhookConfigs);

        detectionEngine = DetectionEngine.builder().listener(this).imageAnalysisService(imageAnalysisService)
                .discordPresenceClient(discordPresenceClient).screenHandler(screenHandler)
                .obsOverlayService(obsOverlayService).webhookDispatcher(webhookDispatcher).params(params)
                .settings(settings).initialMode(initialDetectMode).build();

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
        String appId = secretConfig.getDiscordClientId();
        if (appId.isBlank()) {
            log.warn("Discord Rich Presence enabled but discord.client.id is blank in secrets.properties");
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

    /**
     * Updates the session log section heading to show the current play count in the
     * format "Session Log: N plays".
     */
    private void updateSessionLogLabel() {
        ResourceBundle bundle = LocaleManager.getInstance().getBundle();
        String pattern = bundle.getString("label.main.session.log.count");
        sessionLogLabel.setText(MessageFormat.format(pattern, sessionLogData.size()));
    }

    /**
     * Downloads the latest {@code musiclist.xml} from the URL configured in
     * {@code params["url_musiclist_xml"]} and saves it to
     * {@code resources/musiclist.xml}, then reloads the repository.
     *
     * <p>
     * Mirrors Python's {@code update_musiclist()} at line 259 of
     * {@code sdvx_helper.pyw}.
     * </p>
     *
     * @param params
     *            loaded params map
     * @param musicListRepo
     *            repository to reload after download
     */
    private void downloadMusicListOnStartup(Map<String, String> params, MusicListRepository musicListRepo) {
        String url = params.get("url_musiclist_xml");
        if (url == null || url.isBlank()) {
            log.warn("autoload_musiclist: url_musiclist_xml not set in params.json, skipping");
            return;
        }
        log.info("autoload_musiclist: downloading musiclist.xml from {}", url);
        try {
            java.net.URI uri = java.net.URI.create(url);
            byte[] data = uri.toURL().openStream().readAllBytes();
            java.io.File destDir = new java.io.File("resources");
            destDir.mkdirs();
            java.io.File destFile = new java.io.File(destDir, "musiclist.xml");
            java.nio.file.Files.write(destFile.toPath(), data);
            musicListRepo.load();
            log.info("autoload_musiclist: musiclist.xml updated ({} bytes)", data.length);
        } catch (IOException e) {
            log.warn("autoload_musiclist: failed to download musiclist.xml: {}", e.getMessage());
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
            sessionLogData.add(0, play);
            sessionLogTable.scrollTo(play);
            refreshVfDisplay();
            if (play.getScreenshotFile() != null && !play.getScreenshotFile().isBlank()) {
                ResourceBundle bundle = LocaleManager.getInstance().getBundle();
                String msg = bundle.getString("message.screenshot.saved") + " -> " + play.getScreenshotFile();
                outputArea.appendText(msg + "\n");
            }
        });
    }

    @Override
    public void onModeChanged(DetectMode mode) {
        Platform.runLater(() -> {
            detectModeLabel.setText(mode.name());
            if (mode != DetectMode.RESULT) {
                hideCaptureIndicators();
            }
        });
    }

    @Override
    public void onObsStatusChanged(String status) {
        boolean connected = "connected".equals(status);
        ResourceBundle bundle = LocaleManager.getInstance().getBundle();
        String text = connected
                ? bundle.getString("label.obs.control.connected")
                : bundle.getString("label.main.obs.disconnected");
        Platform.runLater(() -> {
            obsStatusLabel.setText(text);
            obsStatusLabel.getStyleClass().removeAll("obs-connected", "obs-disconnected");
            obsStatusLabel.getStyleClass().add(connected ? "obs-connected" : "obs-disconnected");
        });
    }

    @Override
    public void onObsOutputStarted(String outputType) {
        Platform.runLater(() -> showObsOutputLabel(outputType));
    }

    @Override
    public void onObsOutputStopped(String outputType) {
        Platform.runLater(() -> hideObsOutputLabel(outputType));
    }

    @Override
    public void onResultCaptured(boolean screenshotSaved, boolean summaryGenerated, boolean vfCaptured) {
        Platform.runLater(() -> {
            showCaptureIndicator(captureScreenshotIcon, screenshotSaved);
            showCaptureIndicator(captureSummaryIcon, summaryGenerated);
            showCaptureIndicator(captureVfIcon, vfCaptured);
        });
    }

    private void showCaptureIndicator(Label icon, boolean visible) {
        if (icon == null) {
            return;
        }
        icon.setVisible(visible);
        icon.setManaged(visible);
    }

    private void hideCaptureIndicators() {
        showCaptureIndicator(captureScreenshotIcon, false);
        showCaptureIndicator(captureSummaryIcon, false);
        showCaptureIndicator(captureVfIcon, false);
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
        setStatus("Detection running…");
        executor.submit(detectionEngine::runDetectionLoop);
    }

    // -------------------------------------------------------------------------
    // FXML action handlers
    // -------------------------------------------------------------------------

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
     * Regenerates the OBS overlay summary images ({@code summary_full.png} and
     * {@code summary_small.png}) from all current plays (F5).
     *
     * <p>
     * Mirrors Python {@code capture_summary_btn} which calls
     * {@code gen_summary.generate()}.
     * </p>
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSaveSummary(ActionEvent event) {
        if (detectionEngine == null) {
            setStatus("F5: not ready");
            return;
        }
        executor.submit(() -> {
            boolean generated = detectionEngine.triggerSaveSummary();
            Platform.runLater(() -> setStatus(generated ? "F5: Summary updated" : "F5: Summary update failed"));
        });
    }

    /**
     * Manually triggers result-screen processing on the current frame (F6).
     *
     * <p>
     * Requires the detection engine to be in RESULT mode (i.e. the game is on the
     * result screen). If not, the user is informed via the status bar.
     * </p>
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSaveResult(ActionEvent event) {
        if (detectionEngine == null) {
            setStatus("F6: not ready");
            return;
        }
        if (detectionEngine.getCurrentMode() != DetectMode.RESULT) {
            setStatus("F6: not on result screen (mode=" + detectionEngine.getCurrentMode() + ")");
            return;
        }
        executor.submit(() -> {
            detectionEngine.triggerResultScreen();
            Platform.runLater(() -> setStatus("F6: Result screen processing done"));
        });
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
                    log.debug("Maya2 alive - rival cross-reference would go here");
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
            SettingsController ctrl = loader.getController();
            ctrl.setGenerateJacketsAction(this::handleGenerateJackets);
            ctrl.setProcessPastResultsAction(this::handleProcessPastResults);
            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle(bundle.getString("menu.file.settings"));
            dlg.getDialogPane().setContent(root);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (statusLabel.getScene() != null) {
                dlg.initOwner(statusLabel.getScene().getWindow());
            }
            dlg.setOnShown(_ -> Platform.runLater(() -> clampToScreen(dlg)));
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(_ -> {
                ctrl.save();
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
            dlg.setTitle(bundle.getString("menu.file.obs"));
            dlg.getDialogPane().setContent(root);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (statusLabel.getScene() != null) {
                dlg.initOwner(statusLabel.getScene().getWindow());
            }
            dlg.setOnShown(_ -> Platform.runLater(() -> clampToScreen(dlg)));
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
            dlg.setOnShown(_ -> Platform.runLater(() -> clampToScreen(dlg)));
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
     *
     * <p>
     * This method is idempotent — subsequent calls after the first are silently
     * ignored. This allows both the Exit button handler and
     * {@link com.sdvxhelper.app.SdvxHelperApp#stop()} to call it without
     * duplicating work.
     * </p>
     */
    public void onWindowClose() {
        if (windowCloseDone) {
            log.debug("onWindowClose: already executed, skipping");
            return;
        }
        windowCloseDone = true;
        log.info("Performing on-close actions");
        if (loggerService == null) {
            log.warn("onWindowClose: loggerService not initialised, skipping on-close actions");
            return;
        }
        String autosaveDir = settings.getOrDefault("autosave_dir", "out");
        String summaryFilename = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_summary.png";
        File summaryFile = Path.of(autosaveDir).resolve(summaryFilename).toFile();
        File summarySource = new File("out", "summary_full.png");
        if (sessionLogData.isEmpty()) {
            log.info("Session summary copy skipped: no plays were recorded this session.");
        } else if (summarySource.exists()) {
            try {
                summaryFile.getParentFile().mkdirs();
                Files.copy(summarySource.toPath(), summaryFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Session summary copied from {} to {}", summarySource.getAbsolutePath(),
                        summaryFile.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to copy session summary: {}", e.getMessage());
            }
        } else {
            log.warn("Session summary source not found: {}, skipping save", summarySource.getAbsolutePath());
        }

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
     * Returns the current detection engine instance, or {@code null} if
     * initialisation has not yet completed. Used by
     * {@link com.sdvxhelper.app.SdvxHelperApp} to capture the active mode before a
     * locale-triggered scene rebuild so it can be forwarded to the replacement
     * controller.
     *
     * @return the detection engine, or {@code null}
     */
    public DetectionEngine getDetectionEngine() {
        return detectionEngine;
    }

    /**
     * Sets the detection mode that the new engine should start in. Must be called
     * before the background {@code initialise()} job reads the field. Used by
     * {@link com.sdvxhelper.app.SdvxHelperApp} during a locale-triggered scene
     * rebuild to prevent the replacement engine from falsely re-triggering a screen
     * transition for a screen that was already processed before the switch.
     *
     * @param mode
     *            the mode to start in
     */
    public void setInitialDetectMode(DetectMode mode) {
        initialDetectMode = mode;
    }

    /**
     * Returns a snapshot of the current session log entries so they can be restored
     * after a locale-triggered scene rebuild.
     *
     * @return immutable copy of the session log items
     */
    public List<OnePlayData> getSessionLogSnapshot() {
        return List.copyOf(sessionLogData);
    }

    /**
     * Returns the current text of the output log area so it can be restored after a
     * locale-triggered scene rebuild.
     *
     * @return output area text
     */
    public String getOutputText() {
        return outputArea.getText();
    }

    /**
     * Restores session log rows and output log text that were captured before a
     * locale-triggered scene rebuild. Must be called on the JavaFX application
     * thread.
     *
     * @param plays
     *            session log entries to restore
     * @param outputText
     *            output area text to restore
     */
    public void restoreSessionData(List<OnePlayData> plays, String outputText) {
        sessionLogData.setAll(plays);
        outputArea.setText(outputText);
    }

    /**
     * Stops the detection loop, unregisters global hotkeys, and shuts down the
     * executor. Called by the app on window close and before a locale-triggered
     * scene rebuild.
     */
    public void cleanup() {
        if (detectionEngine != null) {
            detectionEngine.shutdown();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (hotkeyService != null) {
            hotkeyService.stop();
        }
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

    private void showObsOutputLabel(String outputType) {
        if ("Recording".equals(outputType)) {
            obsRecordingLabel.setVisible(true);
            obsRecordingLabel.setManaged(true);
        } else if ("Streaming".equals(outputType)) {
            obsStreamingLabel.setVisible(true);
            obsStreamingLabel.setManaged(true);
        }
    }

    private void hideObsOutputLabel(String outputType) {
        if ("Recording".equals(outputType)) {
            obsRecordingLabel.setVisible(false);
            obsRecordingLabel.setManaged(false);
        } else if ("Streaming".equals(outputType)) {
            obsStreamingLabel.setVisible(false);
            obsStreamingLabel.setManaged(false);
        }
    }

    /**
     * Clamps a dialog window so that it is fully visible on the same screen as its
     * owner window. The owner's position is used to find the target screen because
     * the dialog may already be partially or entirely off every screen when this is
     * called, making its own coordinates unreliable for screen detection.
     *
     * <p>
     * Must be invoked via {@code Platform.runLater} inside an {@code onShown}
     * handler so that the dialog's width and height are fully committed by the
     * layout pass.
     * </p>
     *
     * @param dlg
     *            the dialog to reposition if necessary
     */
    private static void clampToScreen(Dialog<?> dlg) {
        Window window = dlg.getDialogPane().getScene().getWindow();
        Window owner = (window instanceof Stage s) ? s.getOwner() : null;

        // Prefer the owner's screen; fall back to the dialog's own position,
        // then to the primary screen if both are off every monitor.
        Screen screen;
        if (owner != null) {
            ObservableList<Screen> ownerScreens = Screen.getScreensForRectangle(owner.getX(), owner.getY(),
                    owner.getWidth(), owner.getHeight());
            screen = ownerScreens.isEmpty() ? Screen.getPrimary() : ownerScreens.get(0);
        } else {
            ObservableList<Screen> dlgScreens = Screen.getScreensForRectangle(window.getX(), window.getY(),
                    window.getWidth(), window.getHeight());
            screen = dlgScreens.isEmpty() ? Screen.getPrimary() : dlgScreens.get(0);
        }

        Rectangle2D bounds = screen.getVisualBounds();
        double w = window.getWidth();
        double h = window.getHeight();
        double newX = Math.max(bounds.getMinX(), Math.min(window.getX(), bounds.getMaxX() - w));
        double newY = Math.max(bounds.getMinY(), Math.min(window.getY(), bounds.getMaxY() - h));
        window.setX(newX);
        window.setY(newY);
    }

    // -------------------------------------------------------------------------
    // Settings dialog callbacks
    // -------------------------------------------------------------------------

    private void handleGenerateJackets() {
        // TODO: iterate autosave_dir result images, extract jacket crop via
        // ImageAnalysisService,
        // hash with PerceptualHasher, save to jackets/{hash}.png — mirrors Python
        // gen_jacket_imgs()
        log.warn("Generate jackets from result images: not yet implemented");
    }

    private void handleProcessPastResults() {
        // TODO: iterate autosave_dir result images, OCR each into OnePlayData,
        // push via loggerService.pushPlay() — mirrors Python import_from_resultimg()
        log.warn("Process past result images into play log: not yet implemented");
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
