package com.sdvxhelper.app.controller;

import java.io.File;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import com.sdvxhelper.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FXML controller for {@code migrator.fxml}.
 *
 * <p>
 * On startup the controller inspects the working directory. If the backup
 * folder ({@code <name>_old/}) and the Puni Edition {@code app/} directory are
 * both present the migration is considered complete: the Migrate button is
 * disabled and a Revert button is revealed instead.
 * </p>
 *
 * <p>
 * The Migrate path delegates to {@link MigrationService}; the Revert path
 * delegates to {@link RevertService}. Both run on a daemon background thread
 * and report progress through inner callback implementations.
 * </p>
 *
 * <p>
 * Because the migrator runs before any SDVX Helper i18n infrastructure is
 * initialised, all Japanese strings are hardcoded directly in this class rather
 * than loaded from a {@code ResourceBundle}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class MigratorController {

    private static final Logger log = LoggerFactory.getLogger(MigratorController.class);

    /** Language option identifier for English. */
    private static final String LANG_EN = "English";

    /** Language option identifier for Japanese. */
    private static final String LANG_JA = "\u65e5\u672c\u8a9e";

    @FXML
    private Label languageLabel;

    @FXML
    private ComboBox<String> languageCombo;

    @FXML
    private Label titleLabel;

    @FXML
    private TextArea descriptionLabel;

    @FXML
    private Button migrateButton;

    @FXML
    private Button revertButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label overallLabel;

    @FXML
    private Label stepLabel;

    @FXML
    private TextArea logArea;

    /** Stage reference used to update the window title on language change. */
    private Stage stage;

    /**
     * The backup directory name derived from the working directory at startup, e.g.
     * {@code sdvx_helper_old} when the installation is in {@code sdvx_helper/}.
     */
    private String backupDirName;

    /** Currently selected language; defaults to English. */
    private String currentLang;

    /**
     * Whether the Puni Edition migration has already been completed when the
     * migrator started. Determined once in {@link #initialize()} and used to select
     * the correct UI state.
     */
    private boolean migrationAlreadyDone;

    /**
     * Called by the JavaFX runtime after all {@code @FXML} fields have been
     * injected. Computes the dynamic backup directory name, detects whether the
     * migration has already been completed, populates the language combo box, and
     * applies the appropriate initial UI state.
     */
    @FXML
    public void initialize() {
        backupDirName = new File(System.getProperty("user.dir")).getName() + "_old";
        migrationAlreadyDone = detectMigrationCompleted();
        currentLang = LANG_EN;
        languageCombo.getItems().addAll(LANG_EN, LANG_JA);
        languageCombo.setValue(LANG_EN);
        progressBar.setProgress(0.0);
        applyLanguage();
        if (migrationAlreadyDone) {
            migrateButton.setDisable(true);
            revertButton.setVisible(true);
            revertButton.setManaged(true);
            log.info("Migration already detected — revert mode enabled");
        }
    }

    /**
     * Injects the primary {@link Stage} so that the window title can be updated
     * when the language selection changes.
     *
     * @param stage
     *            the primary stage; must not be {@code null}
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Handles the language combo box selection change. Updates {@link #currentLang}
     * and refreshes all localised UI text through {@link #applyLanguage()}.
     */
    @FXML
    public void onLanguageChanged() {
        String selected = languageCombo.getValue();
        if (Objects.isNull(selected)) {
            return;
        }
        currentLang = selected;
        applyLanguage();
        if (migrationAlreadyDone) {
            migrateButton.setDisable(true);
            revertButton.setVisible(true);
            revertButton.setManaged(true);
        }
    }

    /**
     * Handles the <em>Migrate</em> button action. Disables the button, creates a
     * {@link MigrationService} rooted at the JVM working directory, and runs it on
     * a background daemon thread.
     */
    @FXML
    public void onMigrateClicked() {
        migrateButton.setDisable(true);
        logArea.clear();
        progressBar.setProgress(0.0);
        stepLabel.setText(t("Starting migration\u2026",
                "\u30de\u30a4\u30b0\u30ec\u30fc\u30b7\u30e7\u30f3\u958b\u59cb\u4e2d\u2026"));

        File workDir = new File(System.getProperty("user.dir"));
        MigrationCallback callbackHandler = new MigrationCallbackHandler();
        MigrationService service = new MigrationService(workDir, callbackHandler);

        Thread migrationThread = new Thread(service, "migration-worker");
        migrationThread.setDaemon(true);
        migrationThread.start();
        log.info("Migration started from: {}", workDir.getAbsolutePath());
    }

    /**
     * Handles the <em>Revert Migration</em> button action. Disables both buttons,
     * creates a {@link RevertService} rooted at the JVM working directory, and runs
     * it on a background daemon thread.
     */
    @FXML
    public void onRevertClicked() {
        revertButton.setDisable(true);
        migrateButton.setDisable(true);
        logArea.clear();
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        stepLabel.setText(t("Reverting\u2026", "\u5143\u306b\u623b\u3057\u3066\u3044\u307e\u3059\u2026"));

        File workDir = new File(System.getProperty("user.dir"));
        RevertCallback callbackHandler = new RevertCallbackHandler();
        RevertService service = new RevertService(workDir, callbackHandler);

        Thread revertThread = new Thread(service, "revert-worker");
        revertThread.setDaemon(true);
        revertThread.start();
        log.info("Revert started from: {}", workDir.getAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    /**
     * Checks whether the Puni Edition migration has already been completed by
     * verifying that both the backup directory ({@code <name>_old/}) and the Puni
     * Edition {@code app/} directory exist in the working directory.
     *
     * @return {@code true} if both markers are present; {@code false} otherwise
     */
    private boolean detectMigrationCompleted() {
        File workDir = new File(System.getProperty("user.dir"));
        boolean backupExists = new File(workDir, backupDirName).isDirectory();
        boolean puniEditionInPlace = new File(workDir, "app").isDirectory();
        return backupExists && puniEditionInPlace;
    }

    // -------------------------------------------------------------------------
    // Language helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the English string when the current language is English, or the
     * Japanese string otherwise.
     *
     * @param en
     *            the English text
     * @param ja
     *            the Japanese text
     * @return the localised string for the current language
     */
    private String t(String en, String ja) {
        return LANG_JA.equals(currentLang) ? ja : en;
    }

    /**
     * Applies the currently selected language to every localised element in the UI.
     * If the migration has already been detected the description reflects the
     * already-installed state instead of the pre-migration instructions.
     */
    private void applyLanguage() {
        languageLabel.setText(t("Language:", "\u8a00\u8a9e:"));
        titleLabel.setText(t("SDVX Helper \u2014 Puni Edition Migrator",
                "SDVX \u30d8\u30eb\u30d1\u30fc \u2014 \u30d7\u30cb \u30a8\u30c7\u30a3\u30b7\u30e7\u30f3 \u30de\u30a4\u30b0\u30ec\u30fc\u30bf\u30fc"));
        migrateButton.setText(t("Migrate", "\u30de\u30a4\u30b0\u30ec\u30fc\u30c8"));
        revertButton.setText(t("Revert Migration",
                "\u30de\u30a4\u30b0\u30ec\u30fc\u30b7\u30e7\u30f3\u3092\u5143\u306b\u623b\u3059"));
        overallLabel.setText(t("Overall:", "\u9032\u6357:"));

        if (migrationAlreadyDone) {
            descriptionLabel.setText(t(
                    "SDVX Helper \u2014 Puni Edition is already installed. Your previous"
                            + " installation was backed up to " + backupDirName + "/."
                            + " You may revert to the previous installation using the button below."
                            + " Alternatively, you can safely delete migrate.exe."
                            + " All your result screenshots, recorded songs and scores remain intact.",
                    "SDVX Helper \u2014 \u30d7\u30cb \u30a8\u30c7\u30a3\u30b7\u30e7\u30f3 \u306f\u3059\u3067\u306b\u30a4\u30f3\u30b9\u30c8\u30fc\u30eb\u3055\u308c\u3066\u3044\u307e\u3059\u3002"
                            + "\u4ee5\u524d\u306e\u30a4\u30f3\u30b9\u30c8\u30fc\u30eb\u306f " + backupDirName
                            + "/ \u306b\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u3055\u308c\u3066\u3044\u307e\u3059\u3002"
                            + "\u4ee5\u4e0b\u306e\u30dc\u30bf\u30f3\u3067\u4ee5\u524d\u306e\u30a4\u30f3\u30b9\u30c8\u30fc\u30eb\u306b\u623b\u3059\u3053\u3068\u304c\u3067\u304d\u307e\u3059\u3002"
                            + "migrate.exe \u306f\u5b89\u5168\u306b\u524a\u9664\u3067\u304d\u307e\u3059\u3002"
                            + "\u30ea\u30b6\u30eb\u30c8\u30b9\u30af\u30ea\u30fc\u30f3\u30b7\u30e7\u30c3\u30c8\u3001\u8a18\u9332\u3055\u308c\u305f\u697d\u66f2\u3001\u30b9\u30b3\u30a2\u306f\u3059\u3079\u3066\u4fdd\u6301\u3055\u308c\u3066\u3044\u307e\u3059\u3002"));
            stepLabel.setText(t("Puni Edition already installed \u2014 migrate.exe can be safely deleted.",
                    "\u30d7\u30cb \u30a8\u30c7\u30a3\u30b7\u30e7\u30f3 \u306f\u3059\u3067\u306b\u30a4\u30f3\u30b9\u30c8\u30fc\u30eb\u6e08\u307f \u2014 migrate.exe \u306f\u5b89\u5168\u306b\u524a\u9664\u3067\u304d\u307e\u3059\u3002"));
        } else {
            descriptionLabel.setText(t(
                    "This tool will back up your existing installation to " + backupDirName
                            + "/, extract Puni Edition, and migrate your data files and settings."
                            + " All your result screenshots, recorded songs and scores will be preserved."
                            + " Click Migrate to begin.",
                    "\u3053\u306e\u30c4\u30fc\u30eb\u306f\u65e2\u5b58\u306e\u30a4\u30f3\u30b9\u30c8\u30fc\u30eb\u3092 "
                            + backupDirName
                            + "/ \u306b\u30d0\u30c3\u30af\u30a2\u30c3\u30d7\u3057\u3001\u30d7\u30cb \u30a8\u30c7\u30a3\u30b7\u30e7\u30f3\u3092"
                            + "\u5c55\u958b\u3057\u3066\u30c7\u30fc\u30bf\u30d5\u30a1\u30a4\u30eb\u3068\u8a2d\u5b9a\u3092\u79fb\u884c\u3057\u307e\u3059\u3002"
                            + "\u30ea\u30b6\u30eb\u30c8\u30b9\u30af\u30ea\u30fc\u30f3\u30b7\u30e7\u30c3\u30c8\u3001\u8a18\u9332\u3055\u308c\u305f\u697d\u66f2\u3001\u30b9\u30b3\u30a2\u306f\u3059\u3079\u3066\u5f15\u304d\u7d99\u304c\u308c\u307e\u3059\u3002"
                            + "\u300c\u30de\u30a4\u30b0\u30ec\u30fc\u30c8\u300d\u3092\u30af\u30ea\u30c3\u30af\u3057\u3066\u958b\u59cb\u3057\u3066\u304f\u3060\u3055\u3044\u3002"));
            if (!migrateButton.isDisabled()) {
                stepLabel.setText(t("Ready.", "\u6e96\u5099\u5b8c\u4e86\u3002"));
            }
        }

        if (stage != null) {
            stage.setTitle(t("SDVX Helper - Puni Edition Migrator ",
                    "SDVX \u30d8\u30eb\u30d1\u30fc - \u30d7\u30cb \u30a8\u30c7\u30a3\u30b7\u30e7\u30f3 \u30de\u30a4\u30b0\u30ec\u30fc\u30bf\u30fc ")
                    + VersionUtil.getVersion("migrator"));
        }
    }

    // -------------------------------------------------------------------------
    // Inner callback implementations
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
                stepLabel.setText(
                        t("Migration complete.", "\u30de\u30a4\u30b0\u30ec\u30fc\u30b7\u30e7\u30f3\u5b8c\u4e86\u3002"));
                appendLog("");
                appendLog(t("=== Migration complete! ===",
                        "=== \u30de\u30a4\u30b0\u30ec\u30fc\u30b7\u30e7\u30f3\u5b8c\u4e86\uff01 ==="));
                appendLog(t("You can now close this window and launch sdvx_helper.exe.",
                        "\u3053\u306e\u30a6\u30a3\u30f3\u30c9\u30a6\u3092\u9589\u3058\u3066 sdvx_helper.exe \u3092\u8d77\u52d5\u3057\u3066\u304f\u3060\u3055\u3044\u3002"));
            } else {
                stepLabel.setText(t("Migration failed \u2014 check the log above.",
                        "\u30de\u30a4\u30b0\u30ec\u30fc\u30b7\u30e7\u30f3\u5931\u6557 \u2014 \u4e0a\u306e\u30ed\u30b0\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002"));
                appendLog("");
                appendLog(t("=== Migration failed. ===",
                        "=== \u30de\u30a4\u30b0\u30ec\u30fc\u30b7\u30e7\u30f3\u5931\u6557\u3002 ==="));
                appendLog(t("Please review the log above and retry or migrate manually.",
                        "\u4e0a\u306e\u30ed\u30b0\u3092\u78ba\u8a8d\u3057\u3066\u518d\u8a66\u884c\u3059\u308b\u304b\u3001\u624b\u52d5\u3067\u79fb\u884c\u3057\u3066\u304f\u3060\u3055\u3044\u3002"));
                migrateButton.setDisable(false);
            }
        }
    }

    /**
     * Receives progress notifications from {@link RevertService} and updates the UI
     * controls accordingly. All methods are already on the JavaFX Application
     * Thread when called.
     */
    private class RevertCallbackHandler implements RevertCallback {

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
         * Marks the progress bar as complete and shows the post-revert instructions on
         * success, or re-enables the revert button and shows an error on failure.
         *
         * @param success
         *            {@code true} if the revert completed without fatal errors
         */
        @Override
        public void onComplete(boolean success) {
            progressBar.setProgress(1.0);
            if (success) {
                stepLabel.setText(t("Revert complete.", "\u5143\u306b\u623b\u3057\u307e\u3057\u305f\u3002"));
                appendLog("");
                appendLog(t("=== Revert complete! ===", "=== \u5143\u306b\u623b\u3057\u307e\u3057\u305f\uff01 ==="));
                appendLog(t("Your previous installation has been restored.",
                        "\u4ee5\u524d\u306e\u30a4\u30f3\u30b9\u30c8\u30fc\u30eb\u304c\u5fa9\u5143\u3055\u308c\u307e\u3057\u305f\u3002"));
                appendLog(t(
                        "The app/ and runtime/ folders are still present because this"
                                + " application is running from them.",
                        "app/ \u30d5\u30a9\u30eb\u30c0\u30fc\u3068 runtime/ \u30d5\u30a9\u30eb\u30c0\u30fc\u306f\u3053\u306e\u30a2\u30d7\u30ea\u81ea\u8eab\u304c\u4f7f\u7528\u4e2d\u306e\u305f\u3081\u307e\u3060\u6b8b\u3063\u3066\u3044\u307e\u3059\u3002"));
                appendLog(t(
                        "After closing this application you can delete: migrate.exe, the app/ folder and the runtime/ folder.",
                        "\u3053\u306e\u30a2\u30d7\u30ea\u3092\u9589\u3058\u305f\u5f8c\u3001\u6b21\u306e\u30d5\u30a1\u30a4\u30eb\u3092\u524a\u9664\u3067\u304d\u307e\u3059: migrate.exe\u3001app/ \u30d5\u30a9\u30eb\u30c0\u30fc\u3001runtime/ \u30d5\u30a9\u30eb\u30c0\u30fc\u3002"));
            } else {
                stepLabel.setText(t("Revert failed \u2014 check the log above.",
                        "\u5143\u306b\u623b\u3059\u306e\u306b\u5931\u6557\u3057\u307e\u3057\u305f \u2014 \u4e0a\u306e\u30ed\u30b0\u3092\u78ba\u8a8d\u3057\u3066\u304f\u3060\u3055\u3044\u3002"));
                appendLog("");
                appendLog(t("=== Revert failed. ===",
                        "=== \u5143\u306b\u623b\u3059\u306e\u306b\u5931\u6557\u3057\u307e\u3057\u305f\u3002 ==="));
                appendLog(t("Please check the log above and try again.",
                        "\u4e0a\u306e\u30ed\u30b0\u3092\u78ba\u8a8d\u3057\u3066\u518d\u8a66\u884c\u3057\u3066\u304f\u3060\u3055\u3044\u3002"));
                revertButton.setDisable(false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared UI helper
    // -------------------------------------------------------------------------

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
