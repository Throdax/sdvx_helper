package com.sdvxhelper.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link VersionUtil}.
 */
class VersionUtilTest {

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
}
