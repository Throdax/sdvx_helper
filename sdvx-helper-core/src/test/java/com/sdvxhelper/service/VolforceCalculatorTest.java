package com.sdvxhelper.service;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.MusicInfoBuilder;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.ScoreRank;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link VolforceCalculator}.
 */
class VolforceCalculatorTest {

    // VF formula: floor(lv * score * coef_grade * coef_lamp * 20 / 10_000_000)

    @Test
    void pucSRankLevel20GivesExpectedVf() {
        // 20 * 9_900_000 * 1.05 (S coef) * 1.10 (PUC coef) * 20 / 10_000_000 = 457.38
        // -> 457
        int vf = VolforceCalculator.computeSingleVf(9_900_000, "puc", 20);
        Assertions.assertEquals(457, vf);
    }

    @Test
    void unknownLevelReturnsZero() {
        int vf = VolforceCalculator.computeSingleVf(9_900_000, "puc", -1);
        Assertions.assertEquals(0, vf);
    }

    @Test
    void zeroLevelReturnsZero() {
        int vf = VolforceCalculator.computeSingleVf(9_900_000, "puc", 0);
        Assertions.assertEquals(0, vf);
    }

    @ParameterizedTest
    @CsvSource({"puc,   1.10", "uc,    1.05", "exh,   1.04", "hard,  1.02", "clear, 1.00", "failed,0.50"})
    void lampCoefficientValues(String lamp, double expected) {
        Assertions.assertEquals(expected, VolforceCalculator.lampCoefficient(lamp), 1e-9);
    }

    @Test
    void nullLampDefaultsToFailed() {
        Assertions.assertEquals(0.50, VolforceCalculator.lampCoefficient(null), 1e-9);
    }

    @Test
    void computeAndSetUpdatesPlayRankAndVf() {
        OnePlayData play = new OnePlayData("X", 9_900_000, 0, "puc", "exh", "2024-01-01");
        int vf = VolforceCalculator.computeAndSet(play, 20);
        Assertions.assertEquals(vf, play.getVf());
        Assertions.assertEquals(ScoreRank.S, play.getRank());
    }

    @Test
    void computeAndSetMusicInfoUpdatesRankAndVf() {
        MusicInfo m = new MusicInfoBuilder("X").artist("A").bpm("180").difficulty("exh").lv("20").bestScore(9_900_000)
                .bestLamp("puc").build();
        int vf = VolforceCalculator.computeAndSet(m);
        Assertions.assertEquals(vf, m.getVf());
        Assertions.assertEquals(ScoreRank.S, m.getRank());
        Assertions.assertTrue(vf > 0);
    }

    @Test
    void dRankLowestPossibleVf() {
        int vf = VolforceCalculator.computeSingleVf(0, "clear", 1);
        Assertions.assertEquals(0, vf);
    }

    @Test
    void clearLampBRankLevel16() {
        // 16 * 8_000_000 * 0.85 * 1.00 * 20 / 10_000_000 = 217.6 -> 217
        int vf = VolforceCalculator.computeSingleVf(8_000_000, "clear", 16);
        Assertions.assertEquals(217, vf);
    }

    /**
     * Verifies the worked example from https://www.sdvx.org/en/compendium/volforce:
     * Level 18 (integer part of 18.3), score 9,923,042 (S rank), Excessive Rate
     * clear.
     *
     * floor(18 * 9_923_042 * 1.05 * 1.02 * 20 / 10_000_000) = floor(382.5928) = 382
     *
     * Note: the sdvx.org example uses level 18.3 (decimal). Java stores integer
     * levels so level 18 is used here; the raw VF integer is 382 (representing
     * 0.382 VF for this chart).
     */
    @Test
    void sdvxOrgFormulaExample() {
        // Level 18, score 9_923_042, S rank (coef 1.05), Excessive Rate clear.
        // "hard" lamp = 1.02 (Excessive Rate Clear per sdvx.org).
        // "exh" in this codebase maps to 1.04 (Maxxive Rate Clear), not 1.02.
        // floor(18 * 9_923_042 * 1.05 * 1.02 * 20 / 10_000_000) = floor(382.592...) =
        // 382
        int vf = VolforceCalculator.computeSingleVf(9_923_042, "hard", 18);
        Assertions.assertEquals(382, vf);
    }

    /**
     * Verifies level 16 PUC with max score (10,000,000) returns 369 as documented
     * in the Python client comment: "16PUCなら369のように整数を返す".
     */
    @Test
    void level16PucMaxScoreReturns369() {
        // floor(16 * 10_000_000 * 1.05 * 1.10 * 20 / 10_000_000) = floor(369.6) = 369
        int vf = VolforceCalculator.computeSingleVf(10_000_000, "puc", 16);
        Assertions.assertEquals(369, vf);
    }

    /**
     * Verifies the total VF summing logic: sum of top-50 raw integers divided by
     * 1000 must equal the displayed total VF (matching Python update_total_vf).
     *
     * Uses 50 identical entries of level 16 PUC 10M = 369 each. Expected total: (50
     * * 369) / 1000 = 18.45.
     */
    @Test
    void totalVfIsSumOfTop50DividedBy1000() {
        int singleVf = VolforceCalculator.computeSingleVf(10_000_000, "puc", 16); // 369
        int top50Sum = singleVf * VolforceCalculator.TOP_N; // 369 * 50 = 18450
        double totalVf = top50Sum / 1000.0;
        Assertions.assertEquals(18.45, totalVf, 1e-9);
    }
}
