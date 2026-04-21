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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.imageio.ImageIO;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.SettingsRepository;
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

    private static final String WIKI_URL_KONASTE = "https://bemaniwiki.com/index.php?%A5%B3%A5%CA%A5%B9%A5%C6"
            + "/SOUND+VOLTEX+EXCEED+GEAR/%B3%DA%B6%CA%A5%EA%A5%B9%A5%C8";
    private static final String WIKI_URL_OLD = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR"
            + "/%B5%EC%B6%CA%A5%EA%A5%B9%A5%C8";
    private static final String WIKI_URL_NEW = "https://bemaniwiki.com/index.php?SOUND+VOLTEX+EXCEED+GEAR"
            + "/%BF%B7%B6%CA%A5%EA%A5%B9%A5%C8";
    private static final String STOP_PREFIX = "[STOP]";
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    // Crop coordinates from params.json (log_crop_* entries)
    // jacket: sx=57, sy=916, w=263, h=263 → resize to 100×100 for display
    private static final int JACKET_SX = 57, JACKET_SY = 916, JACKET_W = 263, JACKET_H = 263;
    // difficulty band: sx=55, sy=870, w=138, h=30 → display at 137×29
    private static final int DIFF_SX = 55, DIFF_SY = 870, DIFF_W = 138, DIFF_H = 30;
    // info strip: sx=379, sy=1001, w=527, h=65 → display at 526×64
    private static final int INFO_SX = 379, INFO_SY = 1001, INFO_W = 527, INFO_H = 65;

    // -------------------------------------------------------------------------
    // FXML fields
    // -------------------------------------------------------------------------

    @FXML
    private Label lblRegistered;
    @FXML
    private Label lblState;
    @FXML
    private Label lblMusicLoading;
    @FXML
    private Label lblFilesLoading;
    @FXML
    private ProgressBar progMusic;
    @FXML
    private ProgressBar progFiles;
    @FXML
    private ImageView imgJacket;
    @FXML
    private ImageView imgDifficulty;
    @FXML
    private ImageView imgInfo;
    @FXML
    private TextField txtHash;
    @FXML
    private TextField txtHashInfo;
    @FXML
    private TextField txtShaJacket;
    @FXML
    private TextField txtOcrTitle;
    @FXML
    private TextField txtTitle;
    @FXML
    private ComboBox<String> cmbDifficulty;
    @FXML
    private ComboBox<String> cmbDiffDb;
    @FXML
    private Button btnRegister;
    @FXML
    private Button btnSkip;
    @FXML
    private Button btnColorize;
    @FXML
    private Button btnColorizeMissing;
    @FXML
    private Button btnClearFilter;
    @FXML
    private TextArea txtLog;
    @FXML
    private ComboBox<String> cmbLanguage;
    @FXML
    private TextField txtFilter;
    @FXML
    private TableView<WikiSongRow> tblMusic;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicTitle;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicArtist;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicBpm;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicNov;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicAdv;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicExh;
    @FXML
    private TableColumn<WikiSongRow, String> colMusicAppend;
    @FXML
    private TableView<File> tblFiles;
    @FXML
    private TableColumn<File, String> colFileName;
    @FXML
    private TableView<HashEntry> tblHashDb;
    @FXML
    private TableColumn<HashEntry, String> colHashTitle;
    @FXML
    private TableColumn<HashEntry, String> colHashValue;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final PerceptualHasher hasher = new PerceptualHasher();
    private final List<File> imageFiles = new ArrayList<>();

    private final ObservableList<WikiSongRow> wikiSongs = FXCollections.observableArrayList();
    private FilteredList<WikiSongRow> filteredWikiSongs;
    private final ObservableList<File> fileItems = FXCollections.observableArrayList();
    private final ObservableList<HashEntry> hashItems = FXCollections.observableArrayList();

    /** Keyed by filename → JavaFX inline style string. */
    private final Map<String, String> fileColorMap = new HashMap<>();

    private MusicListRepository musicListRepo;

    private final ExecutorService bgExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ocr-bg");
        t.setDaemon(true);
        return t;
    });

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * One row in the BemaniWiki song table ({@code tblMusic}).
     */
    public static class WikiSongRow {
        private final String title;
        private final String artist;
        private final String bpm;
        private final String nov;
        private final String adv;
        private final String exh;
        private final String append;

        /**
         * @param title
         *            song title
         * @param artist
         *            artist name
         * @param bpm
         *            BPM string
         * @param nov
         *            novice level
         * @param adv
         *            advanced level
         * @param exh
         *            exhaust level
         * @param append
         *            maximum/append level (may be {@code null})
         */
        public WikiSongRow(String title, String artist, String bpm, String nov, String adv, String exh, String append) {
            this.title = title;
            this.artist = artist;
            this.bpm = bpm;
            this.nov = nov;
            this.adv = adv;
            this.exh = exh;
            this.append = append;
        }

        /** @return the song title */
        public String getTitle() {
            return title;
        }
        /** @return the artist name */
        public String getArtist() {
            return artist;
        }
        /** @return the BPM string */
        public String getBpm() {
            return bpm;
        }
        /** @return novice chart level */
        public String getNov() {
            return nov;
        }
        /** @return advanced chart level */
        public String getAdv() {
            return adv;
        }
        /** @return exhaust chart level */
        public String getExh() {
            return exh;
        }
        /** @return maximum/append chart level */
        public String getAppend() {
            return append;
        }
    }

    /**
     * Row model for the hash database view.
     */
    public static class HashEntry {
        private final String title;
        private final String hash;

        /**
         * @param title
         *            song title
         * @param hash
         *            jacket hash value
         */
        public HashEntry(String title, String hash) {
            this.title = title;
            this.hash = hash;
        }

        /** @return the song title */
        public String getTitle() {
            return title;
        }
        /** @return the jacket hash */
        public String getHash() {
            return hash;
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cmbDifficulty.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        cmbDifficulty.getSelectionModel().select("exh");
        if (cmbDiffDb != null) {
            cmbDiffDb.getItems().setAll("", "nov", "adv", "exh", "APPEND");
            cmbDiffDb.getSelectionModel().select("exh");
            cmbDiffDb.setOnAction(_ -> refreshHashDb());
        }

        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(_ -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));

        if (colMusicTitle != null) {
            colMusicTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
            colMusicArtist.setCellValueFactory(new PropertyValueFactory<>("artist"));
            colMusicBpm.setCellValueFactory(new PropertyValueFactory<>("bpm"));
            colMusicNov.setCellValueFactory(new PropertyValueFactory<>("nov"));
            colMusicAdv.setCellValueFactory(new PropertyValueFactory<>("adv"));
            colMusicExh.setCellValueFactory(new PropertyValueFactory<>("exh"));
            colMusicAppend.setCellValueFactory(new PropertyValueFactory<>("append"));
            filteredWikiSongs = new FilteredList<>(wikiSongs, _ -> true);
            tblMusic.setItems(filteredWikiSongs);
            tblMusic.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tblMusic.getSelectionModel().selectedItemProperty().addListener((_, _, nv) -> {
                if (nv != null && txtTitle != null) {
                    txtTitle.setText(nv.getTitle());
                }
            });
        }
        if (colFileName != null) {
            colFileName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
            tblFiles.setItems(fileItems);
            tblFiles.getSelectionModel().selectedIndexProperty().addListener((_, _, idx) -> {
                if (idx != null && idx.intValue() >= 0) {
                    showCurrentImage(imageFiles.get(idx.intValue()));
                }
            });
            tblFiles.setRowFactory(_ -> new TableRow<File>() {
                @Override
                protected void updateItem(File item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) {
                        setStyle("");
                    } else {
                        setStyle(fileColorMap.getOrDefault(item.getName(), ""));
                    }
                }
            });
        }
        if (colHashTitle != null) {
            colHashTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
            colHashValue.setCellValueFactory(new PropertyValueFactory<>("hash"));
            tblHashDb.setItems(hashItems);
        }

        if (txtFilter != null) {
            txtFilter.textProperty().addListener((_, _, nv) -> {
                if (filteredWikiSongs != null) {
                    String q = nv == null ? "" : nv.toLowerCase();
                    filteredWikiSongs.setPredicate(m -> q.isBlank() || m.getTitle().toLowerCase().contains(q));
                }
            });
        }

        if (btnColorize != null) {
            btnColorize.setDisable(true);
        }
        if (btnColorizeMissing != null) {
            btnColorizeMissing.setDisable(true);
        }

        loadHashDb();
        bgExecutor.submit(this::loadBemaniWiki);
        autoLoadFromSettings();
    }

    // -------------------------------------------------------------------------
    // BemaniWiki loading
    // -------------------------------------------------------------------------

    private void loadBemaniWiki() {
        Platform.runLater(() -> {
            if (lblMusicLoading != null) {
                lblMusicLoading.setText("Loading BemaniWiki…");
            }
            if (progMusic != null) {
                progMusic.setProgress(-1.0);
            }
        });

        Map<String, WikiSongRow> collected = new HashMap<>();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

        // First URL: コナステ list (simple structure — rows of 7-8 tds)
        fetchKonasteList(http, WIKI_URL_KONASTE, collected);

        // Second and third URLs: AC 旧曲 and 新曲 (rowspan handling)
        fetchAcList(http, WIKI_URL_OLD, collected, 1);
        fetchAcList(http, WIKI_URL_NEW, collected, 2);

        List<WikiSongRow> rows = new ArrayList<>(collected.values());
        rows.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));

        Platform.runLater(() -> {
            wikiSongs.setAll(rows);
            if (lblMusicLoading != null) {
                lblMusicLoading.setText("BemaniWiki: " + rows.size() + " songs loaded");
            }
            if (progMusic != null) {
                progMusic.setProgress(1.0);
            }
            enableColorizeIfReady();
            log.info("BemaniWiki loaded {} songs", rows.size());
        });
    }

    private void fetchKonasteList(HttpClient http, String url, Map<String, WikiSongRow> out) {
        try {
            String html = fetchUrl(http, url);
            if (html == null) {
                return;
            }
            Document doc = Jsoup.parse(html);
            for (Element tr : doc.select("tr")) {
                Elements tds = tr.select("td");
                int n = tds.size();
                if (n != 7 && n != 8) {
                    continue;
                }
                if ("BPM".equals(tds.get(2).text())) {
                    continue;
                }
                String title = tds.get(0).text();
                String artist = tds.get(1).text();
                String bpm = tds.get(2).text();
                String nov = parseLevel(tds.get(3).text());
                String adv = parseLevel(tds.get(4).text());
                String exh = parseLevel(tds.get(5).text());
                String appendTxt = tds.get(6).text();
                String append = (appendTxt.isEmpty() || "-".equals(appendTxt)) ? null : parseLevel(appendTxt);
                out.put(title, new WikiSongRow(title, artist, bpm, nov, adv, exh, append));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Konaste list: {}", e.getMessage());
        }
    }

    private void fetchAcList(HttpClient http, String url, Map<String, WikiSongRow> out, int urlIndex) {
        try {
            String html = fetchUrl(http, url);
            if (html == null) {
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
        } catch (Exception e) {
            log.warn("Failed to fetch AC wiki list {}: {}", urlIndex, e.getMessage());
        }
    }

    private String parseLevel(String text) {
        if (text == null || text.isBlank()) {
            return "??";
        }
        String clean = text.startsWith(STOP_PREFIX) ? text.substring(STOP_PREFIX.length()).trim() : text;
        return clean.isBlank() ? "??" : clean;
    }

    private String lastDigits(String text) {
        if (text == null || text.isBlank()) {
            return "??";
        }
        String clean = text.startsWith(STOP_PREFIX) ? text.substring(STOP_PREFIX.length()).trim() : text;
        Matcher m = DIGIT_PATTERN.matcher(clean);
        String last = "??";
        while (m.find()) {
            last = m.group();
        }
        return last;
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
        try {
            musicListRepo = new MusicListRepository();
            refreshHashDb();
            if (lblRegistered != null) {
                lblRegistered.setText(String.valueOf(hashItems.size()));
            }
        } catch (Exception e) {
            log.warn("Failed to load musiclist.xml: {}", e.getMessage());
        }
    }

    private void refreshHashDb() {
        if (musicListRepo == null || hashItems == null) {
            return;
        }
        String diff = cmbDiffDb != null ? cmbDiffDb.getValue() : "";
        List<HashEntry> rows = new ArrayList<>();
        for (com.sdvxhelper.model.HashEntry h : musicListRepo.getHashesForDifficulty(diff)) {
            rows.add(new HashEntry(h.getTitle(), h.getHash()));
        }
        hashItems.setAll(rows);
    }

    private void autoLoadFromSettings() {
        try {
            SettingsRepository repo = new SettingsRepository();
            Map<String, String> settings = repo.load();
            String dir = settings.get("autosave_dir");
            if (dir != null && !dir.isBlank()) {
                File f = new File(dir);
                if (f.isDirectory()) {
                    loadFolder(f);
                }
            }
        } catch (Exception e) {
            log.debug("No autosave_dir configured or invalid: {}", e.getMessage());
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
        if (txtFilter != null) {
            txtFilter.clear();
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
        String hash = txtHash.getText();
        String title = txtTitle.getText().trim();
        String diff = cmbDifficulty.getValue();
        if (hash.isBlank() || title.isBlank()) {
            appendLog("ERROR: hash or title is empty");
            return;
        }
        try {
            if (musicListRepo != null) {
                musicListRepo.registerHash(hash, title, diff);
            }
            appendLog("Registered: [" + diff + "] " + title + " = " + hash);
        } catch (Exception e) {
            log.error("Failed to register hash", e);
            appendLog("ERROR: " + e.getMessage());
        }
        advanceToNext();
    }

    /**
     * Skips the current image without registering it.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSkip(ActionEvent event) {
        int idx = tblFiles == null ? -1 : tblFiles.getSelectionModel().getSelectedIndex();
        appendLog("Skipped: " + (idx >= 0 && idx < imageFiles.size() ? imageFiles.get(idx).getName() : "?"));
        advanceToNext();
    }

    private void advanceToNext() {
        if (tblFiles == null) {
            return;
        }
        int next = tblFiles.getSelectionModel().getSelectedIndex() + 1;
        if (next < imageFiles.size()) {
            tblFiles.getSelectionModel().select(next);
            tblFiles.scrollTo(next);
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

    private void colorizeAll() {
        colorize(false);
    }

    private void colorizeUnregisteredOnly() {
        colorize(true);
    }

    /**
     * Iterates all image files, computes the jacket hash, and colours the row in
     * {@code tblFiles} based on whether the hash is registered in
     * {@code musicListRepo}.
     *
     * @param missingOnly
     *            if {@code true}, skip files that are already coloured
     */
    private void colorize(boolean missingOnly) {
        if (musicListRepo == null) {
            return;
        }
        int total = imageFiles.size();
        for (int i = 0; i < total; i++) {
            final File f = imageFiles.get(i);
            final int progress = i;
            if (missingOnly) {
                String existing = fileColorMap.get(f.getName());
                if (existing != null && !existing.isBlank()) {
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
            if (i % 10 == 0 || i == total - 1) {
                final double prog = (double) (progress + 1) / total;
                Platform.runLater(() -> {
                    if (progFiles != null) {
                        progFiles.setProgress(prog);
                    }
                    if (tblFiles != null) {
                        tblFiles.refresh();
                    }
                });
            }
        }
        Platform.runLater(() -> {
            if (tblFiles != null) {
                tblFiles.refresh();
            }
            if (lblFilesLoading != null) {
                lblFilesLoading.setText("Colorize done (" + total + " files)");
            }
        });
    }

    private void enableColorizeIfReady() {
        if (!fileItems.isEmpty()) {
            if (btnColorize != null) {
                btnColorize.setDisable(false);
            }
            if (btnColorizeMissing != null) {
                btnColorizeMissing.setDisable(false);
            }
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

        if (!imageFiles.isEmpty()) {
            if (tblFiles != null) {
                tblFiles.getSelectionModel().select(0);
            }
        }
        enableColorizeIfReady();
        appendLog("Loaded " + imageFiles.size() + " image(s) from " + dir.getAbsolutePath());
        if (lblFilesLoading != null) {
            Platform.runLater(() -> lblFilesLoading.setText(imageFiles.size() + " file(s) in folder"));
        }
    }

    private void showCurrentImage(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        try {
            BufferedImage awtImage = ImageIO.read(f);
            if (awtImage == null) {
                return;
            }

            if (imgJacket != null) {
                // jacket_org: crop at (57,916) 263×263, then resize to 100×100
                BufferedImage jacket = cropAndScale(awtImage, JACKET_SX, JACKET_SY, JACKET_W, JACKET_H, 100, 100);
                imgJacket.setImage(toFxImage(jacket));
            }
            if (imgDifficulty != null) {
                // difficulty_org: crop at (55,870) 138×30, display at 137×29
                BufferedImage diff = cropAndScale(awtImage, DIFF_SX, DIFF_SY, DIFF_W, DIFF_H, 137, 29);
                imgDifficulty.setImage(toFxImage(diff));
            }
            if (imgInfo != null) {
                // info: crop at (379,1001) 527×65, display at 526×64
                BufferedImage info = cropAndScale(awtImage, INFO_SX, INFO_SY, INFO_W, INFO_H, 526, 64);
                imgInfo.setImage(toFxImage(info));
            }

            String hash = hasher.hash(awtImage);
            txtHash.setText(hash);
            txtTitle.clear();
            txtOcrTitle.clear();
        } catch (IOException e) {
            log.error("Failed to load image {}", f.getAbsolutePath(), e);
            appendLog("ERROR loading: " + f.getName());
        }
    }

    /**
     * Crops a region from {@code src} and scales it to the requested output size,
     * clamping the crop rectangle to image bounds to avoid exceptions.
     */
    private BufferedImage cropAndScale(BufferedImage src, int x, int y, int w, int h, int outW, int outH) {
        int sx = Math.max(0, Math.min(x, src.getWidth() - 1));
        int sy = Math.max(0, Math.min(y, src.getHeight() - 1));
        int sw = Math.max(1, Math.min(w, src.getWidth() - sx));
        int sh = Math.max(1, Math.min(h, src.getHeight() - sy));
        BufferedImage cropped = src.getSubimage(sx, sy, sw, sh);
        if (sw == outW && sh == outH) {
            return cropped;
        }
        BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        scaled.createGraphics().drawImage(cropped.getScaledInstance(outW, outH, java.awt.Image.SCALE_SMOOTH), 0, 0,
                null);
        return scaled;
    }

    private Image toFxImage(BufferedImage awt) {
        return SwingFXUtils.toFXImage(awt, null);
    }

    private void appendLog(String line) {
        if (txtLog != null) {
            Platform.runLater(() -> txtLog.appendText(line + "\n"));
        }
    }
}
