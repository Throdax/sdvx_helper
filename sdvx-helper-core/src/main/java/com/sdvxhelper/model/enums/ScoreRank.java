package com.sdvxhelper.model.enums;

/**
 * Score rank boundaries used in SDVX Exceed Gear / Konaste.
 *
 * <p>Maps to the Python {@code score_rank} enum in {@code sdvxh_classes.py}.
 * Each constant also exposes the minimum score required and the grade coefficient
 * used in the Volforce calculation.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public enum ScoreRank {

    /** Placeholder when no score has been recorded yet. */
    NO_VALUE(0, 0.0),

    /** S rank: score &ge; 9 900 000. */
    S(9_900_000, 1.05),

    /** AAA+ rank: score &ge; 9 800 000. */
    AAA_PLUS(9_800_000, 1.02),

    /** AAA rank: score &ge; 9 700 000. */
    AAA(9_700_000, 1.00),

    /** AA+ rank: score &ge; 9 500 000. */
    AA_PLUS(9_500_000, 0.97),

    /** AA rank: score &ge; 9 300 000. */
    AA(9_300_000, 0.94),

    /** A+ rank: score &ge; 9 000 000. */
    A_PLUS(9_000_000, 0.91),

    /** A rank: score &ge; 8 700 000. */
    A(8_700_000, 0.88),

    /** B rank: score &ge; 7 500 000. */
    B(7_500_000, 0.85),

    /** C rank: score &ge; 6 500 000. */
    C(6_500_000, 0.82),

    /** D rank: score below 6 500 000. */
    D(0, 0.80);

    private final int minScore;
    private final double gradeCoefficient;

    private ScoreRank(int minScore, double gradeCoefficient) {
        this.minScore = minScore;
        this.gradeCoefficient = gradeCoefficient;
    }

    /**
     * Returns the minimum score required to achieve this rank.
     *
     * @return minimum score threshold
     */
    public int getMinScore() {
        return minScore;
    }

    /**
     * Returns the grade coefficient used in the Volforce formula.
     *
     * @return grade coefficient (e.g. 1.05 for S rank)
     */
    public double getGradeCoefficient() {
        return gradeCoefficient;
    }

    /**
     * Determines the {@code ScoreRank} for the given raw score.
     *
     * @param score raw play score (0–10 000 000)
     * @return the corresponding {@code ScoreRank}
     */
    public static ScoreRank fromScore(int score) {
        if (score >= S.minScore)       return S;
        if (score >= AAA_PLUS.minScore) return AAA_PLUS;
        if (score >= AAA.minScore)      return AAA;
        if (score >= AA_PLUS.minScore)  return AA_PLUS;
        if (score >= AA.minScore)       return AA;
        if (score >= A_PLUS.minScore)   return A_PLUS;
        if (score >= A.minScore)        return A;
        if (score >= B.minScore)        return B;
        if (score >= C.minScore)        return C;
        return D;
    }
}
