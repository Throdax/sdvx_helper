package com.sdvxhelper.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the VF OCR preprocessing pipeline and Tesseract accuracy against a
 * real saved preprocessed badge image.
 *
 * <p>
 * The VF badge ({@code DD.DDD}) is cropped to 96 × 36 px at runtime. With a 5×
 * scale factor the preprocessed image is expected to be
 * {@code (96 * 5 + 40) × (36 * 5 + 40)} = {@code 520 × 220} px (the 40 px
 * accounts for the 20 px white padding added on each side). These tests verify
 * that {@link OcrUtils#preprocessForOcr(BufferedImage, int)} produces the
 * correct output dimensions and that the number-only sub-crop passed to
 * Tesseract is correctly recognised.
 * </p>
 *
 * @author Throdax
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

    /**
     * Tessdata directory used for tests — inside {@code target/} so Maven clean
     * removes it.
     */
    static final String TEST_TESSDATA_DIR = "target/tessdata";

    /**
     * Creates output directories and downloads {@code eng.traineddata} to
     * {@code target/tessdata/} using {@link TesseractLanguageInstaller} so
     * Tesseract has a valid language file for the OCR accuracy test.
     */
    @BeforeAll
    static void createOutputDir() throws Exception {
        Files.createDirectories(Paths.get("target", "test-output", "vf-ocr"));
        TesseractLanguageInstaller.ensureLanguages(List.of("eng"), TEST_TESSDATA_DIR);
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

    /**
     * Verifies that Tesseract can read the VF number from a real saved preprocessed
     * badge image ({@code part_volforce_preprocessed.png}) after applying the
     * number-only sub-crop used at runtime.
     *
     * <p>
     * The sub-crop isolates the lower-right area of the preprocessed badge (520×220
     * px), cutting away the badge icon and the "VOLFORCE" label so that Tesseract
     * receives only the numeric value. The crop coordinates are derived from the
     * known badge layout in the original 96×36 px source: the icon occupies roughly
     * the left 36 px and the number is in the bottom half (y ≥ 18 px):
     * </p>
     * <ul>
     * <li>x = 36 × 5 + 20 = 200</li>
     * <li>y = 18 × 5 + 20 = 110</li>
     * <li>w = 60 × 5 = 300</li>
     * <li>h = 18 × 5 = 90</li>
     * </ul>
     *
     * <p>
     * The test is skipped if {@code part_volforce_preprocessed.png} is not present
     * in the test classpath, or if Tesseract fails to initialise (e.g. missing
     * {@code eng.traineddata}).
     * </p>
     */
    /**
     * Verifies the production path: the full preprocessed VF badge image is written
     * to disk and Tesseract applies OCR only to the empirically determined number
     * region (x=200, y=90, 300×77 px) using PSM 8 and a digit+dot whitelist.
     *
     * <p>
     * This mirrors exactly what {@code ScreenHandler.hasVfChangedByOcr} does at
     * runtime — Tesseract's built-in rectangle support restricts recognition to the
     * number area, so the surrounding VOLFORCE label and badge icon are ignored
     * without needing a separate crop image.
     * </p>
     */
    @Test
    void numberCropFromPreprocessedImageIsRecognisedByTesseract() throws Exception {
        InputStream is = VfOcrTest.class.getResourceAsStream("/part_volforce_preprocessed.png");
        Assumptions.assumeTrue(is != null, "part_volforce_preprocessed.png not in test resources — skipping");

        BufferedImage preprocessed = ImageIO.read(is);
        is.close();
        Assertions.assertNotNull(preprocessed, "Test resource image must be readable as a BufferedImage");

        Path preprocessedFile = Paths.get("target", "test-output", "vf-ocr", "part_volforce_preprocessed.png");
        ImageIO.write(preprocessed, "png", preprocessedFile.toFile());

        TesseractOcr ocr = new TesseractOcr("eng", TEST_TESSDATA_DIR);
        ocr.setVariable("tessedit_char_whitelist", "0123456789.");
        ocr.setPageSegMode(8);
        Rectangle numberRegion = new Rectangle(200, 90, 300, 77);
        String raw = ocr.recognizeText(preprocessedFile.toFile(), numberRegion);

        Pattern vfPattern = Pattern.compile("(\\d+\\.\\d{3})");
        Matcher matcher = vfPattern.matcher(raw != null ? raw : "");
        Assertions.assertTrue(matcher.find(),
                "OCR of number region (200,90,300,77) must match \\d+\\.\\d{3}, but got: '" + raw + "'");
        Assertions.assertEquals("12.233", matcher.group(1),
                "OCR must return 12.233 from the number region of the preprocessed badge image");
    }
}
