package com.sdvxhelper.app.controller;

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

    @FXML
    private TextField txtPlayerName;
    @FXML
    private CheckBox chkAutoUpdate;
    @FXML
    private CheckBox chkIgnoreRankD;

    @FXML
    private TextField txtObsHost;
    @FXML
    private TextField txtObsPort;
    @FXML
    private PasswordField txtObsPassword;
    @FXML
    private TextField txtObsSource;

    @FXML
    private TextField txtDetectWait;
    @FXML
    private TextField txtAutosaveDir;
    @FXML
    private TextField txtAutosaveInterval;

    @FXML
    private TextField txtWebhookName;
    @FXML
    private TextField txtWebhookUrl;

    @FXML
    private CheckBox chkClipLxly;
    @FXML
    private CheckBox chkAlwaysUpdateVf;
    @FXML
    private CheckBox chkSaveJacketImg;
    @FXML
    private CheckBox chkAutosaveAlways;
    @FXML
    private TextField txtAutosavePrewait;
    @FXML
    private CheckBox chkDiscordEnable;
    @FXML
    private TextField txtRtaTargetVf;

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

    /**
     * Opens a directory chooser to pick the auto-save folder.
     *
     * @param event
     *            action event from the Browse button
     */
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

    private void populateFields() {
        setText(txtPlayerName, settings.get("player_name"));
        setCheck(chkAutoUpdate, settings.get("auto_update"));
        setCheck(chkIgnoreRankD, settings.get("ignore_rankD"));

        setText(txtObsHost, settings.get("host"));
        setText(txtObsPort, settings.get("port"));
        setText(txtObsPassword, settings.get("passwd"));
        setText(txtObsSource, settings.get("obs_source"));

        setText(txtDetectWait, settings.get("detect_wait"));
        setText(txtAutosaveDir, settings.get("autosave_dir"));
        setText(txtAutosaveInterval, settings.get("autosave_interval"));

        setText(txtWebhookName, settings.get("webhook_player_name"));

        setCheck(chkClipLxly, settings.get("clip_lxly"));
        setCheck(chkAlwaysUpdateVf, settings.get("always_update_vf"));
        setCheck(chkSaveJacketImg, settings.get("save_jacketimg"));
        setCheck(chkAutosaveAlways, settings.get("autosave_always"));
        setText(txtAutosavePrewait, settings.get("autosave_prewait"));
        setCheck(chkDiscordEnable, settings.get("discord_enable"));
        setText(txtRtaTargetVf, settings.get("rta_target_vf"));
    }

    private void collectFields() {
        putIf(txtPlayerName, "player_name");
        putIf(chkAutoUpdate, "auto_update");
        putIf(chkIgnoreRankD, "ignore_rankD");

        putIf(txtObsHost, "host");
        putIf(txtObsPort, "port");
        if (txtObsPassword != null) {
            settings.put("passwd", txtObsPassword.getText());
        }
        putIf(txtObsSource, "obs_source");

        putIf(txtDetectWait, "detect_wait");
        putIf(txtAutosaveDir, "autosave_dir");
        putIf(txtAutosaveInterval, "autosave_interval");

        putIf(txtWebhookName, "webhook_player_name");

        putIf(chkClipLxly, "clip_lxly");
        putIf(chkAlwaysUpdateVf, "always_update_vf");
        putIf(chkSaveJacketImg, "save_jacketimg");
        putIf(chkAutosaveAlways, "autosave_always");
        putIf(txtAutosavePrewait, "autosave_prewait");
        putIf(chkDiscordEnable, "discord_enable");
        putIf(txtRtaTargetVf, "rta_target_vf");
    }

    private void setText(TextField f, String v) {
        if (f != null) {
            f.setText(v != null ? v : "");
        }
    }

    private void setCheck(CheckBox c, String v) {
        if (c != null) {
            c.setSelected(Boolean.parseBoolean(v));
        }
    }

    private void putIf(TextField f, String key) {
        if (f != null) {
            settings.put(key, f.getText() == null ? "" : f.getText().trim());
        }
    }

    private void putIf(CheckBox c, String key) {
        if (c != null) {
            settings.put(key, Boolean.toString(c.isSelected()));
        }
    }
}
