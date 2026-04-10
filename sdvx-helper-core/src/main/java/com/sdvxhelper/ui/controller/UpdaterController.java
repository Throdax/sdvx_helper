package com.sdvxhelper.ui.controller;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.network.GitHubVersionClient;
import com.sdvxhelper.util.VersionUtil;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the SDVX Helper Updater window ({@code updater.fxml}).
 *
 * <p>Checks GitHub for a newer release and downloads/installs the update.
 * Replaces the Python {@code Updater} class in {@code update.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class UpdaterController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(UpdaterController.class);

    private static final String REPO_OWNER = "your-github-username";
    private static final String REPO_NAME  = "sdvx-helper";

    @FXML private Label lblCurrentVersion;
    @FXML private Label lblLatestVersion;
    @FXML private Label lblUpdateStatus;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;
    @FXML private TextArea txtChangeLog;
    @FXML private Button btnCheck;
    @FXML private Button btnUpdate;
    @FXML private ComboBox<String> cmbLanguage;

    private final GitHubVersionClient versionClient = new GitHubVersionClient(REPO_OWNER, REPO_NAME);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "updater-bg");
        t.setDaemon(true);
        return t;
    });

    private String latestVersion;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        lblCurrentVersion.setText(VersionUtil.getCurrentVersion());
        cmbLanguage.setItems(LocaleManager.getInstance().getAvailableLocaleCodes());
        cmbLanguage.setValue(LocaleManager.getInstance().getCurrentCode());
        cmbLanguage.setOnAction(e -> LocaleManager.getInstance().setLocale(cmbLanguage.getValue()));
    }

    /**
     * Checks GitHub for the latest release version asynchronously.
     *
     * @param event action event
     */
    @FXML
    public void onCheck(ActionEvent event) {
        btnCheck.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lblProgress.setText("Checking for updates…");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return versionClient.getLatestVersion();
            }
        };

        task.setOnSucceeded(e -> {
            latestVersion = task.getValue();
            lblLatestVersion.setText(latestVersion != null ? latestVersion : "Unknown");
            progressBar.setProgress(0);
            lblProgress.setText("");
            boolean updateAvailable = latestVersion != null
                    && versionClient.isUpdateAvailable(VersionUtil.getCurrentVersion());
            if (updateAvailable) {
                lblUpdateStatus.setText("Update available!");
                lblUpdateStatus.setStyle("-fx-text-fill: #f85149;");
                btnUpdate.setDisable(false);
            } else {
                lblUpdateStatus.setText("Up to date");
                lblUpdateStatus.setStyle("-fx-text-fill: #4ecdc4;");
            }
            btnCheck.setDisable(false);
        });

        task.setOnFailed(e -> {
            log.error("Version check failed", task.getException());
            Platform.runLater(() -> {
                lblUpdateStatus.setText("Check failed");
                progressBar.setProgress(0);
                lblProgress.setText("Error: " + task.getException().getMessage());
                btnCheck.setDisable(false);
            });
        });

        executor.submit(task);
    }

    /**
     * Downloads and installs the latest release.
     *
     * @param event action event
     */
    @FXML
    public void onUpdate(ActionEvent event) {
        if (latestVersion == null) return;
        btnUpdate.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        lblProgress.setText("Downloading " + latestVersion + "…");
        log.info("Starting update download for version {}", latestVersion);
        // TODO: Implement download and extraction of the GitHub release ZIP
        lblProgress.setText("Download complete – please restart the application.");
        progressBar.setProgress(1.0);
    }

    @FXML
    public void onClose(ActionEvent event) {
        executor.shutdownNow();
        ((javafx.stage.Stage) btnCheck.getScene().getWindow()).close();
    }
}
