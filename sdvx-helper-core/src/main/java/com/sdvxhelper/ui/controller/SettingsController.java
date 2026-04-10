package com.sdvxhelper.ui.controller;

import com.sdvxhelper.repository.SettingsRepository;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller for the Settings dialog ({@code settings.fxml}).
 *
 * <p>Loads current values from {@link SettingsRepository} on creation and writes
 * them back when the user confirms the dialog (via the OK button which calls
 * {@link #save()}).  The dialog is opened by {@code MainController.onSettings()}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SettingsController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    // ---- General tab -------------------------------------------------------
    @FXML private TextField txtPlayerName;
    @FXML private CheckBox  chkAutoUpdate;
    @FXML private CheckBox  chkIgnoreRankD;

    // ---- OBS tab -----------------------------------------------------------
    @FXML private TextField     txtObsHost;
    @FXML private TextField     txtObsPort;
    @FXML private PasswordField txtObsPassword;
    @FXML private TextField     txtObsSource;

    // ---- Detection tab -----------------------------------------------------
    @FXML private TextField txtDetectWait;
    @FXML private TextField txtAutosaveDir;
    @FXML private TextField txtAutosaveInterval;

    // ---- Webhooks tab ------------------------------------------------------
    @FXML private TextField txtWebhookName;
    @FXML private TextField txtWebhookUrl;

    private final SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, Object> settings;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        populateFields();
    }

    /**
     * Persists the current field values to {@code settings.json}.
     * Called by {@code MainController} when the OK button is pressed.
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

    private void populateFields() {
        txtPlayerName.setText(stringOf("player_name"));
        chkAutoUpdate.setSelected(boolOf("auto_update"));
        chkIgnoreRankD.setSelected(boolOf("ignore_rankD"));

        txtObsHost.setText(stringOf("host"));
        txtObsPort.setText(stringOf("port"));
        txtObsPassword.setText(stringOf("passwd"));
        txtObsSource.setText(stringOf("obs_source"));

        txtDetectWait.setText(String.valueOf(settings.getOrDefault("detect_wait", "2.7")));
        txtAutosaveDir.setText(stringOf("autosave_dir"));
        txtAutosaveInterval.setText(String.valueOf(settings.getOrDefault("autosave_interval", 60)));

        txtWebhookName.setText(stringOf("webhook_player_name"));
    }

    private void collectFields() {
        settings.put("player_name",        txtPlayerName.getText().trim());
        settings.put("auto_update",        chkAutoUpdate.isSelected());
        settings.put("ignore_rankD",       chkIgnoreRankD.isSelected());

        settings.put("host",               txtObsHost.getText().trim());
        settings.put("port",               txtObsPort.getText().trim());
        settings.put("passwd",             txtObsPassword.getText());
        settings.put("obs_source",         txtObsSource.getText().trim());

        settings.put("detect_wait",        parseDouble(txtDetectWait.getText(), 2.7));
        settings.put("autosave_dir",       txtAutosaveDir.getText().trim());
        settings.put("autosave_interval",  parseInt(txtAutosaveInterval.getText(), 60));

        settings.put("webhook_player_name", txtWebhookName.getText().trim());
        // txtWebhookUrl is informational only; full webhook list is managed via the Webhooks menu
    }

    private String stringOf(String key) {
        Object v = settings.get(key);
        return v != null ? v.toString() : "";
    }

    private boolean boolOf(String key) {
        Object v = settings.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
