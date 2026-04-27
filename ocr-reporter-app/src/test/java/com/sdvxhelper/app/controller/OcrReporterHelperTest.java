package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link OcrReporterHelper}.
 */
class OcrReporterHelperTest {

    // =========================================================================
    // lastDigits — positive cases
    // =========================================================================

    @Test
    void lastDigitsReturnsSingleGroup() {
        Assertions.assertEquals("17", OcrReporterHelper.lastDigits("level 17"));
    }

    @Test
    void lastDigitsReturnsLastGroupWhenMultiple() {
        Assertions.assertEquals("15", OcrReporterHelper.lastDigits("12 to 15"));
    }

    @Test
    void lastDigitsStripsStopPrefix() {
        Assertions.assertEquals("17", OcrReporterHelper.lastDigits("[STOP] 17"));
    }

    @Test
    void lastDigitsStripsStopPrefixAndPicksLast() {
        Assertions.assertEquals("20", OcrReporterHelper.lastDigits("[STOP] lv 18 or 20"));
    }

    @Test
    void lastDigitsWorksWithOnlyDigits() {
        Assertions.assertEquals("42", OcrReporterHelper.lastDigits("42"));
    }

    @Test
    void lastDigitsReturnsLongDigitSequence() {
        Assertions.assertEquals("9800000", OcrReporterHelper.lastDigits("score 9800000"));
    }

    // =========================================================================
    // lastDigits — negative cases (returns "??")
    // =========================================================================

    @Test
    void lastDigitsReturnsPlaceholderForNull() {
        Assertions.assertEquals("??", OcrReporterHelper.lastDigits(null));
    }

    @Test
    void lastDigitsReturnsPlaceholderForBlank() {
        Assertions.assertEquals("??", OcrReporterHelper.lastDigits("   "));
    }

    @Test
    void lastDigitsReturnsPlaceholderForEmptyString() {
        Assertions.assertEquals("??", OcrReporterHelper.lastDigits(""));
    }

    @Test
    void lastDigitsReturnsPlaceholderWhenNoDigits() {
        Assertions.assertEquals("??", OcrReporterHelper.lastDigits("no digits here"));
    }

    @Test
    void lastDigitsReturnsPlaceholderForStopPrefixOnly() {
        Assertions.assertEquals("??", OcrReporterHelper.lastDigits("[STOP]"));
    }

    // =========================================================================
    // isResultFilename — positive cases
    // =========================================================================

    @Test
    void isResultFilenameReturnsTrueForLowercase() {
        Assertions.assertTrue(OcrReporterHelper.isResultFilename("sdvx_songname_EXH_clear.png"));
    }

    @Test
    void isResultFilenameReturnsTrueForUppercase() {
        Assertions.assertTrue(OcrReporterHelper.isResultFilename("SDVX_test.png"));
    }

    @Test
    void isResultFilenameReturnsTrueForMixedCase() {
        Assertions.assertTrue(OcrReporterHelper.isResultFilename("Sdvx_file.png"));
    }

    @Test
    void isResultFilenameReturnsTrueForMinimalPrefix() {
        Assertions.assertTrue(OcrReporterHelper.isResultFilename("sdvx_"));
    }

    // =========================================================================
    // isResultFilename — negative cases
    // =========================================================================

    @Test
    void isResultFilenameReturnsFalseForNull() {
        Assertions.assertFalse(OcrReporterHelper.isResultFilename(null));
    }

    @Test
    void isResultFilenameReturnsFalseForEmptyString() {
        Assertions.assertFalse(OcrReporterHelper.isResultFilename(""));
    }

    @Test
    void isResultFilenameReturnsFalseForDifferentPrefix() {
        Assertions.assertFalse(OcrReporterHelper.isResultFilename("obs_screenshot.png"));
    }

    @Test
    void isResultFilenameReturnsFalseForJustSdvxWithoutUnderscore() {
        Assertions.assertFalse(OcrReporterHelper.isResultFilename("sdvx.png"));
    }

    @ParameterizedTest
    @CsvSource({"sdvx_file.png, true", "SDVX_FILE.PNG, true", "random.png, false", ", false"})
    void isResultFilenameParameterized(String filename, boolean expected) {
        if (filename == null || filename.isBlank()) {
            Assertions.assertFalse(OcrReporterHelper.isResultFilename(null));
        } else {
            Assertions.assertEquals(expected, OcrReporterHelper.isResultFilename(filename));
        }
    }

    // =========================================================================
    // cropAndScale — positive cases
    // =========================================================================

    @Test
    void cropAndScaleReturnsCorrectSizeWhenNoScaling() {
        BufferedImage src = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = OcrReporterHelper.cropAndScale(src, 10, 10, 50, 50, 50, 50);
        Assertions.assertEquals(50, result.getWidth());
        Assertions.assertEquals(50, result.getHeight());
    }

    @Test
    void cropAndScaleScalesToRequestedSize() {
        BufferedImage src = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = OcrReporterHelper.cropAndScale(src, 0, 0, 100, 100, 50, 50);
        Assertions.assertEquals(50, result.getWidth());
        Assertions.assertEquals(50, result.getHeight());
    }

    @Test
    void cropAndScaleClampsCropOriginPastEdge() {
        BufferedImage src = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        // x=100, y=100 are beyond the 50×50 image — should clamp and not throw
        Assertions.assertDoesNotThrow(() -> OcrReporterHelper.cropAndScale(src, 100, 100, 10, 10, 10, 10));
    }

    @Test
    void cropAndScaleClampsCropWidthToImageBounds() {
        BufferedImage src = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        // x=20, w=50 would exceed the image (20+50=70 > 30)
        BufferedImage result = OcrReporterHelper.cropAndScale(src, 20, 0, 50, 10, 10, 10);
        Assertions.assertNotNull(result);
    }

    @Test
    void cropAndScaleReturnsAtLeast1x1Image() {
        BufferedImage src = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        // Extreme: x and y are at maximum, w/h are huge
        BufferedImage result = OcrReporterHelper.cropAndScale(src, 999, 999, 999, 999, 5, 5);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.getWidth() > 0);
        Assertions.assertTrue(result.getHeight() > 0);
    }

    // =========================================================================
    // cropAndScale — negative cases
    // =========================================================================

    @Test
    void cropAndScaleWithZeroOffsetAndFullSize() {
        BufferedImage src = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = OcrReporterHelper.cropAndScale(src, 0, 0, 100, 100, 100, 100);
        Assertions.assertEquals(100, result.getWidth());
        Assertions.assertEquals(100, result.getHeight());
    }
}
