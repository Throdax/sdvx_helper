package com.sdvxhelper.model;

/**
 * Result of a select-screen image analysis, holding the detected score, lamp,
 * and arcade flag.
 *
 * <p>
 * Produced by
 * {@link com.sdvxhelper.service.ImageAnalysisService#getScoreOnSelectFull}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SelectResult {

    private int score;
    private String lamp;
    private boolean arcade;

    /**
     * @param score
     *            detected score (0 if unknown)
     * @param lamp
     *            detected lamp string (e.g. {@code "puc"}, {@code "uc"},
     *            {@code "clear"})
     * @param arcade
     *            {@code true} if the score originates from an arcade play
     */
    public SelectResult(int score, String lamp, boolean arcade) {
        this.score = score;
        this.lamp = lamp;
        this.arcade = arcade;
    }

    /** @return detected score */
    public int getScore() {
        return score;
    }

    /** @return detected lamp */
    public String getLamp() {
        return lamp;
    }

    /** @return {@code true} if the score comes from an arcade */
    public boolean isArcade() {
        return arcade;
    }
}
