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
 * <p>
 * SDVX scores are formatted with a comma after every <em>four</em> digits
 * counted from the right (e.g. {@code 9950000} → {@code "995,0000"}), matching
 * the Python reference implementation's {@code format_score} helper.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class ScoreFormatter {

    /**
     * Utility class — not meant to be instantiated.
     */
    private ScoreFormatter() {
    }

    /**
     * Formats a raw score using SDVX's four-digit grouping convention (e.g.
     * {@code 9950000} → {@code "995,0000"}, {@code 393081} → {@code "39,3081"}).
     *
     * <p>
     * Negative values (score differences) are handled transparently:
     * {@code -9583334} → {@code "-958,3334"}. Values with four or fewer characters
     * (including any leading minus) are returned as-is without a comma.
     * </p>
     *
     * @param score
     *            raw play score or score difference
     * @return formatted score string
     */
    public static String formatScore(int score) {
        String str = String.valueOf(score);
        if (str.length() <= 4) {
            return str;
        }
        return str.substring(0, str.length() - 4) + "," + str.substring(str.length() - 4);
    }

    /**
     * Formats a raw score using SDVX's four-digit grouping convention and wraps the
     * leading part in Discord bold markdown, matching the Python
     * {@code format_score(score, bold=True)} output (e.g. {@code 393081} →
     * {@code "**39**,3081"}).
     *
     * @param score
     *            raw play score
     * @return Discord-bold formatted score string
     */
    public static String formatScoreBold(int score) {
        String str = String.valueOf(score);
        if (str.length() <= 4) {
            return "**" + str + "**";
        }
        return "**" + str.substring(0, str.length() - 4) + "**," + str.substring(str.length() - 4);
    }

    /**
     * Formats a score difference with an explicit sign using SDVX's four-digit
     * grouping convention (e.g. {@code 1000000} → {@code "+100,0000"},
     * {@code -9583334} → {@code "-958,3334"}).
     *
     * <p>
     * A {@code "+"} prefix is prepended for positive values. Negative values
     * already carry their {@code "-"} from {@link #formatScore(int)}. Zero is
     * returned without a sign prefix.
     * </p>
     *
     * @param diff
     *            score difference (may be negative)
     * @return signed, SDVX-formatted string
     */
    public static String formatDiff(int diff) {
        String formatted = formatScore(diff);
        if (diff > 0) {
            return "+" + formatted;
        }
        return formatted;
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
