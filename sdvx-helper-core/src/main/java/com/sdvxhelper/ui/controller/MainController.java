package com.sdvxhelper.ui.controller;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.repository.SpecialTitlesRepository;
import com.sdvxhelper.service.SdvxLoggerService;
import com.sdvxhelper.service.TitleNormalizer;
import com.sdvxhelper.util.ScoreFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the main application window ({@code main.fxml}).
 *
 * <p>Orchestrates the detection loop, updates the UI with the current song info,
 * and handles user actions (start/stop detection, undo last play).
 *
 * <p>Replaces the Python {@code SDVXHelper} class in {@code sdvx_helper.pyw}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // -------------------------------------------------------------------------
    // FXML-injected controls
    // -------------------------------------------------------------------------

    @FXML private Label lblObsStatus;
    @FXML private Label lblTotalVf;
    @FXML private Label lblPlayCount;
    @FXML private Label lblTitle;
    @FXML private Label lblDiff;
    @FXML private Label lblLevel;
    @FXML private Label lblBest;
    @FXML private Label lblLamp;
    @FXML private Label lblVf;
    @FXML private Label lblDetectMode;
    @FXML private Label lblStatus;
    @FXML private Button btnStartStop;
    @FXML private Button btnPopPlay;
    @FXML private TableView<OnePlayData> tblSessionLog;
    @FXML private TableColumn<OnePlayData, String> colLogTitle;
    @FXML private TableColumn<OnePlayData, String> colLogDiff;
    @FXML private TableColumn<OnePlayData, Integer> colLogScore;
    @FXML private TableColumn<OnePlayData, String> colLogLamp;
    @FXML private TableColumn<OnePlayData, String> colLogDate;
    @FXML private TextArea txtOutput;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private SdvxLoggerService loggerService;
    private Map<String, Object> settings;
    private final ObservableList<OnePlayData> sessionLogData = FXCollections.observableArrayList();
    private volatile boolean detectionRunning = false;
    private ExecutorService executor;

    // -------------------------------------------------------------------------
    // Initializable
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Wire up table columns
        colLogTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colLogDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        colLogScore.setCellValueFactory(new PropertyValueFactory<>("curScore"));
        colLogLamp.setCellValueFactory(new PropertyValueFactory<>("lamp"));
        colLogDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        tblSessionLog.setItems(sessionLogData);

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "detection-loop");
            t.setDaemon(true);
            return t;
        });

        // Load repositories and services in background
        executor.submit(this::initialise);
    }

    // -------------------------------------------------------------------------
    // FXML action handlers
    // -------------------------------------------------------------------------

    /**
     * Toggles the detection loop on or off.
     *
     * @param event action event from the start/stop button
     */
    @FXML
    public void onStartStop(ActionEvent event) {
        if (detectionRunning) {
            detectionRunning = false;
            Platform.runLater(() -> {
                btnStartStop.setText("Start Detection");
                btnStartStop.setStyle("-fx-background-color: #238636; -fx-text-fill: white;");
                setStatus("Detection stopped");
            });
        } else {
            detectionRunning = true;
            Platform.runLater(() -> {
                btnStartStop.setText("Stop Detection");
                btnStartStop.setStyle("-fx-background-color: #b62324; -fx-text-fill: white;");
                setStatus("Detection running…");
            });
            executor.submit(this::runDetectionLoop);
        }
    }

    /**
     * Removes the most recently recorded play (undo).
     *
     * @param event action event
     */
    @FXML
    public void onPopPlay(ActionEvent event) {
        if (loggerService == null) return;
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

    @FXML public void onSettings(ActionEvent event)     { setStatus("Settings (TODO)"); }
    @FXML public void onObsControl(ActionEvent event)   { setStatus("OBS Control (TODO)"); }
    @FXML public void onGoogleDrive(ActionEvent event)  { setStatus("Google Drive (TODO)"); }
    @FXML public void onWebhooks(ActionEvent event)     { setStatus("Webhooks (TODO)"); }
    @FXML public void onExit(ActionEvent event)         { Platform.exit(); }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void initialise() {
        log.info("Initialising repositories and services…");
        SettingsRepository settingsRepo = new SettingsRepository();
        settings = settingsRepo.load();

        PlayLogRepository playLogRepo = new PlayLogRepository();
        MusicListRepository musicListRepo = new MusicListRepository();
        loggerService = new SdvxLoggerService(playLogRepo, musicListRepo);

        Platform.runLater(() -> {
            refreshVfDisplay();
            setStatus("Ready — " + loggerService.getPlayLog().getPlays().size() + " plays loaded");
        });
        log.info("Initialisation complete");
    }

    private void runDetectionLoop() {
        log.info("Detection loop started");
        while (detectionRunning) {
            // TODO: Capture OBS frame, run ImageAnalysisService, push plays
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Detection loop stopped");
    }

    private void refreshVfDisplay() {
        if (loggerService == null) return;
        lblTotalVf.setText(ScoreFormatter.formatTotalVf(loggerService.getTotalVfInt()));
        lblPlayCount.setText(String.valueOf(loggerService.getPlayLog().getPlays().size()));
    }

    private void setStatus(String msg) {
        lblStatus.setText(msg);
    }

    /**
     * Called by the detection loop (on background thread) when a new song is detected.
     * Marshals the UI update to the JavaFX application thread.
     *
     * @param info best-score info for the detected song/chart
     */
    public void onSongDetected(MusicInfo info) {
        Platform.runLater(() -> {
            lblTitle.setText(info.getTitle());
            lblDiff.setText(info.getDifficulty().toUpperCase());
            lblLevel.setText(ScoreFormatter.formatLevel(info.getLvAsInt()));
            lblBest.setText(ScoreFormatter.formatScore(info.getBestScore()));
            lblLamp.setText(info.getBestLamp());
            lblVf.setText(ScoreFormatter.formatVf(info.getVf()));
        });
    }

    /**
     * Called by the detection loop when the game state changes.
     *
     * @param mode new detection mode
     */
    public void onDetectModeChanged(DetectMode mode) {
        Platform.runLater(() -> lblDetectMode.setText(mode.name()));
    }

    /**
     * Called when a new play is recorded.
     *
     * @param play newly recorded play
     */
    public void onPlayRecorded(OnePlayData play) {
        Platform.runLater(() -> {
            sessionLogData.add(play);
            tblSessionLog.scrollTo(play);
            refreshVfDisplay();
        });
    }

    /**
     * Appends a line to the output text area (thread-safe).
     *
     * @param line log line to append
     */
    public void appendOutput(String line) {
        Platform.runLater(() -> {
            txtOutput.appendText(line + "\n");
        });
    }
}
