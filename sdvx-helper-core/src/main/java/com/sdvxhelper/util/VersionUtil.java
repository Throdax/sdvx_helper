package com.sdvxhelper.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for reading and comparing application version strings.
 *
 * <p>
 * The current version is read from {@code version.properties} on the classpath
 * (key: {@code version}). Version strings must follow the
 * {@code MAJOR.MINOR.PATCH} format (e.g. {@code "2.0.0"}).
 *
 * <p>
 * Maps to the version-related helpers in the Python {@code sdvx_utils.py} file.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class VersionUtil {

    private static final Logger log = LoggerFactory.getLogger(VersionUtil.class);
    private static final String VERSION_RESOURCE = "version.properties";
    private static final String VERSION_KEY = "version";

    private VersionUtil() {
        // utility class
    }

    /**
     * Reads the application version from {@code version.properties} on the
     * classpath.
     *
     * @return version string (e.g. {@code "2.0.0"}), or {@code "unknown"} on
     *         failure
     */
    public static String getCurrentVersion() {
        return getVersion(VERSION_KEY);
    }

    /**
     * Reads a named version property from {@code version.properties} on the
     * classpath. Supported keys include {@code helper}, {@code ocr},
     * {@code manager}, {@code sync}, {@code updater}, and {@code version}.
     *
     * @param key
     *            property key to read
     * @return version string, or {@code "unknown"} if the key is missing
     */
    public static String getVersion(String key) {
        try (InputStream is = VersionUtil.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
            if (is == null) {
                log.warn("version.properties not found on classpath");
                return "unknown";
            }
            Properties props = new Properties();
            props.load(is);
            String v = props.getProperty(key, "unknown").trim();
            return v.isEmpty() ? "unknown" : v;
        } catch (IOException e) {
            log.error("Failed to read version.properties", e);
            return "unknown";
        }
    }

    /**
     * Compares two version strings.
     *
     * @param v1
     *            first version string (e.g. {@code "2.0.0"})
     * @param v2
     *            second version string
     * @return negative if v1 &lt; v2, zero if equal, positive if v1 &gt; v2
     * @throws IllegalArgumentException
     *             if either string cannot be parsed
     */
    public static int compare(String v1, String v2) {
        int[] parts1 = parseParts(v1);
        int[] parts2 = parseParts(v2);
        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int a = i < parts1.length ? parts1[i] : 0;
            int b = i < parts2.length ? parts2[i] : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    /**
     * Returns {@code true} if {@code candidate} is newer than {@code current}.
     *
     * @param current
     *            current version string
     * @param candidate
     *            candidate version string to compare against
     * @return {@code true} if an update is available
     */
    public static boolean isNewerVersion(String current, String candidate) {
        return compare(candidate, current) > 0;
    }

    private static int[] parseParts(String version) {
        String[] tokens = version.trim().split("\\.");
        int[] parts = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                parts[i] = Integer.parseInt(tokens[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid version string: " + version, e);
            }
        }
        return parts;
    }
}
