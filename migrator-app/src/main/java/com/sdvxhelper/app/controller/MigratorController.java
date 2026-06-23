package com.sdvxhelper.app.controller;

import java.io.File;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FXML controller for {@code migrator.fxml}.
 *
 * <p>
 * Wires the UI elements to the {@link MigrationService} background worker. When
 * the user clicks <em>Migrate</em> the button is disabled, a new
 * {@link MigrationService} is constructed with the JVM working directory as the
 * root, and the service is submitted to a daemon thread so it does not prevent
 * JVM shutdown.
 * </p>
 *
 * <p>
 * All UI updates arrive through the {@link MigrationCallback} implementation
 * defined as an inner class, which is always called on the JavaFX Application
 * Thread by {@link MigrationService}.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
public class MigratorController {

    private static final Logger log = LoggerFactory.getLogger(MigratorController.class);

    @FXML
    private Label titleLabel;

    @FXML
    private Button migrateButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label stepLabel;

    @FXML
    private TextArea logArea;

    /**
     * Called by the JavaFX runtime after all {@code @FXML} fields have been
     * injected. Resets the progress bar and step label to their idle state.
     */
    @FXML
    public void initialize() {
        progressBar.setProgress(0.0);
        stepLabel.setText("Ready.");
    }

    /**
     * Handles the <em>Migrate</em> button action. Disables the button so it cannot
     * be pressed twice, creates a {@link MigrationService} rooted at the JVM
     * working directory, and runs it on a background daemon thread.
     */
    @FXML
    public void onMigrateClicked() {
        migrateButton.setDisable(true);
        logArea.clear();
        progressBar.setProgress(0.0);
        stepLabel.setText("Starting migration…");

        File workDir = new File(System.getProperty("user.dir"));
        MigrationCallback callbackHandler = new MigrationCallbackHandler();
        MigrationService service = new MigrationService(workDir, callbackHandler);

        Thread migrationThread = new Thread(service, "migration-worker");
        migrationThread.setDaemon(true);
        migrationThread.start();
        log.info("Migration started from: {}", workDir.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Inner callback implementation
    // -------------------------------------------------------------------------

    /**
     * Receives progress notifications from {@link MigrationService} and updates the
     * UI controls accordingly. All methods are already on the JavaFX Application
     * Thread when called.
     */
    private class MigrationCallbackHandler implements MigrationCallback {

        /**
         * Updates the progress bar and step label when a new step begins.
         *
         * @param step
         *            the step that is starting
         * @param stepIndex
         *            the zero-based index of this step
         * @param totalSteps
         *            the total number of steps
         */
        @Override
        public void onStepStarted(MigrationStep step, int stepIndex, int totalSteps) {
            double progress = (double) stepIndex / totalSteps;
            progressBar.setProgress(progress);
            stepLabel.setText("[" + (stepIndex + 1) + "/" + totalSteps + "] " + step.getDisplayName());
            appendLog("--- " + step.getDisplayName());
        }

        /**
         * Appends a log message to the log text area.
         *
         * @param message
         *            the message to append
         */
        @Override
        public void onLog(String message) {
            if (Objects.isNull(message)) {
                return;
            }
            appendLog(message);
        }

        /**
         * Marks the progress bar as complete and re-enables or re-labels the migrate
         * button based on whether the migration succeeded.
         *
         * @param success
         *            {@code true} if all mandatory steps completed without fatal errors
         */
        @Override
        public void onComplete(boolean success) {
            progressBar.setProgress(1.0);
            if (success) {
                stepLabel.setText("Migration complete.");
                appendLog("");
                appendLog("=== Migration complete! ===");
                appendLog("You can now close this window and launch sdvx_helper.exe.");
            } else {
                stepLabel.setText("Migration failed — check the log above.");
                appendLog("");
                appendLog("=== Migration failed. ===");
                appendLog("Please review the log above and retry or migrate manually.");
                migrateButton.setDisable(false);
            }
        }

        /**
         * Appends a line of text to the log text area, followed by a newline, and
         * scrolls to the bottom so the latest output is always visible.
         *
         * @param line
         *            the text line to append
         */
        private void appendLog(String line) {
            logArea.appendText(line + "\n");
        }
    }
}
