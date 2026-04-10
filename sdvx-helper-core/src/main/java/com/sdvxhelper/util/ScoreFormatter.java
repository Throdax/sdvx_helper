package com.sdvxhelper.util;

/**
 * Formatting helpers for score-related display strings.
 *
 * <p>Provides consistent formatting of scores, Volforce values, and level labels
 * across all views and OBS overlay XML files.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public final class ScoreFormatter {

    private ScoreFormatter() {
        // utility class
    }

    /**
     * Formats a raw score as a comma-separated string (e.g. {@code "9,950,000"}).
     *
     * @param score raw play score
     * @return formatted score string
     */
    public static String formatScore(int score) {
        return String.format("%,d", score);
    }

    /**
     * Formats a Volforce integer value (e.g. {@code 369}) as a decimal string
     * with one decimal place (e.g. {@code "36.9"}).
     *
     * @param vfInt Volforce integer (e.g. from {@code VolforceCalculator})
     * @return formatted VF string
     */
    public static String formatVf(int vfInt) {
        return String.format("%.1f", vfInt / 10.0);
    }

    /**
     * Formats a total Volforce (sum of top-50 chart VFs, integer × 10)
     * as a three-decimal string (e.g. {@code "17.255"}).
     *
     * @param totalVfInt total Volforce integer
     * @return formatted total-VF string
     */
    public static String formatTotalVf(int totalVfInt) {
        return String.format("%.3f", totalVfInt / 1000.0);
    }

    /**
     * Formats a score difference with an explicit sign (e.g. {@code "+50,000"} or
     * {@code "-20,000"}).
     *
     * @param diff score difference (may be negative)
     * @return signed, comma-separated string
     */
    public static String formatDiff(int diff) {
        return String.format("%+,d", diff);
    }

    /**
     * Formats a chart level integer as a label string (e.g. {@code "Lv.18"}).
     *
     * @param lv chart level
     * @return level label string
     */
    public static String formatLevel(int lv) {
        return "Lv." + lv;
    }
}
