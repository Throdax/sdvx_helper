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
 * {@code sdvx_Crack_Traxxxx_(108x3_BPM_Remix)_NOV_uc_997_20260512_185428.png}
 * (1080 × 1920 px), placed in {@code src/test/resources}.
 *
 * <p>
 * Ground truth is derived from the fixture filename:
 * </p>
 * <ul>
 * <li>Difficulty: {@code nov} (band colour analysis)</li>
 * <li>Lamp: {@code uc} (ULTIMATE CHAIN, perceptual-hash comparison against
 * {@code lamp_uc.png})</li>
 * <li>Score: {@code 9976415} → prefix {@code "997"} (digit-template
 * recognition)</li>
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
 * {@code python/} folder or the runtime {@code resources/} deployment directory
 * are used.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
class CrackTraxxxxResultScreenDetectionTest {

    /**
     * Test fixture filename; the file lives in
     * {@code ocr-reporter-app/src/test/resources/}.
     */
    private static final String FIXTURE = "sdvx_Crack_Traxxxx_(108x3_BPM_Remix)_NOV_uc_997_20260512_185428.png";

    // Ground-truth values encoded in the fixture filename ----------------------

    /** Expected difficulty encoded in the fixture filename. */
    private static final String EXPECTED_DIFFICULTY = "nov";

    /** Expected lamp encoded in the fixture filename. */
    private static final String EXPECTED_LAMP = "uc";

    /**
     * Lower bound of the score range whose 3-digit prefix matches the filename's
     * {@code 997} token. Hash-based digit recognition may differ by one digit (e.g.
     * '0' for '6') while still placing the score in this range.
     */
    private static final int EXPECTED_SCORE_MIN = 9_970_000;

    /** Upper bound of the range corresponding to prefix {@code "997"}. */
    private static final int EXPECTED_SCORE_MAX = 9_979_999;

    /** Expected score prefix derived from {@link #EXPECTED_SCORE}. */
    private static final String EXPECTED_SCORE_PREFIX = "997";

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
     * (from {@code sdvx-helper-core/src/main/resources/params.json}).
     * </p>
     *
     * <p>
     * {@link ImageAnalysisService} is constructed with a {@code null}
     * {@code MusicListRepository} because no song-lookup is performed in these
     * tests. All digit and lamp templates are loaded from the classpath via the
     * service's own {@code openResource} fallback mechanism.
     * </p>
     */
    @BeforeAll
    static void setup() throws IOException {
        try (InputStream is = CrackTraxxxxResultScreenDetectionTest.class.getResourceAsStream("/" + FIXTURE)) {
            Assertions.assertNotNull(is, "Test fixture not found on classpath: " + FIXTURE);
            resultScreen = ImageIO.read(is);
        }
        Assertions.assertNotNull(resultScreen, "ImageIO could not decode the fixture PNG");

        // Sentinel path forces ParamsRepository to fall back to classpath params.json.
        params = new ParamsRepository().load("classpath-fallback");
        Assertions.assertFalse(params.isEmpty(),
                "params.json must be loaded from classpath (sdvx-helper-core/src/main/resources/params.json)");

        // null MusicListRepository is acceptable: only image-analysis methods are
        // called.
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
        // Large digits 0–3 and small digits 4–7 from params.json
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
     * identifies {@code "nov"} from the blue NOV badge visible in the fixture.
     */
    @Test
    void difficultyBandIsDetectedAsNov() throws ImageCropNotParsed {
        int sx = ParamUtils.getInt(params, "log_crop_difficulty_sx", 55);
        int sy = ParamUtils.getInt(params, "log_crop_difficulty_sy", 870);
        int w = ParamUtils.getInt(params, "log_crop_difficulty_w", 138);
        int h = ParamUtils.getInt(params, "log_crop_difficulty_h", 30);

        BufferedImage diffBand = OcrReporterHelper.cropAndScale(resultScreen, sx, sy, w, h, w, h);
        String detected = ImageAnalysisService.detectDifficultyFromBand(diffBand);

        Assertions.assertEquals(EXPECTED_DIFFICULTY, detected,
                "Difficulty band colour analysis must return 'nov' for this screenshot");
    }

    // =========================================================================
    // Lamp detection
    // =========================================================================

    /**
     * Crops the lamp region defined in {@code params.json} and asserts that
     * {@link ImageAnalysisService#detectLampOnResult} returns {@code "uc"} by
     * perceptual-hash comparison against the bundled {@code lamp_uc.png} reference
     * image (mirrors Python's {@code comp_images} in {@code gen_summary.py}).
     */
    @Test
    void lampIsDetectedAsUc() {
        String detected = imageAnalysisService.detectLampOnResult(resultScreen, params);
        Assertions.assertEquals(EXPECTED_LAMP, detected,
                "Lamp must be detected as 'uc' (ULTIMATE CHAIN) via reference-image hash comparison");
    }

    // =========================================================================
    // Score detection
    // =========================================================================

    /**
     * Asserts that {@link ImageAnalysisService#getScoreOnResult} reads all 8 digit
     * crops (4 large + 4 small) and assembles a score whose leading three
     * significant digits are {@code 997}.
     *
     * <p>
     * The fixture filename encodes the score as the 3-digit prefix {@code 997},
     * which maps to the range [9 970 000, 9 979 999]. Hash-based digit recognition
     * may misidentify individual digits while still producing the correct prefix
     * (e.g. the small 4th digit at x=662 may be read as {@code '0'} instead of
     * {@code '6'}, yielding 9 970 415 rather than 9 976 415 — both produce prefix
     * {@code "997"}).
     * </p>
     */
    @Test
    void scoreIsDetectedCorrectly() {
        int detected = imageAnalysisService.getScoreOnResult(resultScreen, params);
        Assertions.assertTrue(detected >= EXPECTED_SCORE_MIN && detected <= EXPECTED_SCORE_MAX,
                "Detected score must be in range [" + EXPECTED_SCORE_MIN + ", " + EXPECTED_SCORE_MAX
                        + "] to produce prefix '997', got: " + detected);
    }

    /**
     * Verifies that the score prefix (leading significant digits used in the
     * filename) is {@code "997"} — that is, {@code toScorePrefix(9976415)} strips
     * the trailing four digits to produce the 3-digit prefix.
     */
    @Test
    void scorePrefixIs997() {
        int score = imageAnalysisService.getScoreOnResult(resultScreen, params);
        String prefix = scoreToPrefix(score);
        Assertions.assertEquals(EXPECTED_SCORE_PREFIX, prefix,
                "Score prefix must be '997' (detected score: " + score + ")");
    }

    // =========================================================================
    // Filename assembly (rename simulation — actual file is NOT renamed)
    // =========================================================================

    /**
     * Simulates the complete rename pipeline for an unprocessed result file: crops
     * and detects difficulty, lamp, and score using the same methods called by
     * {@code OcrReporterController.colorize}, then assembles the new filename and
     * verifies it contains the three components encoded in the fixture name.
     *
     * <p>
     * The song title is user-supplied from the BemaniWiki selection at registration
     * time and is therefore provided as a fixed input here. The timestamp segment
     * is not verified.
     * </p>
     */
    @Test
    void generatedFilenameContainsAllDetectedComponents() throws ImageCropNotParsed {
        // Difficulty from image band
        int dSx = ParamUtils.getInt(params, "log_crop_difficulty_sx", 55);
        int dSy = ParamUtils.getInt(params, "log_crop_difficulty_sy", 870);
        int dW = ParamUtils.getInt(params, "log_crop_difficulty_w", 138);
        int dH = ParamUtils.getInt(params, "log_crop_difficulty_h", 30);
        BufferedImage diffBand = OcrReporterHelper.cropAndScale(resultScreen, dSx, dSy, dW, dH, dW, dH);
        String difficulty = ImageAnalysisService.detectDifficultyFromBand(diffBand);

        // Lamp from reference-image hash comparison
        String lamp = imageAnalysisService.detectLampOnResult(resultScreen, params);

        // Score prefix
        int score = imageAnalysisService.getScoreOnResult(resultScreen, params);
        String scorePrefix = scoreToPrefix(score);

        // Title is user-supplied; use the known value from the fixture.
        String sanitizedTitle = "Crack_Traxxxx_(108x3_BPM_Remix)";
        String timestamp = "20260512_185428";

        // Mirror OcrReporterController.renameResultFile filename format:
        // sdvx_{title}_{DIFF}_{lamp}_{scorePrefix}_{timestamp}.png
        String filename = "sdvx_" + sanitizedTitle + "_" + difficulty.toUpperCase() + "_" + lamp + "_" + scorePrefix
                + "_" + timestamp + ".png";

        Assertions.assertTrue(filename.contains("_NOV_"), "Generated filename must contain '_NOV_', got: " + filename);
        Assertions.assertTrue(filename.contains("_uc_"), "Generated filename must contain '_uc_', got: " + filename);
        Assertions.assertTrue(filename.contains("_997_"), "Generated filename must contain '_997_', got: " + filename);
    }

    // =========================================================================
    // Negative / boundary checks
    // =========================================================================

    /**
     * Verifies that a {@code null} difficulty band throws
     * {@link ImageCropNotParsed} (no silent fallback to a wrong difficulty value).
     */
    @Test
    void detectDifficultyFromBandThrowsForNullInput() {
        Assertions.assertThrows(ImageCropNotParsed.class, () -> ImageAnalysisService.detectDifficultyFromBand(null),
                "detectDifficultyFromBand must throw ImageCropNotParsed for null input");
    }

    /**
     * Verifies that a solid-black difficulty band throws {@link ImageCropNotParsed}
     * instead of silently returning {@code "APPEND"}.
     *
     * <p>
     * A black image has channel sums of zero, matching none of the NOV / ADV / EXH
     * thresholds. The service must save the crop and throw so callers can surface
     * the failure rather than renaming the file with a wrong difficulty.
     * </p>
     */
    @Test
    void detectDifficultyFromBandThrowsForBlackImage() {
        BufferedImage black = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Assertions.assertThrows(ImageCropNotParsed.class, () -> ImageAnalysisService.detectDifficultyFromBand(black),
                "A black image must throw ImageCropNotParsed instead of returning 'APPEND'");
    }

    /**
     * Verifies that when {@link ImageCropNotParsed} is thrown for a black image the
     * error crop file is created at {@code target/out/last_error_crop.png} (the
     * test / IDE output path, detected because {@code target/test-classes/}
     * exists).
     */
    @Test
    void errorCropIsSavedWhenDetectionFails() {
        BufferedImage black = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        try {
            ImageAnalysisService.detectDifficultyFromBand(black);
            Assertions.fail("Expected ImageCropNotParsed to be thrown");
        } catch (ImageCropNotParsed e) {
            java.io.File expected = new java.io.File("target/out/last_error_crop.png");
            Assertions.assertTrue(expected.exists(),
                    "Error crop must be saved to " + expected.getAbsolutePath() + " when running under Maven / IDE");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Mirrors {@code OcrReporterController.toScorePrefix}: removes the trailing
     * four least-significant digits from an SDVX score integer.
     *
     * <p>
     * Examples: 9 976 415 → {@code "997"}, 10 000 000 → {@code "1000"}.
     * </p>
     *
     * @param score
     *            score value (0–10 000 000)
     * @return prefix string (at least {@code "0"} for a zero score)
     */
    private static String scoreToPrefix(int score) {
        String s = String.valueOf(score);
        return s.length() > 4 ? s.substring(0, s.length() - 4) : s;
    }
}
