package com.sdvxhelper.ui.controller;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import com.sdvxhelper.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Settings dialog ({@code settings.fxml}).
 *
 * <p>
 * Loads current values from {@link SettingsRepository} on creation and writes
 * them back when the user confirms the dialog (via the OK button which calls
 * {@link #save()}). The dialog is opened by
 * {@code MainController.onSettings()}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SettingsController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    // ---- General tab -------------------------------------------------------
    @FXML
    private TextField txtPlayerName;
    @FXML
    private CheckBox chkAutoUpdate;
    @FXML
    private CheckBox chkIgnoreRankD;

    // ---- OBS tab -----------------------------------------------------------
    @FXML
    private TextField txtObsHost;
    @FXML
    private TextField txtObsPort;
    @FXML
    private PasswordField txtObsPassword;
    @FXML
    private TextField txtObsSource;

    // ---- Detection tab -----------------------------------------------------
    @FXML
    private TextField txtDetectWait;
    @FXML
    private TextField txtAutosaveDir;
    @FXML
    private TextField txtAutosaveInterval;

    // ---- Webhooks tab ------------------------------------------------------
    @FXML
    private TextField txtWebhookName;
    @FXML
    private TextField txtWebhookUrl;

    private final SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, String> settings;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        populateFields();
    }

    /**
     * Persists the current field values to {@code settings.json}. Called by
     * {@code MainController} when the OK button is pressed.
     */
    public void save() {
        collectFields();
        try {
            settingsRepo.save(settings);
            log.info("Settings saved successfully");
        } catch (IOException e) {
            log.error("Failed to save settings", e);
        }
    }

    /** Opens a directory chooser to pick the auto-save folder. */
    @FXML
    public void onBrowseAutosaveDir(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Auto-save Folder");
        String current = txtAutosaveDir.getText();
        if (!current.isBlank()) {
            File existing = new File(current);
            if (existing.isDirectory()) {
                dc.setInitialDirectory(existing);
            }
        }
        File chosen = dc.showDialog(txtAutosaveDir.getScene().getWindow());
        if (chosen != null) {
            txtAutosaveDir.setText(chosen.getAbsolutePath());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Populates the UI fields with values from the loaded settings. Called once on
     * initialization after {@link #settings} is loaded from disk.
     */
    private void populateFields() {
        txtPlayerName.setText(settings.get("player_name"));
        chkAutoUpdate.setSelected(Boolean.valueOf(settings.get("auto_update")));
        chkIgnoreRankD.setSelected(Boolean.valueOf(settings.get("ignore_rankD")));

        txtObsHost.setText(settings.get("host"));
        txtObsPort.setText(settings.get("port"));
        txtObsPassword.setText(settings.get("passwd"));
        txtObsSource.setText(settings.get("obs_source"));

        txtDetectWait.setText(String.valueOf(settings.get("detect_wait")));
        txtAutosaveDir.setText(settings.get("autosave_dir"));
        txtAutosaveInterval.setText(String.valueOf(settings.get("autosave_interval")));

        txtWebhookName.setText(settings.get("webhook_player_name"));
    }

    /**
     * Collects the current values from the UI fields and updates the settings map.
     * Called by {@link #save()} before persisting to disk.
     */
    private void collectFields() {
        settings.put("player_name", txtPlayerName.getText().trim());
        settings.put("auto_update", Boolean.toString(chkAutoUpdate.isSelected()));
        settings.put("ignore_rankD", Boolean.toString(chkIgnoreRankD.isSelected()));

        settings.put("host", txtObsHost.getText().trim());
        settings.put("port", txtObsPort.getText().trim());
        settings.put("passwd", txtObsPassword.getText());
        settings.put("obs_source", txtObsSource.getText().trim());

        settings.put("detect_wait", txtDetectWait.getText());
        settings.put("autosave_dir", txtAutosaveDir.getText().trim());
        settings.put("autosave_interval", txtAutosaveInterval.getText());

        settings.put("webhook_player_name", txtWebhookName.getText().trim());
        // txtWebhookUrl is informational only; full webhook list is managed via the
        // Webhooks menu
    }

}
