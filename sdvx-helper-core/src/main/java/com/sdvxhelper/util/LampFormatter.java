package com.sdvxhelper.util;

/**
 * Utility class for converting internal OCR lamp values to their human-readable
 * display equivalents.
 *
 * <p>
 * Internal values (e.g. {@code exh}, {@code class_clear}) are produced by
 * {@link com.sdvxhelper.service.ImageAnalysisService}. Display values (e.g.
 * {@code MAXXIVE}, {@code SKILL CLEAR}) are shown in the UI, webhooks, and
 * Discord outputs.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
public class LampFormatter {

    /**
     * Utility class — not meant to be instantiated.
     */
    private LampFormatter() {
    }

    /**
     * Converts an internal lamp value to its display string. Comparisons are
     * case-insensitive so both {@code exh} and {@code EXH} produce the same result.
     *
     * @param lamp
     *            the internal lamp value; may be {@code null}
     * @return the display string, or an empty string when {@code lamp} is
     *         {@code null}
     */
    public static String formatDisplay(String lamp) {
        if (lamp == null) {
            return "";
        }
        if ("exh".equalsIgnoreCase(lamp)) {
            return "MAXXIVE";
        }
        if ("class_clear".equalsIgnoreCase(lamp)) {
            return "SKILL CLEAR";
        }
        return lamp.toUpperCase();
    }
}
