package com.sdvxhelper.util;

/**
 * Utility methods for reading typed values from string-valued parameter maps.
 *
 * <p>
 * {@code params.json} is loaded by
 * {@link com.sdvxhelper.repository.ParamsRepository} as a
 * {@code Map<String, String>}. These helpers parse integer and double values
 * from those string entries, returning a caller-specified default when the
 * entry is absent or non-numeric.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class ParamUtils {

    private ParamUtils() {
        // utility class — not instantiable
    }

    /**
     * Parses an integer from a string parameter value.
     *
     * @param value
     *            string value (e.g. from a {@code params.json} entry), or
     *            {@code null}
     * @param defaultValue
     *            fallback if {@code value} is absent or not parseable
     * @return parsed integer, or {@code defaultValue}
     */
    public static int parseIntParam(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            // JSON numbers may be serialised as "308.0" — truncate fraction part
            String trimmed = value.trim();
            if (trimmed.contains(".")) {
                trimmed = trimmed.substring(0, trimmed.indexOf('.'));
            }
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses a double from a string parameter value.
     *
     * @param value
     *            string value, or {@code null}
     * @param defaultValue
     *            fallback if {@code value} is absent or not parseable
     * @return parsed double, or {@code defaultValue}
     */
    public static double parseDoubleParam(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Reads an integer from a {@code Map<String, String>} by key, returning a
     * default when the key is absent or the value is not parseable.
     *
     * @param params
     *            parameter map
     * @param key
     *            parameter name
     * @param defaultValue
     *            fallback value
     * @return parsed integer, or {@code defaultValue}
     */
    public static int getInt(java.util.Map<String, String> params, String key, int defaultValue) {
        return parseIntParam(params.get(key), defaultValue);
    }

    /**
     * Reads a double from a {@code Map<String, String>} by key.
     *
     * @param params
     *            parameter map
     * @param key
     *            parameter name
     * @param defaultValue
     *            fallback value
     * @return parsed double, or {@code defaultValue}
     */
    public static double getDouble(java.util.Map<String, String> params, String key, double defaultValue) {
        return parseDoubleParam(params.get(key), defaultValue);
    }

    /**
     * Reads a boolean from a {@code Map<String, String>} by key.
     *
     * @param params
     *            parameter map
     * @param key
     *            parameter name
     * @param defaultValue
     *            fallback value
     * @return parsed boolean, or {@code defaultValue}
     */
    public static boolean getBool(java.util.Map<String, String> params, String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
