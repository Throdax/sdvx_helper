package com.sdvxhelper.repository;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import com.sdvxhelper.util.SpecialTitles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and saves the externalised title-normalisation data
 * ({@code special_titles.json}).
 *
 * <p>
 * This repository is the runtime counterpart of the bundled
 * {@code special_titles.json} resource. At startup it attempts to load the file
 * from the working directory; if absent it falls back to the bundled classpath
 * resource so the application always has a working default.
 *
 * <p>
 * New title mappings, rating overrides, or ignore rules can be added by editing
 * {@code special_titles.json} — no recompilation or new release is required.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SpecialTitlesRepository {

    private static final Logger log = LoggerFactory.getLogger(SpecialTitlesRepository.class);
    private static final String DEFAULT_PATH = "special_titles.json";
    private static final String CLASSPATH_RESOURCE = "special_titles.json";

    private final File file;
    private final Jsonb jsonb;

    /**
     * Constructs a repository backed by the default file
     * ({@code special_titles.json} in the current working directory).
     */
    public SpecialTitlesRepository() {
        this(new File(DEFAULT_PATH));
    }

    /**
     * Constructs a repository backed by a custom file (useful for testing).
     *
     * @param file
     *            JSON file to read from / write to
     */
    public SpecialTitlesRepository(File file) {
        this.file = file;
        JsonbConfig config = new JsonbConfig().withFormatting(true).withEncoding("UTF-8");
        this.jsonb = JsonbBuilder.create(config);
    }

    /**
     * Loads the special-titles data.
     *
     * <p>
     * Resolution order:
     * </p>
     * <ol>
     * <li>External {@code special_titles.json} in the working directory.</li>
     * <li>Bundled {@code special_titles.json} on the classpath (read-only
     * default).</li>
     * </ol>
     *
     * @return loaded {@link SpecialTitles}; never {@code null}
     */
    public SpecialTitles load() {
        // Try external file first
        if (file.exists()) {
            try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                SpecialTitles st = jsonb.fromJson(reader, SpecialTitles.class);
                log.info("Loaded special_titles.json from {}", file.getAbsolutePath());
                return st != null ? st : new SpecialTitles();
            } catch (IOException e) {
                log.warn("Failed to load external special_titles.json; falling back to bundled default", e);
            }
        } else {
            log.info("special_titles.json not found at {}; using bundled default", file.getAbsolutePath());
        }

        // Fall back to bundled classpath resource
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CLASSPATH_RESOURCE)) {
            if (is == null) {
                log.warn("Bundled special_titles.json not found on classpath; returning empty instance");
                return new SpecialTitles();
            }
            try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                SpecialTitles st = jsonb.fromJson(reader, SpecialTitles.class);
                log.info("Loaded bundled special_titles.json from classpath");
                return st != null ? st : new SpecialTitles();
            }
        } catch (IOException e) {
            log.error("Failed to load bundled special_titles.json", e);
            return new SpecialTitles();
        }
    }

    /**
     * Saves the special-titles data to the external JSON file.
     *
     * @param specialTitles
     *            data to persist
     * @throws IOException
     *             if the file cannot be written
     */
    public void save(SpecialTitles specialTitles) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            jsonb.toJson(specialTitles, writer);
            log.info("Saved special_titles.json to {}", file.getAbsolutePath());
        } catch (Exception e) {
            throw new IOException("Failed to write special_titles.json", e);
        }
    }

    /**
     * Returns the backing file.
     *
     * @return JSON file
     */
    public File getFile() {
        return file;
    }
}
