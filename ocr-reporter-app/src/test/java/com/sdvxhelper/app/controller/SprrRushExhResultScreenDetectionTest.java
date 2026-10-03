package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import javax.imageio.ImageIO;

import com.sdvxhelper.repository.ParamsRepository;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.ImageCropNotParsed;
import com.sdvxhelper.util.ParamUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end detection test using the real result-screen screenshot
 * {@code sdvx_SprrRush!!_EXH_hard_979_20260414_184141.png} (1080 × 1920 px),
 * placed in {@code src/test/resources}.
 *
 * <p>
 * Ground truth is derived from the fixture filename:
 * </p>
 * <ul>
 * <li>Difficulty: {@code exh} (band colour analysis — red EXH badge)</li>
 * <li>Lamp: {@code hard} (HARD CLEAR, perceptual-hash comparison against
 * {@code lamp_clear.png})</li>
 * <li>Score: leading three significant digits {@code 979} → prefix
 * {@code "979"} (digit-template recognition)</li>
 * </ul>
 *
 * <p>
 * The actual fixture file is never renamed; the test only verifies the values
 * that the production {@code colorize} / rename logic would embed in the new
 * filename. The song title is user-supplied from the BemaniWiki list and is
 * therefore not validated here.
 * </p>
 *
 * <p>
 * All resources (digit templates, lamp reference images, {@code params.json})
 * are loaded exclusively from the classpath resources of
 * {@code sdvx-helper-core/src/main/resources/} — no files from the
 * {@code python/} folder are used.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
class SprrRushExhResultScreenDetectionTest {

    /**
     * Test fixture filename; lives in {@code ocr-reporter-app/src/test/resources/}.
     */
    private static final String FIXTURE = "sdvx_SprrRush!!_EXH_hard_979_20260414_184141.png";

    // Ground-truth values encoded in the fixture filename ----------------------

    /** Expected difficulty encoded in the fixture filename. */
    private static final String EXPECTED_DIFFICULTY = "exh";

    /** Expected lamp encoded in the fixture filename. */
    private static final String EXPECTED_LAMP = "hard";

    /**
     * Lower bound of the score range whose 3-digit prefix matches the filename's
     * {@code 979} token.
     */
    private static final int EXPECTED_SCORE_MIN = 9_790_000;

    /** Upper bound of the range corresponding to prefix {@code "979"}. */
    private static final int EXPECTED_SCORE_MAX = 9_799_999;

    /** Expected score prefix derived from the fixture filename. */
    private static final String EXPECTED_SCORE_PREFIX = "979";

    /** Expected image width in pixels. */
    private static final int EXPECTED_WIDTH = 1080;

    /** Expected image height in pixels. */
    private static final int EXPECTED_HEIGHT = 1920;

    // Loaded by @BeforeAll -----------------------------------------------------

    private static BufferedImage resultScreen;
    private static Map<String, String> params;
    private static ImageAnalysisService imageAnalysisService;

    /**
     * Loads the test fixture and initialises the {@link ImageAnalysisService}.
     *
     * <p>
     * {@code params.json} is loaded via {@link ParamsRepository}: the sentinel path
     * {@code "classpath-fallback"} does not resolve to a real file, so the
     * repository falls back to the bundled {@code params.json} on the classpath
     * ({@code sdvx-helper-core/src/main/resources/params.json}).
     * </p>
     */
    @BeforeAll
    static void setup() throws IOException {
        try (InputStream is = SprrRushExhResultScreenDetectionTest.class.getResourceAsStream("/" + FIXTURE)) {
            Assertions.assertNotNull(is, "Test fixture not found on classpath: " + FIXTURE);
            resultScreen = ImageIO.read(is);
        }
        Assertions.assertNotNull(resultScreen, "ImageIO could not decode the fixture PNG");

        params = new ParamsRepository().load("classpath-fallback");
        Assertions.assertFalse(params.isEmpty(),
                "params.json must be loaded from classpath (sdvx-helper-core/src/main/resources/params.json)");

        imageAnalysisService = new ImageAnalysisService(null);
    }

    // =========================================================================
    // Image dimensions
    // =========================================================================

    @Test
    void resultScreenHasExpectedDimensions() {
        Assertions.assertEquals(EXPECTED_WIDTH, resultScreen.getWidth(),
                "Result screen must be " + EXPECTED_WIDTH + " px wide");
        Assertions.assertEquals(EXPECTED_HEIGHT, resultScreen.getHeight(),
                "Result screen must be " + EXPECTED_HEIGHT + " px tall");
    }

    // =========================================================================
    // Crop regions are within image bounds
    // =========================================================================

    @Test
    void difficultyCropRegionIsWithinImageBounds() {
        int sx = ParamUtils.getInt(params, "log_crop_difficulty_sx", 55);
        int sy = ParamUtils.getInt(params, "log_crop_difficulty_sy", 870);
        int w = ParamUtils.getInt(params, "log_crop_difficulty_w", 138);
        int h = ParamUtils.getInt(params, "log_crop_difficulty_h", 30);
        Assertions.assertTrue(sx + w <= resultScreen.getWidth(), "Difficulty band crop must not exceed image width");
        Assertions.assertTrue(sy + h <= resultScreen.getHeight(), "Difficulty band crop must not exceed image height");
    }

    @Test
    void lampCropRegionIsWithinImageBounds() {
        int sx = ParamUtils.getInt(params, "lamp_sx", 630);
        int sy = ParamUtils.getInt(params, "lamp_sy", 930);
        int w = ParamUtils.getInt(params, "lamp_w", 230);
        int h = ParamUtils.getInt(params, "lamp_h", 50);
        Assertions.assertTrue(sx + w <= resultScreen.getWidth(), "Lamp crop must not exceed image width");
        Assertions.assertTrue(sy + h <= resultScreen.getHeight(), "Lamp crop must not exceed image height");
    }

    @Test
    void scoreDigitCropRegionsAreWithinImageBounds() {
        for (int i = 0; i < 4; i++) {
            int sx = ParamUtils.getInt(params, "result_score_large_" + i + "_sx", 0);
            int sy = ParamUtils.getInt(params, "result_score_large_" + i + "_sy", 0);
            int w = ParamUtils.getInt(params, "result_score_large_" + i + "_w", 52);
            int h = ParamUtils.getInt(params, "result_score_large_" + i + "_h", 51);
            Assertions.assertTrue(sx + w <= resultScreen.getWidth(),
                    "Large digit " + i + " crop must not exceed image width");
            Assertions.assertTrue(sy + h <= resultScreen.getHeight(),
                    "Large digit " + i + " crop must not exceed image height");
        }
        for (int i = 4; i < 8; i++) {
            int sx = ParamUtils.getInt(params, "result_score_small_" + i + "_sx", 0);
            int sy = ParamUtils.getInt(params, "result_score_small_" + i + "_sy", 0);
            int w = ParamUtils.getInt(params, "result_score_small_" + i + "_w", 32);
            int h = ParamUtils.getInt(params, "result_score_small_" + i + "_h", 31);
            Assertions.assertTrue(sx + w <= resultScreen.getWidth(),
                    "Small digit " + i + " crop must not exceed image width");
            Assertions.assertTrue(sy + h <= resultScreen.getHeight(),
                    "Small digit " + i + " crop must not exceed image height");
        }
    }

    // =========================================================================
    // Difficulty detection
    // =========================================================================

    /**
     * Crops the difficulty band using coordinates from {@code params.json} and
     * asserts that {@link ImageAnalysisService#detectDifficultyFromBand} correctly
     * identifies {@code "exh"} from the red EXH badge visible in the fixture.
     */
    @Test
    void difficultyBandIsDetectedAsExh() throws ImageCropNotParsed {
        int sx = ParamUtils.getInt(params, "log_crop_difficulty_sx", 55);
        int sy = ParamUtils.getInt(params, "log_crop_difficulty_sy", 870);
        int w = ParamUtils.getInt(params, "log_crop_difficulty_w", 138);
        int h = ParamUtils.getInt(params, "log_crop_difficulty_h", 30);

        BufferedImage diffBand = OcrReporterHelper.cropAndScale(resultScreen, sx, sy, w, h, w, h);
        String detected = ImageAnalysisService.detectDifficultyFromBand(diffBand);

        Assertions.assertEquals(EXPECTED_DIFFICULTY, detected,
                "Difficulty band colour analysis must return 'exh' for this screenshot");
    }

    // =========================================================================
    // Lamp detection
    // =========================================================================

    /**
     * Crops the lamp region defined in {@code params.json} and asserts that
     * {@link ImageAnalysisService#detectLampOnResult} returns {@code "hard"} by
     * perceptual-hash comparison against the bundled {@code lamp_clear.png}
     * reference image (mirrors Python's {@code comp_images} in
     * {@code gen_summary.py}).
     */
    @Test
    void lampIsDetectedAsHard() {
        String detected = imageAnalysisService.detectLampOnResult(resultScreen, params);
        Assertions.assertEquals(EXPECTED_LAMP, detected,
                "Lamp must be detected as 'hard' (HARD CLEAR) via reference-image hash comparison");
    }

    // =========================================================================
    // Score detection
    // =========================================================================

    /**
     * Asserts that {@link ImageAnalysisService#getScoreOnResult} reads all 8 digit
     * crops (4 large + 4 small) and assembles a score whose leading significant
     * digits are {@code 979}.
     */
    @Test
    void scoreIsDetectedCorrectly() {
        int detected = imageAnalysisService.getScoreOnResult(resultScreen, params);
        Assertions.assertTrue(detected >= EXPECTED_SCORE_MIN && detected <= EXPECTED_SCORE_MAX,
                "Detected score must be in range [" + EXPECTED_SCORE_MIN + ", " + EXPECTED_SCORE_MAX
                        + "] to produce prefix '979', got: " + detected);
    }

    /**
     * Verifies that the score prefix (leading significant digits used in the
     * filename) is {@code "979"}.
     */
    @Test
    void scorePrefixIs979() {
        int score = imageAnalysisService.getScoreOnResult(resultScreen, params);
        String prefix = scoreToPrefix(score);
        Assertions.assertEquals(EXPECTED_SCORE_PREFIX, prefix,
                "Score prefix must be '979' (detected score: " + score + ")");
    }

    // =========================================================================
    // Filename assembly (rename simulation — actual file is NOT renamed)
    // =========================================================================

    /**
     * Simulates the complete rename pipeline for an unprocessed result file: crops
     * and detects difficulty, lamp, and score using the same methods called by
     * {@code OcrReporterController.colorize}, then assembles the new filename and
     * verifies it contains the three components encoded in the fixture name.
     */
    @Test
    void generatedFilenameContainsAllDetectedComponents() throws ImageCropNotParsed {
        int dSx = ParamUtils.getInt(params, "log_crop_difficulty_sx", 55);
        int dSy = ParamUtils.getInt(params, "log_crop_difficulty_sy", 870);
        int dW = ParamUtils.getInt(params, "log_crop_difficulty_w", 138);
        int dH = ParamUtils.getInt(params, "log_crop_difficulty_h", 30);
        BufferedImage diffBand = OcrReporterHelper.cropAndScale(resultScreen, dSx, dSy, dW, dH, dW, dH);
        String difficulty = ImageAnalysisService.detectDifficultyFromBand(diffBand);

        String lamp = imageAnalysisService.detectLampOnResult(resultScreen, params);

        int score = imageAnalysisService.getScoreOnResult(resultScreen, params);
        String scorePrefix = scoreToPrefix(score);

        String sanitizedTitle = "SprrRush!!";
        String timestamp = "20260414_184141";

        String filename = "sdvx_" + sanitizedTitle + "_" + difficulty.toUpperCase() + "_" + lamp + "_" + scorePrefix
                + "_" + timestamp + ".png";

        Assertions.assertTrue(filename.contains("_EXH_"), "Generated filename must contain '_EXH_', got: " + filename);
        Assertions.assertTrue(filename.contains("_hard_"),
                "Generated filename must contain '_hard_', got: " + filename);
        Assertions.assertTrue(filename.contains("_979_"), "Generated filename must contain '_979_', got: " + filename);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String scoreToPrefix(int score) {
        String s = String.valueOf(score);
        return s.length() > 4 ? s.substring(0, s.length() - 4) : s;
    }
}
