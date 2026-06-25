package com.sdvxhelper.repository;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

import com.sdvxhelper.model.WebhookConfig;
import com.sdvxhelper.model.WebhookConfigBuilder;
import com.sdvxhelper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the {@code webhooks.json} file which stores all configured
 * Discord webhook destinations as {@link WebhookConfig} objects.
 *
 * <p>
 * On first run the file does not exist and {@link #load()} returns an empty
 * list. {@link #migrateFromLegacySettings(Map)} handles both the original
 * Python format (JSON-array strings such as {@code "[SDVX]"}) and the
 * intermediate Java pipe-separated format (e.g. {@code "SDVX|OTHER"}),
 * converting either into the new {@code webhooks.json} structure.
 * </p>
 *
 * <h2>File format</h2>
 *
 * <pre>{@code
 * {
 *   "webhooks": [
 *     {
 *       "name": "SDVX",
 *       "URL": "https://discord.com/api/webhooks/...",
 *       "send_screenshot": true,
 *       "send_playlist": true,
 *       "enabled_levels": [{"1": true}, {"2": true}, ...],
 *       "enabled_lamp":   [{"PUC": true}, {"UC": true}, ...]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WebhookConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(WebhookConfigRepository.class);
    private static final String DEFAULT_PATH = "webhooks.json";

    /** Ordered lamp keys that map to Python lamp indices 0-5. */
    private static final List<String> LAMP_KEYS = WebhookConfigBuilder.LAMP_KEYS;

    private File file;

    /**
     * Constructs a repository backed by the default {@code webhooks.json} file.
     */
    public WebhookConfigRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by the given file.
     *
     * @param file
     *            backing file
     */
    public WebhookConfigRepository(File file) {
        this.file = file;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Loads webhook configurations from {@code webhooks.json}.
     *
     * @return mutable list of webhook configs; empty if the file does not exist or
     *         is unreadable
     */
    public List<WebhookConfig> load() {
        if (!file.exists()) {
            log.debug("load: {} does not exist, returning empty list", file.getName());
            return new ArrayList<>();
        }
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
                JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject root = jsonReader.readObject();
            JsonArray webhooksArray = root.getJsonArray("webhooks");
            if (webhooksArray == null) {
                log.warn("load: 'webhooks' key not found in {}", file.getName());
                return new ArrayList<>();
            }
            List<WebhookConfig> configs = new ArrayList<>();
            for (JsonValue value : webhooksArray) {
                if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                    configs.add(parseWebhookConfig(value.asJsonObject()));
                }
            }
            log.debug("load: read {} webhook(s) from {}", configs.size(), file.getAbsolutePath());
            return configs;
        } catch (IOException e) {
            log.error("load: failed to read {}: {}", file.getName(), e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Saves the given webhook configurations to {@code webhooks.json}.
     *
     * @param configs
     *            webhook configurations to persist
     * @throws IOException
     *             if the file cannot be written
     */
    public void save(List<WebhookConfig> configs) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        JsonObject root = buildJsonRoot(configs);
        Map<String, Object> properties = new HashMap<>();
        properties.put(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory writerFactory = Json.createWriterFactory(properties);
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8);
                JsonWriter jsonWriter = writerFactory.createWriter(writer)) {
            jsonWriter.writeObject(root);
            log.info("save: {} webhook(s) written to {}", configs.size(), file.getAbsolutePath());
        }
    }

    /**
     * Detects legacy webhook settings in the given settings map and migrates them
     * to {@code webhooks.json}.
     *
     * <p>
     * Migration is skipped when {@code webhooks.json} already exists. Two legacy
     * formats are understood:
     * </p>
     * <ul>
     * <li><b>Python format</b> — values wrapped in {@code […]} (e.g.
     * {@code "[SDVX]"}, {@code "[[true, false, …]]"})</li>
     * <li><b>Java pipe format</b> — names/URLs joined with {@code |}, booleans
     * comma-separated, nested groups separated by {@code ;}</li>
     * </ul>
     *
     * <p>
     * Each migrated webhook and its settings are logged at {@code INFO} level.
     * </p>
     *
     * @param settings
     *            settings map loaded from {@code settings.json}
     * @return {@code true} if migration was performed and the file was written;
     *         {@code false} if the file already existed or there was nothing to
     *         migrate
     */
    public boolean migrateFromLegacySettings(Map<String, String> settings) {
        if (file.exists()) {
            log.debug("migrateFromLegacySettings: {} already exists, skipping", file.getName());
            return false;
        }

        String rawNames = settings.get("webhook_names");
        if (rawNames == null || rawNames.isBlank()) {
            log.debug("migrateFromLegacySettings: no webhook_names in settings, nothing to migrate");
            return false;
        }

        boolean isPythonFormat = rawNames.trim().startsWith("[");
        log.info("migrateFromLegacySettings: detected {} format, migrating to {}",
                isPythonFormat ? "Python" : "Java-pipe", file.getName());

        List<String> names;
        List<String> urls;
        List<Boolean> pics;
        List<Boolean> playlist;
        List<List<Boolean>> lvs;
        List<List<Boolean>> lamps;

        if (isPythonFormat) {
            names = StringUtils.parseListSetting(settings.get("webhook_names"));
            urls = StringUtils.parseListSetting(settings.get("webhook_urls"));
            pics = parseBoolListPython(settings.get("webhook_enable_pics"), names.size(), false);
            playlist = parseBoolListPython(settings.get("webhook_playlist"), names.size(), false);
            lvs = parseNestedBoolListPython(settings.get("webhook_enable_lvs"), names.size(), 20, true);
            lamps = parseNestedBoolListPython(settings.get("webhook_enable_lamps"), names.size(), 6, true);
        } else {
            names = splitPipe(settings.get("webhook_names"));
            urls = splitPipe(settings.get("webhook_urls"));
            pics = parseBoolListPipe(settings.get("webhook_enable_pics"), names.size(), false);
            playlist = parseBoolListPipe(settings.get("webhook_playlist"), names.size(), false);
            lvs = parseNestedBoolListPipe(settings.get("webhook_enable_lvs"), names.size(), 20, true);
            lamps = parseNestedBoolListPipe(settings.get("webhook_enable_lamps"), names.size(), 6, true);
        }

        List<WebhookConfig> configs = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            boolean sendScreenshot = i < pics.size() && Boolean.TRUE.equals(pics.get(i));
            boolean sendPlaylist = i < playlist.size() && Boolean.TRUE.equals(playlist.get(i));

            LinkedHashMap<String, Boolean> levelMap = buildLevelMap(
                    i < lvs.size() ? lvs.get(i) : Collections.emptyList());
            LinkedHashMap<String, Boolean> lampMap = buildLampMap(
                    i < lamps.size() ? lamps.get(i) : Collections.emptyList());

            WebhookConfig config = new WebhookConfigBuilder().name(names.get(i)).url(i < urls.size() ? urls.get(i) : "")
                    .sendScreenshot(sendScreenshot).sendPlaylist(sendPlaylist).enabledLevels(levelMap)
                    .enabledLamps(lampMap).build();
            configs.add(config);

            log.info(
                    "migrateFromLegacySettings: webhook[{}] '{}' → url={}, screenshot={}, playlist={}, levels={}, lamps={}",
                    i, config.getName(), config.getUrl().isBlank() ? "<blank>" : "[configured]", sendScreenshot,
                    sendPlaylist,
                    levelMap.values().stream().filter(v -> v).count() + "/" + levelMap.size() + " enabled",
                    lampMap.values().stream().filter(v -> v).count() + "/" + lampMap.size() + " enabled");
        }

        try {
            save(configs);
            log.info("migrateFromLegacySettings: migration complete - {} webhook(s) written to {}", configs.size(),
                    file.getAbsolutePath());
            removeLegacyKeysFromMap(settings);
            return true;
        } catch (IOException e) {
            log.error("migrateFromLegacySettings: failed to save {}: {}", file.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * Removes the per-webhook settings keys that have been migrated to
     * {@code webhooks.json} from the supplied settings map.
     *
     * <p>
     * This is called after a successful migration so that the caller can persist
     * the cleaned map back to {@code settings.json}. The global
     * {@code webhook_player_name} key is intentionally left in place.
     * </p>
     *
     * @param settings
     *            mutable settings map to clean up
     */
    private void removeLegacyKeysFromMap(Map<String, String> settings) {
        String[] legacyKeys = {"webhook_names", "webhook_urls", "webhook_enable_pics", "webhook_playlist",
                "webhook_enable_lvs", "webhook_enable_lamps"};
        for (String key : legacyKeys) {
            if (settings.remove(key) != null) {
                log.debug("migrateFromLegacySettings: removed legacy key '{}' from settings map", key);
            }
        }
        log.info(
                "migrateFromLegacySettings: legacy webhook keys removed from settings — caller should persist settings.json");
    }

    // -------------------------------------------------------------------------
    // JSON serialisation helpers
    // -------------------------------------------------------------------------

    private JsonObject buildJsonRoot(List<WebhookConfig> configs) {
        JsonArrayBuilder webhooksArray = Json.createArrayBuilder();
        for (WebhookConfig config : configs) {
            webhooksArray.add(buildJsonWebhook(config));
        }
        return Json.createObjectBuilder().add("webhooks", webhooksArray).build();
    }

    private JsonObject buildJsonWebhook(WebhookConfig config) {
        JsonObjectBuilder obj = Json.createObjectBuilder().add("name", config.getName() != null ? config.getName() : "")
                .add("URL", config.getUrl() != null ? config.getUrl() : "")
                .add("send_screenshot", config.isSendScreenshot()).add("send_playlist", config.isSendPlaylist())
                .add("enabled_levels", buildJsonFilterArray(config.getEnabledLevels()))
                .add("enabled_lamp", buildJsonFilterArray(config.getEnabledLamp()));
        return obj.build();
    }

    private JsonArray buildJsonFilterArray(Map<String, Boolean> filterMap) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (Map.Entry<String, Boolean> entry : filterMap.entrySet()) {
            arrayBuilder.add(Json.createObjectBuilder().add(entry.getKey(), entry.getValue()));
        }
        return arrayBuilder.build();
    }

    // -------------------------------------------------------------------------
    // JSON deserialisation helpers
    // -------------------------------------------------------------------------

    private WebhookConfig parseWebhookConfig(JsonObject obj) {
        String name = obj.getString("name", "");
        String url = obj.getString("URL", "");
        boolean sendScreenshot = obj.getBoolean("send_screenshot", false);
        boolean sendPlaylist = obj.getBoolean("send_playlist", false);

        // Start from a full default map so that any key added to the code after this
        // config was saved will default to true rather than disappearing silently.
        LinkedHashMap<String, Boolean> levelMap = new LinkedHashMap<>(WebhookConfigBuilder.buildDefaultLevels(true));
        JsonArray levelsArray = obj.getJsonArray("enabled_levels");
        if (levelsArray != null) {
            for (JsonValue v : levelsArray) {
                if (v.getValueType() == JsonValue.ValueType.OBJECT) {
                    JsonObject entry = v.asJsonObject();
                    for (String key : entry.keySet()) {
                        levelMap.put(key, entry.getBoolean(key, true));
                    }
                }
            }
        }

        // Same merge strategy: defaults first, file values override, unknown future
        // keys (e.g. SKILL CLEAR added after the file was written) stay enabled.
        LinkedHashMap<String, Boolean> lampMap = new LinkedHashMap<>(WebhookConfigBuilder.buildDefaultLamp(true));
        JsonArray lampArray = obj.getJsonArray("enabled_lamp");
        if (lampArray != null) {
            for (JsonValue v : lampArray) {
                if (v.getValueType() == JsonValue.ValueType.OBJECT) {
                    JsonObject entry = v.asJsonObject();
                    for (String key : entry.keySet()) {
                        lampMap.put(key, entry.getBoolean(key, true));
                    }
                }
            }
        }

        return new WebhookConfigBuilder().name(name).url(url).sendScreenshot(sendScreenshot).sendPlaylist(sendPlaylist)
                .enabledLevels(levelMap).enabledLamps(lampMap).build();
    }

    // -------------------------------------------------------------------------
    // Migration — filter map construction
    // -------------------------------------------------------------------------

    private LinkedHashMap<String, Boolean> buildLevelMap(List<Boolean> bools) {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (int lv = 1; lv <= 20; lv++) {
            int index = lv - 1;
            boolean enabled = index < bools.size() ? Boolean.TRUE.equals(bools.get(index)) : true;
            map.put(String.valueOf(lv), enabled);
        }
        return map;
    }

    private LinkedHashMap<String, Boolean> buildLampMap(List<Boolean> bools) {
        LinkedHashMap<String, Boolean> map = new LinkedHashMap<>();
        for (int i = 0; i < LAMP_KEYS.size(); i++) {
            boolean enabled = i < bools.size() ? Boolean.TRUE.equals(bools.get(i)) : true;
            map.put(LAMP_KEYS.get(i), enabled);
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Migration — Python format parsers
    // -------------------------------------------------------------------------

    private List<Boolean> parseBoolListPython(String raw, int minSize, boolean defaultVal) {
        List<String> tokens = StringUtils.parseListSetting(raw);
        List<Boolean> result = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            result.add(Boolean.parseBoolean(trimmed));
        }
        while (result.size() < minSize) {
            result.add(defaultVal);
        }
        return result;
    }

    private List<List<Boolean>> parseNestedBoolListPython(String raw, int outerSize, int innerSize,
            boolean defaultVal) {
        List<String> outerTokens = StringUtils.parseListSetting(raw);
        List<List<Boolean>> result = new ArrayList<>();
        for (String outerToken : outerTokens) {
            List<String> innerTokens = StringUtils.parseListSetting(outerToken);
            List<Boolean> inner = new ArrayList<>();
            for (String token : innerTokens) {
                String trimmed = token.trim();
                inner.add(Boolean.parseBoolean(trimmed));
            }
            while (inner.size() < innerSize) {
                inner.add(defaultVal);
            }
            result.add(inner);
        }
        while (result.size() < outerSize) {
            result.add(defaultBoolList(innerSize, defaultVal));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Migration — Java pipe format parsers
    // -------------------------------------------------------------------------

    private List<String> splitPipe(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(raw.split("\\|", -1)));
    }

    private List<Boolean> parseBoolListPipe(String raw, int minSize, boolean defaultVal) {
        List<Boolean> result = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String trimmedPart = part.trim();
                result.add(Boolean.parseBoolean(trimmedPart));
            }
        }
        while (result.size() < minSize) {
            result.add(defaultVal);
        }
        return result;
    }

    private List<List<Boolean>> parseNestedBoolListPipe(String raw, int outerSize, int innerSize, boolean defaultVal) {
        List<List<Boolean>> result = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String group : raw.split(";")) {
                List<Boolean> inner = new ArrayList<>();
                for (String part : group.split(",")) {
                    String trimmedPart = part.trim();
                    inner.add(Boolean.parseBoolean(trimmedPart));
                }
                while (inner.size() < innerSize) {
                    inner.add(defaultVal);
                }
                result.add(inner);
            }
        }
        while (result.size() < outerSize) {
            result.add(defaultBoolList(innerSize, defaultVal));
        }
        return result;
    }

    private List<Boolean> defaultBoolList(int size, boolean val) {
        List<Boolean> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(val);
        }
        return list;
    }
}
