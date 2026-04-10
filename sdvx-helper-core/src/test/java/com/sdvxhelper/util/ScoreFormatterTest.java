package com.sdvxhelper.util;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link ScoreFormatter}.
 */
class ScoreFormatterTest {

    @Test
    void formatScoreAddsCommas() {
        Assertions.assertEquals("9,950,000", ScoreFormatter.formatScore(9_950_000));
        Assertions.assertEquals("0", ScoreFormatter.formatScore(0));
        Assertions.assertEquals("10,000,000", ScoreFormatter.formatScore(10_000_000));
    }

    @Test
    void formatVfOneDecimalPlace() {
        Assertions.assertEquals("36.9", ScoreFormatter.formatVf(369));
        Assertions.assertEquals("0.0", ScoreFormatter.formatVf(0));
        Assertions.assertEquals("100.0", ScoreFormatter.formatVf(1000));
    }

    @Test
    void formatTotalVfThreeDecimalPlaces() {
        Assertions.assertEquals("17.255", ScoreFormatter.formatTotalVf(17255));
        Assertions.assertEquals("0.000", ScoreFormatter.formatTotalVf(0));
    }

    @Test
    void formatDiffShowsSign() {
        Assertions.assertEquals("+50,000", ScoreFormatter.formatDiff(50_000));
        Assertions.assertEquals("-20,000", ScoreFormatter.formatDiff(-20_000));
        Assertions.assertEquals("+0", ScoreFormatter.formatDiff(0));
    }

    @Test
    void formatLevelAddsPrefix() {
        Assertions.assertEquals("Lv.18", ScoreFormatter.formatLevel(18));
        Assertions.assertEquals("Lv.1", ScoreFormatter.formatLevel(1));
    }
}
