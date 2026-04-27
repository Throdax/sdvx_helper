package com.sdvxhelper.util;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link StringUtils}.
 */
class StringUtilsTest {

    // -------------------------------------------------------------------------
    // sanitize — positive cases
    // -------------------------------------------------------------------------

    @Test
    void sanitizeReplacesBackslash() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a\\b"));
    }

    @Test
    void sanitizeReplacesForwardSlash() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a/b"));
    }

    @Test
    void sanitizeReplacesColon() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a:b"));
    }

    @Test
    void sanitizeReplacesAsterisk() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a*b"));
    }

    @Test
    void sanitizeReplacesQuestionMark() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a?b"));
    }

    @Test
    void sanitizeReplacesDoubleQuote() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a\"b"));
    }

    @Test
    void sanitizeReplacesLessThan() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a<b"));
    }

    @Test
    void sanitizeReplacesGreaterThan() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a>b"));
    }

    @Test
    void sanitizeReplacesPipe() {
        Assertions.assertEquals("a_b", StringUtils.sanitize("a|b"));
    }

    @Test
    void sanitizeLeavesNormalTitleUnchanged() {
        Assertions.assertEquals("Normal Song Title 123", StringUtils.sanitize("Normal Song Title 123"));
    }

    @Test
    void sanitizeReplacesAllUnsafeCharsInOneString() {
        Assertions.assertEquals("a_b_c_d", StringUtils.sanitize("a\\b:c*d"));
    }

    @Test
    void sanitizeEmptyStringReturnsEmpty() {
        Assertions.assertEquals("", StringUtils.sanitize(""));
    }

    // -------------------------------------------------------------------------
    // sanitize — negative cases
    // -------------------------------------------------------------------------

    @Test
    void sanitizeDoesNotReplaceSpaces() {
        Assertions.assertEquals("Song A B", StringUtils.sanitize("Song A B"));
    }

    @Test
    void sanitizeDoesNotReplaceDots() {
        Assertions.assertEquals("1.0.0", StringUtils.sanitize("1.0.0"));
    }

    @Test
    void sanitizeDoesNotReplaceHyphens() {
        Assertions.assertEquals("a-b", StringUtils.sanitize("a-b"));
    }

    // -------------------------------------------------------------------------
    // parseListSetting — positive cases
    // -------------------------------------------------------------------------

    @Test
    void parseListSettingParsesSimplePythonList() {
        List<String> result = StringUtils.parseListSetting("['a', 'b', 'c']");
        Assertions.assertEquals(List.of("a", "b", "c"), result);
    }

    @Test
    void parseListSettingParsesSingleElement() {
        List<String> result = StringUtils.parseListSetting("['hello']");
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("hello", result.get(0));
    }

    @Test
    void parseListSettingParsesDoubleQuotedEntries() {
        List<String> result = StringUtils.parseListSetting("[\"x\", \"y\"]");
        Assertions.assertEquals(List.of("x", "y"), result);
    }

    @Test
    void parseListSettingIgnoresEmptyBrackets() {
        List<String> result = StringUtils.parseListSetting("[]");
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void parseListSettingReturnsEmptyForNull() {
        List<String> result = StringUtils.parseListSetting(null);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void parseListSettingReturnsEmptyForBlankString() {
        List<String> result = StringUtils.parseListSetting("   ");
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void parseListSettingTrimsWhitespace() {
        List<String> result = StringUtils.parseListSetting("[ 'a' , 'b' ]");
        Assertions.assertEquals(List.of("a", "b"), result);
    }

    @Test
    void parseListSettingHandlesNoQuotes() {
        List<String> result = StringUtils.parseListSetting("[foo, bar]");
        Assertions.assertEquals(List.of("foo", "bar"), result);
    }

    // -------------------------------------------------------------------------
    // parseListSetting — negative cases
    // -------------------------------------------------------------------------

    @Test
    void parseListSettingDoesNotReturnNullEntries() {
        List<String> result = StringUtils.parseListSetting("['a', '', 'b']");
        for (String entry : result) {
            Assertions.assertFalse(entry.isBlank(), "Found blank entry in result: '" + entry + "'");
        }
    }

    @Test
    void parseListSettingReturnsMutableList() {
        List<String> result = StringUtils.parseListSetting("['a']");
        // Must not throw UnsupportedOperationException
        Assertions.assertDoesNotThrow(() -> result.add("extra"));
    }

    @ParameterizedTest(name = "[{index}] size={1}")
    @CsvSource(delimiterString = "|", value = {"['a','b','c'] | 3", "[] | 0", "['x'] | 1"})
    void parseListSettingSizeMatchesInputCount(String input, int expectedSize) {
        Assertions.assertEquals(expectedSize, StringUtils.parseListSetting(input).size());
    }
}
