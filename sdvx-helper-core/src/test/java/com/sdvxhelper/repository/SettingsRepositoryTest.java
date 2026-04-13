package com.sdvxhelper.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SettingsRepository}.
 */
class SettingsRepositoryTest {

    @TempDir
    Path tempDir;

    private File tempFile() {
        return tempDir.resolve("settings.json").toFile();
    }

    @Test
    void loadReturnsDefaultsWhenFileAbsent() {
        SettingsRepository repo = new SettingsRepository(tempFile());
        Map<String, String> settings = repo.load();
        // A few key defaults should be present
        Assertions.assertTrue(settings.containsKey("host"));
        Assertions.assertTrue(settings.containsKey("port"));
        Assertions.assertEquals("localhost", settings.get("host"));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        SettingsRepository repo = new SettingsRepository(tempFile());

        Map<String, String> original = repo.load();
        original.put("player_name", "TestPlayer");
        repo.save(original);

        Assertions.assertTrue(tempFile().exists());

        Map<String, String> loaded = repo.load();
        Assertions.assertEquals("TestPlayer", loaded.get("player_name"));
    }

    @Test
    void missingKeysAreMergedFromDefaults() throws IOException {
        SettingsRepository repo = new SettingsRepository(tempFile());

        // Save a minimal settings file (only one key)
        Map<String, String> minimal = Map.of("player_name", "Alice");
        repo.save(minimal);

        Map<String, String> loaded = repo.load();
        // Should have both saved key and default keys
        Assertions.assertEquals("Alice", loaded.get("player_name"));
        Assertions.assertTrue(loaded.containsKey("host"), "Expected defaults to be merged");
        Assertions.assertTrue(loaded.containsKey("auto_update"), "Expected defaults to be merged");
    }
}
