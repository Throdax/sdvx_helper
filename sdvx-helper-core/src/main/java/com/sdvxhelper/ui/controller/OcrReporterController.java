package com.sdvxhelper.ui.controller;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.ocr.PerceptualHasher;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the OCR Reporter maintainer tool ({@code ocr_reporter.fxml}).
 *
 * <p>Allows maintainers to step through unknown jacket screenshots, view the
 * auto-OCR result, confirm or correct the title, and register the perceptual
 * hash in the music list.  Replaces the Python {@code Reporter} class in
 * {@code ocr_reporter.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class OcrReporterController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(OcrReporterController.class);

    @FXML private Button btnOpenFolder;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblImageCount;
    @FXML private ImageView imgPreview;
    @FXML private TextField txtHash;
    @FXML private TextField txtOcrTitle;
    @FXML private TextField txtTitle;
    @FXML private ComboBox<String> cmbDifficulty;
    @FXML private Button btnRegister;
    @FXML private Button btnSkip;
    @FXML private TextArea txtLog;
    @FXML private ComboBox<String> cmbLanguage;

    private final PerceptualHasher hasher = new PerceptualHasher();
    private final List<File> imageFiles = new ArrayList<>();
    private int currentIndex = -1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        cmbDifficulty.getSelectionModel().select("exh");
        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(e -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));
    }

    /**
     * Opens a folder chooser and loads all PNG/JPEG files from the selected directory.
     *
     * @param event action event
     */
    @FXML
    public void onOpenFolder(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Screenshot Folder");
        File dir = chooser.showDialog(btnOpenFolder.getScene().getWindow());
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles(f -> {
            String name = f.getName().toLowerCase();
            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        });

        imageFiles.clear();
        if (files != null) {
            Arrays.sort(files);
            imageFiles.addAll(Arrays.asList(files));
        }

        if (!imageFiles.isEmpty()) {
            currentIndex = 0;
            showCurrentImage();
        }
        updateNavButtons();
        appendLog("Loaded " + imageFiles.size() + " image(s) from " + dir.getAbsolutePath());
    }

    @FXML
    public void onPrev(ActionEvent event) {
        if (currentIndex > 0) { currentIndex--; showCurrentImage(); }
        updateNavButtons();
    }

    @FXML
    public void onNext(ActionEvent event) {
        if (currentIndex < imageFiles.size() - 1) { currentIndex++; showCurrentImage(); }
        updateNavButtons();
    }

    /**
     * Registers the current image's hash in the music list.
     *
     * @param event action event
     */
    @FXML
    public void onRegister(ActionEvent event) {
        String hash  = txtHash.getText();
        String title = txtTitle.getText().trim();
        String diff  = cmbDifficulty.getValue();
        if (hash.isBlank() || title.isBlank()) {
            appendLog("ERROR: hash or title is empty");
            return;
        }
        // TODO: Call MusicListRepository to register hash -> title mapping
        appendLog("Registered: [" + diff + "] " + title + " = " + hash);
        // Auto-advance
        if (currentIndex < imageFiles.size() - 1) { currentIndex++; showCurrentImage(); }
        updateNavButtons();
    }

    @FXML
    public void onSkip(ActionEvent event) {
        appendLog("Skipped: " + (currentIndex < imageFiles.size() ? imageFiles.get(currentIndex).getName() : "?"));
        if (currentIndex < imageFiles.size() - 1) { currentIndex++; showCurrentImage(); }
        updateNavButtons();
    }

    private void showCurrentImage() {
        if (currentIndex < 0 || currentIndex >= imageFiles.size()) return;
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
            // TODO: Run TesseractOcr on the title region and populate txtOcrTitle
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
        txtLog.appendText(line + "\n");
    }
}
