package com.sdvxhelper.util;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link ParamUtils}.
 */
class ParamUtilsTest {

    // -------------------------------------------------------------------------
    // parseIntParam — positive cases
    // -------------------------------------------------------------------------

    @Test
    void parseIntParamParsesPlainInteger() {
        Assertions.assertEquals(42, ParamUtils.parseIntParam("42", 0));
    }

    @Test
    void parseIntParamTruncatesDecimalSuffix() {
        Assertions.assertEquals(308, ParamUtils.parseIntParam("308.0", 0));
    }

    @Test
    void parseIntParamTruncatesLargeDecimal() {
        Assertions.assertEquals(100, ParamUtils.parseIntParam("100.99", -1));
    }

    @Test
    void parseIntParamParsesNegativeInteger() {
        Assertions.assertEquals(-5, ParamUtils.parseIntParam("-5", 0));
    }

    // -------------------------------------------------------------------------
    // parseIntParam — negative cases (fallback to default)
    // -------------------------------------------------------------------------

    @Test
    void parseIntParamReturnsDefaultForNull() {
        Assertions.assertEquals(99, ParamUtils.parseIntParam(null, 99));
    }

    @Test
    void parseIntParamReturnsDefaultForBlank() {
        Assertions.assertEquals(99, ParamUtils.parseIntParam("   ", 99));
    }

    @Test
    void parseIntParamReturnsDefaultForNonNumeric() {
        Assertions.assertEquals(7, ParamUtils.parseIntParam("abc", 7));
    }

    @Test
    void parseIntParamReturnsDefaultForEmptyString() {
        Assertions.assertEquals(3, ParamUtils.parseIntParam("", 3));
    }

    // -------------------------------------------------------------------------
    // parseDoubleParam — positive cases
    // -------------------------------------------------------------------------

    @Test
    void parseDoubleParamParsesDouble() {
        Assertions.assertEquals(3.14, ParamUtils.parseDoubleParam("3.14", 0.0), 1e-9);
    }

    @Test
    void parseDoubleParamParsesWholeNumber() {
        Assertions.assertEquals(10.0, ParamUtils.parseDoubleParam("10", 0.0), 1e-9);
    }

    @Test
    void parseDoubleParamParsesNegative() {
        Assertions.assertEquals(-1.5, ParamUtils.parseDoubleParam("-1.5", 0.0), 1e-9);
    }

    // -------------------------------------------------------------------------
    // parseDoubleParam — negative cases
    // -------------------------------------------------------------------------

    @Test
    void parseDoubleParamReturnsDefaultForNull() {
        Assertions.assertEquals(2.5, ParamUtils.parseDoubleParam(null, 2.5), 1e-9);
    }

    @Test
    void parseDoubleParamReturnsDefaultForBlank() {
        Assertions.assertEquals(2.5, ParamUtils.parseDoubleParam("  ", 2.5), 1e-9);
    }

    @Test
    void parseDoubleParamReturnsDefaultForNonNumeric() {
        Assertions.assertEquals(1.0, ParamUtils.parseDoubleParam("not-a-number", 1.0), 1e-9);
    }

    // -------------------------------------------------------------------------
    // getInt — positive cases
    // -------------------------------------------------------------------------

    @Test
    void getIntReadsKnownKeyFromMap() {
        Map<String, String> params = Map.of("width", "640");
        Assertions.assertEquals(640, ParamUtils.getInt(params, "width", 0));
    }

    @Test
    void getIntHandlesDecimalString() {
        Map<String, String> params = Map.of("scale", "2.0");
        Assertions.assertEquals(2, ParamUtils.getInt(params, "scale", 0));
    }

    // -------------------------------------------------------------------------
    // getInt — negative cases
    // -------------------------------------------------------------------------

    @Test
    void getIntReturnsFallbackForMissingKey() {
        Map<String, String> params = Map.of();
        Assertions.assertEquals(99, ParamUtils.getInt(params, "missing", 99));
    }

    @Test
    void getIntReturnsFallbackForBadValue() {
        Map<String, String> params = Map.of("key", "bad");
        Assertions.assertEquals(5, ParamUtils.getInt(params, "key", 5));
    }

    // -------------------------------------------------------------------------
    // getDouble — positive cases
    // -------------------------------------------------------------------------

    @Test
    void getDoubleReadsKnownKeyFromMap() {
        Map<String, String> params = Map.of("threshold", "0.85");
        Assertions.assertEquals(0.85, ParamUtils.getDouble(params, "threshold", 0.0), 1e-9);
    }

    // -------------------------------------------------------------------------
    // getDouble — negative cases
    // -------------------------------------------------------------------------

    @Test
    void getDoubleReturnsFallbackForMissingKey() {
        Map<String, String> params = Map.of();
        Assertions.assertEquals(1.5, ParamUtils.getDouble(params, "missing", 1.5), 1e-9);
    }

    // -------------------------------------------------------------------------
    // getBool — positive cases
    // -------------------------------------------------------------------------

    @Test
    void getBoolReturnsTrueForTrueString() {
        Map<String, String> params = Map.of("enabled", "true");
        Assertions.assertTrue(ParamUtils.getBool(params, "enabled", false));
    }

    @Test
    void getBoolReturnsFalseForFalseString() {
        Map<String, String> params = Map.of("enabled", "false");
        Assertions.assertFalse(ParamUtils.getBool(params, "enabled", true));
    }

    @Test
    void getBoolIsCaseInsensitive() {
        Map<String, String> params = Map.of("flag", "TRUE");
        Assertions.assertTrue(ParamUtils.getBool(params, "flag", false));
    }

    // -------------------------------------------------------------------------
    // getBool — negative cases
    // -------------------------------------------------------------------------

    @Test
    void getBoolReturnsFallbackForMissingKey() {
        Map<String, String> params = Map.of();
        Assertions.assertTrue(ParamUtils.getBool(params, "missing", true));
    }

    @Test
    void getBoolReturnsFallbackForBlankValue() {
        Map<String, String> params = Map.of("key", "");
        Assertions.assertFalse(ParamUtils.getBool(params, "key", false));
    }

    @Test
    void getBoolReturnsFalseForNonBooleanString() {
        // Boolean.parseBoolean returns false for anything other than "true"
        // (case-insensitive)
        Map<String, String> params = Map.of("key", "yes");
        Assertions.assertFalse(ParamUtils.getBool(params, "key", false));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false", "TRUE, true", "False, false"})
    void getBoolParsesVariousTruthStrings(String value, boolean expected) {
        Map<String, String> params = Map.of("k", value);
        Assertions.assertEquals(expected, ParamUtils.getBool(params, "k", !expected));
    }
}
