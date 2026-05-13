package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sdvxhelper.app.controller.listeners.ResultFilesTableRowListener;
import com.sdvxhelper.app.controller.listeners.TextFilterChangeListener;
import com.sdvxhelper.app.controller.model.HashEntry;
import com.sdvxhelper.app.controller.model.WikiSongRow;
import com.sdvxhelper.config.SecretConfig;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.TesseractOcr;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.util.ParamUtils;

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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

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
    private Button copyTitleButton;
    @FXML
    private Button skipButton;
    @FXML
    private Button colorizeButton;
    @FXML
    private Button colorizeMissingButton;
    // @FXML
    // private Button mergeButton;
    @FXML
    private Button clearFilterButton;
    // @FXML
    // private CheckBox registerAllDiffsCheck;
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
    private ImageAnalysisService imageAnalysisService;
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
    private SecretConfig secretConfig;
    private Map<String, String> settings;

    /** Count of hashes registered in this session. */
    private int sessionRegisteredCount = 0;

    /** Kept for i18n lookups inside background threads (e.g. colorize). */
    private ResourceBundle bundle;

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bundle = resources;

        // registerAllDiffsCheck.setSelected(true);

        difficultyCombo.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        difficultyCombo.getSelectionModel().select("exh");
        difficultyCombo.valueProperty().addListener((_, _, _) -> updateRegisterButtonState());

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
        // Populate title field and update Register button state whenever the
        // wiki selection changes (keyboard navigation included).
        musicTable.getSelectionModel().selectedItemProperty().addListener((_, _, selected) -> {
            if (selected != null) {
                titleField.setText(selected.getTitle());
                titleField.setStyle("-fx-text-fill: black; -fx-background-color: #f8f8f8;");
            }
            updateRegisterButtonState();
        });

        fileNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        fileNameColumn.setSortable(false);
        filesTable.setItems(fileItems);
        filesTable.setRowFactory(_ -> new ResultFilesTableRowListener(this));
        // Load the selected result image whenever the selection changes,
        // including programmatic advances from onRegister → advanceToNext().
        filesTable.getSelectionModel().selectedIndexProperty().addListener((_, _, idx) -> {
            int i = idx.intValue();
            if (i >= 0 && i < imageFiles.size()) {
                showCurrentImage(imageFiles.get(i));
            }
        });

        hashTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        hashValueColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        hashDbTable.setItems(hashItems);

        hashDbDiffCombo.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        hashDbDiffCombo.getSelectionModel().select("");
        hashDbDiffCombo.setOnAction(_ -> refreshHashDb());

        filterField.textProperty().addListener(new TextFilterChangeListener(this));
        // Re-evaluate Register button state whenever the title or hash changes
        // (driven by OCR, wiki selection, or image load).
        titleField.textProperty().addListener((_, _, _) -> updateRegisterButtonState());
        hashField.textProperty().addListener((_, _, _) -> updateRegisterButtonState());

        SettingsRepository settingsRepo = new SettingsRepository();
        settings = settingsRepo.load();
        paramsMap = new ParamsRepository().load(settings.getOrDefault("params_json", "resources/params.json"));
        tesseractOcr = new TesseractOcr();
        discordWebhookClient = new DiscordWebhookClient();
        secretConfig = new SecretConfig();

        colorizeButton.setDisable(true);
        colorizeMissingButton.setDisable(true);

        loadHashDb();
        imageAnalysisService = new ImageAnalysisService(musicListRepo);
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

        // AC 旧曲 drives progress 0.0 → 0.5; 新曲 drives 0.5 → 1.0
        fetchAcList(http, WIKI_URL_OLD, collected, 1, 0.0, 0.5);
        fetchAcList(http, WIKI_URL_NEW, collected, 2, 0.5, 1.0);

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

    private void fetchAcList(HttpClient http, String url, Map<String, WikiSongRow> out, int urlIndex,
            double progressStart, double progressEnd) {
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

            Elements allTrs = doc.select("tr");
            int totalTrs = allTrs.size();
            for (int trIdx = 0; trIdx < totalTrs; trIdx++) {
                Element tr = allTrs.get(trIdx);

                // Throttle to one Platform.runLater every 10 rows.
                if (trIdx % 10 == 0 || trIdx == totalTrs - 1) {
                    final double prog = totalTrs == 0
                            ? progressEnd
                            : progressStart + (progressEnd - progressStart) * trIdx / totalTrs;
                    final int songCount = out.size();
                    Platform.runLater(() -> {
                        musicProgress.setProgress(prog);
                        musicLoadingLabel.setText("Loading BemaniWiki… " + songCount + " songs");
                    });
                }

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
     * Enables the Register button when all three required fields are valid: a
     * non-empty title, a 16-character lowercase hex jacket hash, and a non-blank
     * difficulty. Mirrors the Python guard in {@code register_song}
     * ({@code difficulty != '' and bool(pat.search(hash_jacket))}).
     */
    private void updateRegisterButtonState() {
        String title = titleField.getText().trim();
        String hash = hashField.getText().trim();
        String diff = difficultyCombo.getValue();
        boolean canRegister = !title.isEmpty() && hash.matches("[0-9a-f]{8,}") && diff != null && !diff.isBlank();
        registerButton.setDisable(!canRegister);
    }

    /**
     * Copies the current title field value to the system clipboard.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onCopyTitle(ActionEvent event) {
        String text = titleField.getText();
        if (text != null && !text.isBlank()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }
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
                boolean allDiffs = true; // registerAllDiffsCheck.isSelected();
                if (allDiffs) {
                    for (String d : List.of("nov", "adv", "exh", "APPEND")) {
                        musicListRepo.registerHash(hash, title, d);
                    }
                    appendLog("Registered for all diffs: " + title + " = " + hash);
                } else {
                    musicListRepo.registerHash(hash, title, diff);
                    appendLog("Registered: [" + diff + "] " + title + " = " + hash);
                }
                if (stateLabel != null && bundle != null) {
                    stateLabel.setText(bundle.getString("message.song.registered") + " (" + title + " / " + hash + ")");
                }
                refreshHashDb();
                registeredLabel.setText(String.valueOf(hashItems.size()));
            }
            sessionRegisteredCount++;
            // Capture the currently-selected file here on the FX thread so the
            // background webhook task can read it safely.
            int selIdx = filesTable.getSelectionModel().getSelectedIndex();
            File webhookSourceFile = (selIdx >= 0 && selIdx < imageFiles.size()) ? imageFiles.get(selIdx) : null;
            bgExecutor.submit(() -> sendWebhookOnRegister(title, diff, hash, hashInfo, webhookSourceFile));
        } catch (IOException e) {
            log.error("Failed to register hash", e);
            appendLog("ERROR: " + e.getMessage());
        }
        advanceToNext();
    }

    /**
     * Sends the Discord registration webhook after a successful hash registration.
     *
     * <p>
     * Mirrors Python {@code ocr_reporter.py:293–314} ({@code send_webhook}):
     * </p>
     * <ul>
     * <li>Webhook URL is read from {@code secrets.properties} via
     * {@link SecretConfig#getWebhookRegUrl()} ({@code webhook.reg.url}).</li>
     * <li>Message format matches the Python template using the same i18n keys.</li>
     * <li>Two image crops are attached — {@code info.png} (title/info region,
     * sub-cropped to 260 × 65 px) and {@code difficulty.png} (difficulty-band
     * region) — mirroring the two {@code webhook.add_file()} calls in Python.</li>
     * </ul>
     *
     * <p>
     * If no source file is available or image reading fails, only the text message
     * is sent. If the URL is not configured the call is silently skipped.
     * </p>
     *
     * @param title
     *            song title
     * @param difficulty
     *            difficulty string (e.g. {@code "nov"})
     * @param hashJacket
     *            jacket perceptual hash
     * @param hashInfo
     *            info-region hash (may be blank)
     * @param sourceFile
     *            the result-screen image file captured on the FX thread before this
     *            task was submitted; {@code null} if nothing was selected
     */
    private void sendWebhookOnRegister(String title, String difficulty, String hashJacket, String hashInfo,
            File sourceFile) {
        String webhookUrl = secretConfig.getWebhookRegUrl();
        if (webhookUrl.isBlank()) {
            log.debug("sendWebhookOnRegister: webhook.reg.url not configured in secrets, skipping");
            return;
        }

        // Build message — mirrors Python ocr_reporter.py:296-311
        StringBuilder msg = new StringBuilder();
        msg.append(bundle.getString("webhook.ocr.title")).append(": **").append(title).append("**\n");
        msg.append(" - ").append(bundle.getString("webhook.ocr.hash.jacket")).append(": **").append(hashJacket)
                .append("**");
        if (!hashInfo.isBlank()) {
            msg.append(" - ").append(bundle.getString("webhook.ocr.hash.info")).append(": **").append(hashInfo)
                    .append("**");
        }
        msg.append(" (").append(bundle.getString("webhook.ocr.difficulty")).append(": **")
                .append(difficulty.toUpperCase()).append("**)");

        if (sourceFile == null) {
            discordWebhookClient.sendMessage(webhookUrl, msg.toString());
            return;
        }

        try {
            BufferedImage awtImage = ImageIO.read(sourceFile);
            if (awtImage == null) {
                discordWebhookClient.sendMessage(webhookUrl, msg.toString());
                return;
            }

            // info.png — log_crop_info region, then sub-cropped to 260×65
            // mirrors: parts['info'].crop((0,0,260,65))
            int iSx = ParamUtils.getInt(paramsMap, "log_crop_info_sx", 0);
            int iSy = ParamUtils.getInt(paramsMap, "log_crop_info_sy", 0);
            int iW = ParamUtils.getInt(paramsMap, "log_crop_info_w", 260);
            int iH = ParamUtils.getInt(paramsMap, "log_crop_info_h", 65);
            BufferedImage infoFull = cropAndScale(awtImage, iSx, iSy, iW, iH, iW, iH);
            int subW = Math.min(260, infoFull.getWidth());
            int subH = Math.min(65, infoFull.getHeight());
            BufferedImage infoCrop = infoFull.getSubimage(0, 0, subW, subH);

            // difficulty.png — difficulty band region
            // mirrors: parts['difficulty'] (already cropped in cut_result_parts)
            int dSx = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sx", 55);
            int dSy = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sy", 870);
            int dW = ParamUtils.getInt(paramsMap, "log_crop_difficulty_w", 138);
            int dH = ParamUtils.getInt(paramsMap, "log_crop_difficulty_h", 30);
            BufferedImage diffBand = cropAndScale(awtImage, dSx, dSy, dW, dH, dW, dH);

            java.io.ByteArrayOutputStream baosInfo = new java.io.ByteArrayOutputStream();
            ImageIO.write(infoCrop, "png", baosInfo);
            java.io.ByteArrayOutputStream baosDiff = new java.io.ByteArrayOutputStream();
            ImageIO.write(diffBand, "png", baosDiff);

            // LinkedHashMap preserves insertion order → files[0]=info, files[1]=difficulty
            java.util.Map<String, byte[]> files = new java.util.LinkedHashMap<>();
            files.put("info.png", baosInfo.toByteArray());
            files.put("difficulty.png", baosDiff.toByteArray());

            discordWebhookClient.sendMessageWithMultipleImages(webhookUrl, msg.toString(), files);
        } catch (IOException e) {
            log.warn("sendWebhookOnRegister: failed to attach images, sending text only: {}", e.getMessage());
            discordWebhookClient.sendMessage(webhookUrl, msg.toString());
        }
    }

    /**
     * Sends the current {@code musiclist.xml} file to the Discord webhook on close.
     * Called from the application close event handler.
     */
    public void onWindowClose() {
        String webhookUrl = secretConfig != null ? secretConfig.getWebhookRegUrl() : "";
        if (webhookUrl.isBlank()) {
            log.debug("onWindowClose: webhook.reg.url not configured, skipping close webhook");
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
    // @FXML
    // public void onMerge(ActionEvent event) {
    // FileChooser chooser = new FileChooser();
    // chooser.setTitle("Select musiclist.xml to merge");
    // chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML
    // files", "*.xml"));
    // File selected = chooser.showOpenDialog(mergeButton.getScene().getWindow());
    // if (selected == null) {
    // log.debug("onMerge: no file selected (user cancelled chooser), skipping");
    // return;
    // }
    // bgExecutor.submit(() -> {
    // try {
    // if (musicListRepo == null) {
    // musicListRepo = new MusicListRepository();
    // }
    // int added = musicListRepo.merge(selected);
    // Platform.runLater(() -> {
    // refreshHashDb();
    // registeredLabel.setText(String.valueOf(hashItems.size()));
    // appendLog("Merge complete: " + added + " new entries imported from " +
    // selected.getName());
    // });
    // } catch (IOException e) {
    // log.error("Merge failed", e);
    // Platform.runLater(() -> appendLog("ERROR during merge: " + e.getMessage()));
    // }
    // });
    // }

    private void colorizeAll() {
        colorize(false);
    }

    private void colorizeUnregisteredOnly() {
        colorize(true);
    }

    /**
     * Iterates all image files, computes the jacket hash, and:
     * <ul>
     * <li>Colours the row in {@code filesTable} (blue = registered, grey =
     * unknown).</li>
     * <li>Renames any <em>unprocessed</em> file ({@code sdvx_YYYYMMDD_HHMMSS.png})
     * whose hash is found in the music list to
     * {@code sdvx_{title}_{DIFF}_{timestamp}.png}, mirroring Python's
     * {@code color_file} / {@code do_coloring} rename logic.</li>
     * <li>Updates {@code stateLabel} with progress and final statistics.</li>
     * </ul>
     *
     * <p>
     * When {@code missingOnly} is {@code true} (equivalent to Python
     * {@code do_coloring_missing}), only files matching the unprocessed filename
     * pattern are considered.
     * </p>
     *
     * @param missingOnly
     *            if {@code true}, process only unprocessed
     *            {@code sdvx_YYYYMMDD_HHMMSS.png} files; if {@code false}, process
     *            all {@code sdvx_*} result files
     */
    private void colorize(boolean missingOnly) {
        if (musicListRepo == null) {
            log.warn("colorize: musicListRepo not initialised, cannot colorize {} files", imageFiles.size());
            return;
        }

        long startMs = System.currentTimeMillis();

        Platform.runLater(() -> {
            if (stateLabel != null) {
                stateLabel.setText(bundle != null ? bundle.getString("message.coloring") : "Colorizing…");
            }
            filesProgress.setProgress(-1.0);
        });

        // Snapshot the current list so background thread does not race with UI
        List<File> snapshot = new ArrayList<>(imageFiles);
        int total = snapshot.size();

        // Keyed by list index → renamed File (only for successfully renamed files)
        Map<Integer, File> renames = new LinkedHashMap<>();
        // Keyed by filename (new name after rename, or original if no rename) → style
        Map<String, String> colorUpdates = new HashMap<>();
        int found = 0;
        int notFound = 0;

        for (int i = 0; i < total; i++) {
            File f = snapshot.get(i);

            // do_coloring_missing: only unprocessed sdvx_YYYYMMDD_HHMMSS.png files
            if (missingOnly && !OcrReporterHelper.isUnprocessedResultFilename(f.getName())) {
                continue;
            }
            // Skip files that are not result screenshots at all
            if (!isResultFilename(f.getName())) {
                continue;
            }

            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) {
                    continue;
                }

                String hash = hasher.hash(img);
                String[] match = musicListRepo.findByJacketHash(hash);

                if (match != null) {
                    String title = match[0];
                    String diff = (match[1] != null && !match[1].isBlank()) ? match[1] : "unk";
                    String style = "-fx-background-color: #dde0ff;";

                    if (OcrReporterHelper.isUnprocessedResultFilename(f.getName())) {
                        // Detect difficulty directly from the image band.
                        // The DB match[1] is unreliable when a song is registered for
                        // multiple difficulties: buildIndices last-write-wins, so APPEND
                        // overwrites the correct difficulty in the HashMap.
                        try {
                            String effectiveDiff = detectDifficultyForRename(img);

                            String lamp = imageAnalysisService != null
                                    ? imageAnalysisService.detectLampOnResult(img, paramsMap)
                                    : "uc";
                            int score = imageAnalysisService != null
                                    ? imageAnalysisService.getScoreOnResult(img, paramsMap)
                                    : 0;
                            String scorePrefix = toScorePrefix(score);
                            File renamed = renameResultFile(f, title, effectiveDiff, lamp, scorePrefix);
                            if (renamed != null) {
                                renames.put(i, renamed);
                                colorUpdates.put(renamed.getName(), style);
                            } else {
                                colorUpdates.put(f.getName(), style);
                            }
                        } catch (com.sdvxhelper.service.ImageCropNotParsed e) {
                            // Difficulty band cannot be classified — skip rename, mark grey.
                            log.error("colorize: cannot classify difficulty band for '{}': {}", f.getName(),
                                    e.getMessage());
                            final String errorMsg = e.getMessage();
                            Platform.runLater(() -> {
                                if (stateLabel != null) {
                                    stateLabel.setText(errorMsg);
                                }
                            });
                            colorUpdates.put(f.getName(), "-fx-background-color: #ffe0e0;");
                        }
                    } else {
                        colorUpdates.put(f.getName(), style);
                    }
                    found++;
                } else {
                    colorUpdates.put(f.getName(), "-fx-background-color: #dddddd;");
                    notFound++;
                }
            } catch (IOException e) {
                log.debug("Colorize error for {}: {}", f.getName(), e.getMessage());
            }

            // Throttle UI progress updates
            final int current = i + 1;
            if (current % 10 == 0 || current == total) {
                final int currentCopy = current;
                Platform.runLater(() -> {
                    filesProgress.setProgress((double) currentCopy / total);
                    if (stateLabel != null && bundle != null) {
                        stateLabel
                                .setText(bundle.getString("message.coloring") + " (" + currentCopy + "/" + total + ")");
                    }
                });
            }
        }

        final Map<Integer, File> finalRenames = new HashMap<>(renames);
        final Map<String, String> finalColors = new HashMap<>(colorUpdates);
        final int fFound = found;
        final int fNotFound = notFound;
        final double durSecs = Math.round((System.currentTimeMillis() - startMs) / 10.0) / 100.0;

        Platform.runLater(() -> {
            // Apply renames into the live imageFiles list
            for (Map.Entry<Integer, File> entry : finalRenames.entrySet()) {
                int idx = entry.getKey();
                if (idx < imageFiles.size()) {
                    imageFiles.set(idx, entry.getValue());
                }
            }
            // Merge color updates (old keys left behind are harmless)
            fileColorMap.putAll(finalColors);

            fileItems.setAll(imageFiles);
            filesTable.refresh();
            filesProgress.setProgress(1.0);

            String completionMsg = bundle != null
                    ? MessageFormat.format(bundle.getString("message.coloring.complete"), fNotFound, fFound, durSecs)
                    : "Colorize done. Found: " + fFound + ", not found: " + fNotFound;
            if (stateLabel != null) {
                stateLabel.setText(completionMsg);
            }
            filesLoadingLabel.setText(imageFiles.size() + " file(s) in folder");
        });
    }

    /**
     * Renames an unprocessed result file to the Python-compatible processed format:
     * {@code sdvx_{title}_{DIFF}_{lamp}_{scorePrefix}_{timestamp}.png}.
     *
     * <p>
     * Mirrors the rename logic in Python's {@code color_file} at
     * {@code ocr_reporter.py:581}.
     * </p>
     *
     * @param f
     *            the file to rename
     * @param title
     *            song title to embed (sanitised internally, capped at 120 chars)
     * @param difficulty
     *            difficulty code to embed (upper-cased, e.g. {@code "EXH"})
     * @param lamp
     *            clear lamp string (e.g. {@code "uc"}, {@code "puc"})
     * @param scorePrefix
     *            score prefix string from {@link #toScorePrefix(int)}
     * @return the renamed {@link File}, or {@code null} if renaming was skipped or
     *         failed
     */
    /**
     * Crops the difficulty band from {@code img} using the coordinates from
     * {@link #paramsMap} and delegates to
     * {@link ImageAnalysisService#detectDifficultyFromBand(BufferedImage)}.
     *
     * <p>
     * Used by {@link #colorize} so the renamed filename always reflects the
     * difficulty visible in the screenshot, not the DB registration entry (which
     * returns the last-stored difficulty when a song is registered across multiple
     * difficulty groups).
     * </p>
     *
     * @param img
     *            full result-screen {@link BufferedImage}
     * @return detected difficulty string, or {@code null} if detection fails
     */
    /**
     * Crops the difficulty band from {@code img} using {@link #paramsMap}
     * coordinates and delegates to
     * {@link ImageAnalysisService#detectDifficultyFromBand(BufferedImage)}.
     *
     * @param img
     *            full result-screen image
     * @return detected difficulty string
     * @throws com.sdvxhelper.service.ImageCropNotParsed
     *             propagated from the service when the band cannot be classified
     */
    private String detectDifficultyForRename(BufferedImage img) throws com.sdvxhelper.service.ImageCropNotParsed {
        int dSx = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sx", 55);
        int dSy = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sy", 870);
        int dW = ParamUtils.getInt(paramsMap, "log_crop_difficulty_w", 138);
        int dH = ParamUtils.getInt(paramsMap, "log_crop_difficulty_h", 30);
        BufferedImage diffBand = cropAndScale(img, dSx, dSy, dW, dH, dW, dH);
        return ImageAnalysisService.detectDifficultyFromBand(diffBand);
    }

    private File renameResultFile(File f, String title, String difficulty, String lamp, String scorePrefix) {
        String sanitized = OcrReporterHelper.sanitizeForFilename(title);
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120);
        }
        String timestamp = extractTimestampFromFilename(f.getName());
        String newName = "sdvx_" + sanitized + "_" + difficulty.toUpperCase() + "_" + lamp + "_" + scorePrefix + "_"
                + timestamp + ".png";
        File newFile = new File(f.getParent(), newName);
        if (newFile.equals(f)) {
            return f;
        }
        if (newFile.exists()) {
            log.debug("renameResultFile: target already exists, skipping: {}", newName);
            return null;
        }
        if (f.renameTo(newFile)) {
            log.info("Renamed: {} → {}", f.getName(), newFile.getName());
            return newFile;
        }
        log.warn("renameResultFile: failed to rename {} to {}", f.getName(), newName);
        return null;
    }

    /**
     * Converts an integer score to the filename prefix used by Python's
     * {@code color_file}: {@code str(cur)[:-4]} removes the trailing four zeroes,
     * so {@code 9970000} becomes {@code "997"} and {@code 10000000} becomes
     * {@code "1000"}.
     *
     * @param score
     *            detected score (0–10 000 000)
     * @return score prefix string; at least {@code "0"} even for a zero score
     */
    private static String toScorePrefix(int score) {
        String s = String.valueOf(score);
        return s.length() > 4 ? s.substring(0, s.length() - 4) : s;
    }

    /**
     * Extracts the {@code YYYYMMDD_HHMMSS} timestamp embedded in a result filename.
     * Falls back to the current time if the pattern is not found.
     *
     * @param filename
     *            file base name such as {@code sdvx_20260512_185427.png}
     * @return timestamp string of the form {@code YYYYMMDD_HHMMSS}
     */
    private static String extractTimestampFromFilename(String filename) {
        java.util.regex.Matcher m = Pattern.compile("(\\d{8}_\\d{6})").matcher(filename);
        if (m.find()) {
            return m.group(1);
        }
        return new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
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
                hashInfoField.clear();
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

            // Info hash: average hash of the info region (mirrors Python
            // imagehash.average_hash(parts['info'], 10))
            String infoHash = hasher.hash(info);
            hashInfoField.setText(infoHash);

            String hash = hasher.hash(awtImage);
            hashField.setText(hash);

            // Detect difficulty: DB lookup → filename → band-image colour analysis
            String detectedDiff = detectDifficulty(f, hash, diff);
            if (detectedDiff != null) {
                difficultyCombo.setValue(detectedDiff);
            }

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

    /**
     * Determines the difficulty for the given image file using three strategies in
     * order of reliability:
     * <ol>
     * <li>Jacket-hash lookup in the local music list (exact registered entry).</li>
     * <li>Difficulty token embedded in the filename for already-processed files
     * (e.g. {@code sdvx_Title_EXH_uc_997_20260512.png}).</li>
     * <li>Colour analysis of the cropped difficulty-band image, mirroring Python
     * {@code GenSummary.ocr()} lines 581–591 in {@code gen_summary.py}.</li>
     * </ol>
     *
     * @param f
     *            image file currently displayed
     * @param jacketHash
     *            perceptual hash already computed for the jacket crop
     * @param diffBand
     *            difficulty-band crop already computed by
     *            {@link #showCurrentImage}; may be {@code null}
     * @return difficulty string (e.g. {@code "nov"}, {@code "exh"}) or {@code null}
     *         if all three strategies fail
     */
    /**
     * Determines the difficulty for the given image file using three strategies in
     * order of reliability:
     * <ol>
     * <li>Colour analysis of the cropped difficulty-band image — most accurate
     * because it reads the actual badge shown on screen. Applied first to avoid
     * wrong results when a song is registered under multiple difficulties (in that
     * case the DB returns whichever difficulty was stored last, typically
     * {@code APPEND}).</li>
     * <li>Difficulty token embedded in the filename for already-processed files
     * (e.g. {@code sdvx_Title_EXH_uc_997_20260512.png}).</li>
     * <li>Jacket-hash lookup in the local music list as a last resort when band
     * analysis cannot produce a clear result and no filename token exists.</li>
     * </ol>
     *
     * @param f
     *            image file currently displayed
     * @param jacketHash
     *            perceptual hash already computed for the jacket crop
     * @param diffBand
     *            difficulty-band crop already computed by
     *            {@link #showCurrentImage}; may be {@code null}
     * @return difficulty string (e.g. {@code "nov"}, {@code "exh"}) or {@code null}
     *         if all three strategies fail
     */
    private String detectDifficulty(File f, String jacketHash, BufferedImage diffBand) {
        // 1 — colour analysis of the difficulty band (most direct: reads the image)
        try {
            return detectDifficultyFromBand(diffBand);
        } catch (com.sdvxhelper.service.ImageCropNotParsed e) {
            log.warn("detectDifficulty: band detection failed for {}: {}", f.getName(), e.getMessage());
            if (stateLabel != null) {
                stateLabel.setText(e.getMessage());
            }
        }
        // 2 — parse difficulty token from processed filename
        String fromFilename = parseDifficultyFromFilename(f.getName());
        if (fromFilename != null) {
            return fromFilename;
        }
        // 3 — DB lookup as last resort (unreliable for multi-registered songs)
        if (musicListRepo != null) {
            String[] match = musicListRepo.findByJacketHash(jacketHash);
            if (match != null && match[1] != null && !match[1].isBlank()) {
                return match[1];
            }
        }
        return null;
    }

    /**
     * Extracts a difficulty token from a processed result filename. Looks for
     * {@code _NOV_}, {@code _ADV_}, {@code _EXH_}, or {@code _APPEND_} (case-
     * insensitive) embedded between underscores.
     *
     * @param filename
     *            file base name to inspect
     * @return lower-case difficulty token or {@code null} if not found
     */
    private static String parseDifficultyFromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String upper = filename.toUpperCase();
        if (upper.contains("_APPEND_")) {
            return "APPEND";
        }
        if (upper.contains("_EXH_")) {
            return "exh";
        }
        if (upper.contains("_ADV_")) {
            return "adv";
        }
        if (upper.contains("_NOV_")) {
            return "nov";
        }
        return null;
    }

    /**
     * Delegates to
     * {@link ImageAnalysisService#detectDifficultyFromBand(BufferedImage)}.
     *
     * @param diffBand
     *            difficulty-band image (any resolution)
     * @return detected difficulty string
     * @throws com.sdvxhelper.service.ImageCropNotParsed
     *             propagated from the service when the band cannot be classified
     */
    private static String detectDifficultyFromBand(BufferedImage diffBand)
            throws com.sdvxhelper.service.ImageCropNotParsed {
        return ImageAnalysisService.detectDifficultyFromBand(diffBand);
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

    // onSelectMusic and onSelectResult are superseded by selectedItemProperty /
    // selectedIndexProperty listeners wired in initialize(); these stubs are
    // retained so that any lingering FXML onMouseClicked references compile.

    public Map<String, String> getFileColorMap() {
        return fileColorMap;
    }

    public FilteredList<WikiSongRow> getFilteredWikiSongs() {
        return filteredWikiSongs;
    }
}
