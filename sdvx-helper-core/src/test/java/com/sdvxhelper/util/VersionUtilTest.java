package com.sdvxhelper.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link VersionUtil}.
 */
class VersionUtilTest {

    /**
     * Base directory used for the external-{@code version.properties} tests -
     * inside {@code target/} so Maven {@code clean} removes it. Each test case that
     * writes a {@code version.properties} file uses its own sub-directory under
     * here so the tests can run in parallel without interfering with each other.
     */
    private static final Path EXTERNAL_BASE_DIR = Paths.get("target", "test-output", "version-util");

    @BeforeAll
    static void cleanLeftovers() throws IOException {
        if (Files.exists(EXTERNAL_BASE_DIR)) {
            try (Stream<Path> files = Files.walk(EXTERNAL_BASE_DIR)) {
                files.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
        Files.createDirectories(EXTERNAL_BASE_DIR);
    }

    /**
     * Writes a {@code version.properties} file containing the given {@code key}
     * into its own sub-directory of {@link #EXTERNAL_BASE_DIR}.
     *
     * @param subDirName
     *            name of the per-test sub-directory to create
     * @param key
     *            property key to write
     * @param value
     *            property value to write
     * @return the directory the file was written into, for passing to
     *         {@link VersionUtil#getVersion(String, File)}
     * @throws IOException
     *             if the file cannot be written
     */
    private File writeExternalVersionFile(String subDirName, String key, String value) throws IOException {
        Path dir = EXTERNAL_BASE_DIR.resolve(subDirName);
        Files.createDirectories(dir);
        Path file = dir.resolve("version.properties");
        try (FileWriter writer = new FileWriter(file.toFile(), StandardCharsets.UTF_8)) {
            writer.write(key + "=" + value + System.lineSeparator());
        }
        return dir.toFile();
    }

    @ParameterizedTest
    @CsvSource({"1.0.0, 2.0.0, -1", // v1 < v2
            "2.0.0, 1.0.0,  1", // v1 > v2
            "1.0.0, 1.0.0,  0", // equal
            "1.0,   1.0.0,  0", // shorter vs longer (trailing zeroes)
            "2.0.1, 2.0.0,  1", // patch differs
            "1.9.0, 1.10.0, -1" // multi-digit minor
    })
    void compareVersionStrings(String v1, String v2, int expectedSign) {
        int result = VersionUtil.compare(v1, v2);
        if (expectedSign < 0)
            Assertions.assertTrue(result < 0, "Expected " + v1 + " < " + v2);
        else if (expectedSign > 0)
            Assertions.assertTrue(result > 0, "Expected " + v1 + " > " + v2);
        else
            Assertions.assertEquals(0, result, "Expected " + v1 + " == " + v2);
    }

    @Test
    void isNewerVersionReturnsTrueWhenCandidateIsHigher() {
        Assertions.assertTrue(VersionUtil.isNewerVersion("1.0.0", "1.0.1"));
    }

    @Test
    void isNewerVersionReturnsFalseWhenSame() {
        Assertions.assertFalse(VersionUtil.isNewerVersion("2.0.0", "2.0.0"));
    }

    @Test
    void isNewerVersionReturnsFalseWhenCandidateLower() {
        Assertions.assertFalse(VersionUtil.isNewerVersion("2.0.0", "1.9.9"));
    }

    @Test
    void invalidVersionThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> VersionUtil.compare("1.a.0", "1.0.0"));
    }

    @Test
    void getVersionPrefersExternalFileOverClasspathWhenPresent() throws IOException {
        File dir = writeExternalVersionFile("external-override", "helper", "9.9.9");

        String result = VersionUtil.getVersion("helper", dir);

        Assertions.assertEquals("9.9.9", result,
                "An external version.properties file must take priority over the bundled classpath resource");
    }

    @Test
    void getVersionFallsBackToClasspathWhenNoExternalFileExists() throws IOException {
        Path emptyDir = EXTERNAL_BASE_DIR.resolve("no-external-file");
        Files.createDirectories(emptyDir);

        String result = VersionUtil.getVersion("helper", emptyDir.toFile());

        Assertions.assertNotEquals("unknown", result,
                "With no external file, the bundled classpath version.properties must still be read");
    }

    @Test
    void getVersionFallsBackToClasspathWhenKeyMissingFromExternalFile() throws IOException {
        File dir = writeExternalVersionFile("missing-key", "updater", "9.9.9");

        String result = VersionUtil.getVersion("helper", dir);

        Assertions.assertNotEquals("unknown", result,
                "A key absent from the external file must fall back to the classpath value, not 'unknown'");
        Assertions.assertNotEquals("9.9.9", result, "The fallback must not accidentally return the updater value");
    }

    @Test
    void getVersionReturnsUnknownWhenKeyMissingFromBothSources() throws IOException {
        Path emptyDir = EXTERNAL_BASE_DIR.resolve("unknown-key");
        Files.createDirectories(emptyDir);

        String result = VersionUtil.getVersion("no-such-key", emptyDir.toFile());

        Assertions.assertEquals("unknown", result);
    }
}
