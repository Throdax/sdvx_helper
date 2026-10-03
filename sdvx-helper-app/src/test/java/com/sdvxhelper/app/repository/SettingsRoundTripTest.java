package com.sdvxhelper.app.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.sdvxhelper.repository.SettingsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every settings group survives a full persistence round-trip:
 * values are modified in the in-memory map, saved to a JSON file via
 * {@link SettingsRepository#save}, and then reloaded via
 * {@link SettingsRepository#load}. Each test uses its own dedicated output file
 * so the tests can run in parallel without interference.
 *
 * <p>
 * These tests cover the persistence layer only. They do not exercise
 * {@code SettingsController} UI bindings; their purpose is to ensure that every
 * setting key serialises and deserialises correctly through JSON-B.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
class SettingsRoundTripTest {

    private static final Path OUTPUT_DIR = Paths.get("target", "test-output", "settings");

    /**
     * Removes any JSON files left over from a previous run so each run starts from
     * a clean slate regardless of how the prior run ended.
     *
     * @throws IOException
     *             if a leftover file cannot be deleted
     */
    @BeforeAll
    static void cleanLeftovers() throws IOException {
        if (Files.exists(OUTPUT_DIR)) {
            for (String name : new String[]{"discord.json", "obs.json", "detection.json", "autosave.json",
                    "logwindow.json", "scoreimport.json", "orientation.json", "player.json"}) {
                Files.deleteIfExists(OUTPUT_DIR.resolve(name));
            }
        }
        Files.createDirectories(OUTPUT_DIR);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link SettingsRepository} backed by the given file name inside the
     * test output directory and performs a load to obtain a settings map
     * pre-populated with defaults.
     *
     * @param fileName
     *            name of the JSON file inside the test output directory
     * @return settings map loaded from an empty (defaults-only) repository
     */
    private SettingsRepository repoFor(String fileName) {
        return new SettingsRepository(OUTPUT_DIR.resolve(fileName).toFile());
    }

    // =========================================================================
    // Player / general
    // =========================================================================

    /**
     * Verifies that the player name, auto-update flag, and rank-D filter are
     * persisted correctly.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void playerSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("player.json");
        Map<String, String> settings = repo.load();

        settings.put("player_name", "Throdax");
        settings.put("auto_update", "false");
        settings.put("ignore_rankD", "false");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("Throdax", loaded.get("player_name"));
        Assertions.assertEquals("false", loaded.get("auto_update"));
        Assertions.assertEquals("false", loaded.get("ignore_rankD"));
    }

    // =========================================================================
    // OBS connection
    // =========================================================================

    /**
     * Verifies that the OBS WebSocket host, port, and password survive a save/load
     * cycle.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void obsConnectionSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("obs.json");
        Map<String, String> settings = repo.load();

        settings.put("host", "192.168.1.50");
        settings.put("port", "4455");
        settings.put("passwd", "s3cr3t_pass");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("192.168.1.50", loaded.get("host"));
        Assertions.assertEquals("4455", loaded.get("port"));
        Assertions.assertEquals("s3cr3t_pass", loaded.get("passwd"));
    }

    // =========================================================================
    // Discord presence
    // =========================================================================

    /**
     * Verifies that all four Discord presence settings round-trip correctly when
     * set to {@code true}. This specifically guards against the key-name mismatch
     * bug where the enable checkbox was saved under {@code "discord_enable"}
     * instead of {@code "discord_presence_enable"}.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void discordPresenceAllEnabledRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("discord.json");
        Map<String, String> settings = repo.load();

        settings.put("discord_presence_enable", "true");
        settings.put("discord_presence_upload_jacket", "true");
        settings.put("discord_presence_ocr_titles", "true");
        settings.put("discord_presence_song_as_title", "true");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("true", loaded.get("discord_presence_enable"), "discord_presence_enable must persist");
        Assertions.assertEquals("true", loaded.get("discord_presence_upload_jacket"),
                "discord_presence_upload_jacket must persist");
        Assertions.assertEquals("true", loaded.get("discord_presence_ocr_titles"),
                "discord_presence_ocr_titles must persist");
        Assertions.assertEquals("true", loaded.get("discord_presence_song_as_title"),
                "discord_presence_song_as_title must persist");
    }

    /**
     * Verifies that all four Discord presence settings round-trip correctly when
     * set to {@code false} (the default). Ensures that an explicit {@code false} is
     * written and is not confused with the default-merge behaviour.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void discordPresenceAllDisabledRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("discord.json");
        Map<String, String> settings = repo.load();

        settings.put("discord_presence_enable", "false");
        settings.put("discord_presence_upload_jacket", "false");
        settings.put("discord_presence_ocr_titles", "false");
        settings.put("discord_presence_song_as_title", "false");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("false", loaded.get("discord_presence_enable"));
        Assertions.assertEquals("false", loaded.get("discord_presence_upload_jacket"));
        Assertions.assertEquals("false", loaded.get("discord_presence_ocr_titles"));
        Assertions.assertEquals("false", loaded.get("discord_presence_song_as_title"));
    }

    // =========================================================================
    // Detection / gameplay
    // =========================================================================

    /**
     * Verifies that detection and gameplay-related settings (VF OCR, sample count,
     * rank filter, clip mode, RTA target) survive a save/load cycle.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void detectionSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("detection.json");
        Map<String, String> settings = repo.load();

        settings.put("ignore_rankD", "false");
        settings.put("clip_lxly", "true");
        settings.put("always_update_vf", "true");
        settings.put("vf_ocr_enabled", "true");
        settings.put("detect_sample_count", "7");
        settings.put("rta_target_vf", "15.500");
        settings.put("save_jacketimg", "false");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("false", loaded.get("ignore_rankD"));
        Assertions.assertEquals("true", loaded.get("clip_lxly"));
        Assertions.assertEquals("true", loaded.get("always_update_vf"));
        Assertions.assertEquals("true", loaded.get("vf_ocr_enabled"));
        Assertions.assertEquals("7", loaded.get("detect_sample_count"));
        Assertions.assertEquals("15.500", loaded.get("rta_target_vf"));
        Assertions.assertEquals("false", loaded.get("save_jacketimg"));
    }

    // =========================================================================
    // Auto-save
    // =========================================================================

    /**
     * Verifies that the auto-save directory, interval, always-save flag, and
     * pre-wait delay round-trip correctly, including a path containing spaces and
     * backslashes (typical on Windows).
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void autosaveSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("autosave.json");
        Map<String, String> settings = repo.load();

        settings.put("autosave_dir", "D:\\My Saves\\sdvx");
        settings.put("autosave_interval", "120");
        settings.put("autosave_always", "true");
        settings.put("autosave_prewait", "2.5");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("D:\\My Saves\\sdvx", loaded.get("autosave_dir"));
        Assertions.assertEquals("120", loaded.get("autosave_interval"));
        Assertions.assertEquals("true", loaded.get("autosave_always"));
        Assertions.assertEquals("2.5", loaded.get("autosave_prewait"));
    }

    // =========================================================================
    // Log window / OBS text overlays
    // =========================================================================

    /**
     * Verifies that the log window transparency, blaster-max alert flag, and OBS
     * text overlay prefixes/suffixes round-trip correctly.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void logWindowSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("logwindow.json");
        Map<String, String> settings = repo.load();

        settings.put("logpic_bg_alpha", "128");
        settings.put("alert_blastermax", "true");
        settings.put("obs_txt_plays_header", "plays: ");
        settings.put("obs_txt_plays_footer", " today");
        settings.put("obs_txt_playtime_header", "time: ");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("128", loaded.get("logpic_bg_alpha"));
        Assertions.assertEquals("true", loaded.get("alert_blastermax"));
        Assertions.assertEquals("plays: ", loaded.get("obs_txt_plays_header"));
        Assertions.assertEquals(" today", loaded.get("obs_txt_plays_footer"));
        Assertions.assertEquals("time: ", loaded.get("obs_txt_playtime_header"));
    }

    // =========================================================================
    // Score import
    // =========================================================================

    /**
     * Verifies that the score import flags round-trip correctly.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void scoreImportSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("scoreimport.json");
        Map<String, String> settings = repo.load();

        settings.put("import_from_select", "true");
        settings.put("import_arcade_score", "true");

        repo.save(settings);
        Map<String, String> loaded = repo.load();

        Assertions.assertEquals("true", loaded.get("import_from_select"));
        Assertions.assertEquals("true", loaded.get("import_arcade_score"));
    }

    // =========================================================================
    // Orientation
    // =========================================================================

    /**
     * Verifies that each of the four orientation values ({@code top},
     * {@code bottom}, {@code left}, {@code right}) round-trips correctly.
     *
     * @throws IOException
     *             if the file cannot be saved
     */
    @Test
    void orientationSettingsRoundTrip() throws IOException {
        SettingsRepository repo = repoFor("orientation.json");
        Map<String, String> settings = repo.load();

        for (String value : new String[]{"top", "bottom", "left", "right"}) {
            settings.put("orientation", value);
            repo.save(settings);
            Map<String, String> loaded = repo.load();
            Assertions.assertEquals(value, loaded.get("orientation"),
                    "orientation='" + value + "' must survive round-trip");
        }
    }
}
