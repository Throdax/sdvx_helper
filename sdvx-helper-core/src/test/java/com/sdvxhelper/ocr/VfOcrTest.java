package com.sdvxhelper.ocr;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Smoke-tests for the VF OCR preprocessing pipeline.
 *
 * <p>
 * The VF badge ({@code DD.DDD}) is cropped to 96 × 36 px at runtime. With a 5×
 * scale factor the preprocessed image is expected to be
 * {@code (96 * 5 + 40) × (36 * 5 + 40)} = {@code 520 × 220} px (the 40 px
 * accounts for the 20 px white padding added on each side). These tests verify
 * that {@link OcrUtils#preprocessForOcr(BufferedImage, int)} produces the
 * correct output dimensions for a synthetic crop of that size.
 * </p>
 *
 * <p>
 * A Tesseract accuracy assertion is intentionally deferred to a manual check:
 * run the application with {@code vf_ocr_enabled=true} on a live result screen
 * and inspect {@code out/part_volforce_preprocessed.png} if OCR fails.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
class VfOcrTest {

    /**
     * Width of the VF badge crop in the live application (from params.json vf_w).
     */
    private static final int VF_CROP_W = 96;

    /**
     * Height of the VF badge crop in the live application (from params.json vf_h).
     */
    private static final int VF_CROP_H = 36;

    /** Scale factor applied by the VF OCR path. */
    private static final int VF_SCALE = 5;

    /** White padding added on every side by {@link OcrUtils#preprocessForOcr}. */
    private static final int PAD = 20;

    private static final int EXPECTED_W = VF_CROP_W * VF_SCALE + PAD * 2;
    private static final int EXPECTED_H = VF_CROP_H * VF_SCALE + PAD * 2;

    @BeforeAll
    static void createOutputDir() throws Exception {
        Files.createDirectories(Paths.get("target", "test-output", "vf-ocr"));
    }

    /**
     * Verifies that {@link OcrUtils#preprocessForOcr(BufferedImage, int)} with a
     * scale factor of 5 produces an image of the expected dimensions for a
     * VF-badge- sized input crop (96 × 36 px). The expected width is
     * {@code 96 * 5 + 2 * 20 = 520} and the expected height is
     * {@code 36 * 5 + 2 * 20 = 220}.
     */
    @Test
    void preprocessWithScale5ProducesCorrectDimensions() {
        BufferedImage syntheticVfCrop = new BufferedImage(VF_CROP_W, VF_CROP_H, BufferedImage.TYPE_INT_RGB);
        BufferedImage result = OcrUtils.preprocessForOcr(syntheticVfCrop, VF_SCALE);

        Assertions.assertNotNull(result, "preprocessForOcr must not return null for a valid source image");
        Assertions.assertEquals(EXPECTED_W, result.getWidth(),
                "Preprocessed width must be cropW*scale + 2*pad = " + EXPECTED_W);
        Assertions.assertEquals(EXPECTED_H, result.getHeight(),
                "Preprocessed height must be cropH*scale + 2*pad = " + EXPECTED_H);
    }

    /**
     * Verifies that the no-arg overload of
     * {@link OcrUtils#preprocessForOcr(BufferedImage)} still produces the same
     * dimensions as explicitly passing {@code scaleFactor=3}, confirming the
     * delegation is correct and no regression was introduced.
     */
    @Test
    void preprocessDefaultOverloadDelegatesToScale3() {
        BufferedImage src = new BufferedImage(VF_CROP_W, VF_CROP_H, BufferedImage.TYPE_INT_RGB);
        BufferedImage defaultResult = OcrUtils.preprocessForOcr(src);
        BufferedImage explicitResult = OcrUtils.preprocessForOcr(src, 3);

        Assertions.assertEquals(explicitResult.getWidth(), defaultResult.getWidth(),
                "No-arg overload width must match preprocessForOcr(src, 3)");
        Assertions.assertEquals(explicitResult.getHeight(), defaultResult.getHeight(),
                "No-arg overload height must match preprocessForOcr(src, 3)");
    }

    /**
     * Verifies that passing {@code null} to both overloads returns {@code null}
     * without throwing an exception.
     */
    @Test
    void preprocessNullSourceReturnsNull() {
        Assertions.assertNull(OcrUtils.preprocessForOcr(null), "No-arg overload must return null for null input");
        Assertions.assertNull(OcrUtils.preprocessForOcr(null, 5), "Scale overload must return null for null input");
    }
}
