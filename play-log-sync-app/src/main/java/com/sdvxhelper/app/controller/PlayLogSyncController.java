package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import com.sdvxhelper.app.controller.factories.PlayLogSyncThreadFactory;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.repository.SpecialTitlesRepository;
import com.sdvxhelper.service.XmlExportService;
import com.sdvxhelper.util.SpecialTitles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Play Log Sync tool ({@code play_log_sync.fxml}).
 *
 * <p>
 * Reconciles screenshot files from the results folder with the play log XML and
 * produces OBS overlay XML files. Replaces the Python {@code PlayLogSync} class
 * in {@code play_log_sync.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PlayLogSyncController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PlayLogSyncController.class);

    @FXML
    private TextField playLogPathField;
    @FXML
    private TextField resultsFolderField;
    @FXML
    private TextField outputFolderField;
    @FXML
    private Button syncButton;
    @FXML
    private Button exportButton;
    @FXML
    private TableView<OnePlayData> playsTable;
    @FXML
    private TableColumn<OnePlayData, String> dateColumn;
    @FXML
    private TableColumn<OnePlayData, String> titleColumn;
    @FXML
    private TableColumn<OnePlayData, String> difficultyColumn;
    @FXML
    private TableColumn<OnePlayData, Integer> scoreColumn;
    @FXML
    private TableColumn<OnePlayData, String> lampColumn;
    @FXML
    private TableColumn<OnePlayData, String> statusColumn;
    @FXML
    private TextArea logArea;
    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<String> languageCombo;

    @FXML
    private CheckBox rebuildCheck;
    @FXML
    private TextField timeOffsetField;

    private ObservableList<OnePlayData> plays = FXCollections.observableArrayList();
    private PlayLog currentPlayLog;
    private SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, String> settings;
    private ExecutorService bgExecutor = Executors.newSingleThreadExecutor(new PlayLogSyncThreadFactory());

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        difficultyColumn.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("curScore"));
        lampColumn.setCellValueFactory(new PropertyValueFactory<>("lamp"));
        playsTable.setItems(plays);
        playsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        languageCombo.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        languageCombo.setValue(LocaleManager.getInstance().getCurrentCode());
        languageCombo.setOnAction(_ -> LocaleManager.getInstance().setLocale(languageCombo.getValue()));

        settings = settingsRepo.load();
        prefillFields();
    }

    private void prefillFields() {
        String savedLog = settings.get("play_log_sync.play_log_path");
        if (savedLog == null || savedLog.isBlank()) {
            savedLog = Path.of(System.getProperty("user.dir"), "alllog.xml").toAbsolutePath().toString();
        }
        playLogPathField.setText(savedLog);

        String autosaveDir = settings.get("autosave_dir");
        resultsFolderField.setText(autosaveDir == null ? "" : autosaveDir);

        String savedOut = settings.get("play_log_sync.output_folder");
        if (savedOut == null || savedOut.isBlank()) {
            savedOut = System.getProperty("user.dir");
        }
        outputFolderField.setText(savedOut);
    }

    private void persistFields() {
        settings.put("play_log_sync.play_log_path", playLogPathField.getText().trim());
        settings.put("play_log_sync.output_folder", outputFolderField.getText().trim());
        try {
            settingsRepo.save(settings);
        } catch (IOException e) {
            log.warn("Failed to persist play log sync settings: {}", e.getMessage());
        }
    }

    /**
     * Opens a file chooser to select the play log XML file.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onBrowsePlayLog(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select alllog.xml");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File f = fc.showOpenDialog(syncButton.getScene().getWindow());
        if (f != null) {
            playLogPathField.setText(f.getAbsolutePath());
            persistFields();
        }
    }

    /**
     * Opens a directory chooser to select the results screenshot folder.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onBrowseResults(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Results Screenshot Folder");
        File d = dc.showDialog(syncButton.getScene().getWindow());
        if (d != null) {
            resultsFolderField.setText(d.getAbsolutePath());
        }
    }

    /**
     * Opens a directory chooser to select the output folder for generated XML.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onBrowseOutput(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Output Folder");
        File d = dc.showDialog(syncButton.getScene().getWindow());
        if (d != null) {
            outputFolderField.setText(d.getAbsolutePath());
            persistFields();
        }
    }

    /**
     * Starts the sync process: reads screenshot filenames from the results folder,
     * parses each as a play record, and adds entries not already in the play log
     * (with a 120-second timestamp tolerance). Applies special-titles handling.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSync(ActionEvent event) {
        String logPath = playLogPathField.getText().trim();
        String resultsPath = resultsFolderField.getText().trim();
        if (logPath.isBlank()) {
            appendLog("ERROR: Please select the play log file.");
            return;
        }
        boolean rebuild = rebuildCheck.isSelected();
        int timeOffset;
        try {
            timeOffset = Integer.parseInt(timeOffsetField.getText().trim());
        } catch (NumberFormatException e) {
            timeOffset = 0;
        }
        final int finalTimeOffset = timeOffset;

        syncButton.setDisable(true);
        appendLog("Starting sync…");

        bgExecutor.submit(() -> runSync(logPath, resultsPath, rebuild, finalTimeOffset));
    }

    private void runSync(String logPath, String resultsPath, boolean rebuild, int timeOffsetSeconds) {
        try {
            PlayLogRepository repo = new PlayLogRepository(new File(logPath));
            currentPlayLog = repo.load();

            if (rebuild) {
                appendLog("Rebuild: clearing existing play log.");
                currentPlayLog.getPlays().clear();
            } else {
                SpecialTitles st = new SpecialTitlesRepository().load();
                List<String> directRemoves = st.getDirectRemoves();
                currentPlayLog.getPlays().removeIf(p -> directRemoves.contains(p.getTitle()));
                appendLog("Removed " + directRemoves.size() + " direct-remove entries.");
            }

            if (resultsPath.isBlank()) {
                appendLog("No results folder specified — loading log only.");
                showPlays();
                return;
            }

            File resultsDir = new File(resultsPath);
            if (!resultsDir.isDirectory()) {
                appendLog("ERROR: Results folder not found: " + resultsPath);
                showPlays();
                return;
            }

            SpecialTitles st = new SpecialTitlesRepository().load();
            File[] files = resultsDir.listFiles(f -> f.getName().toLowerCase().endsWith(".png")
                    && f.getName().startsWith("sdvx") && !f.getName().contains("summary"));
            if (files == null || files.length == 0) {
                appendLog("No result screenshot files found in " + resultsPath);
                showPlays();
                return;
            }
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            appendLog("Processing " + files.length + " screenshot files…");

            int processed = 0;
            int added = 0;
            for (File f : files) {
                OnePlayData parsed = parseScreenshotFilename(f.getName());
                if (parsed == null) {
                    processed++;
                    continue;
                }
                if (st.getSpecialTitles().containsKey(parsed.getTitle())) {
                    currentPlayLog.getPlays()
                            .removeIf(p -> p.getTitle() != null && p.getTitle().equals(parsed.getTitle()));
                    processed++;
                    continue;
                }
                if (st.getIgnoredNames().contains(parsed.getTitle())) {
                    processed++;
                    continue;
                }

                if (!isSongInLog(currentPlayLog.getPlays(), parsed, timeOffsetSeconds)) {
                    final String info = "[" + processed + "] " + parsed.getTitle() + " ["
                            + parsed.getDifficulty().toUpperCase() + "] Adding…";
                    appendLog(info);
                    currentPlayLog.getPlays().add(parsed);
                    added++;
                }
                processed++;
                if (processed % 100 == 0) {
                    final int progressCount = processed;
                    final int total = files.length;
                    appendLog(progressCount + " / " + total + " files processed");
                }
            }

            repo.save(currentPlayLog);
            final int finalAdded = added;
            final int finalProcessed = processed;
            appendLog("Sync complete: " + finalAdded + " songs added out of " + finalProcessed + " files.");
            showPlays();
        } catch (IOException e) {
            log.error("Sync failed", e);
            appendLog("ERROR: " + e.getMessage());
        } finally {
            Platform.runLater(() -> syncButton.setDisable(false));
        }
    }

    private OnePlayData parseScreenshotFilename(String filename) {
        return ScreenshotFilenameParser.parse(filename);
    }

    private static boolean isSongInLog(List<OnePlayData> logEntries, OnePlayData candidate, int timeOffsetSeconds) {
        return ScreenshotFilenameParser.isSongInLog(logEntries, candidate, timeOffsetSeconds);
    }

    private void showPlays() {
        Platform.runLater(() -> {
            plays.setAll(currentPlayLog.getPlays());
            statusLabel.setText(plays.size() + " plays in log");
        });
    }

    /**
     * Exports OBS overlay XML files from the current play log.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onExportXml(ActionEvent event) {
        if (currentPlayLog == null) {
            appendLog("No play log loaded. Run sync first.");
            return;
        }
        String outDir = outputFolderField.getText().trim();
        File outFolder = outDir.isBlank() ? new File("out") : new File(outDir);
        try {
            XmlExportService xmlService = new XmlExportService();
            xmlService.writeSdvxBattle(currentPlayLog.getPlays(), new File(outFolder, "sdvx_battle.xml"));
            appendLog("Exported sdvx_battle.xml to " + outFolder.getAbsolutePath());
            statusLabel.setText("Export complete");
        } catch (IOException e) {
            log.error("Failed to export XML", e);
            appendLog("ERROR: " + e.getMessage());
        }
    }

    private void appendLog(String line) {
        logArea.appendText(line + "\n");
    }
}
