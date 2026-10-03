package com.sdvxhelper.repository;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Suppress the unchecked cast: JSON-B's raw LinkedHashMap produces Object values.
// We immediately convert every value to String in toStringMap(), so no data is lost.

/**
 * Reads the detection parameters from {@code resources/params.json}.
 *
 * <p>
 * Parameters are read-only at runtime; they define the screen regions and
 * thresholds used by the image-analysis pipeline. The file is loaded from the
 * path specified in the user settings ({@code params_json} key), falling back
 * to the bundled classpath resource.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ParamsRepository {

    private static final Logger log = LoggerFactory.getLogger(ParamsRepository.class);
    private static final String CLASSPATH_RESOURCE = "params.json";

    private Jsonb jsonb;

    /**
     * Constructs the repository with a shared {@link Jsonb} instance.
     */
    public ParamsRepository() {
        this.jsonb = JsonbBuilder.create();
    }

    /**
     * Loads the detection parameters from the given file path. Falls back to the
     * bundled classpath resource if the file cannot be read.
     *
     * <p>
     * All JSON values (numbers, booleans, strings) are converted to their
     * {@link String} representation so callers receive a uniform
     * {@code Map<String, String>} and can parse values with standard
     * {@link Integer#parseInt} / {@link Double#parseDouble} calls.
     * </p>
     *
     * @param filePath
     *            path to {@code params.json} (from user settings)
     * @return parameter map with all values as strings; never {@code null}
     */
    public Map<String, String> load(String filePath) {
        File externalFile = new File(filePath);
        if (externalFile.exists()) {
            try (FileReader reader = new FileReader(externalFile, StandardCharsets.UTF_8)) {
                Map<String, String> params = parseToStringMap(reader);
                log.info("Loaded params.json from {}", externalFile.getAbsolutePath());
                return params;
            } catch (IOException e) {
                log.warn("Failed to load params.json from {}; trying classpath", filePath, e);
            }
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (is == null) {
                log.error("params.json not found on classpath");
                return Collections.emptyMap();
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Map<String, String> params = parseToStringMap(reader);
                log.info("Loaded params.json from classpath");
                return params;
            }
        } catch (IOException e) {
            log.error("Failed to load bundled params.json", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Parses the JSON from {@code reader} as a raw map and converts every value to
     * its {@link String} representation.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseToStringMap(java.io.Reader reader) {
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
