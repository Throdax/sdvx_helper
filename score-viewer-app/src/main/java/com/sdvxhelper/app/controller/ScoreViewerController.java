package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import com.sdvxhelper.model.SongInfo;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.service.CsvExportService;
import com.sdvxhelper.service.SdvxLoggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the score-viewer window ({@code score_viewer.fxml}).
 *
 * <p>
 * Loads the personal-best list from the play log (at the installation root) and
 * displays it in a filterable, sortable table. Replaces the Python
 * {@code ScoreViewer} class in {@code manage_score.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreViewerController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ScoreViewerController.class);

    @FXML
    private TextField txtFilter;
    @FXML
    private ComboBox<String> cmbLevel;
    @FXML
    private ComboBox<String> cmbLamp;
    @FXML
    private Button btnExport;
    @FXML
    private Button btnDelete;
    @FXML
    private ComboBox<String> cmbColorMode;
    @FXML
    private ComboBox<String> cmbLanguage;
    @FXML
    private TableView<MusicInfo> tblScores;
    @FXML
    private TableColumn<MusicInfo, String> colTitle;
    @FXML
    private TableColumn<MusicInfo, String> colDiff;
    @FXML
    private TableColumn<MusicInfo, String> colLevel;
    @FXML
    private TableColumn<MusicInfo, Integer> colScore;
    @FXML
    private TableColumn<MusicInfo, String> colLamp;
    @FXML
    private TableColumn<MusicInfo, Integer> colVf;
    @FXML
    private TableColumn<MusicInfo, String> colDate;
    @FXML
    private TableColumn<MusicInfo, String> colSTier;
    @FXML
    private Label lblCount;
    @FXML
    private ListView<OnePlayData> lstPlays;

    private final ObservableList<MusicInfo> allScores = FXCollections.observableArrayList();
    private FilteredList<MusicInfo> filteredScores;
    private final ObservableList<OnePlayData> selectedPlays = FXCollections.observableArrayList();
    private SdvxLoggerService loggerService;
    private PlayLogRepository playLogRepo;
    private PlayLog playLog;
    private final CsvExportService csvExport = new CsvExportService();

    /**
     * Initializes the controller after the FXML fields have been injected.
     *
     * <p>
     * Sets up table columns, filter listeners, and loads the initial data.
     * </p>
     *
     * @param location
     *            URL of the FXML file (unused)
     * @param resources
     *            resource bundle for localization (unused)
     */
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

        filteredScores = new FilteredList<>(allScores, _ -> true);
        tblScores.setItems(filteredScores);
        tblScores.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(_ -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));

        txtFilter.textProperty().addListener((_, _, _) -> applyFilter());
        cmbLevel.valueProperty().addListener((_, _, _) -> applyFilter());
        cmbLamp.valueProperty().addListener((_, _, _) -> applyFilter());

        cmbLevel.getItems().add("All");
        for (int i = 1; i <= 20; i++) {
            cmbLevel.getItems().add(String.valueOf(i));
        }
        cmbLevel.getSelectionModel().selectFirst();

        cmbLamp.getItems().addAll("All", "puc", "uc", "exh", "hard", "clear", "failed");
        cmbLamp.getSelectionModel().selectFirst();

        if (cmbColorMode != null) {
            cmbColorMode.getItems().addAll("None", "By Difficulty", "By Lamp");
            cmbColorMode.getSelectionModel().selectFirst();
            cmbColorMode.valueProperty().addListener((_, _, _) -> applyRowColors());
        }

        if (lstPlays != null) {
            lstPlays.setItems(selectedPlays);
            lstPlays.setCellFactory(_ -> new ListCell<OnePlayData>() {
                @Override
                protected void updateItem(OnePlayData item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(String.format("%s  |  %,d (%+d)  |  %s", item.getDate(), item.getCurScore(),
                                item.getDiff(), item.getLamp()));
                    }
                }
            });
            tblScores.getSelectionModel().selectedItemProperty().addListener((_, _, nv) -> refreshPlaysFor(nv));
        }
        if (btnDelete != null) {
            btnDelete.setDisable(true);
            lstPlays.getSelectionModel().selectedItemProperty()
                    .addListener((_, _, nv) -> btnDelete.setDisable(nv == null));
        }

        loadData();
    }

    /**
     * Handles the Export CSV button action.
     *
     * @param event
     *            action event
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

    /**
     * Deletes the currently selected play from {@code alllog.xml} after
     * confirmation.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onDelete(ActionEvent event) {
        OnePlayData selected = lstPlays == null ? null : lstPlays.getSelectionModel().getSelectedItem();
        if (selected == null || playLog == null || playLogRepo == null) {
            return;
        }
        Alert confirm = new Alert(AlertType.CONFIRMATION, "Delete this play?\n" + selected.getTitle() + " ["
                + selected.getDifficulty() + "] " + selected.getCurScore(), ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Delete");
        confirm.showAndWait().filter(bt -> bt == ButtonType.YES).ifPresent(_ -> {
            playLog.getPlays().remove(selected);
            try {
                playLogRepo.save(playLog);
                selectedPlays.remove(selected);
                loadData();
                log.info("Deleted play: {} [{}]", selected.getTitle(), selected.getDifficulty());
            } catch (IOException e) {
                log.error("Failed to save alllog.xml after delete", e);
            }
        });
    }

    /**
     * Loads the personal-best list from the play log and populates the table.
     */
    private void loadData() {
        File logFile = Path.of(System.getProperty("user.dir"), "resources", "alllog.xml").toFile();
        if (!logFile.exists()) {
            lblCount.setText("No play log found at resources/alllog.xml");
            allScores.clear();
            return;
        }
        playLogRepo = new PlayLogRepository(logFile);
        MusicListRepository musicListRepo = new MusicListRepository(new File("resources/musiclist.xml"));
        loggerService = new SdvxLoggerService(playLogRepo, musicListRepo);
        playLog = loggerService.getPlayLog();

        List<MusicInfo> best = loggerService.getBestAllFumen();
        for (MusicInfo mi : best) {
            if (mi.getLv() == null || "??".equals(mi.getLv())) {
                SongInfo si = musicListRepo.findSongInfo(mi.getTitle());
                if (si != null) {
                    mi.setLv(si.getLvForDifficulty(mi.getDifficulty()));
                }
            }
        }
        allScores.setAll(best);
        lblCount.setText(best.size() + " charts");
        log.info("Loaded {} charts into score viewer", best.size());
    }

    private void refreshPlaysFor(MusicInfo chart) {
        selectedPlays.clear();
        if (chart == null || playLog == null) {
            return;
        }
        List<OnePlayData> matches = new ArrayList<>();
        for (OnePlayData p : playLog.getPlays()) {
            if (p.getTitle() != null && p.getTitle().equals(chart.getTitle()) && p.getDifficulty() != null
                    && p.getDifficulty().equalsIgnoreCase(chart.getDifficulty())) {
                matches.add(p);
            }
        }
        selectedPlays.setAll(matches);
    }

    private void applyFilter() {
        String titleFilter = txtFilter.getText().toLowerCase();
        String levelFilter = cmbLevel.getValue();
        String lampFilter = cmbLamp.getValue();

        filteredScores.setPredicate(m -> {
            boolean titleOk = titleFilter.isBlank() || m.getTitle().toLowerCase().contains(titleFilter);
            boolean levelOk = "All".equals(levelFilter) || levelFilter.equals(m.getLv());
            boolean lampOk = "All".equals(lampFilter) || lampFilter.equals(m.getBestLamp());
            return titleOk && levelOk && lampOk;
        });

        lblCount.setText(filteredScores.size() + " / " + allScores.size() + " charts");
    }

    private void applyRowColors() {
        tblScores.setRowFactory(tv -> new TableRow<MusicInfo>() {
            @Override
            protected void updateItem(MusicInfo item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(item == null || empty ? "" : computeStyle(item));
            }
        });
        tblScores.refresh();
    }

    private String computeStyle(MusicInfo item) {
        String mode = cmbColorMode == null ? "None" : cmbColorMode.getValue();
        if (mode == null || "None".equals(mode)) {
            return "";
        }
        if ("By Difficulty".equals(mode)) {
            String diff = item.getDifficulty() == null ? "" : item.getDifficulty().toLowerCase();
            return switch (diff) {
                case "nov" -> "-fx-background-color: #7979D4; -fx-text-fill: white;";
                case "adv" -> "-fx-background-color: #E8B81C; -fx-text-fill: white;";
                case "exh" -> "-fx-background-color: #BD5E5E; -fx-text-fill: white;";
                case "mxm", "inf", "grv", "hvn", "vvd", "xcd" -> "-fx-background-color: #D6D6D6; -fx-text-fill: white;";
                default -> "";
            };
        }
        if ("By Lamp".equals(mode)) {
            String lamp = item.getBestLamp() == null ? "" : item.getBestLamp().toLowerCase();
            return switch (lamp) {
                case "puc" -> "-fx-background-color: #FCDC6D; -fx-text-fill: white;";
                case "uc" -> "-fx-background-color: #E02FBB; -fx-text-fill: white;";
                case "exh" -> "-fx-background-color: #D9D9D9; -fx-text-fill: white;";
                case "hard" -> "-fx-background-color: #CC8190; -fx-text-fill: white;";
                case "clear" -> "-fx-background-color: #98EB98; -fx-text-fill: white;";
                default -> "";
            };
        }
        return "";
    }
}
