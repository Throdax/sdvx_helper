package com.sdvxhelper.service;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.ScoreRank;

/**
 * Stateless calculator for the SDVX Volforce (VF) metric.
 *
 * <p>The Volforce formula is:
 * <pre>
 *   VF_single = floor(level × score × coef_grade × coef_lamp × 20 / 10_000_000)
 * </pre>
 * where the result is an integer representing VF × 10 (e.g. {@code 369} = 36.9 VF).
 *
 * <p>The total Volforce is the sum of the top-50 single-chart VF values, divided by 100:
 * <pre>
 *   total_VF = sum_of_top_50 / 100.0
 * </pre>
 *
 * <p>Maps to the Python {@code get_vf_single()} methods in
 * {@code OnePlayData} and {@code MusicInfo}, and the total-VF calculation in
 * {@code SDVXLogger.update_total_vf()} in {@code sdvxh_classes.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class VolforceCalculator {

    /** Number of top charts counted toward the total Volforce. */
    public static final int TOP_N = 50;

    private VolforceCalculator() {
        // utility class
    }

    /**
     * Returns the clear-lamp coefficient used in the VF formula.
     *
     * @param lamp clear lamp string (e.g. {@code "puc"}, {@code "uc"}, {@code "clear"})
     * @return lamp coefficient
     */
    public static double lampCoefficient(String lamp) {
        if (lamp == null) return 0.5;
        return switch (lamp) {
            case "puc"   -> 1.10;
            case "uc"    -> 1.05;
            case "exh"   -> 1.04;
            case "hard"  -> 1.02;
            case "clear" -> 1.00;
            default      -> 0.50;
        };
    }

    /**
     * Computes the single-chart Volforce for the given score, lamp, and level.
     *
     * <p>Returns {@code 0} if {@code levelInt} is negative (e.g. the level is {@code "??"})</p>
     *
     * @param score    raw play score (0–10 000 000)
     * @param lamp     clear lamp string
     * @param levelInt integer chart level (use {@link MusicInfo#getLvAsInt()} to obtain this)
     * @return Volforce integer (VF × 10), or {@code 0} for unknown levels
     */
    public static int computeSingleVf(int score, String lamp, int levelInt) {
        if (levelInt <= 0) {
            return 0;
        }
        ScoreRank rank = ScoreRank.fromScore(score);
        double coefGrade = rank.getGradeCoefficient();
        double coefLamp  = lampCoefficient(lamp);
        return (int) (levelInt * score * coefGrade * coefLamp * 20L / 10_000_000L);
    }

    /**
     * Computes the Volforce for a {@link OnePlayData} record and updates its
     * {@code rank} and {@code vf} fields in place.
     *
     * @param play     play record (mutated: rank and vf are set)
     * @param levelInt integer chart level
     * @return Volforce integer (VF × 10)
     */
    public static int computeAndSet(OnePlayData play, int levelInt) {
        ScoreRank rank = ScoreRank.fromScore(play.getCurScore());
        play.setRank(rank);
        int vf = computeSingleVf(play.getCurScore(), play.getLamp(), levelInt);
        play.setVf(vf);
        return vf;
    }

    /**
     * Computes the Volforce for a {@link MusicInfo} best-score record and updates
     * its {@code rank} and {@code vf} fields in place.
     *
     * @param info music info (mutated: rank and vf are set)
     * @return Volforce integer (VF × 10)
     */
    public static int computeAndSet(MusicInfo info) {
        ScoreRank rank = ScoreRank.fromScore(info.getBestScore());
        info.setRank(rank);
        int vf = computeSingleVf(info.getBestScore(), info.getBestLamp(), info.getLvAsInt());
        info.setVf(vf);
        return vf;
    }
}
