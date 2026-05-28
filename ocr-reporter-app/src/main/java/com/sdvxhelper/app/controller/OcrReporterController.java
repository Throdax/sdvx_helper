package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
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
import javax.imageio.ImageIO;

import com.sdvxhelper.app.controller.listeners.FileSelectionIndexListener;
import com.sdvxhelper.app.controller.listeners.RegisterButtonStateListener;
import com.sdvxhelper.app.controller.listeners.ResultFilesTableRowListener;
import com.sdvxhelper.app.controller.listeners.TextFilterChangeListener;
import com.sdvxhelper.app.controller.listeners.WikiSongSelectionListener;
import com.sdvxhelper.app.controller.model.HashEntry;
import com.sdvxhelper.app.controller.model.WikiSongRow;
import com.sdvxhelper.app.controller.service.BemaniWikiService;
import com.sdvxhelper.app.controller.service.ColorizerCallback;
import com.sdvxhelper.app.controller.service.ColorizerService;
import com.sdvxhelper.app.controller.service.RegistrationWebhookService;
import com.sdvxhelper.config.SecretConfig;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.TesseractLanguageInstaller;
import com.sdvxhelper.ocr.TesseractOcr;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.service.ImageAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the OCR Reporter maintainer tool ({@code ocr_reporter.fxml}).
 *
 * <p>
 * Acts as a thin orchestrator: delegates BemaniWiki loading to
 * {@link BemaniWikiService}, colorize runs to {@link ColorizerService}, Discord
 * webhook sends to {@link RegistrationWebhookService}, and property listeners
 * to their own named listener classes. This controller retains only FXML field
 * declarations, lifecycle wiring, action handlers, and
 * {@link #showCurrentImage} (which directly manipulates multiple {@code @FXML}
 * views).
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class OcrReporterController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(OcrReporterController.class);

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
    private Button suggestButton;
    @FXML
    private Button skipButton;
    @FXML
    private Button colorizeButton;
    @FXML
    private Button colorizeMissingButton;
    @FXML
    private Button clearFilterButton;
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

    /** Detection parameters from {@code params.json} (log_crop_* entries). */
    private Map<String, String> paramsMap = new java.util.LinkedHashMap<>();

    /**
     * The info-strip crop from the most recently selected result image, reused by
     * the Suggest button to avoid a redundant image read.
     */
    private BufferedImage currentInfoCrop;

    /**
     * Extended-language Tesseract instance for the Suggest feature. Handles
     * Japanese, Latin-script (incl. French), and Greek song titles.
     */
    private TesseractOcr suggestOcr;

    private DiscordWebhookClient discordWebhookClient;
    private SecretConfig secretConfig;
    private Map<String, String> settings;

    /** Count of hashes registered in this session. */
    private int sessionRegisteredCount = 0;

    /** Kept for i18n lookups inside background threads (e.g. colorize). */
    private ResourceBundle bundle;

    // -------------------------------------------------------------------------
    // Services
    // -------------------------------------------------------------------------

    private BemaniWikiService bemaniWikiService;
    private ColorizerService colorizerService;
    private RegistrationWebhookService registrationWebhookService;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.bundle = resources;

        difficultyCombo.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        difficultyCombo.getSelectionModel().select("exh");

        RegisterButtonStateListener stateListener = new RegisterButtonStateListener(this::updateRegisterButtonState);
        difficultyCombo.valueProperty().addListener(stateListener);

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
        musicTable.getSelectionModel().selectedItemProperty()
                .addListener(new WikiSongSelectionListener(titleField, this::updateRegisterButtonState));

        fileNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        fileNameColumn.setSortable(false);
        filesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        filesTable.setItems(fileItems);
        filesTable.setRowFactory(_ -> new ResultFilesTableRowListener(this));
        filesTable.getSelectionModel().selectedIndexProperty()
                .addListener(new FileSelectionIndexListener(imageFiles, this::showCurrentImage));

        hashTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        hashValueColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));
        hashDbTable.setItems(hashItems);

        hashDbDiffCombo.getItems().setAll("", "nov", "adv", "exh", "APPEND");
        hashDbDiffCombo.getSelectionModel().select("");
        hashDbDiffCombo.setOnAction(_ -> refreshHashDb());

        filterField.textProperty().addListener(new TextFilterChangeListener(this));
        titleField.textProperty().addListener(stateListener);
        hashField.textProperty().addListener(stateListener);

        SettingsRepository settingsRepo = new SettingsRepository();
        settings = settingsRepo.load();
        paramsMap = new ParamsRepository().load(settings.getOrDefault("params_json", "resources/params.json"));
        suggestOcr = new TesseractOcr("jpn+eng+fra+ell");
        discordWebhookClient = new DiscordWebhookClient();
        secretConfig = new SecretConfig();

        loadHashDb();
        imageAnalysisService = new ImageAnalysisService(musicListRepo);

        bemaniWikiService = new BemaniWikiService(bgExecutor);
        colorizerService = new ColorizerService(musicListRepo, hasher, imageAnalysisService, paramsMap, bundle);
        registrationWebhookService = new RegistrationWebhookService(secretConfig, discordWebhookClient, bundle,
                paramsMap);

        colorizeButton.setDisable(true);
        colorizeMissingButton.setDisable(true);

        startSuggestLanguageInstall();

        musicLoadingLabel.setText("Loading BemaniWiki…");
        musicProgress.setProgress(-1.0);
        bemaniWikiService.loadAsync(progress -> Platform.runLater(() -> musicProgress.setProgress(progress)),
                status -> Platform.runLater(() -> musicLoadingLabel.setText(status)), rows -> Platform.runLater(() -> {
                    wikiSongs.setAll(rows);
                    musicLoadingLabel.setText("BemaniWiki: " + rows.size() + " songs loaded");
                    musicProgress.setProgress(1.0);
                    enableColorizeIfReady();
                    log.info("BemaniWiki loaded {} songs", rows.size());
                }));

        autoLoadFromSettings();
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
        Map<String, String> autoSettings = repo.load();
        String dir = autoSettings.get("autosave_dir");
        if (dir != null && !dir.isBlank()) {
            File f = new File(dir);
            if (f.isDirectory()) {
                loadFolder(f);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Suggest language install
    // -------------------------------------------------------------------------

    /**
     * Checks for missing Tesseract language files and downloads them in the
     * background. The Suggest button is disabled and relabelled while the download
     * is in progress, and is re-enabled on success.
     */
    private void startSuggestLanguageInstall() {
        String tessdataDir = System.getProperty("TESSDATA_PREFIX", "resources/tessdata");
        List<String> languages = List.of("jpn", "eng", "fra", "ell");
        String installingText = bundle != null
                ? bundle.getString("button.suggest.installing")
                : "Installing language files…";
        String suggestText = bundle != null ? bundle.getString("button.suggest.title") : "Suggest (experimental)";

        suggestButton.setDisable(true);
        suggestButton.setText(installingText);

        bgExecutor.submit(() -> {
            boolean allPresent = TesseractLanguageInstaller.ensureLanguages(languages, tessdataDir);
            Platform.runLater(() -> {
                suggestButton.setText(suggestText);
                suggestButton.setDisable(!allPresent);
                if (!allPresent) {
                    appendLog("Suggest: language install failed — button remains disabled");
                }
            });
        });
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
     * Runs Tesseract OCR on the title line of the cached info crop and writes the
     * recognised text into the BemaniWiki search/filter field.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSuggestTitle(ActionEvent event) {
        int selIdx = filesTable.getSelectionModel().getSelectedIndex();
        if (selIdx < 0 || selIdx >= imageFiles.size()) {
            log.debug("onSuggestTitle: no file selected, skipping");
            return;
        }
        if (currentInfoCrop == null) {
            log.debug("onSuggestTitle: no info crop available for current selection, skipping");
            appendLog("Suggest: no result image loaded — select a result screenshot first");
            return;
        }
        File selectedFile = imageFiles.get(selIdx);
        suggestButton.setDisable(true);

        final BufferedImage infoCrop = currentInfoCrop;
        bgExecutor.submit(() -> {
            try {
                int titleLeftTrim = 10;
                BufferedImage titleLine = infoCrop.getSubimage(titleLeftTrim, 0, infoCrop.getWidth() - titleLeftTrim,
                        infoCrop.getHeight() / 2);
                OcrReporterHelper.saveDebugPart(titleLine, "info_title");
                log.info("onSuggestTitle: OCR attempt on title-line sub-crop ({}x{}) from '{}'", titleLine.getWidth(),
                        titleLine.getHeight(), selectedFile.getName());
                String recognised = suggestOcr.recognizeText(titleLine);
                log.info("onSuggestTitle: Tesseract result '{}'", recognised);
                final String suggestion = (recognised != null) ? recognised : "";
                Platform.runLater(() -> {
                    appendLog("Suggest: Tesseract result: \"" + suggestion + "\"");
                    if (!suggestion.isBlank()) {
                        filterField.setText(suggestion);
                    }
                });
            } finally {
                Platform.runLater(() -> suggestButton.setDisable(false));
            }
        });
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
                boolean allDiffs = true;
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
            int selIdx = filesTable.getSelectionModel().getSelectedIndex();
            File webhookSourceFile = (selIdx >= 0 && selIdx < imageFiles.size()) ? imageFiles.get(selIdx) : null;
            if (webhookSourceFile != null) {
                fileColorMap.put(webhookSourceFile.getName(),
                        "-fx-background-color: #1565c0; -fx-text-fill: white; -fx-background-insets: 0;");
                filesTable.refresh();
            }
            final String finalTitle = title;
            final String finalDiff = diff;
            final String finalHash = hash;
            final String finalHashInfo = hashInfo;
            final File finalSource = webhookSourceFile;
            bgExecutor.submit(() -> registrationWebhookService.sendOnRegister(finalTitle, finalDiff, finalHash,
                    finalHashInfo, finalSource));
        } catch (IOException e) {
            log.error("Failed to register hash", e);
            appendLog("ERROR: " + e.getMessage());
        }
        filterField.clear();
        titleField.clear();
        hashField.clear();
        hashInfoField.clear();
        jacketView.setImage(null);
        difficultyView.setImage(null);
        infoView.setImage(null);
    }

    /**
     * Sends the current {@code musiclist.xml} file to the Discord webhook on close.
     * Delegates to {@link RegistrationWebhookService#sendOnClose}.
     */
    public void onWindowClose() {
        int totalHashes = hashItems != null ? hashItems.size() : 0;
        registrationWebhookService.sendOnClose(sessionRegisteredCount, totalHashes);
    }

    /**
     * Colorizes all files in the file list.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onColorize(ActionEvent event) {
        if (stateLabel != null && bundle != null) {
            stateLabel.setText(bundle.getString("message.coloring"));
        }
        filesProgress.setProgress(-1.0);
        List<File> snapshot = new ArrayList<>(imageFiles);
        bgExecutor.submit(() -> colorizerService.colorize(snapshot, false, new ColorizerCallbackHandler()));
    }

    /**
     * Colorizes only files that are not yet registered in the hash database.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onColorizeMissing(ActionEvent event) {
        if (stateLabel != null && bundle != null) {
            stateLabel.setText(bundle.getString("message.coloring"));
        }
        filesProgress.setProgress(-1.0);
        List<File> snapshot = new ArrayList<>(imageFiles);
        bgExecutor.submit(() -> colorizerService.colorize(snapshot, true, new ColorizerCallbackHandler()));
    }

    // -------------------------------------------------------------------------
    // Register button state
    // -------------------------------------------------------------------------

    /**
     * Enables the Register button only when title, jacket hash, and difficulty are
     * all valid.
     */
    private void updateRegisterButtonState() {
        String title = titleField.getText().trim();
        String hash = hashField.getText().trim();
        String diff = difficultyCombo.getValue();
        boolean canRegister = !title.isEmpty() && hash.matches("[0-9a-f]{8,}") && diff != null && !diff.isBlank();
        registerButton.setDisable(!canRegister);
    }

    // -------------------------------------------------------------------------
    // Colorize internal callback
    // -------------------------------------------------------------------------

    /**
     * Bridges {@link ColorizerService} callbacks to this controller's UI state.
     * Accumulates renamed files and colour updates on the background thread, then
     * applies them all in a single {@code Platform.runLater} call in
     * {@link #onComplete}.
     */
    private class ColorizerCallbackHandler implements ColorizerCallback {

        private Map<Integer, File> pendingRenames = new HashMap<>();
        private Map<String, String> pendingColors = new HashMap<>();

        @Override
        public void onProgress(int current, int total, String statusMessage) {
            Platform.runLater(() -> {
                filesProgress.setProgress((double) current / total);
                if (stateLabel != null) {
                    stateLabel.setText(statusMessage);
                }
            });
        }

        @Override
        public void onFileColorized(String filename, String cssStyle) {
            pendingColors.put(filename, cssStyle);
        }

        @Override
        public void onFileRenamed(int fileIndex, File newFile) {
            pendingRenames.put(fileIndex, newFile);
        }

        @Override
        public void onLog(String message) {
            appendLog(message);
        }

        @Override
        public void onComplete(int found, int notFound, double elapsedSeconds) {
            Map<Integer, File> renames = new HashMap<>(pendingRenames);
            Map<String, String> colors = new HashMap<>(pendingColors);
            Platform.runLater(() -> {
                for (Map.Entry<Integer, File> entry : renames.entrySet()) {
                    int idx = entry.getKey();
                    if (idx < imageFiles.size()) {
                        imageFiles.set(idx, entry.getValue());
                    }
                }
                fileColorMap.putAll(colors);
                fileItems.setAll(imageFiles);
                filesTable.refresh();
                filesProgress.setProgress(1.0);

                String completionMsg = bundle != null
                        ? MessageFormat.format(bundle.getString("message.coloring.complete"), notFound, found,
                                elapsedSeconds)
                        : "Colorize done. Found: " + found + ", not found: " + notFound;
                if (stateLabel != null) {
                    stateLabel.setText(completionMsg);
                }
                logArea.appendText("--- " + completionMsg + "\n");
                filesLoadingLabel.setText(imageFiles.size() + " file(s) in folder");
            });
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
        Platform.runLater(() -> filesTable.getSelectionModel().clearSelection());
        enableColorizeIfReady();
        appendLog("Loaded " + imageFiles.size() + " image(s) from " + dir.getAbsolutePath());
        Platform.runLater(() -> filesLoadingLabel.setText(imageFiles.size() + " file(s) in folder"));
    }

    private void enableColorizeIfReady() {
        if (!fileItems.isEmpty()) {
            colorizeButton.setDisable(false);
            colorizeMissingButton.setDisable(false);
        }
    }

    private void showCurrentImage(File f) {
        if (f == null || !f.exists()) {
            log.debug("showCurrentImage: file null or does not exist, skipping image display");
            return;
        }
        if (!OcrReporterHelper.isResultFilename(f.getName())) {
            currentInfoCrop = null;
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
            if (imageAnalysisService != null && !imageAnalysisService.isResultScreen(awtImage, paramsMap)) {
                log.debug("showCurrentImage: '{}' does not pass isResultScreen — clearing preview", f.getName());
                currentInfoCrop = null;
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

            Map<String, BufferedImage> parts = imageAnalysisService.cutAndSaveResultParts(awtImage, paramsMap);

            BufferedImage jacketRaw = parts.get("jacket");
            BufferedImage jacket = jacketRaw != null
                    ? OcrReporterHelper.cropAndScale(jacketRaw, 0, 0, jacketRaw.getWidth(), jacketRaw.getHeight(), 100,
                            100)
                    : null;
            jacketView.setImage(jacket != null ? toFxImage(jacket) : null);

            BufferedImage diffBand = parts.get("difficulty");
            if (diffBand != null) {
                diffBand = OcrReporterHelper.cropAndScale(diffBand, 0, 0, diffBand.getWidth(), diffBand.getHeight(),
                        137, 29);
            }
            difficultyView.setImage(diffBand != null ? toFxImage(diffBand) : null);

            BufferedImage infoRaw = parts.get("info");
            BufferedImage info = infoRaw != null
                    ? OcrReporterHelper.cropAndScale(infoRaw, 0, 0, infoRaw.getWidth(), infoRaw.getHeight(), 526, 64)
                    : null;
            currentInfoCrop = info;
            infoView.setImage(info != null ? toFxImage(info) : null);

            String infoHash = hasher.hash(info);
            hashInfoField.setText(infoHash);

            String hash = hasher.hash(awtImage);
            hashField.setText(hash);

            String detectedDiff = detectDifficulty(f, hash, diffBand);
            if (detectedDiff != null) {
                difficultyCombo.setValue(detectedDiff);
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
     * <li>Colour analysis of the cropped difficulty-band image.</li>
     * <li>Difficulty token embedded in the filename for already-processed
     * files.</li>
     * <li>Jacket-hash lookup in the local music list as a last resort.</li>
     * </ol>
     */
    private String detectDifficulty(File f, String jacketHash, BufferedImage diffBand) {
        try {
            return OcrReporterHelper.detectDifficultyFromBand(diffBand);
        } catch (com.sdvxhelper.service.ImageCropNotParsed e) {
            log.warn("detectDifficulty: band detection failed for {}: {}", f.getName(), e.getMessage());
            if (stateLabel != null) {
                stateLabel.setText(e.getMessage());
            }
        }
        String fromFilename = OcrReporterHelper.parseDifficultyFromFilename(f.getName());
        if (fromFilename != null) {
            return fromFilename;
        }
        if (musicListRepo != null) {
            String[] match = musicListRepo.findByJacketHash(jacketHash);
            if (match != null && match[1] != null && !match[1].isBlank()) {
                return match[1];
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private Image toFxImage(BufferedImage awt) {
        return SwingFXUtils.toFXImage(awt, null);
    }

    private void appendLog(String line) {
        Platform.runLater(() -> logArea.appendText(line + "\n"));
    }

    // -------------------------------------------------------------------------
    // Public accessors for listeners
    // -------------------------------------------------------------------------

    /** @return the file-colour map used by {@link ResultFilesTableRowListener} */
    public Map<String, String> getFileColorMap() {
        return fileColorMap;
    }

    /**
     * @return the filtered wiki song list used by {@link TextFilterChangeListener}
     */
    public FilteredList<WikiSongRow> getFilteredWikiSongs() {
        return filteredWikiSongs;
    }
}
