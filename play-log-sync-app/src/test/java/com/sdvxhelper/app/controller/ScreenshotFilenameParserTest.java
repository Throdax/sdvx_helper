package com.sdvxhelper.app.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.sdvxhelper.model.OnePlayData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ScreenshotFilenameParser}.
 */
class ScreenshotFilenameParserTest {

    /**
     * Date format that {@link com.sdvxhelper.model.MusicInfo#unmarshalDate}
     * recognises.
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String fmt(LocalDateTime dt) {
        return dt.format(DATE_FMT);
    }

    // =========================================================================
    // parse — positive cases
    // =========================================================================

    @Test
    void parseSimpleFilename() {
        // sdvx_{title}_{DIFF}_{lamp}_{score}_{date}_{time}.png
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_Song_EXH_clear_900_20240101_120000.png");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("Song", result.getTitle());
        Assertions.assertEquals("exh", result.getDifficulty());
        Assertions.assertEquals("clear", result.getLamp());
        Assertions.assertEquals(9_000_000, result.getCurScore()); // 900 * 10000
    }

    @Test
    void parseMultiWordTitle() {
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_My Awesome_Song_EXH_uc_980_20240115_153000.png");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("My Awesome Song", result.getTitle());
        Assertions.assertEquals("exh", result.getDifficulty());
        Assertions.assertEquals("uc", result.getLamp());
        Assertions.assertEquals(9_800_000, result.getCurScore());
    }

    @Test
    void parseAppendDifficulty() {
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_HeavenSong_APPEND_puc_1000_20240201_090000.png");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("HeavenSong", result.getTitle());
        Assertions.assertEquals("append", result.getDifficulty());
        Assertions.assertEquals("puc", result.getLamp());
        Assertions.assertEquals(10_000_000, result.getCurScore());
    }

    @Test
    void parseNovDifficulty() {
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_EasySong_NOV_failed_500_20240305_080000.png");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("EasySong", result.getTitle());
        Assertions.assertEquals("nov", result.getDifficulty());
        Assertions.assertEquals("failed", result.getLamp());
    }

    @Test
    void parseSkipsClassPrefixBeforeLamp() {
        // When "class" appears after difficulty, it is skipped
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_TheSong_ADV_class_hard_900_20240101_120000.png");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("TheSong", result.getTitle());
        Assertions.assertEquals("adv", result.getDifficulty());
        Assertions.assertEquals("hard", result.getLamp());
    }

    @Test
    void parseDateIsSet() {
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_Title_EXH_clear_900_20240615_143022.png");
        Assertions.assertNotNull(result);
        LocalDateTime date = result.getDate();
        Assertions.assertNotNull(date);
        Assertions.assertEquals(2024, date.getYear());
        Assertions.assertEquals(6, date.getMonthValue());
        Assertions.assertEquals(15, date.getDayOfMonth());
        Assertions.assertEquals(14, date.getHour());
        Assertions.assertEquals(30, date.getMinute());
        Assertions.assertEquals(22, date.getSecond());
    }

    @Test
    void parseScoreZeroOnNonNumericScoreField() {
        // Bad score field — should default to 0 rather than throw
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_Song_EXH_clear_abc_20240101_120000.png");
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.getCurScore());
    }

    @Test
    void parseFilenameWithoutPngExtension() {
        OnePlayData result = ScreenshotFilenameParser.parse("sdvx_Song_EXH_clear_900_20240101_120000");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("Song", result.getTitle());
    }

    // =========================================================================
    // parse — negative cases (returns null)
    // =========================================================================

    @Test
    void parseReturnNullForTooFewParts() {
        // Only 5 parts — below minimum of 6
        Assertions.assertNull(ScreenshotFilenameParser.parse("sdvx_Song_EXH_clear_900.png"));
    }

    @Test
    void parseReturnsNullForBadDate() {
        // Date field is not a valid yyyyMMdd pattern
        Assertions.assertNull(ScreenshotFilenameParser.parse("sdvx_Song_EXH_clear_900_BADDATE_120000.png"));
    }

    @Test
    void parseReturnsNullForEmptyTitle() {
        // Difficulty immediately after sdvx_ — no title tokens
        Assertions.assertNull(ScreenshotFilenameParser.parse("sdvx_EXH_clear_900_20240101_120000.png"));
    }

    @Test
    void parseReturnsNullForNullInput() {
        Assertions.assertThrows(NullPointerException.class, () -> ScreenshotFilenameParser.parse(null));
    }

    // =========================================================================
    // isSongInLog — positive cases
    // =========================================================================

    @Test
    void isSongInLogReturnsTrueForMatchWithinWindow() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        OnePlayData logEntry = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", fmt(base));
        OnePlayData candidate = new OnePlayData("Song A", 9_000_000, 0, "clear", "exh", fmt(base.plusSeconds(60)));

        boolean result = ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0);
        Assertions.assertTrue(result, "60s apart should be within the 120s window");
    }

    @Test
    void isSongInLogReturnsTrueWhenExactlyOneSecondApart() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        OnePlayData logEntry = new OnePlayData("Song A", 0, 0, "clear", "exh", fmt(base));
        OnePlayData candidate = new OnePlayData("Song A", 0, 0, "clear", "exh", fmt(base.plusSeconds(1)));

        Assertions.assertTrue(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0));
    }

    @Test
    void isSongInLogAppliesTimeOffset() {
        // Log entry timestamp is 180s AHEAD of the screenshot (candidate at T=0).
        // Without offset: |180 - 0| = 180s → outside 120s window.
        // With offset=70s: adjustedEntry = 180-70 = 110, |110 - 0| = 110 < 120 →
        // inside.
        LocalDateTime candidateTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        LocalDateTime entryTime = candidateTime.plusSeconds(180);
        OnePlayData logEntry = new OnePlayData("Song B", 0, 0, "clear", "exh", fmt(entryTime));
        OnePlayData candidate = new OnePlayData("Song B", 0, 0, "clear", "exh", fmt(candidateTime));

        Assertions.assertTrue(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 70));
    }

    @Test
    void isSongInLogMatchesDifficultyIgnoringCase() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        OnePlayData logEntry = new OnePlayData("Song", 0, 0, "clear", "EXH", fmt(base));
        OnePlayData candidate = new OnePlayData("Song", 0, 0, "clear", "exh", fmt(base.plusSeconds(10)));

        Assertions.assertTrue(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0));
    }

    // =========================================================================
    // isSongInLog — negative cases
    // =========================================================================

    @Test
    void isSongInLogReturnsFalseForOutsideWindow() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        OnePlayData logEntry = new OnePlayData("Song A", 0, 0, "clear", "exh", fmt(base));
        OnePlayData candidate = new OnePlayData("Song A", 0, 0, "clear", "exh", fmt(base.plusSeconds(200)));

        Assertions.assertFalse(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0),
                "200s apart should exceed the 120s window");
    }

    @Test
    void isSongInLogReturnsFalseForDifferentTitle() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        OnePlayData logEntry = new OnePlayData("Song A", 0, 0, "clear", "exh", fmt(base));
        OnePlayData candidate = new OnePlayData("Song B", 0, 0, "clear", "exh", fmt(base.plusSeconds(1)));

        Assertions.assertFalse(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0));
    }

    @Test
    void isSongInLogReturnsFalseForDifferentDifficulty() {
        LocalDateTime base = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        OnePlayData logEntry = new OnePlayData("Song A", 0, 0, "clear", "adv", fmt(base));
        OnePlayData candidate = new OnePlayData("Song A", 0, 0, "clear", "exh", fmt(base.plusSeconds(1)));

        Assertions.assertFalse(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0));
    }

    @Test
    void isSongInLogReturnsFalseForNullCandidateDate() {
        OnePlayData logEntry = new OnePlayData("Song A", 0, 0, "clear", "exh", "2024-01-01 12:00:00");
        OnePlayData candidate = new OnePlayData("Song A", 0, 0, "clear", "exh", "");

        Assertions.assertFalse(ScreenshotFilenameParser.isSongInLog(List.of(logEntry), candidate, 0));
    }

    @Test
    void isSongInLogReturnsFalseForEmptyLog() {
        OnePlayData candidate = new OnePlayData("Song", 0, 0, "clear", "exh", "2024-01-01 12:00:00");
        Assertions.assertFalse(ScreenshotFilenameParser.isSongInLog(new ArrayList<>(), candidate, 0));
    }

    @Test
    void isSongInLogSkipsEntriesWithNullDate() {
        // Entry has an empty date string → date is null; should be skipped, not cause
        // NPE
        OnePlayData noDateEntry = new OnePlayData("Song A", 0, 0, "clear", "exh", "");
        OnePlayData candidate = new OnePlayData("Song A", 0, 0, "clear", "exh",
                fmt(LocalDateTime.of(2024, 1, 1, 12, 0, 0)));
        Assertions.assertFalse(ScreenshotFilenameParser.isSongInLog(List.of(noDateEntry), candidate, 0));
    }
}
