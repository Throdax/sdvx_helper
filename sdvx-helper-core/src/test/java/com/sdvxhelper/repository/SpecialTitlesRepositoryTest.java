package com.sdvxhelper.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.sdvxhelper.util.SpecialTitles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SpecialTitlesRepository}.
 */
class SpecialTitlesRepositoryTest {

    private static final Path TEST_DIR = Paths.get("target", "test-work", "SpecialTitlesRepositoryTest");

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
    void loadFromClasspathFallbackWhenFileAbsent() {
        SpecialTitlesRepository repo = new SpecialTitlesRepository(testFile("special_titles.json"));
        SpecialTitles st = repo.load();
        Assertions.assertNotNull(st);
        Assertions.assertEquals("archive::zip", st.restoreTitle("archivezip"));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        SpecialTitlesRepository repo = new SpecialTitlesRepository(testFile("special_titles.json"));

        SpecialTitles original = new SpecialTitles();
        original.setSpecialTitles(Map.of("testkey", "test value"));
        original.setIgnoredNames(List.of("Ignored Song"));
        original.setDirectRemoves(List.of("Bad Song"));
        original.setDirectOverrides(Map.of("Override", List.of("EXH:20")));

        repo.save(original);
        Assertions.assertTrue(testFile("special_titles.json").exists());

        SpecialTitles loaded = repo.load();
        Assertions.assertEquals("test value", loaded.restoreTitle("testkey"));
        Assertions.assertTrue(loaded.isIgnored("Ignored Song"));
        Assertions.assertTrue(loaded.shouldRemove("Bad Song"));
        Assertions.assertEquals(List.of("EXH:20"), loaded.getDirectOverrides("Override"));
    }

    @Test
    void loadReturnsNonNullWhenBothFileAndClasspathMissing() {
        File nonExistent = testFile("does_not_exist.json");
        SpecialTitlesRepository repo = new SpecialTitlesRepository(nonExistent);
        SpecialTitles st = repo.load();
        Assertions.assertNotNull(st);
    }
}
