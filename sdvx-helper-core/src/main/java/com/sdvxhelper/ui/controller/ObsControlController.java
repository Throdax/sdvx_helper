package com.sdvxhelper.ui.controller;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.repository.SettingsRepository;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller for the OBS Control dialog ({@code obs_control.fxml}).
 *
 * <p>
 * Provides connect/disconnect controls and per-state (boot, select, play,
 * result, quit) scene and source visibility configuration. Changes are written
 * back to {@code settings.json} when the user confirms the dialog (OK button
 * triggers {@link #save()}).
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ObsControlController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ObsControlController.class);

    // ---- Connection --------------------------------------------------------
    @FXML
    private Label lblConnStatus;
    @FXML
    private Button btnConnect;
    @FXML
    private Button btnDisconnect;

    // ---- Boot tab ----------------------------------------------------------
    @FXML
    private TextField txtBootScene;
    @FXML
    private TextField txtBootEnable;
    @FXML
    private TextField txtBootDisable;

    // ---- Select tab --------------------------------------------------------
    @FXML
    private TextField txtSelectScene;
    @FXML
    private TextField txtSelectEnable;
    @FXML
    private TextField txtSelectDisable;

    // ---- Play tab ----------------------------------------------------------
    @FXML
    private TextField txtPlayScene;
    @FXML
    private TextField txtPlayEnable;
    @FXML
    private TextField txtPlayDisable;

    // ---- Result tab --------------------------------------------------------
    @FXML
    private TextField txtResultScene;
    @FXML
    private TextField txtResultEnable;
    @FXML
    private TextField txtResultDisable;

    // ---- Quit tab ----------------------------------------------------------
    @FXML
    private TextField txtQuitScene;
    @FXML
    private TextField txtQuitEnable;
    @FXML
    private TextField txtQuitDisable;

    private final SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, String> settings;
    private ObsWebSocketClient obsClient;

    /**
     * Initialises the controller by loading settings from {@code settings.json} and
     * populating the fields. Called by JavaFX after the FXML is loaded.
     * 
     * @param location  the location used to resolve relative paths for the root object, or null if the location is not known
     * @param resources the resources used to localize the root object, or null if the
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        populateFields();
        updateConnectionUi(false);
    }

    /**
     * Persists the current field values to {@code settings.json}. Called by
     * {@code MainController} when the OK button is pressed.
     */
    public void save() {
        collectFields();
        try {
            settingsRepo.save(settings);
            log.info("OBS control settings saved");
        } catch (IOException e) {
            log.error("Failed to save OBS control settings", e);
        }
    }

    /** 
     * Establishes a connection to the OBS WebSocket server. 
     * 
     * @param event the action event triggered by the Connect button
     */
    @FXML
    public void onConnect(ActionEvent event) {
        collectFields();
        String host = settings.get("host");
        int port = Integer.parseInt(settings.get("port"));
        String passwd = settings.get("passwd");
        obsClient = new ObsWebSocketClient(host, port, passwd);
        try {
            obsClient.connect();
            updateConnectionUi(true);
            log.info("Connected to OBS at {}:{}", host, port);
        } catch (IOException e) {
            log.error("Failed to connect to OBS", e);
            lblConnStatus.setText("Error: " + e.getMessage());
        }
    }

    /** 
     * Closes the connection to the OBS WebSocket server. 
     * 
     * @param event the action event triggered by the Disconnect button
     */
    @FXML
    public void onDisconnect(ActionEvent event) {
        if (obsClient != null) {
            obsClient.close();
            obsClient = null;
        }
        updateConnectionUi(false);
        log.info("Disconnected from OBS");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** 
     * Populates the text fields with values from the settings map. Called during
     * initialization to reflect the current settings in the UI.
     */
    private void populateFields() {
        txtBootScene.setText(stringOf("obs_scene_boot"));
        txtBootEnable.setText(listToText("obs_enable_boot"));
        txtBootDisable.setText(listToText("obs_disable_boot"));

        txtSelectScene.setText(stringOf("obs_scene_select"));
        txtSelectEnable.setText(listToText("obs_enable_select0"));
        txtSelectDisable.setText(listToText("obs_disable_select0"));

        txtPlayScene.setText(stringOf("obs_scene_play"));
        txtPlayEnable.setText(listToText("obs_enable_play0"));
        txtPlayDisable.setText(listToText("obs_disable_play0"));

        txtResultScene.setText(stringOf("obs_scene_result"));
        txtResultEnable.setText(listToText("obs_enable_result0"));
        txtResultDisable.setText(listToText("obs_disable_result0"));

        txtQuitScene.setText(stringOf("obs_scene_quit"));
        txtQuitEnable.setText(listToText("obs_enable_quit"));
        txtQuitDisable.setText(listToText("obs_disable_quit"));
    }

    /** 
     * Collects the current field values and updates the settings map. Called before
     * saving or connecting to ensure the latest values are used.
     */
    private void collectFields() {
        settings.put("obs_scene_boot", txtBootScene.getText().trim());
        settings.put("obs_enable_boot", txtBootEnable.getText());
        settings.put("obs_disable_boot", txtBootDisable.getText());

        settings.put("obs_scene_select", txtSelectScene.getText().trim());
        settings.put("obs_enable_select0", txtSelectEnable.getText());
        settings.put("obs_disable_select0", txtSelectDisable.getText());

        settings.put("obs_scene_play", txtPlayScene.getText().trim());
        settings.put("obs_enable_play0", txtPlayEnable.getText());
        settings.put("obs_disable_play0", txtPlayDisable.getText());

        settings.put("obs_scene_result", txtResultScene.getText().trim());
        settings.put("obs_enable_result0", txtResultEnable.getText());
        settings.put("obs_disable_result0", txtResultDisable.getText());

        settings.put("obs_scene_quit", txtQuitScene.getText().trim());
        settings.put("obs_enable_quit", txtQuitEnable.getText());
        settings.put("obs_disable_quit", txtQuitDisable.getText());
    }

    /**
     * Updates the connection status label and enables/disables the Connect and Disconnect buttons
     * based on the connection state.
     *
     * @param connected true if connected to OBS, false otherwise
     */
    private void updateConnectionUi(boolean connected) {
        lblConnStatus.setText(connected ? "Connected" : "Disconnected");
        lblConnStatus.getStyleClass().removeAll("obs-connected", "obs-disconnected");
        lblConnStatus.getStyleClass().add(connected ? "obs-connected" : "obs-disconnected");
        btnConnect.setDisable(connected);
        btnDisconnect.setDisable(!connected);
    }

    /**
     * Retrieves a string value from the settings map. If the value is null, an empty string is returned.
     *
     * @param key the settings key to retrieve
     * @return the string value associated with the key, or an empty string if the value is null
     */
    private String stringOf(String key) {
        Object v = settings.get(key);
        return v != null ? v.toString() : "";
    }

    /**
     * Converts a list value from the settings map into a comma-separated string.
     * If the value is not a list, an empty string is returned.
     *
     * @param key the settings key to retrieve
     * @return a comma-separated string of the list elements, or an empty string if the value is not a list
     */
    @SuppressWarnings("unchecked")
    private String listToText(String key) {
        Object v = settings.get(key);
        if (v instanceof List) {
            return ((List<Object>) v).stream().map(Object::toString).collect(Collectors.joining(", "));
        }
        return "";
    }

}
