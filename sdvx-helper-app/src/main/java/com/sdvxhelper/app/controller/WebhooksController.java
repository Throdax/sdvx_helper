package com.sdvxhelper.app.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;

import com.sdvxhelper.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Webhooks configuration dialog ({@code webhooks.fxml}).
 *
 * <p>
 * Manages the list of Discord webhooks, their URLs, per-webhook image/playlist
 * flags, and the level (L1-L20) and lamp filters. Mirrors the Python
 * {@code gui_webhook} implementation.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhooksController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(WebhooksController.class);

    @FXML
    private TextField txtPlayerName;
    @FXML
    private ListView<String> lstWebhooks;
    @FXML
    private Button btnAdd;
    @FXML
    private Button btnDelete;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtUrl;
    @FXML
    private CheckBox chkSendImages;
    @FXML
    private CheckBox chkSendPlaylist;
    @FXML
    private CheckBox chkLevelAll;
    @FXML
    private CheckBox chkLampAll;
    @FXML
    private CheckBox chkLampPuc;
    @FXML
    private CheckBox chkLampUc;
    @FXML
    private CheckBox chkLampExh;
    @FXML
    private CheckBox chkLampHard;
    @FXML
    private CheckBox chkLampClear;
    @FXML
    private CheckBox chkLampFailed;
    @FXML
    private FlowPane paneLevels;

    private final List<CheckBox> levelBoxes = new ArrayList<>();
    private final SettingsRepository settingsRepo = new SettingsRepository();
    private Map<String, String> settings;

    private final ObservableList<String> webhookNames = FXCollections.observableArrayList();
    private List<String> webhookUrls = new ArrayList<>();
    private List<Boolean> webhookEnablePics = new ArrayList<>();
    private List<Boolean> webhookPlaylist = new ArrayList<>();
    private List<List<Boolean>> webhookEnableLvs = new ArrayList<>();
    private List<List<Boolean>> webhookEnableLamps = new ArrayList<>();

    private int selectedIndex = -1;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        settings = settingsRepo.load();
        buildLevelCheckBoxes();
        wireAllToggles();
        if (lstWebhooks != null) {
            lstWebhooks.setItems(webhookNames);
            lstWebhooks.getSelectionModel().selectedIndexProperty()
                    .addListener((_, _, idx) -> onSelect(idx.intValue()));
        }
        loadFromSettings();
        if (txtPlayerName != null) {
            txtPlayerName.setText(settings.getOrDefault("webhook_player_name", ""));
        }
    }

    private void buildLevelCheckBoxes() {
        if (paneLevels == null) {
            return;
        }
        paneLevels.getChildren().clear();
        levelBoxes.clear();
        for (int lv = 1; lv <= 20; lv++) {
            CheckBox cb = new CheckBox(String.valueOf(lv));
            levelBoxes.add(cb);
            paneLevels.getChildren().add(cb);
        }
    }

    private void wireAllToggles() {
        if (chkLevelAll != null) {
            chkLevelAll.setOnAction(_ -> {
                for (CheckBox cb : levelBoxes) {
                    cb.setSelected(chkLevelAll.isSelected());
                }
            });
        }
        if (chkLampAll != null) {
            chkLampAll.setOnAction(_ -> {
                boolean v = chkLampAll.isSelected();
                for (CheckBox cb : new CheckBox[]{chkLampPuc, chkLampUc, chkLampExh, chkLampHard, chkLampClear,
                        chkLampFailed}) {
                    if (cb != null) {
                        cb.setSelected(v);
                    }
                }
            });
        }
    }

    /**
     * Persists the webhook configuration back to settings.
     */
    public void save() {
        captureCurrent();
        if (txtPlayerName != null) {
            settings.put("webhook_player_name", txtPlayerName.getText().trim());
        }
        settings.put("webhook_names", String.join("|", webhookNames));
        settings.put("webhook_urls", String.join("|", webhookUrls));
        settings.put("webhook_enable_pics", joinBools(webhookEnablePics));
        settings.put("webhook_playlist", joinBools(webhookPlaylist));
        settings.put("webhook_enable_lvs", joinListOfBools(webhookEnableLvs));
        settings.put("webhook_enable_lamps", joinListOfBools(webhookEnableLamps));
        try {
            settingsRepo.save(settings);
            log.info("Webhook settings saved ({} hooks)", webhookNames.size());
        } catch (IOException e) {
            log.error("Failed to save webhook settings", e);
        }
    }

    /**
     * Adds a new webhook with a user-supplied name.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onAdd(ActionEvent event) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setHeaderText("New webhook name");
        dlg.showAndWait().ifPresent(name -> {
            String nm = name.trim();
            if (nm.isEmpty()) {
                return;
            }
            webhookNames.add(nm);
            webhookUrls.add("");
            webhookEnablePics.add(Boolean.FALSE);
            webhookPlaylist.add(Boolean.FALSE);
            webhookEnableLvs.add(newBoolList(20, true));
            webhookEnableLamps.add(newBoolList(6, true));
            lstWebhooks.getSelectionModel().select(webhookNames.size() - 1);
        });
    }

    /**
     * Deletes the currently selected webhook.
     *
     * @param event
     *            action event
     */
    @FXML
    public void onDelete(ActionEvent event) {
        int idx = lstWebhooks == null ? -1 : lstWebhooks.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= webhookNames.size()) {
            return;
        }
        selectedIndex = -1;
        webhookNames.remove(idx);
        webhookUrls.remove(idx);
        webhookEnablePics.remove(idx);
        webhookPlaylist.remove(idx);
        webhookEnableLvs.remove(idx);
        webhookEnableLamps.remove(idx);
    }

    private void onSelect(int idx) {
        captureCurrent();
        selectedIndex = idx;
        if (idx < 0 || idx >= webhookNames.size()) {
            clearFields();
            return;
        }
        txtName.setText(webhookNames.get(idx));
        txtUrl.setText(webhookUrls.get(idx));
        chkSendImages.setSelected(webhookEnablePics.get(idx));
        chkSendPlaylist.setSelected(webhookPlaylist.get(idx));
        List<Boolean> lvs = webhookEnableLvs.get(idx);
        for (int i = 0; i < levelBoxes.size() && i < lvs.size(); i++) {
            levelBoxes.get(i).setSelected(lvs.get(i));
        }
        List<Boolean> lamps = webhookEnableLamps.get(idx);
        CheckBox[] lampBoxes = {chkLampPuc, chkLampUc, chkLampExh, chkLampHard, chkLampClear, chkLampFailed};
        for (int i = 0; i < lampBoxes.length && i < lamps.size(); i++) {
            if (lampBoxes[i] != null) {
                lampBoxes[i].setSelected(lamps.get(i));
            }
        }
    }

    private void captureCurrent() {
        if (selectedIndex < 0 || selectedIndex >= webhookNames.size()) {
            return;
        }
        if (txtName != null) {
            webhookNames.set(selectedIndex, txtName.getText().trim());
        }
        if (txtUrl != null) {
            webhookUrls.set(selectedIndex, txtUrl.getText().trim());
        }
        if (chkSendImages != null) {
            webhookEnablePics.set(selectedIndex, chkSendImages.isSelected());
        }
        if (chkSendPlaylist != null) {
            webhookPlaylist.set(selectedIndex, chkSendPlaylist.isSelected());
        }
        List<Boolean> lvs = new ArrayList<>();
        for (CheckBox cb : levelBoxes) {
            lvs.add(cb.isSelected());
        }
        webhookEnableLvs.set(selectedIndex, lvs);
        List<Boolean> lamps = new ArrayList<>();
        CheckBox[] lampBoxes = {chkLampPuc, chkLampUc, chkLampExh, chkLampHard, chkLampClear, chkLampFailed};
        for (CheckBox cb : lampBoxes) {
            lamps.add(cb != null && cb.isSelected());
        }
        webhookEnableLamps.set(selectedIndex, lamps);
    }

    private void clearFields() {
        if (txtName != null)
            txtName.clear();
        if (txtUrl != null)
            txtUrl.clear();
        if (chkSendImages != null)
            chkSendImages.setSelected(false);
        if (chkSendPlaylist != null)
            chkSendPlaylist.setSelected(false);
    }

    private void loadFromSettings() {
        webhookNames.setAll(splitPipe(settings.get("webhook_names")));
        webhookUrls = new ArrayList<>(splitPipe(settings.get("webhook_urls")));
        webhookEnablePics = new ArrayList<>(
                splitBools(settings.get("webhook_enable_pics"), webhookNames.size(), false));
        webhookPlaylist = new ArrayList<>(splitBools(settings.get("webhook_playlist"), webhookNames.size(), false));
        webhookEnableLvs = splitListOfBools(settings.get("webhook_enable_lvs"), webhookNames.size(), 20, true);
        webhookEnableLamps = splitListOfBools(settings.get("webhook_enable_lamps"), webhookNames.size(), 6, true);
        while (webhookUrls.size() < webhookNames.size())
            webhookUrls.add("");
        while (webhookEnablePics.size() < webhookNames.size())
            webhookEnablePics.add(Boolean.FALSE);
        while (webhookPlaylist.size() < webhookNames.size())
            webhookPlaylist.add(Boolean.FALSE);
        while (webhookEnableLvs.size() < webhookNames.size())
            webhookEnableLvs.add(newBoolList(20, true));
        while (webhookEnableLamps.size() < webhookNames.size())
            webhookEnableLamps.add(newBoolList(6, true));
    }

    private static List<String> splitPipe(String s) {
        if (s == null || s.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(s.split("\\|", -1)));
    }

    private static List<Boolean> splitBools(String s, int minSize, boolean defaultVal) {
        List<Boolean> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (String p : s.split(",")) {
                out.add(Boolean.parseBoolean(p.trim()));
            }
        }
        while (out.size() < minSize)
            out.add(defaultVal);
        return out;
    }

    private static List<List<Boolean>> splitListOfBools(String s, int outerSize, int innerSize, boolean defaultVal) {
        List<List<Boolean>> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (String group : s.split(";")) {
                List<Boolean> inner = new ArrayList<>();
                for (String p : group.split(",")) {
                    inner.add(Boolean.parseBoolean(p.trim()));
                }
                while (inner.size() < innerSize)
                    inner.add(defaultVal);
                out.add(inner);
            }
        }
        while (out.size() < outerSize)
            out.add(newBoolList(innerSize, defaultVal));
        return out;
    }

    private static List<Boolean> newBoolList(int size, boolean val) {
        List<Boolean> l = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
            l.add(val);
        return l;
    }

    private static String joinBools(List<Boolean> bools) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bools.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append(bools.get(i));
        }
        return sb.toString();
    }

    private static String joinListOfBools(List<List<Boolean>> lol) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lol.size(); i++) {
            if (i > 0)
                sb.append(";");
            sb.append(joinBools(lol.get(i)));
        }
        return sb.toString();
    }
}
