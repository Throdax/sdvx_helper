package com.sdvxhelper.repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.PlayLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlayLogRepository}.
 */
class PlayLogRepositoryTest {

    private static final Path TEST_DIR = Paths.get("target", "test-work", "PlayLogRepositoryTest");

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
    void loadReturnsEmptyLogWhenFileAbsent() {
        PlayLogRepository repo = new PlayLogRepository(testFile("alllog.xml"));
        PlayLog log = repo.load();
        Assertions.assertNotNull(log);
        Assertions.assertTrue(log.getPlays().isEmpty());
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        PlayLogRepository repo = new PlayLogRepository(testFile("alllog.xml"));

        PlayLog original = new PlayLog();
        original.setPlays(
                List.of(new OnePlayData("Song A", 9_000_000, 8_800_000, "clear", "exh", "2024-01-01 12:00:00"),
                        new OnePlayData("Song B", 9_900_000, 9_800_000, "puc", "mxm", "2024-01-02 18:30:00")));

        repo.save(original);
        Assertions.assertTrue(testFile("alllog.xml").exists());

        PlayLog loaded = repo.load();
        Assertions.assertEquals(2, loaded.getPlays().size());

        OnePlayData first = loaded.getPlays().get(0);
        Assertions.assertEquals("Song A", first.getTitle());
        Assertions.assertEquals(9_000_000, first.getCurScore());
        Assertions.assertEquals("clear", first.getLamp());
        Assertions.assertEquals(LocalDateTime.of(2024, 1, 1, 12, 0, 0), first.getDate());
    }

    @Test
    void loadedListIsSortedByDate() throws IOException {
        PlayLogRepository repo = new PlayLogRepository(testFile("alllog.xml"));

        PlayLog pl = new PlayLog();
        pl.setPlays(List.of(new OnePlayData("X", 100, 0, "clear", "exh", "2024-01-03 10:00:00"),
                new OnePlayData("Y", 100, 0, "clear", "exh", "2024-01-01 08:00:00"),
                new OnePlayData("Z", 100, 0, "clear", "exh", "2024-01-02 09:00:00")));
        repo.save(pl);

        PlayLog loaded = repo.load();
        List<OnePlayData> plays = loaded.getPlays();
        Assertions.assertEquals(LocalDateTime.of(2024, 1, 1, 8, 0, 0), plays.get(0).getDate());
        Assertions.assertEquals(LocalDateTime.of(2024, 1, 2, 9, 0, 0), plays.get(1).getDate());
        Assertions.assertEquals(LocalDateTime.of(2024, 1, 3, 10, 0, 0), plays.get(2).getDate());
    }
}
