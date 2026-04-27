package com.sdvxhelper.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.util.ParamUtils;
import com.sdvxhelper.util.ScoreFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a composite summary image for today's session plays and saves it to
 * the autosave directory.
 *
 * <p>
 * Mirrors Python's {@code GenSummary} class in {@code gen_summary.py}. Each
 * play is represented as a horizontal strip containing score, lamp, difficulty,
 * and timestamp; strips are stacked vertically into a single PNG.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SummaryImageService {

    private static final Logger log = LoggerFactory.getLogger(SummaryImageService.class);

    private static final int ROW_HEIGHT = 80;
    private static final int ROW_WIDTH = 960;
    private static final int JACKET_W = 80;
    private static final int JACKET_H = 80;
    private static final int DIFF_BAND_W = 12;
    private static final int PADDING = 8;
    private static final int FONT_SIZE = 16;
    private static final int FONT_SIZE_SMALL = 12;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Builds a composite PNG from the given play list and writes it to
     * {@code outDir/summary_<timestamp>.png}.
     *
     * <p>
     * Delegates to {@link #generateAndSave(List, Path, Map)} with an empty params
     * map, producing text-only rows.
     * </p>
     *
     * @param plays
     *            plays to include (most-recent last)
     * @param outDir
     *            directory to write the summary PNG into
     */
    public void generateAndSave(List<OnePlayData> plays, Path outDir) {
        generateAndSave(plays, outDir, Collections.emptyMap());
    }

    /**
     * Builds a composite PNG from the given play list and writes it to
     * {@code outDir/summary_<timestamp>.png}.
     *
     * <p>
     * When a play has a {@link OnePlayData#getScreenshotFile() screenshotFile} and
     * {@code params} contains result-screen region coordinates, actual screenshot
     * crops (jacket, difficulty band, score strip, rank icon) are composited into
     * each row. Falls back to a text-only strip when the screenshot is unavailable.
     * </p>
     *
     * @param plays
     *            plays to include (most-recent last)
     * @param outDir
     *            directory to write the summary PNG into
     * @param params
     *            detection parameters from {@code params.json}; used to locate crop
     *            regions within the result screenshot
     */
    public void generateAndSave(List<OnePlayData> plays, Path outDir, Map<String, String> params) {
        int rows = Math.max(1, plays.size());
        int imgH = rows * ROW_HEIGHT;
        BufferedImage canvas = new BufferedImage(ROW_WIDTH, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE));
        FontMetrics fm = g.getFontMetrics();

        if (plays.isEmpty()) {
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, ROW_WIDTH, ROW_HEIGHT);
            g.setColor(Color.WHITE);
            g.drawString("No plays today.", PADDING, PADDING + fm.getAscent());
        } else {
            for (int i = 0; i < plays.size(); i++) {
                OnePlayData p = plays.get(i);
                int y = i * ROW_HEIGHT;

                Color bg = lampColor(p.getLamp());
                g.setColor(bg);
                g.fillRect(0, y, ROW_WIDTH, ROW_HEIGHT);

                boolean drew = drawPlayWithScreenshot(g, p, y, params);
                if (!drew) {
                    drawPlayTextOnly(g, fm, p, y);
                }

                g.setColor(new Color(0, 0, 0, 40));
                g.drawRect(0, y, ROW_WIDTH - 1, ROW_HEIGHT - 1);
            }
        }

        g.dispose();

        try {
            File dir = outDir.toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("Could not create output directory: {}", outDir);
                return;
            }
            String name = "summary_" + LocalDateTime.now().format(TS_FMT) + ".png";
            File outFile = outDir.resolve(name).toFile();
            ImageIO.write(canvas, "png", outFile);
            log.info("Summary image saved to {}", outFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save summary image", e);
        }
    }

    /**
     * Attempts to draw a play row using crops from the result screenshot. Returns
     * {@code true} if drawing succeeded, {@code false} if it should fall back to
     * text-only rendering.
     */
    private boolean drawPlayWithScreenshot(Graphics2D g, OnePlayData p, int rowY, Map<String, String> params) {
        String screenshotPath = p.getScreenshotFile();
        if (screenshotPath == null || screenshotPath.isEmpty()) {
            return false;
        }
        File screenshotFile = new File(screenshotPath);
        if (!screenshotFile.exists()) {
            return false;
        }
        try {
            BufferedImage shot = ImageIO.read(screenshotFile);
            if (shot == null) {
                return false;
            }

            int curX = 0;

            // --- Jacket ---
            int jx = ParamUtils.getInt(params, "result_jacket_sx", -1);
            int jy = ParamUtils.getInt(params, "result_jacket_sy", -1);
            int jw = ParamUtils.getInt(params, "result_jacket_w", 120);
            int jh = ParamUtils.getInt(params, "result_jacket_h", 120);
            if (jx >= 0 && jy >= 0) {
                BufferedImage jacket = safeSubimage(shot, jx, jy, jw, jh);
                if (jacket != null) {
                    Image scaled = jacket.getScaledInstance(JACKET_W, JACKET_H, Image.SCALE_SMOOTH);
                    g.drawImage(scaled, curX, rowY, null);
                    curX += JACKET_W + PADDING;
                }
            }

            // --- Difficulty colour band ---
            g.setColor(diffColor(p.getDifficulty()));
            g.fillRect(curX, rowY, DIFF_BAND_W, ROW_HEIGHT);
            curX += DIFF_BAND_W + PADDING;

            // --- Score strip from screenshot ---
            int scx = ParamUtils.getInt(params, "result_score_sx", -1);
            int scy = ParamUtils.getInt(params, "result_score_sy", -1);
            int scw = ParamUtils.getInt(params, "result_score_w", 300);
            int sch = ParamUtils.getInt(params, "result_score_h", 50);
            if (scx >= 0 && scy >= 0) {
                BufferedImage scoreCrop = safeSubimage(shot, scx, scy, scw, sch);
                if (scoreCrop != null) {
                    int targetH = Math.min(ROW_HEIGHT - PADDING, sch);
                    int targetW = scw * targetH / sch;
                    Image scaled = scoreCrop.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                    g.drawImage(scaled, curX, rowY + PADDING / 2, null);
                    curX += targetW + PADDING;
                }
            } else {
                // Draw text score as fallback within composite row
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE));
                g.setColor(Color.BLACK);
                g.drawString(ScoreFormatter.formatScore(p.getCurScore()), curX,
                        rowY + PADDING + g.getFontMetrics().getAscent());
                curX += 160 + PADDING;
            }

            // --- Rank icon ---
            int rx = ParamUtils.getInt(params, "result_rank_sx", -1);
            int ry = ParamUtils.getInt(params, "result_rank_sy", -1);
            int rw = ParamUtils.getInt(params, "result_rank_w", 80);
            int rh = ParamUtils.getInt(params, "result_rank_h", 50);
            if (rx >= 0 && ry >= 0) {
                BufferedImage rankCrop = safeSubimage(shot, rx, ry, rw, rh);
                if (rankCrop != null) {
                    int targetH = Math.min(ROW_HEIGHT - PADDING, rh);
                    int targetW = rw * targetH / rh;
                    Image scaled = rankCrop.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                    g.drawImage(scaled, curX, rowY + PADDING / 2, null);
                    curX += targetW + PADDING;
                }
            }

            // --- Lamp icon ---
            int lx = ParamUtils.getInt(params, "result_lamp_sx", -1);
            int ly = ParamUtils.getInt(params, "result_lamp_sy", -1);
            int lw = ParamUtils.getInt(params, "result_lamp_w", 80);
            int lh = ParamUtils.getInt(params, "result_lamp_h", 50);
            if (lx >= 0 && ly >= 0) {
                BufferedImage lampCrop = safeSubimage(shot, lx, ly, lw, lh);
                if (lampCrop != null) {
                    int targetH = Math.min(ROW_HEIGHT - PADDING, lh);
                    int targetW = lw * targetH / lh;
                    Image scaled = lampCrop.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                    g.drawImage(scaled, curX, rowY + PADDING / 2, null);
                    curX += targetW + PADDING;
                }
            }

            // --- Title + metadata text on the right side ---
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE_SMALL));
            g.setColor(Color.BLACK);
            LocalDateTime date = p.getDate();
            String dateStr = date != null ? date.format(DISPLAY_FMT) : "----";
            String diffStr = p.getDiff() >= 0 ? "+" + p.getDiff() : String.valueOf(p.getDiff());
            g.drawString(p.getTitle(), curX, rowY + PADDING + g.getFontMetrics().getAscent());
            g.drawString(p.getDifficulty().toUpperCase() + "  " + diffStr + "  " + dateStr, curX,
                    rowY + PADDING + g.getFontMetrics().getAscent() * 2 + 2);

            return true;
        } catch (IOException e) {
            log.debug("Could not load screenshot for summary: {}", e.getMessage());
            return false;
        }
    }

    /** Draws a simple text-only play strip. */
    private void drawPlayTextOnly(Graphics2D g, FontMetrics fm, OnePlayData p, int rowY) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, FONT_SIZE));
        g.setColor(Color.BLACK);
        String score = ScoreFormatter.formatScore(p.getCurScore());
        String diffStr = p.getDiff() >= 0 ? "+" + p.getDiff() : String.valueOf(p.getDiff());
        LocalDateTime date = p.getDate();
        String dateStr = date != null ? date.format(DISPLAY_FMT) : "----";
        String text = String.format("%-40s  %s  [%s]  %s  %s  (%s)", p.getTitle(), score,
                p.getDifficulty().toUpperCase(), p.getLamp() != null ? p.getLamp().toUpperCase() : "—", diffStr,
                dateStr);
        g.drawString(text, PADDING, rowY + PADDING + fm.getAscent());
    }

    /**
     * Safely crops a region from an image, returning {@code null} if out of bounds.
     */
    private static BufferedImage safeSubimage(BufferedImage img, int x, int y, int w, int h) {
        if (x < 0 || y < 0 || x + w > img.getWidth() || y + h > img.getHeight()) {
            log.debug("safeSubimage: invalid region (x={}, y={}, w={}, h={}) for image {}x{}, skipping", x, y, w, h,
                    img.getWidth(), img.getHeight());
            return null;
        }
        return img.getSubimage(x, y, w, h);
    }

    private static Color lampColor(String lamp) {
        if (lamp == null) {
            return Color.LIGHT_GRAY;
        }
        return switch (lamp.toLowerCase()) {
            case "puc" -> new Color(0xFF, 0xFF, 0x66);
            case "uc" -> new Color(0xFF, 0xAA, 0xAA);
            case "hard" -> new Color(0xFF, 0xCC, 0xFF);
            case "clear" -> new Color(0x77, 0xFF, 0x77);
            default -> new Color(0xAA, 0xAA, 0xAA);
        };
    }

    private static Color diffColor(String difficulty) {
        if (difficulty == null) {
            return Color.GRAY;
        }
        return switch (difficulty.toLowerCase()) {
            case "nov" -> new Color(0x89, 0xA2, 0xD0);
            case "adv" -> new Color(0xFF, 0xBF, 0x00);
            case "exh" -> new Color(0xE5, 0x46, 0x46);
            case "mxm" -> new Color(0xFF, 0xFF, 0xFF);
            case "inf" -> new Color(0xFF, 0x66, 0xFF);
            case "grv" -> new Color(0xFF, 0x99, 0x33);
            case "hvn" -> new Color(0x77, 0xDD, 0xFF);
            case "vvd" -> new Color(0xFF, 0x33, 0x99);
            case "xcd" -> new Color(0xAA, 0xFF, 0xAA);
            default -> Color.GRAY;
        };
    }
}
