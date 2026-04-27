package com.sdvxhelper.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.MusicInfoBuilder;
import com.sdvxhelper.model.OnePlayData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CsvExportService}.
 */
class CsvExportServiceTest {

    private static final Path TEST_DIR = Paths.get("target", "test-work", "CsvExportServiceTest");

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

    private final CsvExportService service = new CsvExportService();

    @Test
    void writeBestCsvContainsHeader() throws IOException {
        MusicInfo m = new MusicInfoBuilder("Song A").artist("Artist").bpm("180").difficulty("exh").lv("18")
                .bestScore(9_000_000).bestLamp("clear").build();
        m.setVf(300);
        File out = testFile("best.csv");
        service.writeBestCsv(List.of(m), out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.startsWith("title,difficulty,lv,score,lamp,vf"));
        Assertions.assertTrue(content.contains("Song A"));
        Assertions.assertTrue(content.contains("30.0"));
    }

    @Test
    void writeAllLogCsvContainsAllPlays() throws IOException {
        List<OnePlayData> plays = List.of(new OnePlayData("X", 9_000_000, 8_000_000, "clear", "exh", "2024-01-01"),
                new OnePlayData("Y", 9_500_000, 9_000_000, "uc", "mxm", "2024-01-02"));
        File out = testFile("alllog.csv");
        service.writeAllLogCsv(plays, out);
        Assertions.assertTrue(out.exists());
        Assertions.assertTrue(out.length() > 0);
    }

    @Test
    void writePlayCountCsvGroupsByDate() throws IOException {
        List<OnePlayData> plays = List.of(new OnePlayData("X", 9_000_000, 0, "clear", "exh", "2024-01-01 20:00"),
                new OnePlayData("Y", 9_000_000, 0, "clear", "exh", "2024-01-01 21:00"),
                new OnePlayData("Z", 9_000_000, 0, "clear", "exh", "2024-01-02 20:00"));
        File out = testFile("playcount.csv");
        service.writePlayCountCsv(plays, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("2024-01-01,2"));
        Assertions.assertTrue(content.contains("2024-01-02,1"));
    }

    @Test
    void csvEscapesCommasInTitle() throws IOException {
        MusicInfo m = new MusicInfoBuilder("Song, Part 2").artist("A").bpm("180").difficulty("exh").lv("18")
                .bestScore(9_000_000).bestLamp("clear").build();
        m.setVf(300);
        File out = testFile("best_escape.csv");
        service.writeBestCsv(List.of(m), out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("\"Song, Part 2\""), "Expected quoted CSV field");
    }
}
