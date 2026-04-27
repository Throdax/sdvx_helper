package com.sdvxhelper.app.controller;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.util.Callback;

import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @FXML
    private Label connStatusLabel;
    @FXML
    private Button connectButton;
    @FXML
    private Button disconnectButton;

    @FXML
    private ComboBox<String> bootSceneCombo;
    @FXML
    private ListView<String> bootEnableList;
    @FXML
    private ListView<String> bootDisableList;

    @FXML
    private ComboBox<String> selectSceneCombo;
    @FXML
    private ListView<String> selectEnableList;
    @FXML
    private ListView<String> selectDisableList;

    @FXML
    private ComboBox<String> playSceneCombo;
    @FXML
    private ListView<String> playEnableList;
    @FXML
    private ListView<String> playDisableList;

    @FXML
    private ComboBox<String> resultSceneCombo;
    @FXML
    private ListView<String> resultEnableList;
    @FXML
    private ListView<String> resultDisableList;

    @FXML
    private ComboBox<String> quitSceneCombo;
    @FXML
    private ListView<String> quitEnableList;
    @FXML
    private ListView<String> quitDisableList;

    private SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, String> settings;
    private ObsWebSocketClient obsClient;

    private Map<String, BooleanProperty> checkStates = new java.util.HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        populateFields();
        updateConnectionUi(false);
        wireSceneChangeListeners();
        onConnect(null);
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
     * Establishes a connection to the OBS WebSocket server and discovers scenes.
     *
     * @param event
     *            the action event triggered by the Connect button
     */
    @FXML
    public void onConnect(ActionEvent event) {
        String host = settings.getOrDefault("host", "127.0.0.1");
        int port = parseInt(settings.get("port"), 4455);
        String passwd = settings.getOrDefault("passwd", "");
        obsClient = new ObsWebSocketClient(host, port, passwd);
        try {
            obsClient.connect();
            updateConnectionUi(true);
            log.info("Connected to OBS at {}:{}", host, port);
            discoverScenes();
        } catch (IOException e) {
            log.error("Failed to connect to OBS", e);
            connStatusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Closes the connection to the OBS WebSocket server.
     *
     * @param event
     *            the action event triggered by the Disconnect button
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

    private void discoverScenes() {
        if (obsClient == null) {
            log.warn("discoverScenes: obsClient is null, cannot perform action");
            return;
        }
        try {
            List<String> scenes = obsClient.getSceneNames();
            List<String> withEmpty = new java.util.ArrayList<>();
            withEmpty.add("");
            withEmpty.addAll(scenes);
            for (ComboBox<String> cb : allSceneCombos()) {
                String current = cb.getValue();
                cb.getItems().setAll(withEmpty);
                if (current != null && withEmpty.contains(current)) {
                    cb.setValue(current);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to discover OBS scenes: {}", e.getMessage());
        }
    }

    private List<ComboBox<String>> allSceneCombos() {
        return List.of(bootSceneCombo, selectSceneCombo, playSceneCombo, resultSceneCombo, quitSceneCombo);
    }

    private void wireSceneChangeListeners() {
        wire(bootSceneCombo, bootEnableList, bootDisableList, "obs_enable_boot", "obs_disable_boot");
        wire(selectSceneCombo, selectEnableList, selectDisableList, "obs_enable_select0", "obs_disable_select0");
        wire(playSceneCombo, playEnableList, playDisableList, "obs_enable_play0", "obs_disable_play0");
        wire(resultSceneCombo, resultEnableList, resultDisableList, "obs_enable_result0", "obs_disable_result0");
        wire(quitSceneCombo, quitEnableList, quitDisableList, "obs_enable_quit", "obs_disable_quit");
    }

    private void wire(ComboBox<String> cb, ListView<String> enable, ListView<String> disable, String enKey,
            String disKey) {
        cb.valueProperty().addListener((_, _, scene) -> loadSources(scene, enable, disable, enKey, disKey));
    }

    private void loadSources(String scene, ListView<String> enable, ListView<String> disable, String enKey,
            String disKey) {
        if (obsClient == null || scene == null || scene.isBlank()) {
            enable.getItems().clear();
            disable.getItems().clear();
            return;
        }
        try {
            List<String> sources = obsClient.getSourceNames(scene);
            List<String> enSel = parseCsv(settings.get(enKey));
            List<String> disSel = parseCsv(settings.get(disKey));
            populateCheckList(enable, sources, enSel, enKey);
            populateCheckList(disable, sources, disSel, disKey);
        } catch (IOException e) {
            log.warn("Failed to load OBS sources for scene '{}': {}", scene, e.getMessage());
        }
    }

    private void populateCheckList(ListView<String> lv, List<String> items, List<String> selected, String key) {
        lv.getItems().setAll(items);
        Callback<String, javafx.beans.value.ObservableValue<Boolean>> cb = item -> {
            BooleanProperty prop = checkStates.computeIfAbsent(key + ":" + item,
                    _ -> new SimpleBooleanProperty(selected.contains(item)));
            prop.set(selected.contains(item));
            return prop;
        };
        lv.setCellFactory(CheckBoxListCell.forListView(cb));
    }

    private void populateFields() {
        setValue(bootSceneCombo, "obs_scene_boot");
        setValue(selectSceneCombo, "obs_scene_select");
        setValue(playSceneCombo, "obs_scene_play");
        setValue(resultSceneCombo, "obs_scene_result");
        setValue(quitSceneCombo, "obs_scene_quit");
    }

    private void setValue(ComboBox<String> cb, String key) {
        String v = settings.get(key);
        cb.getItems().clear();
        cb.getItems().add("");
        if (v != null && !v.isEmpty()) {
            cb.getItems().add(v);
            cb.setValue(v);
        } else {
            cb.setValue("");
        }
    }

    private void collectFields() {
        putScene("obs_scene_boot", bootSceneCombo);
        putScene("obs_scene_select", selectSceneCombo);
        putScene("obs_scene_play", playSceneCombo);
        putScene("obs_scene_result", resultSceneCombo);
        putScene("obs_scene_quit", quitSceneCombo);

        collectChecked("obs_enable_boot");
        collectChecked("obs_disable_boot");
        collectChecked("obs_enable_select0");
        collectChecked("obs_disable_select0");
        collectChecked("obs_enable_play0");
        collectChecked("obs_disable_play0");
        collectChecked("obs_enable_result0");
        collectChecked("obs_disable_result0");
        collectChecked("obs_enable_quit");
        collectChecked("obs_disable_quit");
    }

    private void putScene(String key, ComboBox<String> cb) {
        String v = cb.getValue();
        settings.put(key, v == null ? "" : v);
    }

    private void collectChecked(String key) {
        List<String> checked = new java.util.ArrayList<>();
        String prefix = key + ":";
        for (Map.Entry<String, BooleanProperty> e : checkStates.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().get()) {
                checked.add(e.getKey().substring(prefix.length()));
            }
        }
        if (!checked.isEmpty()) {
            settings.put(key, String.join(", ", checked));
        }
    }

    private void updateConnectionUi(boolean connected) {
        connStatusLabel.setText(connected ? "Connected" : "Disconnected");
        connStatusLabel.getStyleClass().removeAll("obs-connected", "obs-disconnected");
        connStatusLabel.getStyleClass().add(connected ? "obs-connected" : "obs-disconnected");
        connectButton.setDisable(connected);
        disconnectButton.setDisable(!connected);
    }

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static List<String> parseCsv(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
    }
}
