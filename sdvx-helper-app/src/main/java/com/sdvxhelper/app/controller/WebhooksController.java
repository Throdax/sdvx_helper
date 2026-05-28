package com.sdvxhelper.app.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;

import com.sdvxhelper.model.WebhookConfig;
import com.sdvxhelper.model.WebhookConfigBuilder;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.repository.WebhookConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Webhooks configuration dialog ({@code webhooks.fxml}).
 *
 * <p>
 * Manages the list of Discord webhooks, their URLs, per-webhook image/playlist
 * flags, and the level (L1–L20) and lamp filter maps. Webhook configurations
 * are loaded from and saved to {@code webhooks.json} via
 * {@link WebhookConfigRepository}. The global player name continues to be
 * stored in {@code settings.json}.
 * </p>
 *
 * <p>
 * Lamp checkbox order (FXML {@code fx:id} → internal key):
 * <ul>
 * <li>{@code lampPucCheck} → {@code PUC}</li>
 * <li>{@code lampUcCheck} → {@code UC}</li>
 * <li>{@code lampExhCheck} → {@code MAXXIVE}</li>
 * <li>{@code lampHardCheck} → {@code HARD}</li>
 * <li>{@code lampClearCheck} → {@code CLEAR}</li>
 * <li>{@code lampFailedCheck} → {@code FAILED}</li>
 * </ul>
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhooksController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(WebhooksController.class);

    @FXML
    private TextField playerNameField;
    @FXML
    private ListView<WebhookConfig> webhooksList;
    @FXML
    private TextField nameField;
    @FXML
    private TextField urlField;
    @FXML
    private CheckBox sendImagesCheck;
    @FXML
    private CheckBox sendPlaylistCheck;
    @FXML
    private CheckBox levelAllCheck;
    @FXML
    private CheckBox lampAllCheck;
    @FXML
    private CheckBox lampPucCheck;
    @FXML
    private CheckBox lampUcCheck;
    @FXML
    private CheckBox lampExhCheck;
    @FXML
    private CheckBox lampHardCheck;
    @FXML
    private CheckBox lampClearCheck;
    @FXML
    private CheckBox lampFailedCheck;
    @FXML
    private FlowPane paneLevels;

    private final List<CheckBox> levelBoxes = new ArrayList<>();
    private SettingsRepository settingsRepo;
    private WebhookConfigRepository webhookRepo;
    private Map<String, String> settings;

    private ObservableList<WebhookConfig> configs = FXCollections.observableArrayList();

    private int selectedIndex = -1;
    private boolean capturingCurrent = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settingsRepo = new SettingsRepository();
        webhookRepo = new WebhookConfigRepository();
        settings = settingsRepo.load();
        buildLevelCheckBoxes();
        wireAllToggles();
        webhooksList.setItems(configs);
        webhooksList.setCellFactory(lv -> new ListCell<WebhookConfig>() {
            @Override
            protected void updateItem(WebhookConfig item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        webhooksList.getSelectionModel().selectedIndexProperty().addListener((_, _, idx) -> onSelect(idx.intValue()));
        loadFromRepository();
        playerNameField.setText(settings.getOrDefault("webhook_player_name", ""));
    }

    private void buildLevelCheckBoxes() {
        paneLevels.getChildren().clear();
        levelBoxes.clear();
        for (int lv = 1; lv <= 20; lv++) {
            CheckBox cb = new CheckBox(String.valueOf(lv));
            levelBoxes.add(cb);
            paneLevels.getChildren().add(cb);
        }
    }

    private void wireAllToggles() {
        levelAllCheck.setOnAction(_ -> {
            boolean checked = levelAllCheck.isSelected();
            for (CheckBox cb : levelBoxes) {
                cb.setSelected(checked);
            }
        });
        lampAllCheck.setOnAction(_ -> {
            boolean checked = lampAllCheck.isSelected();
            for (CheckBox cb : lampCheckBoxes()) {
                cb.setSelected(checked);
            }
        });
    }

    /**
     * Saves the current webhook configurations and the global player name. Called
     * by the parent dialog when the user presses OK/Save.
     */
    public void save() {
        captureCurrent();
        settings.put("webhook_player_name", playerNameField.getText().trim());
        try {
            settingsRepo.save(settings);
        } catch (IOException e) {
            log.error("Failed to save player name to settings", e);
        }
        try {
            webhookRepo.save(new ArrayList<>(configs));
            log.info("save: {} webhook(s) written to webhooks.json", configs.size());
        } catch (IOException e) {
            log.error("Failed to save webhooks.json", e);
        }
    }

    /**
     * Adds a new webhook entry with a user-supplied name and default filter values.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onAdd(ActionEvent event) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setHeaderText("New webhook name");
        dlg.showAndWait().ifPresent(name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                log.debug("onAdd: webhook name is blank, ignoring");
                return;
            }
            WebhookConfig newConfig = new WebhookConfigBuilder().name(trimmed).url("").sendScreenshot(false)
                    .sendPlaylist(false).build();
            configs.add(newConfig);
            webhooksList.getSelectionModel().select(configs.size() - 1);
            log.debug("onAdd: added webhook '{}'", trimmed);
        });
    }

    /**
     * Deletes the currently selected webhook entry.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onDelete(ActionEvent event) {
        int idx = webhooksList.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= configs.size()) {
            log.warn("onDelete: selected index {} out of range [0,{})", idx, configs.size());
            return;
        }
        String deletedName = configs.get(idx).getName();
        selectedIndex = -1;
        configs.remove(idx);
        clearFields();
        log.debug("onDelete: removed webhook '{}' at index {}", deletedName, idx);
    }

    private void onSelect(int idx) {
        captureCurrent();
        selectedIndex = idx;
        if (idx < 0 || idx >= configs.size()) {
            clearFields();
            return;
        }
        WebhookConfig config = configs.get(idx);
        nameField.setText(config.getName());
        urlField.setText(config.getUrl());
        sendImagesCheck.setSelected(config.isSendScreenshot());
        sendPlaylistCheck.setSelected(config.isSendPlaylist());

        Map<String, Boolean> levelMap = config.getEnabledLevels();
        for (int i = 0; i < levelBoxes.size(); i++) {
            String key = String.valueOf(i + 1);
            levelBoxes.get(i).setSelected(Boolean.TRUE.equals(levelMap.getOrDefault(key, Boolean.TRUE)));
        }

        Map<String, Boolean> lampMap = config.getEnabledLamp();
        List<String> lampKeyList = WebhookConfigBuilder.LAMP_KEYS;
        CheckBox[] boxes = lampCheckBoxes();
        for (int i = 0; i < boxes.length && i < lampKeyList.size(); i++) {
            boxes[i].setSelected(Boolean.TRUE.equals(lampMap.getOrDefault(lampKeyList.get(i), Boolean.TRUE)));
        }
    }

    /**
     * Reads the current UI field values and applies them back to the currently
     * selected {@link WebhookConfig} in the list.
     */
    private void captureCurrent() {
        if (capturingCurrent) {
            return;
        }
        final int idx = selectedIndex;
        if (idx < 0 || idx >= configs.size()) {
            return;
        }
        capturingCurrent = true;
        try {
            LinkedHashMap<String, Boolean> levelMap = new LinkedHashMap<>();
            for (int i = 0; i < levelBoxes.size(); i++) {
                levelMap.put(String.valueOf(i + 1), levelBoxes.get(i).isSelected());
            }
            LinkedHashMap<String, Boolean> lampMap = new LinkedHashMap<>();
            List<String> lampKeyList = WebhookConfigBuilder.LAMP_KEYS;
            CheckBox[] boxes = lampCheckBoxes();
            for (int i = 0; i < lampKeyList.size() && i < boxes.length; i++) {
                lampMap.put(lampKeyList.get(i), boxes[i].isSelected());
            }
            WebhookConfig updated = new WebhookConfigBuilder().name(nameField.getText().trim())
                    .url(urlField.getText().trim()).sendScreenshot(sendImagesCheck.isSelected())
                    .sendPlaylist(sendPlaylistCheck.isSelected()).enabledLevels(levelMap).enabledLamps(lampMap).build();
            configs.set(idx, updated);
        } finally {
            capturingCurrent = false;
        }
    }

    private void clearFields() {
        nameField.clear();
        urlField.clear();
        sendImagesCheck.setSelected(false);
        sendPlaylistCheck.setSelected(false);
        for (CheckBox cb : levelBoxes) {
            cb.setSelected(true);
        }
        for (CheckBox cb : lampCheckBoxes()) {
            cb.setSelected(true);
        }
    }

    private void loadFromRepository() {
        List<WebhookConfig> loaded = webhookRepo.load();
        configs.setAll(loaded);
        log.debug("loadFromRepository: loaded {} webhook(s)", loaded.size());
    }

    private CheckBox[] lampCheckBoxes() {
        return new CheckBox[]{lampPucCheck, lampUcCheck, lampExhCheck, lampHardCheck, lampClearCheck, lampFailedCheck};
    }
}
