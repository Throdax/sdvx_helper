package com.sdvxhelper.repository;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sdvxhelper.config.DefaultSettings;

/**
 * Reads and writes user settings to/from {@code settings.json} using JSON-B with
 * Eclipse Yasson as the implementation.
 *
 * <p>On load, missing keys are merged from {@link DefaultSettings#getDefaults()} to
 * ensure forward-compatibility when new settings are introduced without requiring a
 * manual migration step.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(SettingsRepository.class);
    private static final String DEFAULT_PATH = "settings.json";

    private final File file;
    private final Jsonb jsonb;

    /**
     * Constructs a repository backed by the default file.
     */
    public SettingsRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file (useful for testing).
     *
     * @param file JSON settings file
     */
    public SettingsRepository(File file) {
        this.file = file;
        JsonbConfig config = new JsonbConfig()
                .withFormatting(true)
                .withEncoding("UTF-8");
        this.jsonb = JsonbBuilder.create(config);
    }

    /**
     * Loads user settings from disk, merging any missing keys from the defaults.
     *
     * @return settings map; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> load() {
        Map<String, String> settings = new LinkedHashMap<>();

        if (file.exists()) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                Map<String, String> loaded = jsonb.fromJson(reader, LinkedHashMap.class);
                if (loaded != null) {
                    settings.putAll(loaded);
                }
                log.info("Loaded settings from {}", file.getAbsolutePath());
            } catch (Exception e) {
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

        return settings;
    }

    /**
     * Saves the settings map to disk as formatted JSON.
     *
     * @param settings settings map to persist
     * @throws IOException if the file cannot be written
     */
    public void save(Map<String, String> settings) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            jsonb.toJson(settings, writer);
            log.info("Saved settings to {}", file.getAbsolutePath());
        } catch (Exception e) {
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
}
