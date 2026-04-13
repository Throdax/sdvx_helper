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
}
