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

    private StringUtils() {
        // utility class — not instantiable
    }

    /**
     * Replaces file-system-unsafe characters in a song title so it can be used
     * safely as part of a file name.
     *
     * @param name
     *            raw title string
     * @return sanitized string with {@code \/:*?"<>|} replaced by underscores
     */
    public static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
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
