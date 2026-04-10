package com.sdvxhelper.repository;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the detection parameters from {@code resources/params.json}.
 *
 * <p>Parameters are read-only at runtime; they define the screen regions and
 * thresholds used by the image-analysis pipeline.  The file is loaded from the
 * path specified in the user settings ({@code params_json} key), falling back to
 * the bundled classpath resource.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ParamsRepository {

    private static final Logger log = LoggerFactory.getLogger(ParamsRepository.class);
    private static final String CLASSPATH_RESOURCE = "params.json";

    private final Jsonb jsonb;

    /**
     * Constructs the repository with a shared {@link Jsonb} instance.
     */
    public ParamsRepository() {
        this.jsonb = JsonbBuilder.create();
    }

    /**
     * Loads the detection parameters from the given file path.
     * Falls back to the bundled classpath resource if the file cannot be read.
     *
     * @param filePath path to {@code params.json} (from user settings)
     * @return nested parameter map; never {@code null}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> load(String filePath) {
        // Try external file first
        File externalFile = new File(filePath);
        if (externalFile.exists()) {
            try (FileReader reader = new FileReader(externalFile, StandardCharsets.UTF_8)) {
                Map<String, Object> params = jsonb.fromJson(reader, LinkedHashMap.class);
                log.info("Loaded params.json from {}", externalFile.getAbsolutePath());
                return params != null ? params : Collections.emptyMap();
            } catch (IOException e) {
                log.warn("Failed to load params.json from {}; trying classpath", filePath, e);
            }
        }

        // Fall back to bundled classpath resource
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (is == null) {
                log.error("params.json not found on classpath");
                return Collections.emptyMap();
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Map<String, Object> params = jsonb.fromJson(reader, LinkedHashMap.class);
                log.info("Loaded params.json from classpath");
                return params != null ? params : Collections.emptyMap();
            }
        } catch (IOException e) {
            log.error("Failed to load bundled params.json", e);
            return Collections.emptyMap();
        }
    }
}
