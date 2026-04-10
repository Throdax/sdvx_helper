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
 * Unit tests for {@link XmlExportService}.
 */
class XmlExportServiceTest {

    @TempDir
    Path tempDir;

    private final XmlExportService service = new XmlExportService();

    @Test
    void writeHistoryCurSongProducesValidXml() throws IOException {
        List<OnePlayData> plays = List.of(
            new OnePlayData("冥", 9_500_000, 9_200_000, "clear", "exh", "2024-01-01"),
            new OnePlayData("冥", 9_800_000, 9_500_000, "uc",    "exh", "2024-01-02")
        );
        File out = tempDir.resolve("history.xml").toFile();
        service.writeHistoryCurSong(plays, 20, out);

        Assertions.assertTrue(out.exists());
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<history>"));
        Assertions.assertTrue(content.contains("score=\"9500000\""));
        Assertions.assertTrue(content.contains("lamp=\"clear\""));
    }

    @Test
    void writeSdvxBattleProducesValidXml() throws IOException {
        List<OnePlayData> plays = List.of(
            new OnePlayData("Song A", 8_000_000, 0, "clear", "exh", "2024-01-01")
        );
        File out = tempDir.resolve("battle.xml").toFile();
        service.writeSdvxBattle(plays, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<battle>"));
        Assertions.assertTrue(content.contains("Song A"));
    }

    @Test
    void writeVfOnSelectWithNullInfo() throws IOException {
        File out = tempDir.resolve("vf_onselect.xml").toFile();
        service.writeVfOnSelect(null, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<vf_onselect/>"));
    }

    @Test
    void writeVfOnSelectWithInfo() throws IOException {
        MusicInfo info = new MusicInfo("冥", "A", "180", "exh", "20", 9_900_000, "puc", "", "", "");
        info.setVf(456);
        File out = tempDir.resolve("vf_onselect.xml").toFile();
        service.writeVfOnSelect(info, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("puc"));
        Assertions.assertTrue(content.contains("45.6"));
    }

    @Test
    void specialCharactersAreXmlEscaped() throws IOException {
        // Title contains & which should be escaped to &amp;
        List<OnePlayData> plays = List.of(
            new OnePlayData("A&B", 9_000_000, 0, "clear", "exh", "2024-01-01")
        );
        File out = tempDir.resolve("battle_escape.xml").toFile();
        service.writeSdvxBattle(plays, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("A&amp;B"), "Expected XML-escaped ampersand");
    }

    @Test
    void writeTotalVfProducesValidXml() throws IOException {
        MusicInfo m = new MusicInfo("Song X", "A", "180", "exh", "18", 9_900_000, "puc", "", "", "");
        m.setVf(369);
        File out = tempDir.resolve("total_vf.xml").toFile();
        service.writeTotalVf(List.of(m), 3690, out);
        String content = Files.readString(out.toPath());
        Assertions.assertTrue(content.contains("<total_vf"));
        Assertions.assertTrue(content.contains("3.690"));
    }
}
