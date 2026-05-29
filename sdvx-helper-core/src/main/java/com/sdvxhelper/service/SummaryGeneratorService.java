package com.sdvxhelper.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.util.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates the OBS overlay summary images ({@code out/summary_full.png} and
 * {@code out/summary_small.png}) by compositing cropped parts from the
 * session's saved result screenshots.
 *
 * <p>
 * Mirrors Python {@code GenSummary.generate()} in {@code gen_summary.py:674}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SummaryGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SummaryGeneratorService.class);

    private ImageAnalysisService imageAnalysisService;

    /**
     * @param imageAnalysisService
     *            used to detect the lamp from a result screenshot when the
     *            {@link OnePlayData} stub has no lamp set (e.g. plays pre-loaded
     *            from disk at startup)
     */
    public SummaryGeneratorService(ImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
    }

    /**
     * Hard-coded resize targets that mirror Python
     * {@code GenSummary.cut_result_parts()}. Title parts are pasted at their
     * natural crop size.
     */
    private static final int DIFFICULTY_W = 69;
    private static final int DIFFICULTY_H = 15;
    private static final int SCORE_W = 86;
    private static final int SCORE_H = 20;
    private static final int RANK_W = 37;
    private static final int RANK_H = 25;
    private static final int RATE_W = 80;
    private static final int RATE_H = 20;
    private static final int JACKET_W = 36;
    private static final int JACKET_H = 36;

    /**
     * Scans {@code autosaveDir} for result screenshot files ({@code sdvx_*.png})
     * that are newer than {@code now - logpicOffsetHours} and composites them into
     * {@code out/summary_full.png} and {@code out/summary_small.png}.
     *
     * <p>
     * Mirrors the startup call {@code self.gen_summary.generate()} in Python's
     * {@code sdvx_helper.pyw:1511}, which reads existing result files from the
     * autosave directory to pre-populate the OBS overlay before any new play is
     * recorded. The overlay is always written (with correct opacity), even if no
     * matching result files are found.
     * </p>
     *
     * @param autosaveDir
     *            directory containing previously saved result screenshots
     * @param logpicOffsetHours
     *            how many hours back to look for result files (mirrors Python
     *            {@code logpic_offset_time})
     * @param params
     *            detection parameters (supplies all {@code log_*} coordinates)
     * @param settings
     *            application settings (supplies {@code logpic_bg_alpha})
     * @param resourcesDir
     *            path to the resources directory containing lamp icons
     * @return list of pre-loaded plays (oldest-first) that are within the time
     *         window; an empty list is returned when no matching files are found.
     *         The caller should seed its session-play list with this result so that
     *         subsequent {@link #generate} calls include both the startup history
     *         and any new session plays.
     */
    public List<OnePlayData> generateFromResultsDir(String autosaveDir, int logpicOffsetHours,
            Map<String, String> params, Map<String, String> settings, String resourcesDir) {
        File dir = new File(autosaveDir);
        List<OnePlayData> existingPlays = new ArrayList<>();

        if (dir.exists() && dir.isDirectory()) {
            FilenameFilter filter = (d, name) -> name.startsWith("sdvx_") && name.endsWith(".png");
            File[] files = dir.listFiles(filter);
            if (files != null && files.length > 0) {
                long cutoffMs = System.currentTimeMillis() - (long) logpicOffsetHours * 3600 * 1000;
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                for (File file : files) {
                    if (file.lastModified() < cutoffMs) {
                        continue;
                    }
                    OnePlayData play = new OnePlayData();
                    play.setScreenshotFile(file.getAbsolutePath());
                    existingPlays.add(play);
                }
                log.info("generateFromResultsDir: found {} result file(s) within {}h window in '{}'",
                        existingPlays.size(), logpicOffsetHours, autosaveDir);
            } else {
                log.debug("generateFromResultsDir: no sdvx_*.png files found in '{}'", autosaveDir);
            }
        } else {
            log.debug("generateFromResultsDir: autosave directory does not exist: '{}'", autosaveDir);
        }

        generate(existingPlays, params, settings, resourcesDir);
        return existingPlays;
    }

    /**
     * Generates {@code out/summary_full.png} and {@code out/summary_small.png} from
     * the result screenshots recorded in {@code sessionPlays}.
     *
     * <p>
     * Only plays that have a non-null {@link OnePlayData#getScreenshotFile()} are
     * included. Plays are processed in reverse-insertion order (newest first) and
     * limited to {@code log_maxnum} rows. Any individual row that cannot be
     * composed (e.g. the screenshot file is missing) is silently skipped. The
     * overlay images are always written — even when no rows are composited — so
     * that OBS always has a valid (empty) overlay with the correct opacity.
     * </p>
     *
     * @param sessionPlays
     *            plays recorded during the current session (ordered oldest-first)
     * @param params
     *            detection parameters (supplies all {@code log_*} coordinates)
     * @param settings
     *            application settings (supplies {@code logpic_bg_alpha})
     * @param resourcesDir
     *            path to the resources directory containing lamp icons
     * @return {@code true} if the overlay images were written successfully
     */
    public boolean generate(List<OnePlayData> sessionPlays, Map<String, String> params, Map<String, String> settings,
            String resourcesDir) {
        int logMaxNum = ParamUtils.getInt(params, "log_maxnum", 30);
        int logRowSize = ParamUtils.getInt(params, "log_rowsize", 40);
        int logMargin = ParamUtils.getInt(params, "log_margin", 20);
        int logWidth = ParamUtils.getInt(params, "log_width", 960);
        int logSmallWidth = ParamUtils.getInt(params, "log_small_width", 590);
        int alpha = ParamUtils.getInt(settings, "logpic_bg_alpha", 200);

        int height = logMargin * 2 + logMaxNum * logRowSize;

        BufferedImage bg = createTransparentBackground(logWidth, height, alpha);
        BufferedImage bgSmall = createTransparentBackground(logSmallWidth, height, alpha);

        List<OnePlayData> plays = sessionPlays != null ? sessionPlays : new ArrayList<>();
        List<OnePlayData> reversed = new ArrayList<>(plays);
        java.util.Collections.reverse(reversed);

        int idx = 0;
        for (OnePlayData play : reversed) {
            if (idx >= logMaxNum) {
                break;
            }
            if (play.getScreenshotFile() == null) {
                log.debug("generate: play '{}' has no screenshot file, skipping", play.getTitle());
                continue;
            }
            try {
                boolean placed = putResult(play, bg, bgSmall, idx, logRowSize, logMargin, params, resourcesDir);
                if (placed) {
                    idx++;
                }
            } catch (IOException e) {
                log.warn("generate: failed to composite row {} for '{}': {}", idx, play.getTitle(), e.getMessage());
            }
        }

        try {
            File outDir = new File("out");
            outDir.mkdirs();
            ImageIO.write(bg, "png", new File(outDir, "summary_full.png"));
            ImageIO.write(bgSmall, "png", new File(outDir, "summary_small.png"));
            log.info("Summary images saved ({} rows)", idx);
            return true;
        } catch (IOException e) {
            log.warn("generate: failed to save summary images: {}", e.getMessage());
            return false;
        }
    }

    private boolean putResult(OnePlayData play, BufferedImage bg, BufferedImage bgSmall, int idx, int rowSize,
            int logMargin, Map<String, String> params, String resourcesDir) throws IOException {
        File screenshotFile = new File(play.getScreenshotFile());
        if (!screenshotFile.exists()) {
            log.debug("putResult: screenshot not found at {}", screenshotFile.getAbsolutePath());
            return false;
        }
        BufferedImage img = ImageIO.read(screenshotFile);
        if (img == null) {
            log.debug("putResult: could not decode screenshot {}", screenshotFile.getName());
            return false;
        }

        BufferedImage jacket = resize(safeCrop(img, params, "log_crop_jacket"), JACKET_W, JACKET_H);
        BufferedImage difficulty = resize(safeCrop(img, params, "log_crop_difficulty"), DIFFICULTY_W, DIFFICULTY_H);
        BufferedImage title = safeCrop(img, params, "log_crop_title");
        BufferedImage titleSmall = safeCrop(img, params, "log_crop_title_small");
        BufferedImage score = resize(safeCrop(img, params, "log_crop_score"), SCORE_W, SCORE_H);
        BufferedImage rank = resize(safeCrop(img, params, "log_crop_rank"), RANK_W, RANK_H);
        BufferedImage rate = resize(safeCrop(img, params, "log_crop_rate"), RATE_W, RATE_H);

        String lamp = play.getLamp();
        if ((lamp == null || lamp.isBlank()) && imageAnalysisService != null) {
            lamp = imageAnalysisService.detectLampOnResult(img, params);
            log.debug("putResult: lamp not set on play, detected from screenshot: '{}'", lamp);
        }
        BufferedImage lampIcon = loadLampIcon(lamp, resourcesDir);

        int yOffset = logMargin + rowSize * idx;

        pasteOn(bg, jacket, params, "log_pos_jacket", yOffset);
        pasteOn(bg, difficulty, params, "log_pos_difficulty", yOffset);
        pasteOn(bg, title, params, "log_pos_title", yOffset);
        pasteOn(bg, score, params, "log_pos_score", yOffset);
        pasteOn(bg, rank, params, "log_pos_rank", yOffset);
        pasteOn(bg, rate, params, "log_pos_rate", yOffset);
        if (lampIcon != null) {
            pasteOn(bg, lampIcon, params, "log_pos_lamp", yOffset);
        }

        pasteOn(bgSmall, jacket, params, "log_pos_jacket_small", yOffset);
        pasteOn(bgSmall, difficulty, params, "log_pos_difficulty_small", yOffset);
        pasteOn(bgSmall, titleSmall, params, "log_pos_title_small", yOffset);
        pasteOn(bgSmall, score, params, "log_pos_score_small", yOffset);
        if (lampIcon != null) {
            pasteOn(bgSmall, lampIcon, params, "log_pos_lamp_small", yOffset);
        }
        return true;
    }

    private BufferedImage safeCrop(BufferedImage src, Map<String, String> params, String prefix) {
        int sx = ParamUtils.getInt(params, prefix + "_sx", 0);
        int sy = ParamUtils.getInt(params, prefix + "_sy", 0);
        int w = ParamUtils.getInt(params, prefix + "_w", 100);
        int h = ParamUtils.getInt(params, prefix + "_h", 30);
        int safeX = Math.max(0, Math.min(sx, src.getWidth() - 1));
        int safeY = Math.max(0, Math.min(sy, src.getHeight() - 1));
        int safeW = Math.max(1, Math.min(w, src.getWidth() - safeX));
        int safeH = Math.max(1, Math.min(h, src.getHeight() - safeY));
        return src.getSubimage(safeX, safeY, safeW, safeH);
    }

    private void pasteOn(BufferedImage dst, BufferedImage src, Map<String, String> params, String prefix, int yOffset) {
        int sx = ParamUtils.getInt(params, prefix + "_sx", 0);
        int sy = ParamUtils.getInt(params, prefix + "_sy", 0);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, sx, sy + yOffset, null);
        g.dispose();
    }

    private BufferedImage loadLampIcon(String lamp, String resourcesDir) {
        if (lamp == null || lamp.isBlank()) {
            return null;
        }
        File iconFile = new File(resourcesDir, "images/log_lamp_" + lamp + ".png");
        if (!iconFile.exists()) {
            log.debug("loadLampIcon: icon not found for lamp '{}' at {}", lamp, iconFile.getAbsolutePath());
            return null;
        }
        try {
            return ImageIO.read(iconFile);
        } catch (IOException e) {
            log.debug("loadLampIcon: failed to read '{}': {}", iconFile.getName(), e.getMessage());
            return null;
        }
    }

    private BufferedImage resize(BufferedImage src, int targetW, int targetH) {
        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return dst;
    }

    private BufferedImage createTransparentBackground(int width, int height, int alpha) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, alpha / 255.0f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return img;
    }

    @Override
    public String toString() {
        return "SummaryGeneratorService{}";
    }
}
