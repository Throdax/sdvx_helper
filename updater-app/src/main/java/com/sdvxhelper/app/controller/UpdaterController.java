package com.sdvxhelper.app.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.network.GitHubVersionClient;
import com.sdvxhelper.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the SDVX Helper Updater window ({@code updater.fxml}).
 *
 * <p>
 * Checks GitHub for a newer release and downloads/installs the update. Replaces
 * the Python {@code Updater} class in {@code update.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class UpdaterController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(UpdaterController.class);

    private static final String REPO_OWNER = "your-github-username";
    private static final String REPO_NAME = "sdvx-helper";

    @FXML
    private Label currentVersionLabel;
    @FXML
    private Label latestVersionLabel;
    @FXML
    private Label updateStatusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;
    @FXML
    private TextArea changeLogArea;
    @FXML
    private Button checkButton;
    @FXML
    private Button updateButton;
    @FXML
    private ComboBox<String> languageCombo;

    private GitHubVersionClient versionClient = new GitHubVersionClient(REPO_OWNER, REPO_NAME);
    private ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "updater-bg");
        t.setDaemon(true);
        return t;
    });

    private String latestVersion;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentVersionLabel.setText(VersionUtil.getCurrentVersion());
        languageCombo.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        languageCombo.setValue(LocaleManager.getInstance().getCurrentCode());
        languageCombo.setOnAction(_ -> LocaleManager.getInstance().setLocale(languageCombo.getValue()));
    }

    /**
     * Checks GitHub for the latest release version asynchronously.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onCheck(ActionEvent event) {
        checkButton.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Checking for updates…");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return versionClient.getLatestVersion();
            }
        };

        task.setOnSucceeded(_ -> {
            latestVersion = task.getValue();
            latestVersionLabel.setText(latestVersion != null ? latestVersion : "Unknown");
            progressBar.setProgress(0);
            progressLabel.setText("");
            boolean updateAvailable = latestVersion != null
                    && versionClient.isUpdateAvailable(VersionUtil.getCurrentVersion());
            if (updateAvailable) {
                updateStatusLabel.setText("Update available!");
                updateStatusLabel.setStyle("-fx-text-fill: #f85149;");
                updateButton.setDisable(false);
            } else {
                updateStatusLabel.setText("Up to date");
                updateStatusLabel.setStyle("-fx-text-fill: #4ecdc4;");
            }
            checkButton.setDisable(false);
        });

        task.setOnFailed(_ -> {
            log.error("Version check failed", task.getException());
            Platform.runLater(() -> {
                updateStatusLabel.setText("Check failed");
                progressBar.setProgress(0);
                progressLabel.setText("Error: " + task.getException().getMessage());
                checkButton.setDisable(false);
            });
        });

        executor.submit(task);
    }

    /**
     * Downloads the latest release ZIP from GitHub, extracts it to a temporary
     * directory, copies files over the current installation, and prompts the user
     * to restart.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onUpdate(ActionEvent event) {
        if (latestVersion == null) {
            log.warn("onUpdate: latestVersion is null, cannot update — check version must be run first");
            return;
        }
        updateButton.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Downloading " + latestVersion + "…");
        log.info("Starting update download for version {}", latestVersion);

        executor.submit(() -> {
            try {
                String downloadUrl = versionClient.getDownloadUrl(latestVersion);
                log.info("Downloading update from {}", downloadUrl);

                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.ALWAYS).build();
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(downloadUrl)).timeout(Duration.ofMinutes(5))
                        .GET().build();
                HttpResponse<InputStream> response = http.send(req, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    String msg = "Download failed: HTTP " + response.statusCode();
                    log.error(msg);
                    Platform.runLater(() -> {
                        progressLabel.setText(msg);
                        progressBar.setProgress(0);
                        updateButton.setDisable(false);
                    });
                    return;
                }

                Platform.runLater(() -> {
                    progressLabel.setText("Extracting…");
                    progressBar.setProgress(0.5);
                });

                Path tempDir = Files.createTempDirectory("sdvx_helper_update_");
                Path currentDir = Path.of(System.getProperty("user.dir"));

                try (ZipInputStream zis = new ZipInputStream(response.body())) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            zis.closeEntry();
                            continue;
                        }
                        String name = entry.getName();
                        // Strip leading distribution folder name if present
                        int slash = name.indexOf('/');
                        if (slash >= 0) {
                            name = name.substring(slash + 1);
                        }
                        if (name.isBlank()) {
                            zis.closeEntry();
                            continue;
                        }
                        Path dest = tempDir.resolve(name);
                        Files.createDirectories(dest.getParent());
                        try (FileOutputStream fos = new FileOutputStream(dest.toFile())) {
                            zis.transferTo(fos);
                        }
                        zis.closeEntry();
                    }
                }

                // Copy extracted files over the current directory
                Files.walk(tempDir).filter(p -> !Files.isDirectory(p)).forEach(src -> {
                    try {
                        Path relative = tempDir.relativize(src);
                        Path target = currentDir.resolve(relative);
                        Files.createDirectories(target.getParent());
                        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.warn("Could not copy update file {}: {}", src, e.getMessage());
                    }
                });

                deleteDirectory(tempDir.toFile());

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressLabel.setText("Update complete – please restart the application.");
                    updateStatusLabel.setText("Restart required");
                });
                log.info("Update {} installed successfully", latestVersion);

            } catch (IOException | InterruptedException e) {
                log.error("Update failed", e);
                Platform.runLater(() -> {
                    progressLabel.setText("Update failed: " + e.getMessage());
                    progressBar.setProgress(0);
                    updateButton.setDisable(false);
                });
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * Closes the updater window and shuts down background tasks.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onClose(ActionEvent event) {
        executor.shutdownNow();
        ((javafx.stage.Stage) checkButton.getScene().getWindow()).close();
    }
}
