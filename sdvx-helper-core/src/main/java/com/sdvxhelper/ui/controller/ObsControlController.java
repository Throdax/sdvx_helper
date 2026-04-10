package com.sdvxhelper.ui.controller;

import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.repository.SettingsRepository;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the OBS Control dialog ({@code obs_control.fxml}).
 *
 * <p>Provides connect/disconnect controls and per-state (boot, select, play,
 * result, quit) scene and source visibility configuration.  Changes are written
 * back to {@code settings.json} when the user confirms the dialog (OK button
 * triggers {@link #save()}).</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ObsControlController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ObsControlController.class);

    // ---- Connection --------------------------------------------------------
    @FXML private Label  lblConnStatus;
    @FXML private Button btnConnect;
    @FXML private Button btnDisconnect;

    // ---- Boot tab ----------------------------------------------------------
    @FXML private TextField txtBootScene;
    @FXML private TextField txtBootEnable;
    @FXML private TextField txtBootDisable;

    // ---- Select tab --------------------------------------------------------
    @FXML private TextField txtSelectScene;
    @FXML private TextField txtSelectEnable;
    @FXML private TextField txtSelectDisable;

    // ---- Play tab ----------------------------------------------------------
    @FXML private TextField txtPlayScene;
    @FXML private TextField txtPlayEnable;
    @FXML private TextField txtPlayDisable;

    // ---- Result tab --------------------------------------------------------
    @FXML private TextField txtResultScene;
    @FXML private TextField txtResultEnable;
    @FXML private TextField txtResultDisable;

    // ---- Quit tab ----------------------------------------------------------
    @FXML private TextField txtQuitScene;
    @FXML private TextField txtQuitEnable;
    @FXML private TextField txtQuitDisable;

    private final SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, Object> settings;
    private ObsWebSocketClient obsClient;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        populateFields();
        updateConnectionUi(false);
    }

    /**
     * Persists the current field values to {@code settings.json}.
     * Called by {@code MainController} when the OK button is pressed.
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

    /** Establishes a connection to the OBS WebSocket server. */
    @FXML
    public void onConnect(ActionEvent event) {
        collectFields();
        String host   = (String) settings.getOrDefault("host", "localhost");
        int    port   = Integer.parseInt((String) settings.getOrDefault("port", "4444"));
        String passwd = (String) settings.getOrDefault("passwd", "");
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

    /** Closes the connection to the OBS WebSocket server. */
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

    private void collectFields() {
        settings.put("obs_scene_boot",     txtBootScene.getText().trim());
        settings.put("obs_enable_boot",    textToList(txtBootEnable.getText()));
        settings.put("obs_disable_boot",   textToList(txtBootDisable.getText()));

        settings.put("obs_scene_select",   txtSelectScene.getText().trim());
        settings.put("obs_enable_select0", textToList(txtSelectEnable.getText()));
        settings.put("obs_disable_select0", textToList(txtSelectDisable.getText()));

        settings.put("obs_scene_play",     txtPlayScene.getText().trim());
        settings.put("obs_enable_play0",   textToList(txtPlayEnable.getText()));
        settings.put("obs_disable_play0",  textToList(txtPlayDisable.getText()));

        settings.put("obs_scene_result",   txtResultScene.getText().trim());
        settings.put("obs_enable_result0", textToList(txtResultEnable.getText()));
        settings.put("obs_disable_result0", textToList(txtResultDisable.getText()));

        settings.put("obs_scene_quit",     txtQuitScene.getText().trim());
        settings.put("obs_enable_quit",    textToList(txtQuitEnable.getText()));
        settings.put("obs_disable_quit",   textToList(txtQuitDisable.getText()));
    }

    private void updateConnectionUi(boolean connected) {
        lblConnStatus.setText(connected ? "Connected" : "Disconnected");
        lblConnStatus.getStyleClass().removeAll("obs-connected", "obs-disconnected");
        lblConnStatus.getStyleClass().add(connected ? "obs-connected" : "obs-disconnected");
        btnConnect.setDisable(connected);
        btnDisconnect.setDisable(!connected);
    }

    private String stringOf(String key) {
        Object v = settings.get(key);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private String listToText(String key) {
        Object v = settings.get(key);
        if (v instanceof List) {
            return ((List<Object>) v).stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
        return "";
    }

    private List<String> textToList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
