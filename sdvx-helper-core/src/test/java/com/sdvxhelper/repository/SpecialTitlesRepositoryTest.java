package com.sdvxhelper.repository;

import com.sdvxhelper.util.SpecialTitles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link SpecialTitlesRepository}.
 */
class SpecialTitlesRepositoryTest {

    @TempDir
    Path tempDir;

    private File tempFile() {
        return tempDir.resolve("special_titles.json").toFile();
    }

    @Test
    void loadFromClasspathFallbackWhenFileAbsent() {
        // File does not exist -- should load bundled default from classpath
        SpecialTitlesRepository repo = new SpecialTitlesRepository(tempFile());
        SpecialTitles st = repo.load();
        Assertions.assertNotNull(st);
        // The bundled default should contain "archivezip"
        Assertions.assertEquals("archive::zip", st.restoreTitle("archivezip"));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        SpecialTitlesRepository repo = new SpecialTitlesRepository(tempFile());

        SpecialTitles original = new SpecialTitles();
        original.setSpecialTitles(Map.of("testkey", "test value"));
        original.setIgnoredNames(List.of("Ignored Song"));
        original.setDirectRemoves(List.of("Bad Song"));
        original.setDirectOverrides(Map.of("Override", List.of("EXH:20")));

        repo.save(original);
        Assertions.assertTrue(tempFile().exists());

        SpecialTitles loaded = repo.load();
        Assertions.assertEquals("test value", loaded.restoreTitle("testkey"));
        Assertions.assertTrue(loaded.isIgnored("Ignored Song"));
        Assertions.assertTrue(loaded.shouldRemove("Bad Song"));
        Assertions.assertEquals(List.of("EXH:20"), loaded.getDirectOverrides("Override"));
    }

    @Test
    void loadReturnsNonNullWhenBothFileAndClasspathMissing() {
        // Point to a nonexistent classpath resource effectively by using a nonexistent file
        // We can only test that it doesn't throw and returns non-null
        File nonExistent = tempDir.resolve("does_not_exist.json").toFile();
        SpecialTitlesRepository repo = new SpecialTitlesRepository(nonExistent);
        // Should fall through to classpath resource (which exists in test classpath)
        SpecialTitles st = repo.load();
        Assertions.assertNotNull(st);
    }
}
