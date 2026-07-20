package com.sdvxhelper.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.imageio.ImageIO;

import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.repository.ParamsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests that {@link ImageAnalysisService#detectResultDifficulty} correctly
 * reads the difficulty colour band from real result-screen captures and returns
 * the expected difficulty string. The {@code log_crop_difficulty_*} coordinates
 * are loaded from the bundled {@code params.json}, ensuring the test exercises
 * the exact same crop region used by the runtime application.
 *
 * @author Throdax
 * @since 2.0.0
 */
class ResultScreenDifficultyDetectionTest {

    private static final String RESOURCE_666_NOV = "sdvx_666_NOV_hard_992_20250424_183843.png";
    private static final String RESOURCE_STRUGGLE_ADV = "sdvx_Struggle_ADV_uc_997_20260604_180949.png";
    private static final Path OUTPUT_DIR = Paths.get("target", "test-output", "result-difficulty-detection");

    private static Map<String, String> params;
    private static ImageAnalysisService imageAnalysisService;

    @BeforeAll
    static void setUp() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        params = new ParamsRepository().load("");
        imageAnalysisService = new ImageAnalysisService(Mockito.mock(MusicListRepository.class));
    }

    /**
     * Verifies that the difficulty colour band from the result screen for "666"
     * (NOVICE, HARD clear, score 9 928 527) is detected as {@code "nov"}.
     *
     * <p>
     * The default crop region ({@code log_crop_difficulty_*}: sx=55, sy=870, w=138,
     * h=30) covers approximately 2 100 pixels, which aligns with the normalised
     * reference scale used by the RGB-sum thresholds inside
     * {@link ImageAnalysisService#detectDifficultyFromBand(BufferedImage)}.
     * </p>
     */
    @Test
    void testNovDifficultyDetectedFrom666ResultScreen() throws IOException {
        BufferedImage frame = loadResource(RESOURCE_666_NOV);
        String diff = imageAnalysisService.detectResultDifficulty(frame, params);
        Assertions.assertEquals("nov", diff,
                "detectResultDifficulty must return 'nov' for the 666 NOVICE result screen");
    }

    /**
     * Verifies that the difficulty colour band from the result screen for
     * "Struggle" (ADVANCE, ULTIMATE CHAIN, score 9 970 000) is detected as
     * {@code "adv"}.
     */
    @Test
    void testAdvDifficultyDetectedFromStruggleResultScreen() throws IOException {
        BufferedImage frame = loadResource(RESOURCE_STRUGGLE_ADV);
        String diff = imageAnalysisService.detectResultDifficulty(frame, params);
        Assertions.assertEquals("adv", diff,
                "detectResultDifficulty must return 'adv' for the Struggle ADVANCED result screen");
    }

    /**
     * Verifies that passing a {@code null} frame returns {@code null} without
     * throwing an exception.
     */
    @Test
    void testNullFrameReturnsNull() {
        String diff = imageAnalysisService.detectResultDifficulty(null, params);
        Assertions.assertNull(diff, "detectResultDifficulty must return null for a null frame");
    }

    /**
     * Loads an image resource from the test classpath.
     *
     * @param filename
     *            resource file name present in {@code src/test/resources}
     * @return the loaded {@link BufferedImage}
     * @throws IOException
     *             if the resource cannot be found or decoded
     */
    private static BufferedImage loadResource(String filename) throws IOException {
        InputStream is = ResultScreenDifficultyDetectionTest.class.getResourceAsStream("/" + filename);
        Assertions.assertNotNull(is, "Test resource not found on classpath: " + filename);
        return ImageIO.read(is);
    }
}
