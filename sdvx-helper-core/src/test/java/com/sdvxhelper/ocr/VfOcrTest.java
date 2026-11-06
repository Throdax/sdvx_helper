package com.sdvxhelper.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

    /** Number-only sub-region within the 520×220 preprocessed badge image. */
    private static final Rectangle NUMBER_REGION = new Rectangle(200, 90, 300, 77);

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
     * Verifies the production path: the full preprocessed VF badge image is written
     * to disk and {@link VfOcrReader#read(File, Rectangle)} applies OCR only to the
     * empirically determined number region (x=200, y=90, 300×77 px), reading the
     * known-good sample correctly as {@code "12.233"}.
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
        File preprocessedFile = extractResourceToFile("/part_volforce_preprocessed.png",
                "part_volforce_preprocessed.png");

        String result = new VfOcrReader(TEST_TESSDATA_DIR).read(preprocessedFile, NUMBER_REGION);

        Assertions.assertEquals("12.233", result,
                "Combined-engine OCR must return 12.233 from the number region of the preprocessed badge image");
    }

    /**
     * Verifies the exact real-world misread reported by the user: a preprocessed
     * badge image showing {@code "12.234"} was previously read by a single-pass PSM
     * 8 / OEM 1 (LSTM) Tesseract call as {@code "12.349"}. This test confirms that
     * {@link VfOcrReader#read(File, Rectangle)} — which combines the OEM 0 (legacy)
     * engine's reliable decimal digits with the OEM 1 (LSTM) engine's reliable
     * leading-digit count — now reads the correct value.
     */
    @Test
    void numberCropFromMisreadSampleIsNowRecognisedCorrectly() throws Exception {
        File misreadFile = extractResourceToFile("/part_volforce_preprocessed_misread.png",
                "part_volforce_preprocessed_misread.png");

        String result = new VfOcrReader(TEST_TESSDATA_DIR).read(misreadFile, NUMBER_REGION);

        Assertions.assertEquals("12.234", result,
                "Combined-engine OCR must correctly read 12.234 from the previously-misread sample");
    }

    /**
     * Copies a classpath test resource image to {@code target/test-output/vf-ocr/}
     * so Tesseract (which requires a file path, not an in-memory raster) can read
     * it. The test is skipped if the resource is not present on the classpath.
     *
     * @param resourcePath
     *            classpath-relative resource path (e.g. {@code "/foo.png"})
     * @param outputFileName
     *            file name to use under {@code target/test-output/vf-ocr/}
     * @return the extracted file
     * @throws Exception
     *             if the resource cannot be read or written
     */
    private File extractResourceToFile(String resourcePath, String outputFileName) throws Exception {
        InputStream is = VfOcrTest.class.getResourceAsStream(resourcePath);
        Assumptions.assumeTrue(is != null, resourcePath + " not in test resources — skipping");

        BufferedImage image = ImageIO.read(is);
        is.close();
        Assertions.assertNotNull(image, "Test resource image must be readable as a BufferedImage");

        Path outputFile = Paths.get("target", "test-output", "vf-ocr", outputFileName);
        ImageIO.write(image, "png", outputFile.toFile());
        return outputFile.toFile();
    }
}
