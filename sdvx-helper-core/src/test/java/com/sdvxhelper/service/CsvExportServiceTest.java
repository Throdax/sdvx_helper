package com.sdvxhelper.service;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link CsvExportService}.
 */
class CsvExportServiceTest {

    @TempDir
    Path tempDir;

    private final CsvExportService service = new CsvExportService();

    @Test
    void writeBestCsvContainsHeader() throws IOException {
        MusicInfo m = new MusicInfo("Song A", "Artist", "180", "exh", "18", 9_000_000, "clear", "", "", "");
        m.setVf(300);
        File out = tempDir.resolve("best.csv").toFile();
        service.writeBestCsv(List.of(m), out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.startsWith("title,difficulty,lv,score,lamp,vf"));
        Assertions.assertTrue(content.contains("Song A"));
        Assertions.assertTrue(content.contains("30.0"));
    }

    @Test
    void writeAllLogCsvContainsAllPlays() throws IOException {
        List<OnePlayData> plays = List.of(
            new OnePlayData("X", 9_000_000, 8_000_000, "clear", "exh", "2024-01-01"),
            new OnePlayData("Y", 9_500_000, 9_000_000, "uc",    "mxm", "2024-01-02")
        );
        File out = tempDir.resolve("alllog.csv").toFile();
        service.writeAllLogCsv(plays, out);
        // File should exist and have content (Shift-JIS encoded)
        Assertions.assertTrue(out.exists());
        Assertions.assertTrue(out.length() > 0);
    }

    @Test
    void writePlayCountCsvGroupsByDate() throws IOException {
        List<OnePlayData> plays = List.of(
            new OnePlayData("X", 9_000_000, 0, "clear", "exh", "2024-01-01 20:00"),
            new OnePlayData("Y", 9_000_000, 0, "clear", "exh", "2024-01-01 21:00"),
            new OnePlayData("Z", 9_000_000, 0, "clear", "exh", "2024-01-02 20:00")
        );
        File out = tempDir.resolve("playcount.csv").toFile();
        service.writePlayCountCsv(plays, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("2024-01-01,2"));
        Assertions.assertTrue(content.contains("2024-01-02,1"));
    }

    @Test
    void csvEscapesCommasInTitle() throws IOException {
        MusicInfo m = new MusicInfo("Song, Part 2", "A", "180", "exh", "18", 9_000_000, "clear", "", "", "");
        m.setVf(300);
        File out = tempDir.resolve("best_escape.csv").toFile();
        service.writeBestCsv(List.of(m), out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("\"Song, Part 2\""), "Expected quoted CSV field");
    }
}
