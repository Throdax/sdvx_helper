package com.sdvxhelper.ui.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.service.SdvxLoggerService;
import com.sdvxhelper.util.ScoreFormatter;

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

/**
 * Controller for the main application window ({@code main.fxml}).
 *
 * <p>
 * Orchestrates the detection loop, updates the UI with the current song info,
 * and handles user actions (start/stop detection, undo last play, function
 * hotkeys).
 *
 * <p>
 * Replaces the Python {@code SDVXHelper} class in {@code sdvx_helper.pyw}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // -------------------------------------------------------------------------
    // FXML-injected controls
    // -------------------------------------------------------------------------

    @FXML
    private Label lblObsStatus;
    @FXML
    private Label lblTotalVf;
    @FXML
    private Label lblPlayCount;
    @FXML
    private Label lblTitle;
    @FXML
    private Label lblDiff;
    @FXML
    private Label lblLevel;
    @FXML
    private Label lblBest;
    @FXML
    private Label lblLamp;
    @FXML
    private Label lblVf;
    @FXML
    private Label lblDetectMode;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnStartStop;
    @FXML
    private Button btnPopPlay;
    @FXML
    private Button btnF9;
    @FXML
    private TableView<OnePlayData> tblSessionLog;
    @FXML
    private TableColumn<OnePlayData, String> colLogTitle;
    @FXML
    private TableColumn<OnePlayData, String> colLogDiff;
    @FXML
    private TableColumn<OnePlayData, Integer> colLogScore;
    @FXML
    private TableColumn<OnePlayData, String> colLogLamp;
    @FXML
    private TableColumn<OnePlayData, String> colLogDate;
    @FXML
    private TextArea txtOutput;
    @FXML
    private ComboBox<String> cmbLanguage;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private SdvxLoggerService loggerService;
    private final ObservableList<OnePlayData> sessionLogData = FXCollections.observableArrayList();
    private volatile boolean detectionRunning = false;
    private ExecutorService executor;
    private NativeKeyListener hotkeyListener;

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
        tblSessionLog.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // F9 button label (no image, text only)
        if (btnF9 != null) {
            btnF9.setText("F9");
        }

        // Language combo
        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(_ -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "detection-loop");
            t.setDaemon(true);
            return t;
        });

        registerHotkeys();

        // Load repositories and services in background, then auto-start detection
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
            stopDetection();
        } else {
            startDetection();
        }
    }

    /**
     * Removes the most recently recorded play (undo).
     *
     * @param event action event
     */
    @FXML
    public void onPopPlay(ActionEvent event) {
        if (loggerService == null) {
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
     * Triggers saving the current Volforce to disk (F4). 
     * 
     * @param event action event from the Save Volforce menu item or F4 hotkey
     */
    @FXML
    public void onSaveVolforce(ActionEvent event) {
        setStatus("F4: Save Volforce (TODO)");
        //TODO: Implement Volforce saving (requires file I/O, user prompts, etc.)
        log.info("Save Volforce triggered");
    }

    /** 
     * Saves a summary image of the current session to disk (F5). 
     * 
     * @param event action event from the Save Summary menu item or F5 hotkey
     */
    @FXML
    public void onSaveSummary(ActionEvent event) {
        setStatus("F5: Save Summary (TODO)");
        //TODO: Implement summary image generation (requires JavaFX snapshot, image processing, file I/O, etc.)
        log.info("Save Summary triggered");
    }

    /** 
     * Saves a screenshot of the result screen to disk (F6). 
     * 
     * @param event action event from the Save Result menu item or F6 hotkey
     */
    @FXML
    public void onSaveResult(ActionEvent event) {
        setStatus("F6: Save Result (TODO)");
        //TODO: Implement result screen capture (requires OBS API integration, image processing, file I/O, etc.)
        log.info("Save Result triggered");
    }

    /** 
     * Imports a score from the music selection screen (F7). 
     * 
     * @param event action event from the Import Score menu item or F7 hotkey
     */
    @FXML
    public void onImportScore(ActionEvent event) {
        setStatus("F7: Import Score (TODO)");
        //TODO: Implement score importing (requires server API, parsing, UI elements, etc.)
        log.info("Import Score triggered");
    }

    /** 
     * Updates the rival score data from the server (F8). 
     * 
     * @param event action event from the Update Rival menu item or F8 hotkey
     */
    @FXML
    public void onUpdateRival(ActionEvent event) {
        setStatus("F8: Update Rival (TODO)");
        //TODO: Implement rival score fetching and display (requires server API, parsing, UI elements, etc.)
        log.info("Update Rival triggered");
    }

    /** 
     * Starts RTA (Real-Time Attack) mode (F9). 
     * 
     * @param event action event from the RTA menu item or F9 hotkey
     */
    @FXML
    public void onStartRta(ActionEvent event) {
        setStatus("F9: Start RTA Mode (TODO)");
        //TODO: Implement RTA mode (auto-start detection on game launch, auto-stop on exit, session timer, etc.)
        log.info("Start RTA triggered");
    }

    /** 
     * Opens the Settings dialog. 
     * 
     * @param event action event from the Settings menu item
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
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(_ -> ((SettingsController) loader.getController()).save());
        } catch (IOException e) {
            log.error("Failed to open Settings dialog", e);
            setStatus("Error opening Settings: " + e.getMessage());
        }
    }

   /** 
    * Opens the OBS Control dialog. 
    * 
    * @param event action event from the OBS Control menu item
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
            dlg.showAndWait().filter(bt -> bt == ButtonType.OK).ifPresent(_ -> ((ObsControlController) loader.getController()).save());
        } catch (IOException e) {
            log.error("Failed to open OBS Control dialog", e);
            setStatus("Error opening OBS Control: " + e.getMessage());
        }
    }

    /**
     * Placeholder for Google Drive integration (exporting logs, uploading screenshots, etc.).
     *
     * @param event action event from the Google Drive menu item
     */
    @FXML
    public void onGoogleDrive(ActionEvent event) {
        setStatus("Google Drive (TODO)");
    }

    /**
     * Placeholder for Webhooks integration (e.g. Discord bot updates).
     *
     * @param event action event from the Webhooks menu item
     */
    @FXML
    public void onWebhooks(ActionEvent event) {
        setStatus("Webhooks (TODO)");
    }

    /** 
     * Exits the application. 
     * @param event action event from the Exit menu item
     */
    @FXML
    public void onExit(ActionEvent event) {
        Platform.exit();
    }

    // -------------------------------------------------------------------------
    // Public API (called from detection loop / app)
    // -------------------------------------------------------------------------

    /**
     * Called by the detection loop (on background thread) when a new song is
     * detected.
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
        Platform.runLater(() -> txtOutput.appendText(line + "\n"));
    }

    /**
     * Stops the detection loop and releases the JNativeHook global hotkey listener.
     * Called by the app before a locale-triggered scene rebuild.
     */
    public void cleanup() {
        detectionRunning = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        unregisterHotkeys();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** 
     * Initializes repositories and services, then starts the detection loop.
     * Runs on a background thread started by {@link #initialize(URL, ResourceBundle)}. 
     */
    private void initialise() {
        log.info("Initialising repositories and services…");
        PlayLogRepository playLogRepo = new PlayLogRepository();
        MusicListRepository musicListRepo = new MusicListRepository();
        loggerService = new SdvxLoggerService(playLogRepo, musicListRepo);

        Platform.runLater(() -> {
            refreshVfDisplay();
            setStatus("Ready — " + loggerService.getPlayLog().getPlays().size() + " plays loaded");
            startDetection();
        });
        log.info("Initialisation complete");
    }

    /**
     * Starts the detection loop on a background thread and updates the UI to
     * reflect the running state.
     */
    private void startDetection() {
        if (detectionRunning) {
            return;
        }
        detectionRunning = true;
        btnStartStop.setText("Stop Detection");
        btnStartStop.getStyleClass().removeAll("button-success");
        btnStartStop.getStyleClass().add("button-danger");
        setStatus("Detection running…");
        executor.submit(this::runDetectionLoop);
    }

    /**
     * Stops the detection loop and updates the UI accordingly.
     */
    private void stopDetection() {
        detectionRunning = false;
        btnStartStop.setText("Start Detection");
        btnStartStop.getStyleClass().removeAll("button-danger");
        btnStartStop.getStyleClass().add("button-success");
        setStatus("Detection stopped");
    }

    /**
     * Main loop that captures OBS frames, runs the image analysis, and pushes
     * new plays to the logger service.
     *
     * <p>Runs on a background thread started by {@link #startDetection()}.</p>
     */
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

    /**
     * Refreshes the total Volforce and play count display from the logger service.
     * Should be called after any change to the play log (new play, undo, etc.).
     */
    private void refreshVfDisplay() {
        if (loggerService == null) {
            return;
        }
        lblTotalVf.setText(ScoreFormatter.formatTotalVf(loggerService.getTotalVfInt()));
        lblPlayCount.setText(String.valueOf(loggerService.getPlayLog().getPlays().size()));
    }

    /**
     * Updates the status label with the given message. Should be called on the JavaFX
     * 
     * @param msg status message to display
     */
    private void setStatus(String msg) {
        lblStatus.setText(msg);
    }

    /**
     * Registers global hotkeys (F4-F9) using JNativeHook, mapping them to the corresponding
     * action handlers. Should be called once during initialization.
     */
    private void registerHotkeys() {
        try {
            GlobalScreen.registerNativeHook();
            hotkeyListener = new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == NativeKeyEvent.VC_F4) {
                        Platform.runLater(() -> onSaveVolforce(null));
                    } else if (code == NativeKeyEvent.VC_F5) {
                        Platform.runLater(() -> onSaveSummary(null));
                    } else if (code == NativeKeyEvent.VC_F6) {
                        Platform.runLater(() -> onSaveResult(null));
                    } else if (code == NativeKeyEvent.VC_F7) {
                        Platform.runLater(() -> onImportScore(null));
                    } else if (code == NativeKeyEvent.VC_F8) {
                        Platform.runLater(() -> onUpdateRival(null));
                    } else if (code == NativeKeyEvent.VC_F9) {
                        Platform.runLater(() -> onStartRta(null));
                    }
                }
            };
            GlobalScreen.addNativeKeyListener(hotkeyListener);
            log.info("Global hotkeys F4-F9 registered");
        } catch (NativeHookException e) {
            log.warn("Could not register global hotkeys (JNativeHook): {}", e.getMessage());
        }
    }

    /**
     * Unregisters the global hotkey listener and native hook. Should be called during cleanup
     * to release resources.
     */
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
}
