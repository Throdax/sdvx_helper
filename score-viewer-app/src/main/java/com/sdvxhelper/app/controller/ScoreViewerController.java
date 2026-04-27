package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
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
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import com.sdvxhelper.app.controller.factories.LastPlaysCellFactory;
import com.sdvxhelper.app.controller.factories.RivalsRowFactory;
import com.sdvxhelper.app.controller.factories.ScoreRowFactory;
import com.sdvxhelper.app.controller.factories.ScoreViewerThreadFactory;
import com.sdvxhelper.app.controller.model.RivalScoreRow;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import com.sdvxhelper.model.RivalEntry;
import com.sdvxhelper.model.RivalLog;
import com.sdvxhelper.model.SongInfo;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.repository.PlayLogRepository;
import com.sdvxhelper.repository.RivalLogRepository;
import com.sdvxhelper.repository.SettingsRepository;
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
    private TextField filterField;

    @FXML
    private ComboBox<String> levelCombo;

    @FXML
    private ComboBox<String> lampCombo;

    @FXML
    private Button exportButton;

    @FXML
    private Button deleteButton;

    @FXML
    private ComboBox<String> colorModeCombo;

    @FXML
    private ComboBox<String> languageCombo;

    @FXML
    private TableView<MusicInfo> scoresTable;

    @FXML
    private TableColumn<MusicInfo, String> titleColumn;

    @FXML
    private TableColumn<MusicInfo, String> difficultyColumn;

    @FXML
    private TableColumn<MusicInfo, String> levelColumn;

    @FXML
    private TableColumn<MusicInfo, Integer> scoreColumn;

    @FXML
    private TableColumn<MusicInfo, String> lampColumn;

    @FXML
    private TableColumn<MusicInfo, Integer> vfColumn;

    @FXML
    private TableColumn<MusicInfo, String> dateColumn;

    @FXML
    private TableColumn<MusicInfo, String> sTierColumn;

    @FXML
    private Label countLabel;

    @FXML
    private ListView<OnePlayData> playsList;

    @FXML
    private TableView<RivalScoreRow> rivalsTable;

    @FXML
    private TableColumn<RivalScoreRow, String> rivalNameColumn;

    @FXML
    private TableColumn<RivalScoreRow, Integer> rivalScoreColumn;

    @FXML
    private TableColumn<RivalScoreRow, String> rivalLampColumn;

    private ObservableList<MusicInfo> allScores = FXCollections.observableArrayList();
    private FilteredList<MusicInfo> filteredScores;
    private ObservableList<OnePlayData> selectedPlays = FXCollections.observableArrayList();
    private ObservableList<RivalScoreRow> rivalScores = FXCollections.observableArrayList();

    private PlayLogRepository playLogRepo;
    private PlayLog playLog;
    private RivalLog rivalLog;
    private CsvExportService csvExport = new CsvExportService();

    private ExecutorService bgExecutor = Executors.newCachedThreadPool(new ScoreViewerThreadFactory());

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
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        difficultyColumn.setCellValueFactory(new PropertyValueFactory<>("difficulty"));
        levelColumn.setCellValueFactory(new PropertyValueFactory<>("lv"));
        scoreColumn.setCellValueFactory(new PropertyValueFactory<>("bestScore"));
        lampColumn.setCellValueFactory(new PropertyValueFactory<>("bestLamp"));
        vfColumn.setCellValueFactory(new PropertyValueFactory<>("vf"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        sTierColumn.setCellValueFactory(new PropertyValueFactory<>("sTier"));

        filteredScores = new FilteredList<>(allScores, _ -> true);
        scoresTable.setItems(filteredScores);
        scoresTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        languageCombo.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        languageCombo.setValue(LocaleManager.getInstance().getCurrentCode());
        languageCombo.setOnAction(_ -> LocaleManager.getInstance().setLocale(languageCombo.getValue()));

        filterField.textProperty().addListener((_, _, _) -> applyFilter());
        levelCombo.valueProperty().addListener((_, _, _) -> applyFilter());
        lampCombo.valueProperty().addListener((_, _, _) -> applyFilter());

        levelCombo.getItems().add("All");
        for (int i = 1; i <= 20; i++) {
            levelCombo.getItems().add(String.valueOf(i));
        }
        levelCombo.getSelectionModel().selectFirst();

        lampCombo.getItems().addAll("All", "puc", "uc", "exh", "hard", "clear", "failed");
        lampCombo.getSelectionModel().selectFirst();

        colorModeCombo.getItems().addAll("None", "By Difficulty", "By Lamp");
        colorModeCombo.getSelectionModel().selectFirst();
        colorModeCombo.valueProperty().addListener((_, _, _) -> applyRowColors());

        playsList.setItems(selectedPlays);
        playsList.setCellFactory(new LastPlaysCellFactory());
        scoresTable.getSelectionModel().selectedItemProperty().addListener((_, _, nv) -> refreshPlaysFor(nv));

        deleteButton.setDisable(true);
        playsList.getSelectionModel().selectedItemProperty()
                .addListener((_, _, nv) -> deleteButton.setDisable(nv == null));

        rivalNameColumn.setCellValueFactory(new PropertyValueFactory<>("rivalName"));
        rivalScoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        rivalLampColumn.setCellValueFactory(new PropertyValueFactory<>("lamp"));

        rivalsTable.setItems(rivalScores);
        rivalsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        rivalsTable.setRowFactory(_ -> new RivalsRowFactory());

        loadData();
        bgExecutor.submit(this::downloadMusiclistIfNeeded);
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
        File file = chooser.showSaveDialog(scoresTable.getScene().getWindow());
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
        OnePlayData selected = playsList.getSelectionModel().getSelectedItem();
        if (selected == null || playLog == null || playLogRepo == null) {
            log.warn("onDelete: selected={}, playLog={}, playLogRepo={} — cannot proceed",
                    selected == null ? "null" : selected, playLog == null ? "null" : "present",
                    playLogRepo == null ? "null" : "present");
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
     * Downloads {@code musiclist.xml} from {@code url_musiclist} in
     * {@code params.json} if the {@code autoload_musiclist} setting is
     * {@code true}.
     *
     * <p>
     * The URL from Python's {@code params.json} points to a {@code .pkl} file. Java
     * instead expects a {@code .xml} file at the same base URL. The {@code .pkl}
     * suffix is replaced with {@code .xml} before downloading.
     * </p>
     *
     * <p>
     * Mirrors Python's {@code update_musiclist()} in {@code manage_score.py}.
     * </p>
     */
    private void downloadMusiclistIfNeeded() {
        SettingsRepository settingsRepo = new SettingsRepository();
        Map<String, String> settings = settingsRepo.load();

        if (!settings.get("autoload_musiclist").equalsIgnoreCase("true")) {
            log.debug("autoload musiclist disabled by setting, skipping");
            return;
        }
        String paramsPath = settings.getOrDefault("params_json", "resources/params.json");
        Map<String, String> params = new ParamsRepository().load(paramsPath);
        String urlStr = params.get("url_musiclist");

        if (urlStr == null || urlStr.isBlank()) {
            log.debug("musiclist URL not configured in settings, skipping download");
            return;
        }
        if (urlStr.endsWith(".pkl")) {
            urlStr = urlStr.substring(0, urlStr.length() - 4) + ".xml";
        }
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(urlStr)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "sdvx-helper/2.0").GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

            if (resp.statusCode() == 200) {
                File resourcesDir = new File("resources");
                if (!resourcesDir.exists()) {
                    resourcesDir.mkdirs();
                }
                Files.write(Path.of("resources/musiclist.xml"), resp.body());
                log.info("musiclist.xml downloaded from {}", urlStr);
                Platform.runLater(this::loadData);
            } else {
                log.warn("Failed to download musiclist.xml: HTTP {}", resp.statusCode());
            }
        } catch (IOException e) {
            log.warn("Could not download musiclist.xml: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Loads the personal-best list from the play log and populates the table.
     */
    private void loadData() {
        File logFile = Path.of(System.getProperty("user.dir"), "resources", "alllog.xml").toFile();

        if (!logFile.exists()) {
            countLabel.setText("No play log found at resources/alllog.xml");
            allScores.clear();
            return;
        }
        playLogRepo = new PlayLogRepository(logFile);
        MusicListRepository musicListRepo = new MusicListRepository(new File("resources/musiclist.xml"));
        SdvxLoggerService loggerService = new SdvxLoggerService(playLogRepo, musicListRepo);
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
        countLabel.setText(best.size() + " charts");
        log.info("Loaded {} charts into score viewer", best.size());

        RivalLogRepository rivalLogRepo = new RivalLogRepository();
        rivalLog = rivalLogRepo.load();
        log.info("Loaded rival log with {} rivals", rivalLog.getRivals().size());
    }

    /**
     * Refreshes the list of plays and rival scores for the selected chart.
     *
     * <p>
     * When a chart is selected in the main table, this method filters the play log
     * to find all plays that match the chart's title and difficulty. The matching
     * plays are displayed in {@code playsList}. Additionally, if a rival log is
     * available, it populates the rival scores for the same chart and displays them
     * in {@code rivalsTable}.
     * </p>
     *
     * @param chart
     *            the selected music chart for which to refresh plays and rival
     *            scores
     */
    private void refreshPlaysFor(MusicInfo chart) {
        selectedPlays.clear();
        rivalScores.clear();
        if (chart == null || playLog == null) {
            log.warn("Cannot show play details: chart={}, playLog={}", chart == null ? "null" : chart,
                    playLog == null ? "null" : "present");
            return;
        }

        List<OnePlayData> matches = new ArrayList<>();
        for (OnePlayData p : playLog.getPlays()) {
            if (p.getTitle() != null && p.getTitle().equals(chart.getTitle()) && p.getDifficulty() != null
                    && p.getDifficulty().equalsIgnoreCase(chart.getDifficulty())) {
                matches.add(p);
            }
        }
        Collections.sort(matches);
        Collections.reverse(matches);
        selectedPlays.setAll(matches);

        if (rivalLog != null) {
            List<RivalScoreRow> rows = populateRivalsScore(chart);
            rows.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
            rivalScores.setAll(rows);
        }
    }

    /**
     * Populates the rival scores for a given chart.
     *
     * @param chart
     *            the music chart for which to collect rival scores
     * @return list of rival score rows corresponding to the given chart
     */
    private List<RivalScoreRow> populateRivalsScore(MusicInfo chart) {
        List<RivalScoreRow> rows = new ArrayList<>();
        for (RivalEntry rival : rivalLog.getRivals()) {
            for (MusicInfo ri : rival.getScores()) {
                if (ri.getTitle() != null && ri.getTitle().equals(chart.getTitle()) && ri.getDifficulty() != null
                        && ri.getDifficulty().equalsIgnoreCase(chart.getDifficulty())) {
                    rows.add(new RivalScoreRow(rival.getName(), ri.getBestScore(), ri.getBestLamp()));
                    break;
                }
            }
        }
        return rows;
    }

    /**
     * Applies the current filter settings to the score list.
     */
    private void applyFilter() {
        String titleFilter = filterField.getText().toLowerCase();
        String levelFilter = levelCombo.getValue();
        String lampFilter = lampCombo.getValue();

        filteredScores.setPredicate(m -> {
            boolean titleOk = titleFilter.isBlank() || m.getTitle().toLowerCase().contains(titleFilter);
            boolean levelOk = "All".equals(levelFilter) || levelFilter.equals(m.getLv());
            boolean lampOk = "All".equals(lampFilter) || lampFilter.equals(m.getBestLamp());
            return titleOk && levelOk && lampOk;
        });

        countLabel.setText(filteredScores.size() + " / " + allScores.size() + " charts");
    }

    /**
     * Applies row colors based on the selected color mode.
     */
    private void applyRowColors() {
        scoresTable.setRowFactory(_ -> new ScoreRowFactory(this));
        scoresTable.refresh();
    }

    /**
     * @return the colorModeCombo
     */
    public ComboBox<String> getColorModeCombo() {
        return colorModeCombo;
    }

}
