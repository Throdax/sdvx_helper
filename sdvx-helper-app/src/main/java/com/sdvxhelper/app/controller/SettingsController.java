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
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
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
    private TextField playerNameField;
    @FXML
    private CheckBox autoUpdateCheck;
    @FXML
    private CheckBox ignoreRankDCheck;

    @FXML
    private TextField obsHostField;
    @FXML
    private TextField obsPortField;
    @FXML
    private PasswordField obsPasswordField;

    @FXML
    private TextField detectWaitField;
    @FXML
    private TextField autosaveDirField;
    @FXML
    private TextField autosaveIntervalField;

    @FXML
    private CheckBox clipLxlyCheck;
    @FXML
    private CheckBox alwaysUpdateVfCheck;
    @FXML
    private CheckBox saveJacketImgCheck;
    @FXML
    private CheckBox autosaveAlwaysCheck;
    @FXML
    private TextField autosavePrewaitField;
    @FXML
    private CheckBox discordEnableCheck;
    @FXML
    private TextField rtaTargetVfField;

    @FXML
    private RadioButton orientationTopRadio;
    @FXML
    private RadioButton orientationBottomRadio;
    @FXML
    private RadioButton orientationLeftRadio;
    @FXML
    private RadioButton orientationRightRadio;

    private SettingsRepository settingsRepo = new SettingsRepository();

    private Map<String, String> settings;

    @FXML
    private CheckBox discordUseSongName;

    @FXML
    private CheckBox discordUploadJacket;

    @FXML
    private CheckBox discordUseOCR;

    @FXML
    private CheckBox blasterGaugeMaxCheck;
    @FXML
    private TextField logWindowTransparencyField;
    @FXML
    private CheckBox importFromSelectCheck;
    @FXML
    private CheckBox includeArcadeScoresCheck;
    @FXML
    private TextField playsPrefixField;
    @FXML
    private TextField playsSuffixField;
    @FXML
    private TextField playtimePrefixField;

    private Runnable generateJacketsAction;
    private Runnable processPastResultsAction;

    private ToggleGroup tgOrientation;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        populateFields();

        tgOrientation = new ToggleGroup();
        orientationTopRadio.setToggleGroup(tgOrientation);
        orientationBottomRadio.setToggleGroup(tgOrientation);
        orientationLeftRadio.setToggleGroup(tgOrientation);
        orientationRightRadio.setToggleGroup(tgOrientation);
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
     * Wires the action invoked when the user clicks "Generate Jackets". Must be
     * called by {@code MainController} before the dialog is shown.
     *
     * @param action
     *            the callback to run
     */
    public void setGenerateJacketsAction(Runnable action) {
        generateJacketsAction = action;
    }

    /**
     * Wires the action invoked when the user clicks "Process Past Results". Must be
     * called by {@code MainController} before the dialog is shown.
     *
     * @param action
     *            the callback to run
     */
    public void setProcessPastResultsAction(Runnable action) {
        processPastResultsAction = action;
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
        String current = autosaveDirField.getText();
        if (!current.isBlank()) {
            File existing = new File(current);
            if (existing.isDirectory()) {
                dc.setInitialDirectory(existing);
            }
        }
        File chosen = dc.showDialog(autosaveDirField.getScene().getWindow());
        if (chosen != null) {
            autosaveDirField.setText(chosen.getAbsolutePath());
        }
    }

    private void populateFields() {
        playerNameField.setText(settings.get("player_name"));
        autoUpdateCheck.setSelected(Boolean.parseBoolean(settings.get("auto_update")));
        ignoreRankDCheck.setSelected(Boolean.parseBoolean(settings.get("ignore_rankD")));

        obsHostField.setText(settings.get("host"));
        obsPortField.setText(settings.get("port"));
        obsPasswordField.setText(settings.get("passwd"));

        detectWaitField.setText(settings.get("detect_wait"));
        autosaveDirField.setText(settings.get("autosave_dir"));
        autosaveIntervalField.setText(settings.get("autosave_interval"));

        clipLxlyCheck.setSelected(Boolean.parseBoolean(settings.get("clip_lxly")));
        alwaysUpdateVfCheck.setSelected(Boolean.parseBoolean(settings.get("always_update_vf")));
        saveJacketImgCheck.setSelected(Boolean.parseBoolean(settings.get("save_jacketimg")));
        autosaveAlwaysCheck.setSelected(Boolean.parseBoolean(settings.get("autosave_always")));
        autosavePrewaitField.setText(settings.getOrDefault("autosave_prewait", ""));
        rtaTargetVfField.setText(settings.get("rta_target_vf"));

        String orientation = settings.get("orientation");
        setRadioOrientation(orientation);

        discordEnableCheck.setSelected(Boolean.parseBoolean(settings.get("discord_presence_enable")));
        discordUploadJacket.setSelected(Boolean.parseBoolean(settings.get("discord_presence_upload_jacket")));
        discordUseOCR.setSelected(Boolean.parseBoolean(settings.get("discord_presence_ocr_titles")));
        discordUseSongName.setSelected(Boolean.parseBoolean(settings.get("discord_presence_song_as_title")));

        blasterGaugeMaxCheck.setSelected(Boolean.parseBoolean(settings.get("alert_blastermax")));
        logWindowTransparencyField.setText(settings.get("logpic_bg_alpha"));
        importFromSelectCheck.setSelected(Boolean.parseBoolean(settings.get("import_from_select")));
        includeArcadeScoresCheck.setSelected(Boolean.parseBoolean(settings.get("import_arcade_score")));
        playsPrefixField.setText(settings.get("obs_txt_plays_header"));
        playsSuffixField.setText(settings.get("obs_txt_plays_footer"));
        playtimePrefixField.setText(settings.get("obs_txt_playtime_header"));
    }

    private void collectFields() {
        settings.put("player_name", playerNameField.getText().trim());
        settings.put("auto_update", Boolean.toString(autoUpdateCheck.isSelected()));
        settings.put("ignore_rankD", Boolean.toString(ignoreRankDCheck.isSelected()));

        settings.put("host", obsHostField.getText().trim());
        settings.put("port", obsPortField.getText().trim());
        settings.put("passwd", obsPasswordField.getText());

        settings.put("detect_wait", detectWaitField.getText().trim());
        settings.put("autosave_dir", autosaveDirField.getText().trim());
        settings.put("autosave_interval", autosaveIntervalField.getText().trim());

        settings.put("clip_lxly", Boolean.toString(clipLxlyCheck.isSelected()));
        settings.put("always_update_vf", Boolean.toString(alwaysUpdateVfCheck.isSelected()));
        settings.put("save_jacketimg", Boolean.toString(saveJacketImgCheck.isSelected()));
        settings.put("autosave_always", Boolean.toString(autosaveAlwaysCheck.isSelected()));
        settings.put("autosave_prewait", autosavePrewaitField.getText().trim());
        settings.put("discord_enable", Boolean.toString(discordEnableCheck.isSelected()));
        settings.put("rta_target_vf", rtaTargetVfField.getText().trim());

        settings.put("orientation", getSelectedOrientation());

        settings.put("alert_blastermax", Boolean.toString(blasterGaugeMaxCheck.isSelected()));
        settings.put("logpic_bg_alpha", logWindowTransparencyField.getText().trim());
        settings.put("import_from_select", Boolean.toString(importFromSelectCheck.isSelected()));
        settings.put("import_arcade_score", Boolean.toString(includeArcadeScoresCheck.isSelected()));
        settings.put("obs_txt_plays_header", playsPrefixField.getText().trim());
        settings.put("obs_txt_plays_footer", playsSuffixField.getText().trim());
        settings.put("obs_txt_playtime_header", playtimePrefixField.getText().trim());
    }

    /**
     * Handles the "Generate Jackets" button click by delegating to the injected
     * callback.
     *
     * @param event
     *            action event from the Generate Jackets button
     */
    @FXML
    public void onGenerateJackets(ActionEvent event) {
        if (generateJacketsAction != null) {
            generateJacketsAction.run();
        } else {
            log.warn("Generate jackets action not wired");
        }
    }

    /**
     * Handles the "Process Past Results" button click by delegating to the injected
     * callback.
     *
     * @param event
     *            action event from the Process Past Results button
     */
    @FXML
    public void onProcessPastResults(ActionEvent event) {
        if (processPastResultsAction != null) {
            processPastResultsAction.run();
        } else {
            log.warn("Process past results action not wired");
        }
    }

    private void setRadioOrientation(String value) {
        switch (value) {
            case "bottom" -> orientationBottomRadio.setSelected(true);
            case "left" -> orientationLeftRadio.setSelected(true);
            case "right" -> orientationRightRadio.setSelected(true);
            default -> orientationTopRadio.setSelected(true);
        }
    }

    private String getSelectedOrientation() {
        if (orientationBottomRadio.isSelected()) {
            return "bottom";
        }
        if (orientationLeftRadio.isSelected()) {
            return "left";
        }
        if (orientationRightRadio.isSelected()) {
            return "right";
        }
        return "top";
    }
}
