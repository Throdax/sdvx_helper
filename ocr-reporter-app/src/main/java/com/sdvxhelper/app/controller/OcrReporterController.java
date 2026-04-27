package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javax.imageio.ImageIO;

import com.sdvxhelper.app.controller.listeners.ResultFilesTableRowListener;
import com.sdvxhelper.app.controller.listeners.TextFilterChangeListener;
import com.sdvxhelper.app.controller.model.HashEntry;
import com.sdvxhelper.app.controller.model.WikiSongRow;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.TesseractOcr;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.util.ParamUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the OCR Reporter maintainer tool ({@code ocr_reporter.fxml}).
 *
 * <p>
 * Allows maintainers to step through unknown jacket screenshots, view the
 * auto-OCR result, confirm or correct the title, and register the perceptual
 * hash in the music list. Replaces the Python {@code Reporter} class in
 * {@code ocr_reporter.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class OcrReporterController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(OcrReporterController.class);

    // private static final String WIKI_URL_KONASTE =
    // "https://bemaniwiki.com/index.php?%A5%B3%A5%CA%A5%B9%A5%C6"
    // + "/SOUND+VOLTEX+EXCEED+GEAR/%B3%DA%B6%CA%A5%EA%A5%B9%A5%C8";
    private static final String WIKI_URL_OLD = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%E6%97%A7%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";
    private static final String WIKI_URL_NEW = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR/%E6%96%B0%E6%9B%B2%E3%83%AA%E3%82%B9%E3%83%88";
    private ExecutorService bgExecutor = Executors.newCachedThreadPool(new OcrReporterThreadFactory());

    // -------------------------------------------------------------------------
    // FXML fields
    // -------------------------------------------------------------------------

    @FXML
    private Label registeredLabel;
    @FXML
    private Label stateLabel;
    @FXML
    private Label musicLoadingLabel;
    @FXML
    private Label filesLoadingLabel;
    @FXML
    private ProgressBar musicProgress;
    @FXML
    private ProgressBar filesProgress;
    @FXML
    private ImageView jacketView;
    @FXML
    private ImageView difficultyView;
    @FXML
    private ImageView infoView;
    @FXML
    private TextField hashField;
    @FXML
    private TextField hashInfoField;
    @FXML
    private TextField titleField;
    @FXML
    private ComboBox<String> difficultyCombo;
    @FXML
    private Button registerButton;
    @FXML
    private Button skipButton;
    @FXML
    private Button colorizeButton;
    @FXML
    private Button colorizeMissingButton;
    @FXML
    private Button mergeButton;
    @FXML
    private Button clearFilterButton;
    @FXML
    private CheckBox registerAllDiffsCheck;
    @FXML
    private ComboBox<String> hashDbDiffCombo;
    @FXML
    private TextArea logArea;
    @FXML
    private ComboBox<String> languageCombo;
    @FXML
    private TextField filterField;
    @FXML
    private TableView<WikiSongRow> musicTable;
    @FXML
    private TableColumn<WikiSongRow, String> musicTitleColumn;
    @FXML
    private TableColumn<WikiSongRow, String> musicArtistColumn;
    @FXML
    private TableColumn<WikiSongRow, String> musicBpmColumn;
    @FXML
    private TableColumn<WikiSongRow, String> musicNovColumn;
    @FXML
    private TableColumn<WikiSongRow, String> musicAdvColumn;
    @FXML
    private TableColumn<WikiSongRow, String> musicExhColumn;
    @FXML
    private TableColumn<WikiSongRow, String> musicAppendColumn;
    @FXML
    private TableView<File> filesTable;
    @FXML
    private TableColumn<File, String> fileNameColumn;
    @FXML
    private TableView<HashEntry> hashDbTable;
    @FXML
    private TableColumn<HashEntry, String> hashTitleColumn;
    @FXML
    private TableColumn<HashEntry, String> hashValueColumn;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private PerceptualHasher hasher = new PerceptualHasher();
    private List<File> imageFiles = new ArrayList<>();

    private ObservableList<WikiSongRow> wikiSongs = FXCollections.observableArrayList();
    private FilteredList<WikiSongRow> filteredWikiSongs;
    private ObservableList<File> fileItems = FXCollections.observableArrayList();
    private ObservableList<HashEntry> hashItems = FXCollections.observableArrayList();

    /** Keyed by filename → JavaFX inline style string. */
    Map<String, String> fileColorMap = new HashMap<>();

    private MusicListRepository musicListRepo;

    /** Detection parameters from params.json (log_crop_* entries). */
    private Map<String, String> paramsMap = new java.util.LinkedHashMap<>();

    private com.sdvxhelper.ocr.TesseractOcr tesseractOcr;

    private DiscordWebhookClient discordWebhookClient;
    private Map<String, String> settings;

    /** Count of hashes registered in this session. */
    private int sessionRegisteredCount = 0;

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        difficultyCombo.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        difficultyCombo.getSelectionModel().select("exh");

        languageCombo.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        languageCombo.setValue(LocaleManager.getInstance().getCurrentCode());
        languageCombo.setOnAction(_ -> LocaleManager.getInstance().setLocale(languageCombo.getValue()));

        musicTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        musicArtistColumn.setCellValueFactory(new PropertyValueFactory<>("artist"));
        musicBpmColumn.setCellValueFactory(new PropertyValueFactory<>("bpm"));
        musicNovColumn.setCellValueFactory(new PropertyValueFactory<>("nov"));
        musicAdvColumn.setCellValueFactory(new PropertyValueFactory<>("adv"));
        musicExhColumn.setCellValueFactory(new PropertyValueFactory<>("exh"));
        musicAppendColumn.setCellValueFactory(new PropertyValueFactory<>("append"));

        filteredWikiSongs = new FilteredList<>(wikiSongs, _ -> true);

        musicTable.setItems(filteredWikiSongs);
        musicTable.getSelectionModel().clearSelection();
        musicTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        fileNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        filesTable.setItems(fileItems);
        filesTable.setRowFactory(_ -> new ResultFilesTableRowListener(this));

        hashTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        hashValueColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        hashDbTable.setItems(hashItems);

        hashDbDiffCombo.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        hashDbDiffCombo.getSelectionModel().select("");
        hashDbDiffCombo.setOnAction(_ -> refreshHashDb());

        filterField.textProperty().addListener(new TextFilterChangeListener(this));

        SettingsRepository settingsRepo = new SettingsRepository();
        settings = settingsRepo.load();
        paramsMap = new ParamsRepository().load(settings.getOrDefault("params_json", "resources/params.json"));
        tesseractOcr = new TesseractOcr();
        discordWebhookClient = new DiscordWebhookClient();

        colorizeButton.setDisable(true);
        colorizeMissingButton.setDisable(true);

        loadHashDb();
        bgExecutor.submit(this::loadBemaniWiki);
        autoLoadFromSettings();
    }

    // -------------------------------------------------------------------------
    // BemaniWiki loading
    // -------------------------------------------------------------------------

    private void loadBemaniWiki() {
        Platform.runLater(() -> {
            musicLoadingLabel.setText("Loading BemaniWiki…");
            musicProgress.setProgress(-1.0);
        });

        Map<String, WikiSongRow> collected = new HashMap<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

        // First URL: コナステ list (simple structure — rows of 7-8 tds)
        // fetchKonasteList(http, WIKI_URL_KONASTE, collected);

        // Second and third URLs: AC 旧曲 and 新曲 (rowspan handling)
        fetchAcList(http, WIKI_URL_OLD, collected, 1);
        fetchAcList(http, WIKI_URL_NEW, collected, 2);

        List<WikiSongRow> rows = new ArrayList<>(collected.values());
        rows.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));

        Platform.runLater(() -> {
            wikiSongs.setAll(rows);
            musicLoadingLabel.setText("BemaniWiki: " + rows.size() + " songs loaded");
            musicProgress.setProgress(1.0);
            enableColorizeIfReady();
            log.info("BemaniWiki loaded {} songs", rows.size());
        });
    }

    // private void fetchKonasteList(HttpClient http, String url, Map<String,
    // WikiSongRow> out) {
    // try {
    // String html = fetchUrl(http, url);
    // if (html == null) {
    // return;
    // }
    // Document doc = Jsoup.parse(html);
    // for (Element tr : doc.select("tr")) {
    // Elements tds = tr.select("td");
    // int n = tds.size();
    // if (n != 7 && n != 8) {
    // continue;
    // }
    // if ("BPM".equals(tds.get(2).text())) {
    // continue;
    // }
    // String title = tds.get(0).text();
    // String artist = tds.get(1).text();
    // String bpm = tds.get(2).text();
    // String nov = parseLevel(tds.get(3).text());
    // String adv = parseLevel(tds.get(4).text());
    // String exh = parseLevel(tds.get(5).text());
    // String appendTxt = tds.get(6).text();
    // String append = (appendTxt.isEmpty() || "-".equals(appendTxt)) ? null :
    // parseLevel(appendTxt);
    // out.put(title, new WikiSongRow(title, artist, bpm, nov, adv, exh, append));
    // }
    // } catch (Exception e) {
    // log.warn("Failed to fetch Konaste list: {}", e.getMessage());
    // }
    // }

    private void fetchAcList(HttpClient http, String url, Map<String, WikiSongRow> out, int urlIndex) {
        try {
            String html = fetchUrl(http, url);
            if (html == null) {
                log.debug("fetchAcList: HTTP response was null for URL '{}', skipping parse", url);
                return;
            }
            Document doc = Jsoup.parse(html);
            int cntRowspanArtist = 0;
            int cntRowspanBpm = 0;
            String preArtist = "";
            String preBpm = "";

            for (Element tr : doc.select("tr")) {
                Elements tds = tr.select("td");
                int n = tds.size();

                int titleFlg = 0;
                int rowspanFlg = 0;
                if (!tds.isEmpty() && tds.get(0).text().matches("\\d{4}/\\d{2}/\\d{2}.*")) {
                    titleFlg = 1;
                    rowspanFlg = 1;
                }

                if (n != 7 + rowspanFlg && n != 8 + rowspanFlg) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
                    continue;
                }
                if ("BPM".equals(tds.get(3).text())) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
                    continue;
                }

                String title = tds.get(0 + titleFlg).text();
                if (tds.get(0).text().matches("\\d{4}/\\d{2}/\\d{2}")) {
                    title = tds.get(1).text();
                }
                String artist = tds.get(1 + titleFlg).text();
                String bpm = tds.get(2 + titleFlg).text();

                Element artistTd = tds.get(1 + titleFlg);
                if (artistTd.hasAttr("rowspan")) {
                    cntRowspanArtist = Integer.parseInt(artistTd.attr("rowspan"));
                    preArtist = artistTd.text();
                } else if (cntRowspanArtist > 0) {
                    rowspanFlg -= 1;
                    artist = preArtist;
                    bpm = tds.get(1 + titleFlg).text();
                }

                Element bpmTd = tds.get(2 + titleFlg);
                if (bpmTd.hasAttr("rowspan")) {
                    cntRowspanBpm = Integer.parseInt(bpmTd.attr("rowspan"));
                    preBpm = bpmTd.text();
                } else if (cntRowspanBpm > 0) {
                    rowspanFlg -= 1;
                    bpm = preBpm;
                }

                Element novTd = tds.get(3 + rowspanFlg);
                if ("-".equals(novTd.text())) {
                    cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                    cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
                    continue;
                }

                String nov = lastDigits(tds.get(3 + rowspanFlg).text());
                String adv = lastDigits(tds.get(4 + rowspanFlg).text());
                String exh = lastDigits(tds.get(5 + rowspanFlg).text());
                String appendTxt = tds.get(6 + rowspanFlg).text();
                String append = (appendTxt.isEmpty() || "-".equals(appendTxt)) ? null : lastDigits(appendTxt);

                if (!out.containsKey(title)) {
                    out.put(title, new WikiSongRow(title, artist, bpm, nov, adv, exh, append));
                }

                cntRowspanArtist = Math.max(0, cntRowspanArtist - 1);
                cntRowspanBpm = Math.max(0, cntRowspanBpm - 1);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to fetch AC wiki list {}: {}", urlIndex, e.getMessage());
        }
    }

    // private String parseLevel(String text) {
    // if (text == null || text.isBlank()) {
    // return "??";
    // }
    // String clean = text.startsWith(STOP_PREFIX) ?
    // text.substring(STOP_PREFIX.length()).trim() : text;
    // return clean.isBlank() ? "??" : clean;
    // }

    private String lastDigits(String text) {
        return OcrReporterHelper.lastDigits(text);
    }

    private String fetchUrl(HttpClient http, String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "sdvx-helper/2.0").GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            log.warn("HTTP {} for {}", resp.statusCode(), url);
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to fetch {}: {}", url, e.getMessage());
            Thread.currentThread().interrupt();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Hash DB loading
    // -------------------------------------------------------------------------

    private void loadHashDb() {
        musicListRepo = new MusicListRepository();
        refreshHashDb();
        registeredLabel.setText(String.valueOf(hashItems.size()));
    }

    private void refreshHashDb() {
        if (musicListRepo == null || hashItems == null) {
            log.warn("refreshHashDb: musicListRepo={}, hashItems={} — cannot refresh",
                    musicListRepo == null ? "null" : "present", hashItems == null ? "null" : "present");
            return;
        }
        List<HashEntry> rows = new ArrayList<>();

        String selectedDiff = hashDbDiffCombo.getValue();
        if (selectedDiff != null && !selectedDiff.isBlank()) {
            for (com.sdvxhelper.model.HashEntry h : musicListRepo.getHashesForDifficulty(selectedDiff)) {
                rows.add(new HashEntry(h.getTitle(), h.getHash()));
            }
        } else {
            difficultyCombo.getItems().forEach(d -> {
                for (com.sdvxhelper.model.HashEntry h : musicListRepo.getHashesForDifficulty(d)) {
                    rows.add(new HashEntry(h.getTitle(), h.getHash()));
                }
            });
        }
        hashItems.setAll(rows);
    }

    private void autoLoadFromSettings() {
        SettingsRepository repo = new SettingsRepository();
        Map<String, String> settings = repo.load();
        String dir = settings.get("autosave_dir");
        if (dir != null && !dir.isBlank()) {
            File f = new File(dir);
            if (f.isDirectory()) {
                loadFolder(f);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Action handlers
    // -------------------------------------------------------------------------

    /**
     * Clears the filter text field.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onClearFilter(ActionEvent event) {
        filterField.clear();
    }

    /**
     * Registers the current image's hash in the music list.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onRegister(ActionEvent event) {
        String hash = hashField.getText();
        String hashInfo = hashInfoField.getText();
        String title = titleField.getText().trim();
        String diff = difficultyCombo.getValue();
        if (hash.isBlank() || title.isBlank()) {
            appendLog("ERROR: hash or title is empty");
            return;
        }
        try {
            if (musicListRepo != null) {
                boolean allDiffs = registerAllDiffsCheck.isSelected();
                if (allDiffs) {
                    for (String d : List.of("nov", "adv", "exh", "APPEND")) {
                        musicListRepo.registerHash(hash, title, d);
                    }
                    appendLog("Registered for all diffs: " + title + " = " + hash);
                } else {
                    musicListRepo.registerHash(hash, title, diff);
                    appendLog("Registered: [" + diff + "] " + title + " = " + hash);
                }
            }
            sessionRegisteredCount++;
            bgExecutor.submit(() -> sendWebhookOnRegister(title, diff, hash, hashInfo));
        } catch (IOException e) {
            log.error("Failed to register hash", e);
            appendLog("ERROR: " + e.getMessage());
        }
        advanceToNext();
    }

    /**
     * Sends a Discord webhook with the newly registered jacket/info hashes. Uses
     * the {@code webhook_reg_url} setting (no SHA-256 is sent).
     */
    private void sendWebhookOnRegister(String title, String difficulty, String hashJacket, String hashInfo) {
        if (settings == null) {
            log.debug("sendWebhookOnRegister: settings not loaded, skipping webhook");
            return;
        }
        String webhookUrl = settings.getOrDefault("webhook_reg_url", "").trim();
        if (webhookUrl.isBlank()) {
            log.debug("sendWebhookOnRegister: ocr_webhook_url not configured, skipping");
            return;
        }
        StringBuilder msg = new StringBuilder();
        msg.append("New registration: **").append(title).append("**\n");
        msg.append(" - jacket hash: **").append(hashJacket).append("**");
        if (!hashInfo.isBlank()) {
            msg.append("\n - info hash: **").append(hashInfo).append("**");
        }
        msg.append(" (").append(difficulty.toUpperCase()).append(")");

        // Attach jacket screenshot crops if available
        int fileIndex = filesTable.getSelectionModel().getSelectedIndex();
        if (fileIndex >= 0 && fileIndex < imageFiles.size()) {
            File imgFile = imageFiles.get(fileIndex);
            try {
                BufferedImage awtImage = ImageIO.read(imgFile);
                if (awtImage != null) {
                    int jSx = ParamUtils.getInt(paramsMap, "log_crop_jacket_sx", 57);
                    int jSy = ParamUtils.getInt(paramsMap, "log_crop_jacket_sy", 916);
                    int jW = ParamUtils.getInt(paramsMap, "log_crop_jacket_w", 263);
                    int jH = ParamUtils.getInt(paramsMap, "log_crop_jacket_h", 263);
                    BufferedImage jacketCrop = cropAndScale(awtImage, jSx, jSy, jW, jH, jW, jH);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    ImageIO.write(jacketCrop, "png", baos);
                    discordWebhookClient.sendMessageWithImage(webhookUrl, msg.toString(), baos.toByteArray(),
                            "jacket.png");
                } else {
                    discordWebhookClient.sendMessage(webhookUrl, msg.toString());
                }
            } catch (IOException e) {
                log.warn("Failed to send registration webhook with image: {}", e.getMessage());
                discordWebhookClient.sendMessage(webhookUrl, msg.toString());
            }
        } else {
            discordWebhookClient.sendMessage(webhookUrl, msg.toString());
        }
    }

    /**
     * Sends the current {@code musiclist.xml} file to the Discord webhook on close.
     * Called from the application close event handler.
     */
    public void onWindowClose() {
        if (settings == null) {
            log.debug("onWindowClose: settings not loaded, skipping webhook");
            return;
        }
        String webhookUrl = settings.getOrDefault("webhook_reg_url", "").trim();
        if (webhookUrl.isBlank()) {
            log.debug("onWindowClose: webhook_reg_url not configured, skipping close webhook");
            return;
        }
        File musiclistFile = new File("resources/musiclist.xml");
        if (!musiclistFile.exists()) {
            log.debug("musiclist.xml not found, skipping close webhook");
            return;
        }
        try {
            byte[] xmlBytes = java.nio.file.Files.readAllBytes(musiclistFile.toPath());
            int totalHashes = hashItems != null ? hashItems.size() : 0;
            String msg = "Session ended. Registered: " + sessionRegisteredCount + ", total: " + totalHashes;
            discordWebhookClient.sendMessageWithFile(webhookUrl, msg, xmlBytes, "musiclist.xml", "application/xml");
            log.info("Musiclist sent to Discord on close");
        } catch (IOException e) {
            log.warn("Failed to send musiclist on close: {}", e.getMessage());
        }
    }

    private void advanceToNext() {
        int next = filesTable.getSelectionModel().getSelectedIndex() + 1;
        if (next < imageFiles.size()) {
            filesTable.getSelectionModel().select(next);
            filesTable.scrollTo(next);
        }
    }

    /**
     * Colorizes all files in the file list (heavy).
     *
     * @param event
     *            action event
     */
    @FXML
    public void onColorize(ActionEvent event) {
        bgExecutor.submit(this::colorizeAll);
    }

    /**
     * Colorizes only files that are not yet registered in the hash database.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onColorizeMissing(ActionEvent event) {
        bgExecutor.submit(this::colorizeUnregisteredOnly);
    }

    /**
     * Opens a file chooser to select an external {@code musiclist.xml} and merges
     * its hashes into the current music list, skipping duplicates.
     *
     * <p>
     * Mirrors Python {@code merge_musiclist()} in {@code ocr_reporter.py}.
     * </p>
     *
     * @param event
     *            action event
     */
    @FXML
    public void onMerge(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select musiclist.xml to merge");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File selected = chooser.showOpenDialog(mergeButton.getScene().getWindow());
        if (selected == null) {
            log.debug("onMerge: no file selected (user cancelled chooser), skipping");
            return;
        }
        bgExecutor.submit(() -> {
            try {
                if (musicListRepo == null) {
                    musicListRepo = new MusicListRepository();
                }
                int added = musicListRepo.merge(selected);
                Platform.runLater(() -> {
                    refreshHashDb();
                    registeredLabel.setText(String.valueOf(hashItems.size()));
                    appendLog("Merge complete: " + added + " new entries imported from " + selected.getName());
                });
            } catch (IOException e) {
                log.error("Merge failed", e);
                Platform.runLater(() -> appendLog("ERROR during merge: " + e.getMessage()));
            }
        });
    }

    private void colorizeAll() {
        colorize(false);
    }

    private void colorizeUnregisteredOnly() {
        colorize(true);
    }

    /**
     * Iterates all image files, computes the jacket hash, and colours the row in
     * {@code filesTable} based on whether the hash is registered in
     * {@code musicListRepo}.
     *
     * @param missingOnly
     *            if {@code true}, skip files that are already coloured
     */
    private void colorize(boolean missingOnly) {
        if (musicListRepo == null) {
            log.warn("colorize: musicListRepo not initialised, cannot colorize {} files", imageFiles.size());
            return;
        }
        int total = imageFiles.size();
        Platform.runLater(() -> {
            filesProgress.setProgress(-1.0);
        });
        for (int i = 0; i < total; i++) {
            final File f = imageFiles.get(i);
            if (missingOnly) {
                String existing = fileColorMap.get(f.getName());
                if (existing != null && !existing.isBlank()) {
                    continue;
                }
                // Only process files matching the sdvx_* result screenshot pattern
                if (!isResultFilename(f.getName())) {
                    continue;
                }
            }
            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) {
                    continue;
                }
                String hash = hasher.hash(img);
                String[] match = musicListRepo.findByJacketHash(hash);
                String style = (match != null) ? "-fx-background-color: #dde0ff;" : "-fx-background-color: #dddddd;";
                fileColorMap.put(f.getName(), style);
            } catch (IOException e) {
                log.debug("Colorize error for {}: {}", f.getName(), e.getMessage());
            }
        }
        Platform.runLater(() -> {
            filesProgress.setProgress(1.0);
            filesTable.refresh();
            filesLoadingLabel.setText("Colorize done (" + total + " files)");
        });
    }

    private void enableColorizeIfReady() {
        if (!fileItems.isEmpty()) {
            colorizeButton.setDisable(false);
            colorizeMissingButton.setDisable(false);
        }
    }

    // -------------------------------------------------------------------------
    // File loading and image display
    // -------------------------------------------------------------------------

    private void loadFolder(File dir) {
        File[] files = dir.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                    && !name.contains("summary");
        });

        imageFiles.clear();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            imageFiles.addAll(Arrays.asList(files));
        }
        fileItems.setAll(imageFiles);
        fileColorMap.clear();
        Platform.runLater(() -> {
            filesTable.getSelectionModel().clearSelection();
        });
        enableColorizeIfReady();
        appendLog("Loaded " + imageFiles.size() + " image(s) from " + dir.getAbsolutePath());
        Platform.runLater(() -> filesLoadingLabel.setText(imageFiles.size() + " file(s) in folder"));
    }

    private static boolean isResultFilename(String filename) {
        return OcrReporterHelper.isResultFilename(filename);
    }

    private void showCurrentImage(File f) {
        if (f == null || !f.exists()) {
            log.debug("showCurrentImage: file null or does not exist, skipping image display");
            return;
        }

        if (!isResultFilename(f.getName())) {
            // Clear preview if this is not a result screenshot
            Platform.runLater(() -> {
                jacketView.setImage(null);
                difficultyView.setImage(null);
                infoView.setImage(null);
                titleField.setStyle("-fx-text-fill: red;");
                titleField.setText("(not a result screenshot)");
                hashField.clear();
            });
            return;
        }

        try {
            BufferedImage awtImage = ImageIO.read(f);
            if (awtImage == null) {
                log.debug("showCurrentImage: failed to load image (awtImage null), skipping display");
                return;
            }

            int jSx = ParamUtils.getInt(paramsMap, "log_crop_jacket_sx", 57);
            int jSy = ParamUtils.getInt(paramsMap, "log_crop_jacket_sy", 916);
            int jW = ParamUtils.getInt(paramsMap, "log_crop_jacket_w", 263);
            int jH = ParamUtils.getInt(paramsMap, "log_crop_jacket_h", 263);
            BufferedImage jacket = cropAndScale(awtImage, jSx, jSy, jW, jH, 100, 100);
            jacketView.setImage(toFxImage(jacket));

            int dSx = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sx", 55);
            int dSy = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sy", 870);
            int dW = ParamUtils.getInt(paramsMap, "log_crop_difficulty_w", 138);
            int dH = ParamUtils.getInt(paramsMap, "log_crop_difficulty_h", 30);
            BufferedImage diff = cropAndScale(awtImage, dSx, dSy, dW, dH, 137, 29);
            difficultyView.setImage(toFxImage(diff));

            int iSx = ParamUtils.getInt(paramsMap, "log_crop_info_sx", 379);
            int iSy = ParamUtils.getInt(paramsMap, "log_crop_info_sy", 1001);
            int iW = ParamUtils.getInt(paramsMap, "log_crop_info_w", 527);
            int iH = ParamUtils.getInt(paramsMap, "log_crop_info_h", 65);
            BufferedImage info = cropAndScale(awtImage, iSx, iSy, iW, iH, 526, 64);
            infoView.setImage(toFxImage(info));

            String hash = hasher.hash(awtImage);
            hashField.setText(hash);

            if (tesseractOcr != null) {
                int tsx = ParamUtils.getInt(paramsMap, "info_title_sx", 201);
                int tsy = ParamUtils.getInt(paramsMap, "info_title_sy", 1091);
                int tw = ParamUtils.getInt(paramsMap, "info_title_w", 678);
                int th = ParamUtils.getInt(paramsMap, "info_title_h", 122);
                BufferedImage titleRegion = cropAndScale(awtImage, tsx, tsy, tw, th, tw, th);
                String ocrTitle = tesseractOcr.recognizeText(titleRegion);
                Platform.runLater(() -> titleField.setText(ocrTitle != null ? ocrTitle.trim() : ""));
            } else {
                Platform.runLater(() -> titleField.clear());
            }
        } catch (IOException e) {
            log.error("Failed to load image {}", f.getAbsolutePath(), e);
            appendLog("ERROR loading: " + f.getName());
        }
    }

    private BufferedImage cropAndScale(BufferedImage src, int x, int y, int w, int h, int outW, int outH) {
        return OcrReporterHelper.cropAndScale(src, x, y, w, h, outW, outH);
    }

    private Image toFxImage(BufferedImage awt) {
        return SwingFXUtils.toFXImage(awt, null);
    }

    private void appendLog(String line) {
        Platform.runLater(() -> logArea.appendText(line + "\n"));
    }

    @FXML
    public void onSelectMusic(MouseEvent event) {
        WikiSongRow selectedItem = musicTable.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            titleField.setText(selectedItem.getTitle());
            titleField.setStyle("-fx-text-fill: black;");
        }
    }

    @FXML
    public void onSelectResult(MouseEvent event) {
        int selectedIndex = filesTable.getSelectionModel().getSelectedIndex();

        if (selectedIndex >= 0) {
            showCurrentImage(imageFiles.get(selectedIndex));
        }
    }

    public Map<String, String> getFileColorMap() {
        return fileColorMap;
    }

    public FilteredList<WikiSongRow> getFilteredWikiSongs() {
        return filteredWikiSongs;
    }
}
