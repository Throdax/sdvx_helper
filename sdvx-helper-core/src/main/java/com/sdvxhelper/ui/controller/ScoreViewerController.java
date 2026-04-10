package com.sdvxhelper.ui.controller;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.service.CsvExportService;
import com.sdvxhelper.service.SdvxLoggerService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the score-viewer window ({@code score_viewer.fxml}).
 *
 * <p>Loads the personal-best list from the play log and displays it in a
 * filterable, sortable table.  Replaces the Python {@code ScoreViewer} class
 * in {@code manage_score.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreViewerController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ScoreViewerController.class);

    @FXML private TextField txtFilter;
    @FXML private ComboBox<String> cmbLevel;
    @FXML private ComboBox<String> cmbLamp;
    @FXML private Button btnExport;
    @FXML private ComboBox<String> cmbLanguage;
    @FXML private TableView<MusicInfo> tblScores;
    @FXML private TableColumn<MusicInfo, String> colTitle;
    @FXML private TableColumn<MusicInfo, String> colDiff;
    @FXML private TableColumn<MusicInfo, String> colLevel;
    @FXML private TableColumn<MusicInfo, Integer> colScore;
    @FXML private TableColumn<MusicInfo, String> colLamp;
    @FXML private TableColumn<MusicInfo, Integer> colVf;
    @FXML private TableColumn<MusicInfo, String> colDate;
    @FXML private TableColumn<MusicInfo, String> colSTier;
    @FXML private Label lblCount;

    private final ObservableList<MusicInfo> allScores = FXCollections.observableArrayList();
    private FilteredList<MusicInfo> filteredScores;
    private SdvxLoggerService loggerService;
    private final CsvExportService csvExport = new CsvExportService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDiff.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        colLevel.setCellValueFactory(new PropertyValueFactory<>("lv"));
        colScore.setCellValueFactory(new PropertyValueFactory<>("bestScore"));
        colLamp.setCellValueFactory(new PropertyValueFactory<>("bestLamp"));
        colVf.setCellValueFactory(new PropertyValueFactory<>("vf"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colSTier.setCellValueFactory(new PropertyValueFactory<>("sTier"));

        filteredScores = new FilteredList<>(allScores, p -> true);
        tblScores.setItems(filteredScores);
        tblScores.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(e -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));

        // Filter listeners
        txtFilter.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        cmbLevel.valueProperty().addListener((obs, o, n) -> applyFilter());
        cmbLamp.valueProperty().addListener((obs, o, n) -> applyFilter());

        // Populate level/lamp combos
        cmbLevel.getItems().add("All");
        for (int i = 1; i <= 20; i++) cmbLevel.getItems().add(String.valueOf(i));
        cmbLevel.getSelectionModel().selectFirst();

        cmbLamp.getItems().addAll("All", "puc", "uc", "exh", "hard", "clear", "failed");
        cmbLamp.getSelectionModel().selectFirst();

        loadData();
    }

    /**
     * Handles the Export CSV button action.
     *
     * @param event action event
     */
    @FXML
    public void onExportCsv(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Best CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(tblScores.getScene().getWindow());
        if (file != null) {
            try {
                csvExport.writeBestCsv(allScores, file);
                log.info("Exported best CSV to {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to export CSV", e);
            }
        }
    }

    private void loadData() {
        PlayLogRepository playLogRepo = new PlayLogRepository();
        MusicListRepository musicListRepo = new MusicListRepository();
        loggerService = new SdvxLoggerService(playLogRepo, musicListRepo);

        List<MusicInfo> best = loggerService.getBestAllFumen();
        allScores.setAll(best);
        lblCount.setText(best.size() + " charts");
        log.info("Loaded {} charts into score viewer", best.size());
    }

    private void applyFilter() {
        String titleFilter = txtFilter.getText().toLowerCase();
        String levelFilter = cmbLevel.getValue();
        String lampFilter  = cmbLamp.getValue();

        filteredScores.setPredicate(m -> {
            boolean titleOk = titleFilter.isBlank() || m.getTitle().toLowerCase().contains(titleFilter);
            boolean levelOk = "All".equals(levelFilter) || levelFilter.equals(m.getLv());
            boolean lampOk  = "All".equals(lampFilter)  || lampFilter.equals(m.getBestLamp());
            return titleOk && levelOk && lampOk;
        });

        lblCount.setText(filteredScores.size() + " / " + allScores.size() + " charts");
    }
}
