package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import javafx.stage.DirectoryChooser;
import javax.imageio.ImageIO;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.SettingsRepository;
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

    @FXML
    private Button btnOpenFolder;
    @FXML
    private Button btnPrev;
    @FXML
    private Button btnNext;
    @FXML
    private Label lblImageCount;
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
    private ImageView imgPreview;
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
    private Button btnClearFilter;
    @FXML
    private TextArea txtLog;
    @FXML
    private ComboBox<String> cmbLanguage;
    @FXML
    private TextField txtFilter;
    @FXML
    private TableView<MusicInfo> tblMusic;
    @FXML
    private TableColumn<MusicInfo, String> colMusicTitle;
    @FXML
    private TableColumn<MusicInfo, String> colMusicArtist;
    @FXML
    private TableColumn<MusicInfo, String> colMusicBpm;
    @FXML
    private TableColumn<MusicInfo, String> colMusicLv;
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

    private final PerceptualHasher hasher = new PerceptualHasher();
    private final List<File> imageFiles = new ArrayList<>();
    private int currentIndex = -1;

    private final ObservableList<MusicInfo> allMusic = FXCollections.observableArrayList();
    private FilteredList<MusicInfo> filteredMusic;
    private final ObservableList<File> fileItems = FXCollections.observableArrayList();
    private final ObservableList<HashEntry> hashItems = FXCollections.observableArrayList();

    private MusicListRepository musicListRepo;

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
            colMusicLv.setCellValueFactory(new PropertyValueFactory<>("lv"));
            filteredMusic = new FilteredList<>(allMusic, _ -> true);
            tblMusic.setItems(filteredMusic);
            tblMusic.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        }
        if (colFileName != null) {
            colFileName.setCellValueFactory(
                    cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getName()));
            tblFiles.setItems(fileItems);
            tblFiles.getSelectionModel().selectedIndexProperty().addListener((_, _, idx) -> {
                if (idx != null && idx.intValue() >= 0) {
                    currentIndex = idx.intValue();
                    showCurrentImage();
                }
            });
        }
        if (colHashTitle != null) {
            colHashTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
            colHashValue.setCellValueFactory(new PropertyValueFactory<>("hash"));
            tblHashDb.setItems(hashItems);
        }

        if (txtFilter != null && filteredMusic != null) {
            txtFilter.textProperty().addListener((_, _, nv) -> {
                String q = nv == null ? "" : nv.toLowerCase();
                filteredMusic.setPredicate(m -> q.isBlank() || m.getTitle().toLowerCase().contains(q));
            });
        }

        loadMusicList();
        autoLoadFromSettings();
    }

    private void loadMusicList() {
        try {
            musicListRepo = new MusicListRepository();
            allMusic.setAll(musicListRepo.getAll());
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
     * Opens a folder chooser and loads all PNG/JPEG files from the selected
     * directory.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onOpenFolder(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Screenshot Folder");
        File dir = chooser.showDialog(btnOpenFolder.getScene().getWindow());
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        loadFolder(dir);
    }

    private void loadFolder(File dir) {
        File[] files = dir.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        });

        imageFiles.clear();
        if (files != null) {
            Arrays.sort(files);
            imageFiles.addAll(Arrays.asList(files));
        }
        fileItems.setAll(imageFiles);

        if (!imageFiles.isEmpty()) {
            currentIndex = 0;
            showCurrentImage();
            if (tblFiles != null) {
                tblFiles.getSelectionModel().select(0);
            }
        }
        updateNavButtons();
        appendLog("Loaded " + imageFiles.size() + " image(s) from " + dir.getAbsolutePath());
    }

    /**
     * Navigates to the previous image.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onPrev(ActionEvent event) {
        if (currentIndex > 0) {
            currentIndex--;
            showCurrentImage();
            if (tblFiles != null) {
                tblFiles.getSelectionModel().select(currentIndex);
            }
        }
        updateNavButtons();
    }

    /**
     * Navigates to the next image.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onNext(ActionEvent event) {
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            showCurrentImage();
            if (tblFiles != null) {
                tblFiles.getSelectionModel().select(currentIndex);
            }
        }
        updateNavButtons();
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
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            showCurrentImage();
        }
        updateNavButtons();
    }

    /**
     * Skips the current image without registering it.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onSkip(ActionEvent event) {
        appendLog("Skipped: " + (currentIndex < imageFiles.size() ? imageFiles.get(currentIndex).getName() : "?"));
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            showCurrentImage();
        }
        updateNavButtons();
    }

    private void showCurrentImage() {
        if (currentIndex < 0 || currentIndex >= imageFiles.size()) {
            return;
        }
        File f = imageFiles.get(currentIndex);
        lblImageCount.setText((currentIndex + 1) + " / " + imageFiles.size());
        try {
            Image fxImage = new Image(f.toURI().toString());
            imgPreview.setImage(fxImage);

            BufferedImage awtImage = ImageIO.read(f);
            String hash = hasher.hash(awtImage);
            txtHash.setText(hash);
            txtTitle.clear();
            txtOcrTitle.clear();
        } catch (IOException e) {
            log.error("Failed to load image {}", f.getAbsolutePath(), e);
            appendLog("ERROR loading: " + f.getName());
        }
    }

    private void updateNavButtons() {
        btnPrev.setDisable(currentIndex <= 0);
        btnNext.setDisable(imageFiles.isEmpty() || currentIndex >= imageFiles.size() - 1);
    }

    private void appendLog(String line) {
        if (txtLog != null) {
            txtLog.appendText(line + "\n");
        }
    }
}
