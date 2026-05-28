package com.sdvxhelper.util;

import java.util.Locale;

/**
 * Formatting helpers for score-related display strings.
 *
 * <p>
 * Provides consistent formatting of scores, Volforce values, and level labels
 * across all views and OBS overlay XML files.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class ScoreFormatter {

    private ScoreFormatter() {
        // utility class
    }

    /**
     * Formats a raw score as a comma-separated string (e.g. {@code "9,950,000"}).
     *
     * @param score
     *            raw play score
     * @return formatted score string
     */
    public static String formatScore(int score) {
        return String.format(Locale.ROOT, "%,d", score);
    }

    /**
     * Formats a raw single-chart VF integer (e.g. {@code 369} meaning 0.369 VF) as
     * a one-decimal display string (e.g. {@code "36.9"}).
     *
     * <p>
     * This follows the same display convention as the Python client, where the raw
     * integer is divided by 10 for a compact representation in OBS overlays and CSV
     * exports. To display the actual decimal VF contribution divide by 1000
     * instead.
     * </p>
     *
     * @param vfInt
     *            raw VF integer from {@link VolforceCalculator#computeSingleVf}
     * @return formatted VF display string (raw ÷ 10, one decimal place)
     */
    public static String formatVf(int vfInt) {
        return String.format(Locale.ROOT, "%.1f", vfInt / 10.0);
    }

    /**
     * Formats a total Volforce integer (sum of the top-50 raw chart VF values, each
     * being VF × 1000) as a three-decimal string (e.g. {@code 17255} →
     * {@code "17.255"}).
     *
     * <p>
     * This mirrors the Python {@code update_total_vf} formula: {@code ret / 1000}.
     * </p>
     *
     * @param totalVfInt
     *            sum of top-50 raw VF integers (as returned by
     *            {@link VolforceCalculator#computeSingleVf})
     * @return formatted total-VF string with three decimal places
     */
    public static String formatTotalVf(int totalVfInt) {
        return String.format(Locale.ROOT, "%.3f", totalVfInt / 1000.0);
    }

    /**
     * Formats a score difference with an explicit sign (e.g. {@code "+50,000"} or
     * {@code "-20,000"}).
     *
     * @param diff
     *            score difference (may be negative)
     * @return signed, comma-separated string
     */
    public static String formatDiff(int diff) {
        return String.format(Locale.ROOT, "%+,d", diff);
    }

    /**
     * Formats a chart level integer as a label string (e.g. {@code "Lv.18"}).
     *
     * @param lv
     *            chart level
     * @return level label string
     */
    public static String formatLevel(int lv) {
        return "Lv." + lv;
    }
}
