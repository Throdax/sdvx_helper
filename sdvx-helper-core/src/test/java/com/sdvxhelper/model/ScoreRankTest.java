package com.sdvxhelper.model;

import com.sdvxhelper.model.enums.ScoreRank;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link ScoreRank}.
 */
class ScoreRankTest {

    @ParameterizedTest
    @CsvSource({"10000000, S", "9900000,  S", "9899999,  AAA_PLUS", "9800000,  AAA_PLUS", "9799999,  AAA",
            "9700000,  AAA", "9699999,  AA_PLUS", "9500000,  AA_PLUS", "9499999,  AA", "9300000,  AA",
            "9299999,  A_PLUS", "9000000,  A_PLUS", "8999999,  A", "8700000,  A", "8699999,  B", "7500000,  B",
            "7499999,  C", "6500000,  C", "6499999,  D", "0,        D"})
    void fromScoreReturnsCorrectRank(int score, String expectedName) {
        ScoreRank rank = ScoreRank.fromScore(score);
        Assertions.assertEquals(expectedName, rank.name());
    }

    @Test
    void gradeCoefficientForS() {
        Assertions.assertEquals(1.05, ScoreRank.S.getGradeCoefficient(), 1e-9);
    }

    @Test
    void gradeCoefficientForD() {
        Assertions.assertEquals(0.80, ScoreRank.D.getGradeCoefficient(), 1e-9);
    }

    @Test
    void minScoreForS() {
        Assertions.assertEquals(9_900_000, ScoreRank.S.getMinScore());
    }
}
