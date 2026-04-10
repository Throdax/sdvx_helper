package com.sdvxhelper.repository;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link PlayLogRepository}.
 */
class PlayLogRepositoryTest {

    @TempDir
    Path tempDir;

    private File tempFile() {
        return tempDir.resolve("alllog.xml").toFile();
    }

    @Test
    void loadReturnsEmptyLogWhenFileAbsent() {
        PlayLogRepository repo = new PlayLogRepository(tempFile());
        PlayLog log = repo.load();
        Assertions.assertNotNull(log);
        Assertions.assertTrue(log.getPlays().isEmpty());
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        PlayLogRepository repo = new PlayLogRepository(tempFile());

        PlayLog original = new PlayLog();
        original.setPlays(List.of(
            new OnePlayData("Song A", 9_000_000, 8_800_000, "clear", "exh", "2024-01-01"),
            new OnePlayData("Song B", 9_900_000, 9_800_000, "puc",   "mxm", "2024-01-02")
        ));

        repo.save(original);
        Assertions.assertTrue(tempFile().exists());

        PlayLog loaded = repo.load();
        Assertions.assertEquals(2, loaded.getPlays().size());

        OnePlayData first = loaded.getPlays().get(0);
        Assertions.assertEquals("Song A", first.getTitle());
        Assertions.assertEquals(9_000_000, first.getCurScore());
        Assertions.assertEquals("clear", first.getLamp());
    }

    @Test
    void loadedListIsSortedByDate() throws IOException {
        PlayLogRepository repo = new PlayLogRepository(tempFile());

        PlayLog pl = new PlayLog();
        pl.setPlays(List.of(
            new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-03"),
            new OnePlayData("Y", 100, 0, "clear", "exh", "2024-01-01"),
            new OnePlayData("Z", 100, 0, "clear", "exh", "2024-01-02")
        ));
        repo.save(pl);

        PlayLog loaded = repo.load();
        List<OnePlayData> plays = loaded.getPlays();
        Assertions.assertEquals("2024-01-01", plays.get(0).getDate());
        Assertions.assertEquals("2024-01-02", plays.get(1).getDate());
        Assertions.assertEquals("2024-01-03", plays.get(2).getDate());
    }
}
