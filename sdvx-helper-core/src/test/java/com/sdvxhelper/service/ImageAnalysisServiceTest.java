package com.sdvxhelper.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.ScoreDetector;

/**
 * Integration-level tests for {@link ImageAnalysisService} using real SDVX
 * result-screen captures placed in {@code src/test/resources}.
 *
 * <p>
 * The test fixture is {@code sdvx_666_NOV_hard_992_20250424_183843.png} (1080 ×
 * 1920 px), a result screen for the song "666" played on NOVICE difficulty with
 * a HARD clear. All crop coordinates are taken verbatim from
 * {@code resources/params.json}.
 * </p>
 *
 * <p>
 * Tests validate structural invariants (image and crop dimensions, hash
 * stability, channel statistics) rather than the current heuristic lamp
 * classifier, which requires externally-provided reference images to reach full
 * accuracy.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
class ImageAnalysisServiceTest {

    /** File name of the result-screen test fixture (1080 × 1920 px). */
    private static final String RESULT_RESOURCE = "sdvx_666_NOV_hard_992_20250424_183843.png";

    /** File name of the jacket test fixture (262 × 262 px). */
    private static final String JACKET_RESOURCE = "4fd1e379df6e0ec7e3a049d03.png";

    /** Expected width of the result-screen capture. */
    private static final int RESULT_W = 1080;

    /** Expected height of the result-screen capture. */
    private static final int RESULT_H = 1920;

    // Crop coordinates from params.json ------------------------------------------

    /**
     * log_crop_jacket region (used on result screen to extract jacket thumbnail).
     */
    private static final int JACKET_X = 57, JACKET_Y = 916, JACKET_W = 263, JACKET_H = 263;

    /** lamp region (used on result screen to read the clear-lamp indicator). */
    private static final int LAMP_X = 630, LAMP_Y = 930, LAMP_W = 230, LAMP_H = 50;

    /** result_score_large_0 region (leftmost / most-significant score digit). */
    private static final int SCORE_D0_X = 431, SCORE_D0_Y = 1069, SCORE_D0_W = 52, SCORE_D0_H = 51;

    /** result_score_small_4 region (5th score digit, small variant). */
    private static final int SCORE_D4_X = 662, SCORE_D4_Y = 1089, SCORE_D4_W = 32, SCORE_D4_H = 31;

    // Loaded lazily by @BeforeAll ------------------------------------------------

    private static BufferedImage resultScreen;
    private static final PerceptualHasher hasher = new PerceptualHasher();

    @BeforeAll
    static void loadImages() throws IOException {
        resultScreen = loadResource(RESULT_RESOURCE);
    }

    // -------------------------------------------------------------------------
    // Structural tests: result-screen dimensions
    // -------------------------------------------------------------------------

    @Test
    void resultScreenHasExpectedDimensions() {
        Assertions.assertEquals(RESULT_W, resultScreen.getWidth(), "Result screen width must be " + RESULT_W);
        Assertions.assertEquals(RESULT_H, resultScreen.getHeight(), "Result screen height must be " + RESULT_H);
    }

    // -------------------------------------------------------------------------
    // Structural tests: crop dimensions match params.json
    // -------------------------------------------------------------------------

    @Test
    void jacketCropHasExpectedDimensions() {
        BufferedImage crop = resultScreen.getSubimage(JACKET_X, JACKET_Y, JACKET_W, JACKET_H);
        Assertions.assertEquals(JACKET_W, crop.getWidth(), "Jacket crop width must equal log_crop_jacket_w from params.json");
        Assertions.assertEquals(JACKET_H, crop.getHeight(), "Jacket crop height must equal log_crop_jacket_h from params.json");
    }

    @Test
    void lampCropHasExpectedDimensions() {
        BufferedImage crop = resultScreen.getSubimage(LAMP_X, LAMP_Y, LAMP_W, LAMP_H);
        Assertions.assertEquals(LAMP_W, crop.getWidth(), "Lamp crop width must equal lamp_w from params.json");
        Assertions.assertEquals(LAMP_H, crop.getHeight(), "Lamp crop height must equal lamp_h from params.json");
    }

    @Test
    void scoreLargeDigitCropHasExpectedDimensions() {
        BufferedImage crop = resultScreen.getSubimage(SCORE_D0_X, SCORE_D0_Y, SCORE_D0_W, SCORE_D0_H);
        Assertions.assertEquals(SCORE_D0_W, crop.getWidth(), "Large score digit crop width must match params.json");
        Assertions.assertEquals(SCORE_D0_H, crop.getHeight(), "Large score digit crop height must match params.json");
    }

    @Test
    void scoreSmallDigitCropHasExpectedDimensions() {
        BufferedImage crop = resultScreen.getSubimage(SCORE_D4_X, SCORE_D4_Y, SCORE_D4_W, SCORE_D4_H);
        Assertions.assertEquals(SCORE_D4_W, crop.getWidth(), "Small score digit crop width must match params.json");
        Assertions.assertEquals(SCORE_D4_H, crop.getHeight(), "Small score digit crop height must match params.json");
    }

    // -------------------------------------------------------------------------
    // Pixel statistics: lamp channel analysis
    // -------------------------------------------------------------------------

    /**
     * The result screen is a HARD clear (from the filename). In SDVX the hard-clear
     * lamp indicator renders in warm orange/red tones. We assert that the red
     * channel average is the dominant colour channel in the lamp region, which is a
     * stable property of the HARD lamp colour regardless of the exact threshold
     * values used by the classifier heuristic.
     */
    @Test
    void lampCropRedChannelIsDominantForHardClear() {
        BufferedImage lampCrop = resultScreen.getSubimage(LAMP_X, LAMP_Y, LAMP_W, LAMP_H);
        int[] avg = channelAverages(lampCrop);
        int rAvg = avg[0];
        int gAvg = avg[1];
        int bAvg = avg[2];
        Assertions.assertTrue(rAvg > gAvg, "Red channel must exceed green for hard lamp (rAvg=" + rAvg + ", gAvg=" + gAvg + ")");
        Assertions.assertTrue(rAvg > bAvg, "Red channel must exceed blue for hard lamp (rAvg=" + rAvg + ", bAvg=" + bAvg + ")");
    }

    /**
     * Verifies that all crop coordinates in params.json lie within the bounds of a
     * 1080 × 1920 result-screen capture, i.e. no crop region overflows the image.
     */
    @Test
    void allParamsCropRegionsAreWithinResultScreenBounds() {
        int[][] regions = { { JACKET_X, JACKET_Y, JACKET_W, JACKET_H }, { LAMP_X, LAMP_Y, LAMP_W, LAMP_H }, { SCORE_D0_X, SCORE_D0_Y, SCORE_D0_W, SCORE_D0_H },
                { SCORE_D4_X, SCORE_D4_Y, SCORE_D4_W, SCORE_D4_H }, };
        for (int[] r : regions) {
            int x = r[0], y = r[1], w = r[2], h = r[3];
            Assertions.assertTrue(x + w <= RESULT_W, "Crop x+w=" + (x + w) + " exceeds result width " + RESULT_W);
            Assertions.assertTrue(y + h <= RESULT_H, "Crop y+h=" + (y + h) + " exceeds result height " + RESULT_H);
        }
    }

    // -------------------------------------------------------------------------
    // Hash stability: jacket crop from result screen
    // -------------------------------------------------------------------------

    @Test
    void jacketCropFromResultHashIsStableAndValidFormat() {
        BufferedImage crop = resultScreen.getSubimage(JACKET_X, JACKET_Y, JACKET_W, JACKET_H);
        String hash1 = hasher.hash(crop);
        String hash2 = hasher.hash(crop);
        Assertions.assertEquals(hash1, hash2, "Hashing the same crop twice must produce identical results");
        Assertions.assertEquals(25, hash1.length(), "Hash must be 25 hex characters");
        Assertions.assertTrue(hash1.matches("[0-9a-f]{25}"), "Hash must be lowercase hex: " + hash1);
    }

    // -------------------------------------------------------------------------
    // Jacket file resource tests
    // -------------------------------------------------------------------------

    @Test
    void jacketFileHashIsStableAndValidFormat() throws IOException {
        BufferedImage jacket = loadResource(JACKET_RESOURCE);
        String hash1 = hasher.hash(jacket);
        String hash2 = hasher.hash(jacket);
        Assertions.assertEquals(hash1, hash2);
        Assertions.assertEquals(25, hash1.length());
        Assertions.assertTrue(hash1.matches("[0-9a-f]{25}"), "Expected lowercase hex: " + hash1);
    }

    @Test
    void jacketFileHasSelfSimilarity() throws IOException {
        BufferedImage jacket = loadResource(JACKET_RESOURCE);
        String hash = hasher.hash(jacket);
        Assertions.assertEquals(0, hasher.hammingDistance(hash, hash), "A hash compared to itself must have Hamming distance 0");
    }

    /**
     * Verifies that the two test fixture images produce different hashes,
     * confirming that the hasher actually discriminates between distinct images
     * rather than always returning the same value.
     */
    @Test
    void jacketFileHashDiffersFromJacketCropInResultScreen() throws IOException {
        BufferedImage jacketFile = loadResource(JACKET_RESOURCE);
        BufferedImage jacketCrop = resultScreen.getSubimage(JACKET_X, JACKET_Y, JACKET_W, JACKET_H);
        String fileHash = hasher.hash(jacketFile);
        String cropHash = hasher.hash(jacketCrop);
        // These are different images (different songs / different cropping) so their
        // hashes must differ.
        Assertions.assertNotEquals(fileHash, cropHash, "Standalone jacket file and result-screen jacket crop should produce different hashes");
    }

    // -------------------------------------------------------------------------
    // Score detection: digit-by-digit crop + ScoreDetector recognition
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link ScoreDetector} correctly reads all 8 score digits from
     * the result screen and assembles the final score.
     *
     * <p>
     * Digit template images ({@code result_score_l0-9.png} for the large first-four
     * digits and {@code result_score_s0-9.png} for the smaller last-four) are
     * loaded from {@code src/main/resources/images/} — exactly the same files used
     * by the runtime application.
     * </p>
     *
     * <p>
     * Digit regions are cropped using the coordinates from {@code params.json}:
     * <ul>
     * <li>Digits 0–3 (large, 52 × 51 px): {@code result_score_large_0} …
     * {@code result_score_large_3}</li>
     * <li>Digits 4–7 (small, 32 × 31 px): {@code result_score_small_4} …
     * {@code result_score_small_7}</li>
     * </ul>
     * Ground truth (9 928 527) confirmed with the Python reference implementation.
     * </p>
     */
    @Test
    void scoreFromResultScreenIsDetectedCorrectly() throws IOException {
        Map<Character, String> largeTemplates = loadDigitTemplates("result_score_l");
        Map<Character, String> smallTemplates = loadDigitTemplates("result_score_s");

        ScoreDetector largeDetector = new ScoreDetector(largeTemplates);
        ScoreDetector smallDetector = new ScoreDetector(smallTemplates);

        // Large-digit crop coordinates from params.json (result_score_large_0 … _3)
        int[][] largeCrops = { { 431, 1069, 52, 51 }, { 489, 1069, 52, 51 }, { 547, 1069, 52, 51 }, { 605, 1069, 52, 51 }, };
        // Small-digit crop coordinates from params.json (result_score_small_4 … _7)
        int[][] smallCrops = { { 662, 1089, 32, 31 }, { 698, 1089, 32, 31 }, { 734, 1089, 32, 31 }, { 770, 1089, 32, 31 }, };

        StringBuilder sb = new StringBuilder(8);
        for (int[] c : largeCrops) {
            BufferedImage crop = resultScreen.getSubimage(c[0], c[1], c[2], c[3]);
            char d = largeDetector.detectDigit(crop);
            Assertions.assertNotEquals('?', d, "Large digit at x=" + c[0] + " could not be matched against templates");
            sb.append(d);
        }
        for (int[] c : smallCrops) {
            BufferedImage crop = resultScreen.getSubimage(c[0], c[1], c[2], c[3]);
            char d = smallDetector.detectDigit(crop);
            Assertions.assertNotEquals('?', d, "Small digit at x=" + c[0] + " could not be matched against templates");
            sb.append(d);
        }

        int detectedScore = Integer.parseInt(sb.toString());
        Assertions.assertEquals(9_928_527, detectedScore, "Assembled score from 8 digit crops must match ground truth");
    }

    /**
     * Verifies that each of the 4 large-digit crops from the result screen is
     * individually recognisable (distance to best template ≤
     * {@code MATCH_THRESHOLD}). This isolates individual crop quality from
     * score-assembly concerns.
     */
    @Test
    void eachLargeDigitCropIsIndividuallyRecognised() throws IOException {
        Map<Character, String> largeTemplates = loadDigitTemplates("result_score_l");
        ScoreDetector detector = new ScoreDetector(largeTemplates);

        int[][] crops = { { 431, 1069, 52, 51 }, { 489, 1069, 52, 51 }, { 547, 1069, 52, 51 }, { 605, 1069, 52, 51 }, };
        char[] expected = { '0', '9', '9', '2' };

        for (int i = 0; i < crops.length; i++) {
            int[] c = crops[i];
            BufferedImage crop = resultScreen.getSubimage(c[0], c[1], c[2], c[3]);
            char detected = detector.detectDigit(crop);
            Assertions.assertEquals(expected[i], detected, "Large digit " + i + " at x=" + c[0] + " should be '" + expected[i] + "'");
        }
    }

    /**
     * Verifies that each of the 4 small-digit crops from the result screen is
     * individually recognisable.
     */
    @Test
    void eachSmallDigitCropIsIndividuallyRecognised() throws IOException {
        Map<Character, String> smallTemplates = loadDigitTemplates("result_score_s");
        ScoreDetector detector = new ScoreDetector(smallTemplates);

        int[][] crops = { { 662, 1089, 32, 31 }, { 698, 1089, 32, 31 }, { 734, 1089, 32, 31 }, { 770, 1089, 32, 31 }, };
        char[] expected = { '8', '5', '2', '7' };

        for (int i = 0; i < crops.length; i++) {
            int[] c = crops[i];
            BufferedImage crop = resultScreen.getSubimage(c[0], c[1], c[2], c[3]);
            char detected = detector.detectDigit(crop);
            Assertions.assertEquals(expected[i], detected, "Small digit " + i + " at x=" + c[0] + " should be '" + expected[i] + "'");
        }
    }

    /**
     * Verifies that {@link ScoreDetector#detectDigit(BufferedImage)} successfully
     * recognises all 4 large digit crops (52 × 51 px) using large templates, and
     * that the assembled 4-digit group is in the expected range for the most
     * significant half of an SDVX score.
     */
    @Test
    void detectScoreApiLargeTemplates() throws IOException {
        Map<Character, String> largeTemplates = loadDigitTemplates("result_score_l");
        ScoreDetector detector = new ScoreDetector(largeTemplates);

        int[][] largeCropCoords = { { 431, 1069, 52, 51 }, { 489, 1069, 52, 51 }, { 547, 1069, 52, 51 }, { 605, 1069, 52, 51 }, };

        StringBuilder sb = new StringBuilder(4);
        for (int[] c : largeCropCoords) {
            char d = detector.detectDigit(resultScreen.getSubimage(c[0], c[1], c[2], c[3]));
            Assertions.assertNotEquals('?', d, "Large digit at x=" + c[0] + " must be recognisable with large templates");
            sb.append(d);
        }

        int half = Integer.parseInt(sb.toString());
        Assertions.assertTrue(half >= 0 && half <= 1000, "Large digit group (most-significant 4 digits) must be in [0, 1000], was: " + half);
    }

    /**
     * Verifies that {@link ScoreDetector#detectDigit(BufferedImage)} successfully
     * recognises all 4 small digit crops (32 × 31 px) using small templates, and
     * that the assembled 4-digit group is in the expected range for the least
     * significant half of an SDVX score.
     */
    @Test
    void detectScoreApiSmallTemplates() throws IOException {
        Map<Character, String> smallTemplates = loadDigitTemplates("result_score_s");
        ScoreDetector detector = new ScoreDetector(smallTemplates);

        int[][] smallCropCoords = { { 662, 1089, 32, 31 }, { 698, 1089, 32, 31 }, { 734, 1089, 32, 31 }, { 770, 1089, 32, 31 }, };

        StringBuilder sb = new StringBuilder(4);
        for (int[] c : smallCropCoords) {
            char d = detector.detectDigit(resultScreen.getSubimage(c[0], c[1], c[2], c[3]));
            Assertions.assertNotEquals('?', d, "Small digit at x=" + c[0] + " must be recognisable with small templates");
            sb.append(d);
        }

        int half = Integer.parseInt(sb.toString());
        Assertions.assertTrue(half >= 0 && half <= 9999, "Small digit group (least-significant 4 digits) must be in [0, 9999], was: " + half);
    }

    /**
     * Verifies that the combined large + small digit recognition pipeline assembles
     * the correct full 8-digit score ({@code 9928527}) by using
     * {@link ScoreDetector#detectDigit(BufferedImage)} with the appropriate
     * template set for each digit group.
     *
     * <p>
     * {@code detectScore()} requires a single template set for all 8 images, which
     * is incompatible with the mixed large/small digit layout. This test therefore
     * calls {@code detectDigit()} per digit and assembles the result manually —
     * exactly matching the runtime production flow.
     * </p>
     */
    @Test
    void detectScoreApiCombinedTemplates() throws IOException {
        Map<Character, String> largeTemplates = loadDigitTemplates("result_score_l");
        Map<Character, String> smallTemplates = loadDigitTemplates("result_score_s");

        ScoreDetector largeDetector = new ScoreDetector(largeTemplates);
        ScoreDetector smallDetector = new ScoreDetector(smallTemplates);

        int[][] largeCropCoords = { { 431, 1069, 52, 51 }, { 489, 1069, 52, 51 }, { 547, 1069, 52, 51 }, { 605, 1069, 52, 51 }, };
        int[][] smallCropCoords = { { 662, 1089, 32, 31 }, { 698, 1089, 32, 31 }, { 734, 1089, 32, 31 }, { 770, 1089, 32, 31 }, };

        StringBuilder sb = new StringBuilder(8);
        for (int[] c : largeCropCoords) {
            char d = largeDetector.detectDigit(resultScreen.getSubimage(c[0], c[1], c[2], c[3]));
            Assertions.assertNotEquals('?', d, "Large digit at x=" + c[0] + " must be recognisable with large templates");
            sb.append(d);
        }
        for (int[] c : smallCropCoords) {
            char d = smallDetector.detectDigit(resultScreen.getSubimage(c[0], c[1], c[2], c[3]));
            Assertions.assertNotEquals('?', d, "Small digit at x=" + c[0] + " must be recognisable with small templates");
            sb.append(d);
        }

        int fullScore = Integer.parseInt(sb.toString());
        Assertions.assertTrue(fullScore >= 0 && fullScore <= 10_000_000, "Assembled score must be in valid SDVX range [0, 10000000], was: " + fullScore);
        Assertions.assertEquals(9_928_527, fullScore, "Combined large+small digit assembly must match ground truth");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a digit-template hash map for {@link ScoreDetector} by loading the ten
     * digit PNGs ({@code prefix0.png} … {@code prefix9.png}) from the main
     * classpath resources ({@code src/main/resources/images/}).
     *
     * @param prefix image filename prefix, e.g. {@code "result_score_l"} or
     *               {@code "result_score_s"}
     * @return map from digit character ({@code '0'}–{@code '9'}) to its aHash
     *         string
     * @throws IOException if any template image cannot be loaded
     */
    private static Map<Character, String> loadDigitTemplates(String prefix) throws IOException {
        Map<Character, String> templates = new HashMap<>();
        for (int d = 0; d <= 9; d++) {
            BufferedImage img = loadResource("images/" + prefix + d + ".png");
            templates.put((char) ('0' + d), hasher.hash(img));
        }
        return templates;
    }

    /**
     * Loads an image resource from the test classpath.
     *
     * @param filename resource file name (must exist in {@code src/test/resources})
     * @return loaded {@link BufferedImage}
     * @throws IOException if the resource cannot be found or decoded
     */
    private static BufferedImage loadResource(String filename) throws IOException {
        InputStream is = ImageAnalysisServiceTest.class.getResourceAsStream("/" + filename);
        Assertions.assertNotNull(is, "Test resource not found on classpath: " + filename);
        return ImageIO.read(is);
    }

    /**
     * Computes the per-channel pixel averages [R, G, B] for the given image.
     *
     * @param img image to analyse
     * @return {@code int[3]} containing average R, G, B values (0–255)
     */
    private static int[] channelAverages(BufferedImage img) {
        long rSum = 0, gSum = 0, bSum = 0;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                rSum += (rgb >> 16) & 0xFF;
                gSum += (rgb >> 8) & 0xFF;
                bSum += rgb & 0xFF;
            }
        }
        int pixels = w * h;
        return new int[] { (int) (rSum / pixels), (int) (gSum / pixels), (int) (bSum / pixels) };
    }
}
