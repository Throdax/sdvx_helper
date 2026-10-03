package com.sdvxhelper.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SettingsRepository}.
 */
class SettingsRepositoryTest {

    private static final Path TEST_DIR = Paths.get("target", "test-work", "SettingsRepositoryTest");

    @BeforeAll
    static void createTestDir() throws IOException {
        Files.createDirectories(TEST_DIR);
    }

    @AfterEach
    void cleanTestDir() throws IOException {
        try (Stream<Path> files = Files.list(TEST_DIR)) {
            files.forEach(p -> p.toFile().delete());
        }
    }

    private File testFile(String name) {
        return TEST_DIR.resolve(name).toFile();
    }

    @Test
    void loadReturnsDefaultsWhenFileAbsent() {
        SettingsRepository repo = new SettingsRepository(testFile("settings.json"));
        Map<String, String> settings = repo.load();
        Assertions.assertTrue(settings.containsKey("host"));
        Assertions.assertTrue(settings.containsKey("port"));
        Assertions.assertEquals("localhost", settings.get("host"));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        SettingsRepository repo = new SettingsRepository(testFile("settings.json"));

        Map<String, String> original = repo.load();
        original.put("player_name", "TestPlayer");
        repo.save(original);

        Assertions.assertTrue(testFile("settings.json").exists());

        Map<String, String> loaded = repo.load();
        Assertions.assertEquals("TestPlayer", loaded.get("player_name"));
    }

    @Test
    void missingKeysAreMergedFromDefaults() throws IOException {
        SettingsRepository repo = new SettingsRepository(testFile("settings.json"));

        Map<String, String> minimal = Map.of("player_name", "Alice");
        repo.save(minimal);

        Map<String, String> loaded = repo.load();
        Assertions.assertEquals("Alice", loaded.get("player_name"));
        Assertions.assertTrue(loaded.containsKey("host"), "Expected defaults to be merged");
        Assertions.assertTrue(loaded.containsKey("auto_update"), "Expected defaults to be merged");
    }
}
