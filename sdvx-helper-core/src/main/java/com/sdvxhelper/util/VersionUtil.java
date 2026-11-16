package com.sdvxhelper.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilities for reading and comparing application version strings.
 *
 * <p>
 * The current version is read from a {@code version.properties} file (key:
 * {@code version}). Version strings must follow the {@code MAJOR.MINOR.PATCH}
 * format (e.g. {@code "2.0.0"}).
 *
 * <p>
 * Two sources are consulted, mirroring the external-file-first pattern used by
 * {@code ParamsRepository}: a {@code version.properties} file in the current
 * working directory is tried first, so that the install-root copy shipped
 * alongside the jpackage launcher EXEs (see the distribution's
 * {@code version.properties}) can be edited after installation and take effect
 * on the next launch without a rebuild. If no external file is present (or it
 * cannot be read), the {@code version.properties} bundled on the classpath is
 * used as a fallback, which keeps IDE runs, tests, and any deployment without
 * an install-root copy working.
 * </p>
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
    private static final String VERSION_FILE_NAME = "version.properties";
    private static final String VERSION_KEY = "version";

    private VersionUtil() {
        // utility class
    }

    /**
     * Reads the application version (key {@code version}) from
     * {@code version.properties}.
     *
     * @return version string (e.g. {@code "2.0.0"}), or {@code "unknown"} on
     *         failure
     */
    public static String getCurrentVersion() {
        return getVersion(VERSION_KEY);
    }

    /**
     * Reads a named version property from {@code version.properties}. Supported
     * keys include {@code helper}, {@code ocr}, {@code manager}, {@code sync},
     * {@code updater}, {@code migrator}, and {@code version}.
     *
     * <p>
     * A {@code version.properties} file in the current working directory (the
     * install root for jpackage-launched EXEs) takes priority over the bundled
     * classpath resource, so the value can be updated post-install without
     * rebuilding or reinstalling the application.
     * </p>
     *
     * @param key
     *            property key to read
     * @return version string, or {@code "unknown"} if the key is missing from both
     *         sources
     */
    public static String getVersion(String key) {
        return getVersion(key, new File("."));
    }

    /**
     * Reads a named version property, resolving the external
     * {@code version.properties} file relative to the given directory instead of
     * always using the current working directory.
     *
     * <p>
     * Package-private so tests can point the external lookup at a
     * {@code target/}-relative directory instead of writing to the real working
     * directory.
     * </p>
     *
     * @param key
     *            property key to read
     * @param externalDir
     *            directory in which to look for an external
     *            {@code version.properties} file
     * @return version string, or {@code "unknown"} if the key is missing from both
     *         sources
     */
    static String getVersion(String key, File externalDir) {
        Properties externalProps = loadExternalProperties(externalDir);
        if (externalProps != null) {
            String externalValue = trimToNull(externalProps.getProperty(key));
            if (externalValue != null) {
                return externalValue;
            }
        }
        return getVersionFromClasspath(key);
    }

    /**
     * Loads {@code version.properties} from the given directory, if present.
     *
     * @param directory
     *            directory in which to look for {@code version.properties}
     * @return the loaded {@link Properties}, or {@code null} if the file does not
     *         exist or could not be read
     */
    private static Properties loadExternalProperties(File directory) {
        File externalFile = new File(directory, VERSION_FILE_NAME);
        if (!externalFile.exists()) {
            return null;
        }
        try (InputStream is = new FileInputStream(externalFile)) {
            Properties props = new Properties();
            props.load(is);
            log.debug("Loaded version.properties from {}", externalFile.getAbsolutePath());
            return props;
        } catch (IOException e) {
            log.warn("Failed to load version.properties from {}; trying classpath", externalFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Reads a named version property from {@code version.properties} bundled on the
     * classpath.
     *
     * @param key
     *            property key to read
     * @return version string, or {@code "unknown"} if the key is missing or the
     *         resource cannot be read
     */
    private static String getVersionFromClasspath(String key) {
        try (InputStream is = VersionUtil.class.getClassLoader().getResourceAsStream(VERSION_FILE_NAME)) {
            if (is == null) {
                log.warn("version.properties not found on classpath");
                return "unknown";
            }
            Properties props = new Properties();
            props.load(is);
            String v = trimToNull(props.getProperty(key));
            return v != null ? v : "unknown";
        } catch (IOException e) {
            log.error("Failed to read version.properties from classpath", e);
            return "unknown";
        }
    }

    /**
     * Trims a property value and converts blank results to {@code null} so missing
     * and empty values are treated the same way.
     *
     * @param value
     *            the raw property value (may be {@code null})
     * @return the trimmed value, or {@code null} if {@code value} was {@code null}
     *         or blank
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
