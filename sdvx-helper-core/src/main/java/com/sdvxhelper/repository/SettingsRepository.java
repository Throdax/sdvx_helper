package com.sdvxhelper.repository;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import com.sdvxhelper.config.DefaultSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes user settings to/from {@code settings.json} using JSON-B
 * with Eclipse Yasson as the implementation.
 *
 * <p>
 * On load, missing keys are merged from {@link DefaultSettings#getDefaults()}
 * to ensure forward-compatibility when new settings are introduced without
 * requiring a manual migration step.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(SettingsRepository.class);
    private static final String DEFAULT_PATH = "settings.json";

    private File file;
    private Jsonb jsonb;

    /**
     * Constructs a repository backed by the default file.
     */
    public SettingsRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file (useful for testing).
     *
     * @param file
     *            JSON settings file
     */
    public SettingsRepository(File file) {
        this.file = file;
        JsonbConfig config = new JsonbConfig().withFormatting(true).withEncoding("UTF-8");
        this.jsonb = JsonbBuilder.create(config);
    }

    /**
     * Loads user settings from disk, merging any missing keys from the defaults.
     *
     * <p>
     * All JSON values — regardless of whether they are strings, numbers, booleans,
     * arrays, or nested objects — are converted to their {@link String}
     * representation. This ensures that settings files produced by the Python
     * version of SDVX Helper (which uses native JSON types instead of all-string
     * values) can be loaded without causing {@link ClassCastException} at runtime.
     * </p>
     *
     * @return settings map; never {@code null}
     */
    public Map<String, String> load() {
        Map<String, String> settings = new LinkedHashMap<>();
        boolean firstRun = !file.exists();

        if (file.exists()) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                settings.putAll(parseToStringMap(reader));
                log.info("Loaded settings from {}", file.getAbsolutePath());
            } catch (IOException | jakarta.json.bind.JsonbException e) {
                log.warn("Failed to load settings.json; using defaults", e);
            }
        } else {
            log.info("settings.json not found; using defaults");
        }

        // Merge missing keys from defaults
        Map<String, String> defaults = DefaultSettings.getDefaults();
        int added = 0;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!settings.containsKey(entry.getKey())) {
                settings.put(entry.getKey(), entry.getValue());
                added++;
            }
        }
        if (added > 0) {
            log.info("Merged {} missing keys from defaults", added);
        }

        if (firstRun) {
            try {
                save(settings);
                log.info("Created initial settings.json at {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Failed to write initial settings.json: {}", e.getMessage());
            }
        }

        return settings;
    }

    /**
     * Saves the settings map to disk as formatted JSON.
     *
     * @param settings
     *            settings map to persist
     * @throws IOException
     *             if the file cannot be written
     */
    public void save(Map<String, String> settings) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            jsonb.toJson(settings, writer);
            log.info("Saved settings to {}", file.getAbsolutePath());
        } catch (IOException | jakarta.json.bind.JsonbException e) {
            throw new IOException("Failed to write settings.json", e);
        }
    }

    /**
     * Returns the backing file.
     *
     * @return JSON settings file
     */
    public File getFile() {
        return file;
    }

    /**
     * Parses JSON from the given reader into a raw map and converts every value to
     * its {@link String} representation.
     *
     * <p>
     * Using the raw {@link LinkedHashMap} type instructs JSON-B to store each JSON
     * value as its natural Java counterpart ({@code BigDecimal} for numbers,
     * {@code Boolean} for booleans, {@code List} for arrays, {@code Map} for nested
     * objects). We then normalise every value to a {@code String} so the rest of
     * the application always receives a uniform {@code Map<String, String>}
     * regardless of how the file was originally written (Java or Python format).
     * </p>
     *
     * @param reader
     *            source of the JSON content
     * @return string-valued map; empty if the reader yields {@code null}
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseToStringMap(Reader reader) {
        Map<Object, Object> raw = jsonb.fromJson(reader, LinkedHashMap.class);
        if (raw == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result.put(key, value);
        }
        return result;
    }
}
