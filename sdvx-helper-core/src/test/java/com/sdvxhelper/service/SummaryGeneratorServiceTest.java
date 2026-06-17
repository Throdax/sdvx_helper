package com.sdvxhelper.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.repository.ParamsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link SummaryGeneratorService} verifying that
 * {@link SummaryGeneratorService#generateAll} produces an uncapped PNG whose
 * height grows with the play count, while
 * {@link SummaryGeneratorService#generate} remains capped at {@code log_maxnum}
 * rows.
 *
 * <p>
 * No real screenshot files are required; stub {@link OnePlayData} instances
 * without a screenshot path are sufficient to verify canvas dimensions because
 * the canvas is always allocated to accommodate all rows before any compositing
 * is attempted.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
class SummaryGeneratorServiceTest {

    private static final Path OUTPUT_DIR = Paths.get("target", "test-output", "summary-generator");
    private static final int STUB_PLAY_COUNT = 40;

    @BeforeAll
    static void cleanOutputDir() throws IOException {
        if (Files.exists(OUTPUT_DIR)) {
            File[] files = OUTPUT_DIR.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    Files.deleteIfExists(file.toPath());
                }
            }
        }
        Files.createDirectories(OUTPUT_DIR);
    }

    /**
     * Verifies that {@link SummaryGeneratorService#generateAll} writes a PNG whose
     * height accommodates all 40 stub plays — i.e. height is at least
     * {@code STUB_PLAY_COUNT * log_rowsize}, which exceeds the {@code log_maxnum}
     * cap used by {@link SummaryGeneratorService#generate}.
     */
    @Test
    void testGenerateAllProducesUncappedHeight() throws IOException {
        Map<String, String> params = new ParamsRepository().load("");
        Map<String, String> settings = Collections.emptyMap();

        int logRowSize = Integer.parseInt(params.getOrDefault("log_rowsize", "40"));
        int logMaxNum = Integer.parseInt(params.getOrDefault("log_maxnum", "30"));
        int logMargin = Integer.parseInt(params.getOrDefault("log_margin", "20"));

        Assertions.assertTrue(STUB_PLAY_COUNT > logMaxNum,
                "Test requires STUB_PLAY_COUNT > log_maxnum to verify uncapping");

        List<OnePlayData> plays = buildStubPlays(STUB_PLAY_COUNT);
        File targetFile = OUTPUT_DIR.resolve("generateAll_40plays.png").toFile();

        SummaryGeneratorService service = new SummaryGeneratorService(null);
        service.generateAll(plays, targetFile, params, settings, "resources");

        Assertions.assertTrue(targetFile.exists(), "generateAll output file should exist");
        BufferedImage result = ImageIO.read(targetFile);
        Assertions.assertNotNull(result, "Output file should be a readable PNG");

        int expectedMinHeight = STUB_PLAY_COUNT * logRowSize;
        int expectedHeight = logMargin * 2 + STUB_PLAY_COUNT * logRowSize;
        Assertions.assertEquals(expectedHeight, result.getHeight(),
                "generateAll canvas height should equal margin*2 + plays.size()*rowSize when plays > logMaxNum");
        Assertions.assertTrue(result.getHeight() >= expectedMinHeight,
                "generateAll canvas height should be at least STUB_PLAY_COUNT * rowSize");
    }

    /**
     * Verifies that {@link SummaryGeneratorService#generate} writes a PNG whose
     * height is capped at {@code log_maxnum * log_rowsize + 2 * log_margin}, even
     * when more plays are provided.
     */
    @Test
    void testGenerateStaysCapppedAtLogMaxNum() {
        Map<String, String> params = new ParamsRepository().load("");
        Map<String, String> settings = Collections.emptyMap();

        int logRowSize = Integer.parseInt(params.getOrDefault("log_rowsize", "40"));
        int logMaxNum = Integer.parseInt(params.getOrDefault("log_maxnum", "30"));
        int logMargin = Integer.parseInt(params.getOrDefault("log_margin", "20"));

        List<OnePlayData> plays = buildStubPlays(STUB_PLAY_COUNT);

        SummaryGeneratorService service = new SummaryGeneratorService(null);
        service.generate(plays, params, settings, "resources");

        File summaryFull = new File("out", "summary_full.png");
        Assertions.assertTrue(summaryFull.exists(), "generate() should produce out/summary_full.png");

        BufferedImage result;
        try {
            result = ImageIO.read(summaryFull);
        } catch (IOException e) {
            Assertions.fail("Could not read summary_full.png: " + e.getMessage());
            return;
        }
        Assertions.assertNotNull(result, "summary_full.png should be a readable PNG");

        int expectedHeight = logMargin * 2 + logMaxNum * logRowSize;
        Assertions.assertEquals(expectedHeight, result.getHeight(),
                "generate() canvas height should be capped at logMaxNum * rowSize + 2 * margin");
    }

    /**
     * Builds a list of {@code count} stub {@link OnePlayData} instances with no
     * screenshot file set. These stubs exercise the canvas-dimension logic without
     * requiring any actual result images on disk.
     *
     * @param count
     *            number of stub plays to create
     * @return list of stub plays (never {@code null})
     */
    private List<OnePlayData> buildStubPlays(int count) {
        List<OnePlayData> plays = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OnePlayData play = new OnePlayData();
            play.setTitle("Stub Song " + (i + 1));
            plays.add(play);
        }
        return plays;
    }
}
