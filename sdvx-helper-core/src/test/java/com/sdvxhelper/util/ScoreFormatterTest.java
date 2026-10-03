package com.sdvxhelper.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ScoreFormatter}.
 */
class ScoreFormatterTest {

    @Test
    void formatScoreUsesSdvxFourDigitGrouping() {
        Assertions.assertEquals("995,0000", ScoreFormatter.formatScore(9_950_000));
        Assertions.assertEquals("1000,0000", ScoreFormatter.formatScore(10_000_000));
        Assertions.assertEquals("39,3081", ScoreFormatter.formatScore(393_081));
        Assertions.assertEquals("958,3334", ScoreFormatter.formatScore(9_583_334));
        Assertions.assertEquals("0", ScoreFormatter.formatScore(0));
    }

    @Test
    void formatScoreHandlesNegativeDiffs() {
        Assertions.assertEquals("-958,3334", ScoreFormatter.formatScore(-9_583_334));
        Assertions.assertEquals("-919,0253", ScoreFormatter.formatScore(-9_190_253));
    }

    @Test
    void formatScoreBoldWrapsLeadingPartInDiscordMarkdown() {
        Assertions.assertEquals("**39**,3081", ScoreFormatter.formatScoreBold(393_081));
        Assertions.assertEquals("**1000**,0000", ScoreFormatter.formatScoreBold(10_000_000));
        Assertions.assertEquals("**995**,0000", ScoreFormatter.formatScoreBold(9_950_000));
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
    void formatDiffShowsSignWithSdvxGrouping() {
        Assertions.assertEquals("+5,0000", ScoreFormatter.formatDiff(50_000));
        Assertions.assertEquals("-2,0000", ScoreFormatter.formatDiff(-20_000));
        Assertions.assertEquals("+100,0000", ScoreFormatter.formatDiff(1_000_000));
        Assertions.assertEquals("-958,3334", ScoreFormatter.formatDiff(-9_583_334));
        Assertions.assertEquals("0", ScoreFormatter.formatDiff(0));
    }

    @Test
    void formatLevelAddsPrefix() {
        Assertions.assertEquals("Lv.18", ScoreFormatter.formatLevel(18));
        Assertions.assertEquals("Lv.1", ScoreFormatter.formatLevel(1));
    }
}
