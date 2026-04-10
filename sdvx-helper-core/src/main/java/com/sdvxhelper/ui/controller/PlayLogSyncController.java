package com.sdvxhelper.ui.controller;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.service.XmlExportService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the Play Log Sync tool ({@code play_log_sync.fxml}).
 *
 * <p>Reconciles screenshot files from the results folder with the play log XML
 * and produces OBS overlay XML files.  Replaces the Python {@code PlayLogSync}
 * class in {@code play_log_sync.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class PlayLogSyncController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(PlayLogSyncController.class);

    @FXML private TextField txtPlayLogPath;
    @FXML private TextField txtResultsFolder;
    @FXML private TextField txtOutputFolder;
    @FXML private Button btnSync;
    @FXML private Button btnExport;
    @FXML private TableView<OnePlayData> tblPlays;
    @FXML private TableColumn<OnePlayData, String> colDate;
    @FXML private TableColumn<OnePlayData, String> colTitle;
    @FXML private TableColumn<OnePlayData, String> colDiff;
    @FXML private TableColumn<OnePlayData, Integer> colScore;
    @FXML private TableColumn<OnePlayData, String> colLamp;
    @FXML private TableColumn<OnePlayData, String> colStatus;
    @FXML private TextArea txtLog;
    @FXML private Label lblStatus;
    @FXML private ComboBox<String> cmbLanguage;

    private final ObservableList<OnePlayData> plays = FXCollections.observableArrayList();
    private PlayLog currentPlayLog;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("curScore"));
        colLamp.setCellValueFactory(new PropertyValueFactory<>("lamp"));
        tblPlays.setItems(plays);
        tblPlays.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(e -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));
    }

    @FXML public void onBrowsePlayLog(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select alllog.xml");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));
        File f = fc.showOpenDialog(btnSync.getScene().getWindow());
        if (f != null) txtPlayLogPath.setText(f.getAbsolutePath());
    }

    @FXML public void onBrowseResults(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Results Screenshot Folder");
        File d = dc.showDialog(btnSync.getScene().getWindow());
        if (d != null) txtResultsFolder.setText(d.getAbsolutePath());
    }

    @FXML public void onBrowseOutput(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Output Folder");
        File d = dc.showDialog(btnSync.getScene().getWindow());
        if (d != null) txtOutputFolder.setText(d.getAbsolutePath());
    }

    /**
     * Loads the play log and displays it in the table.
     *
     * @param event action event
     */
    @FXML
    public void onSync(ActionEvent event) {
        String logPath = txtPlayLogPath.getText().trim();
        if (logPath.isBlank()) {
            appendLog("ERROR: Please select the play log file.");
            return;
        }
        try {
            PlayLogRepository repo = new PlayLogRepository(new File(logPath));
            currentPlayLog = repo.load();
            plays.setAll(currentPlayLog.getPlays());
            lblStatus.setText("Loaded " + plays.size() + " plays");
            appendLog("Loaded play log: " + plays.size() + " plays from " + logPath);
        } catch (Exception e) {
            log.error("Failed to load play log", e);
            appendLog("ERROR: " + e.getMessage());
        }
    }

    /**
     * Exports OBS overlay XML files from the current play log.
     *
     * @param event action event
     */
    @FXML
    public void onExportXml(ActionEvent event) {
        if (currentPlayLog == null) {
            appendLog("No play log loaded. Run sync first.");
            return;
        }
        String outDir = txtOutputFolder.getText().trim();
        File outFolder = outDir.isBlank() ? new File("out") : new File(outDir);
        try {
            XmlExportService xmlService = new XmlExportService();
            xmlService.writeSdvxBattle(currentPlayLog.getPlays(),
                    new File(outFolder, "sdvx_battle.xml"));
            appendLog("Exported sdvx_battle.xml to " + outFolder.getAbsolutePath());
            lblStatus.setText("Export complete");
        } catch (IOException e) {
            log.error("Failed to export XML", e);
            appendLog("ERROR: " + e.getMessage());
        }
    }

    private void appendLog(String line) {
        txtLog.appendText(line + "\n");
    }
}
