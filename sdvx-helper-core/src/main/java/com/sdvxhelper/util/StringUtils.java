package com.sdvxhelper.util;

import java.util.ArrayList;
import java.util.List;

/**
 * General-purpose string utilities shared across modules.
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class StringUtils {

    private static final String ILLEGAL_FILENAME_CHARACTERS = "\\\\/:*?\"<>|";

    private StringUtils() {
        // utility class — not instantiable
    }

    /**
     * Makes a song title safe for use as part of a file name.
     *
     * <p>
     * Mirrors Python {@code sdvx_helper.pyw} lines 335-339:
     * <ol>
     * <li>Windows-illegal characters ({@code \/:*?"<>|}) are <em>removed</em> (not
     * replaced), so they leave no trace in the sanitized string.</li>
     * <li>ASCII spaces and full-width spaces (U+3000) are replaced with
     * {@code _}.</li>
     * </ol>
     * This means that {@code "ΛNXIENT:LEGΛXIEZ"} becomes {@code "ΛNXIENTLEGΛXIEZ"},
     * which matches the key stored in {@code special_titles.json} and allows
     * {@link SpecialTitles#restoreTitle} to recover the canonical title.
     * </p>
     *
     * @param name
     *            raw title string
     * @return sanitized string safe for use in file names
     */
    public static String sanitize(String name) {
        return name.replaceAll("[" + ILLEGAL_FILENAME_CHARACTERS + "]", "").replace(' ', '_').replace('\u3000', '_');
    }

    /**
     * Removes spaces that Tesseract inserts between individual CJK characters.
     *
     * <p>
     * Tesseract's LSTM engine treats each kanji/kana glyph as a separate token and
     * inserts a space after every character, producing output like
     * {@code "幻 想 プ ロ ミ ネ ン ス"} instead of {@code "幻想プロミネンス"}. This method strips
     * those spurious spaces by removing any whitespace that is immediately preceded
     * <em>and</em> followed by a CJK character, while leaving legitimate spaces
     * inside Latin-alphabet words or mixed-script titles intact.
     * </p>
     *
     * @param text
     *            raw Tesseract output, may be {@code null}
     * @return text with inter-CJK spaces removed, or the original value if
     *         {@code text} is {@code null}
     */
    public static String removeInterCjkSpaces(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?<=[\\u3040-\\u309F\\u30A0-\\u30FF\\u3000-\\u9FFF\\uF900-\\uFAFF])\\s+"
                + "(?=[\\u3040-\\u309F\\u30A0-\\u30FF\\u3000-\\u9FFF\\uF900-\\uFAFF])", "");
    }

    /**
     * Parses a settings value stored as a Python-style list string (e.g.
     * {@code "['a', 'b']"}) into a Java list of strings.
     *
     * @param raw
     *            raw list string from the settings file
     * @return mutable list of trimmed, unquoted entries; empty list when
     *         {@code raw} is blank or {@code null}
     */
    public static List<String> parseListSetting(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String clean = part.trim().replaceAll("^['\"]|['\"]$", "");
            if (!clean.isBlank()) {
                result.add(clean);
            }
        }
        return result;
    }
}
