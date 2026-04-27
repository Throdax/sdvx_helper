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
 * Unit tests for {@link XmlExportService}.
 */
class XmlExportServiceTest {

    private static final Path TEST_DIR = Paths.get("target", "test-work", "XmlExportServiceTest");

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

    private final XmlExportService service = new XmlExportService();

    @Test
    void writeHistoryCurSongProducesValidXml() throws IOException {
        List<OnePlayData> plays = List.of(new OnePlayData("冥", 9_500_000, 9_200_000, "clear", "exh", "2024-01-01"),
                new OnePlayData("冥", 9_800_000, 9_500_000, "uc", "exh", "2024-01-02"));
        File out = testFile("history.xml");
        service.writeHistoryCurSong(plays, 20, out);

        Assertions.assertTrue(out.exists());
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<history>"));
        Assertions.assertTrue(content.contains("score=\"9500000\""));
        Assertions.assertTrue(content.contains("lamp=\"clear\""));
    }

    @Test
    void writeSdvxBattleProducesValidXml() throws IOException {
        List<OnePlayData> plays = List.of(new OnePlayData("Song A", 8_000_000, 0, "clear", "exh", "2024-01-01"));
        File out = testFile("battle.xml");
        service.writeSdvxBattle(plays, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<battle>"));
        Assertions.assertTrue(content.contains("Song A"));
    }

    @Test
    void writeVfOnSelectWithNullInfo() throws IOException {
        File out = testFile("vf_onselect.xml");
        service.writeVfOnSelect(null, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<vf_onselect/>"));
    }

    @Test
    void writeVfOnSelectWithInfo() throws IOException {
        MusicInfo info = new MusicInfoBuilder("冥").artist("A").bpm("180").difficulty("exh").lv("20")
                .bestScore(9_900_000).bestLamp("puc").build();
        info.setVf(456);
        File out = testFile("vf_onselect.xml");
        service.writeVfOnSelect(info, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("puc"));
        Assertions.assertTrue(content.contains("45.6"));
    }

    @Test
    void specialCharactersAreXmlEscaped() throws IOException {
        List<OnePlayData> plays = List.of(new OnePlayData("A&B", 9_000_000, 0, "clear", "exh", "2024-01-01"));
        File out = testFile("battle_escape.xml");
        service.writeSdvxBattle(plays, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("A&amp;B"), "Expected XML-escaped ampersand");
    }

    @Test
    void writeTotalVfProducesValidXml() throws IOException {
        MusicInfo m = new MusicInfoBuilder("Song X").artist("A").bpm("180").difficulty("exh").lv("18")
                .bestScore(9_900_000).bestLamp("puc").build();
        m.setVf(369);
        File out = testFile("total_vf.xml");
        service.writeTotalVf(List.of(m), 3690, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<total_vf"));
        Assertions.assertTrue(content.contains("3.690"));
    }
}
