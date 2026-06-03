package com.sdvxhelper.app.controller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.ImageCropNotParsed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-logic helpers for {@link OcrReporterController}.
 *
 * <p>
 * Extracted from {@link OcrReporterController} to allow unit-testing without
 * JavaFX. All methods are stateless.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class OcrReporterHelper {

    private static final Logger log = LoggerFactory.getLogger(OcrReporterHelper.class);

    private static final String STOP_PREFIX = "[STOP]";
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(\\d{8}_\\d{6})");

    /**
     * Pattern for an <em>unprocessed</em> result screenshot:
     * {@code sdvx_YYYYMMDD_HHMMSS.png}. These are the raw auto-save files that have
     * not yet been renamed with a title.
     */
    private static final Pattern UNPROCESSED_PATTERN = Pattern.compile("^sdvx_\\d{8}_\\d{6}\\.png$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Utility class — not meant to be instantiated.
     */
    private OcrReporterHelper() {
    }

    /**
     * Extracts the last sequence of digits from an OCR text string. Returns
     * {@code "??"} when {@code text} is blank or contains no digits.
     *
     * @param text
     *            OCR-recognized text, may be {@code null}
     * @return last digit sequence found, or {@code "??"}
     */
    public static String lastDigits(String text) {
        if (text == null || text.isBlank()) {
            return "??";
        }
        String clean = text.startsWith(STOP_PREFIX) ? text.substring(STOP_PREFIX.length()).trim() : text;
        Matcher m = DIGIT_PATTERN.matcher(clean);
        String last = "??";
        while (m.find()) {
            last = m.group();
        }
        return last;
    }

    /**
     * Returns {@code true} if {@code filename} matches the result screenshot naming
     * pattern ({@code sdvx_*}) used by the main application.
     *
     * @param filename
     *            file base name to test
     * @return {@code true} if the filename starts with {@code sdvx_}
     */
    public static boolean isResultFilename(String filename) {
        return filename != null && filename.toLowerCase().startsWith("sdvx_");
    }

    /**
     * Returns {@code true} if {@code filename} is an <em>unprocessed</em> result
     * screenshot matching {@code sdvx_YYYYMMDD_HHMMSS.png}.
     *
     * @param filename
     *            file base name to test
     * @return {@code true} if the filename is an unprocessed auto-save file
     */
    public static boolean isUnprocessedResultFilename(String filename) {
        return filename != null && UNPROCESSED_PATTERN.matcher(filename).matches();
    }

    /**
     * Strips characters that are illegal in Windows/Unix filenames and replaces
     * spaces with underscores.
     *
     * @param title
     *            raw song title
     * @return filename-safe version of the title, or an empty string if
     *         {@code title} is {@code null}
     */
    public static String sanitizeForFilename(String title) {
        if (title == null) {
            return "";
        }
        String t = title;
        for (char ch : new char[]{'\\', '/', ':', '*', '?', '"', '<', '>', '|'}) {
            t = t.replace(String.valueOf(ch), "");
        }
        t = t.replace(' ', '_').replace('\u3000', '_');
        return t;
    }

    /**
     * Crops a region from {@code src} and scales it to the requested output size,
     * clamping the crop rectangle to image bounds.
     *
     * @param src
     *            source image
     * @param x
     *            crop origin x (clamped)
     * @param y
     *            crop origin y (clamped)
     * @param w
     *            crop width (clamped)
     * @param h
     *            crop height (clamped)
     * @param outW
     *            output width
     * @param outH
     *            output height
     * @return cropped (and optionally scaled) image
     */
    public static BufferedImage cropAndScale(BufferedImage src, int x, int y, int w, int h, int outW, int outH) {
        int sx = Math.max(0, Math.min(x, src.getWidth() - 1));
        int sy = Math.max(0, Math.min(y, src.getHeight() - 1));
        int sw = Math.max(1, Math.min(w, src.getWidth() - sx));
        int sh = Math.max(1, Math.min(h, src.getHeight() - sy));
        BufferedImage cropped = src.getSubimage(sx, sy, sw, sh);
        if (sw == outW && sh == outH) {
            return cropped;
        }
        BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
        scaled.createGraphics().drawImage(cropped.getScaledInstance(outW, outH, java.awt.Image.SCALE_SMOOTH), 0, 0,
                null);
        return scaled;
    }

    /**
     * Saves a cropped image to {@code out/part_{partName}.png} for debug
     * inspection, mirroring the Python {@code cut_result_parts} save loop.
     *
     * @param img
     *            the cropped region to persist
     * @param partName
     *            label used in the filename (e.g. {@code "jacket"})
     */
    public static void saveDebugPart(BufferedImage img, String partName) {
        if (img == null) {
            return;
        }
        try {
            File outDir = new File("out");
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            File outFile = new File(outDir, "part_" + partName + ".png");
            ImageIO.write(img, "PNG", outFile);
            log.debug("saveDebugPart: wrote '{}'", outFile.getPath());
        } catch (IOException e) {
            log.warn("saveDebugPart: failed to save part '{}': {}", partName, e.getMessage());
        }
    }

    /**
     * Extracts a difficulty token from a processed result filename.
     *
     * @param filename
     *            file base name to inspect
     * @return lower-case difficulty token or {@code null} if not found
     */
    public static String parseDifficultyFromFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String upper = filename.toUpperCase();
        if (upper.contains("_APPEND_")) {
            return "APPEND";
        }
        if (upper.contains("_EXH_")) {
            return "exh";
        }
        if (upper.contains("_ADV_")) {
            return "adv";
        }
        if (upper.contains("_NOV_")) {
            return "nov";
        }
        return null;
    }

    /**
     * Converts an integer score to the filename prefix: removes the trailing four
     * digits so {@code 9970000} becomes {@code "997"}.
     *
     * @param score
     *            detected score (0–10 000 000)
     * @return score prefix string
     */
    public static String toScorePrefix(int score) {
        String s = String.valueOf(score);
        return s.length() > 4 ? s.substring(0, s.length() - 4) : s;
    }

    /**
     * Extracts the {@code YYYYMMDD_HHMMSS} timestamp from a result filename. Falls
     * back to the current time if the pattern is not found.
     *
     * @param filename
     *            file base name such as {@code sdvx_20260512_185427.png}
     * @return timestamp string of the form {@code YYYYMMDD_HHMMSS}
     */
    public static String extractTimestampFromFilename(String filename) {
        Matcher m = TIMESTAMP_PATTERN.matcher(filename);
        if (m.find()) {
            return m.group(1);
        }
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    /**
     * Pre-processes a title crop for Tesseract OCR.
     *
     * <p>
     * Three steps are applied in order:
     * </p>
     * <ol>
     * <li><b>Scale up 3x</b> with bicubic interpolation — Tesseract accuracy
     * improves significantly when characters are at least 60–90 px tall.</li>
     * <li><b>White padding</b> (20 px on every side) — Tesseract needs a small
     * margin around text to avoid clipping ascenders/descenders.</li>
     * <li><b>Invert + threshold</b> — SDVX result screens use light text on a dark
     * background; Tesseract prefers dark text on a white background, so the image
     * is inverted and binarised with a 50% threshold.</li>
     * </ol>
     *
     * @param src
     *            source image (typically the {@code title_small} crop)
     * @return pre-processed image ready for Tesseract, or {@code null} if
     *         {@code src} is {@code null}
     */
    public static BufferedImage preprocessForOcr(BufferedImage src) {
        if (src == null) {
            return null;
        }

        // Step 1: scale up 3x with bicubic interpolation
        int scaledW = src.getWidth() * 3;
        int scaledH = src.getHeight() * 3;
        BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB);
        Graphics2D gScale = scaled.createGraphics();
        gScale.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gScale.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gScale.drawImage(src, 0, 0, scaledW, scaledH, null);
        gScale.dispose();

        // Step 2: convert to grayscale then invert+threshold.
        // SDVX result screen has light text on a dark background.
        // Tesseract requires dark text on a white background, so bright pixels
        // (text) become black and dark pixels (background) become white.
        BufferedImage binary = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gGray = binary.createGraphics();
        gGray.drawImage(scaled, 0, 0, null);
        gGray.dispose();

        for (int y = 0; y < scaledH; y++) {
            for (int x = 0; x < scaledW; x++) {
                int grayVal = binary.getRaster().getSample(x, y, 0);
                int bw = grayVal > 128 ? 0 : 255;
                binary.getRaster().setSample(x, y, 0, bw);
            }
        }

        // Step 3: add white padding AFTER binarization so the border is white
        int pad = 20;
        int paddedW = scaledW + pad * 2;
        int paddedH = scaledH + pad * 2;
        BufferedImage padded = new BufferedImage(paddedW, paddedH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gPad = padded.createGraphics();
        gPad.setColor(Color.WHITE);
        gPad.fillRect(0, 0, paddedW, paddedH);
        gPad.drawImage(binary, pad, pad, null);
        gPad.dispose();

        return padded;
    }

    /**
     * Removes spaces that Tesseract inserts between individual CJK characters.
     *
     * <p>
     * Tesseract's LSTM engine treats each kanji/kana glyph as a separate token and
     * inserts a space after every character, producing output like
     * {@code "幻 想 プ ロ ミ ネ ン ス"} instead of {@code "幻想プロミネンス"}.
     * This method strips those spurious spaces by removing any whitespace that is
     * immediately preceded <em>and</em> followed by a CJK character, while leaving
     * legitimate spaces inside Latin-alphabet words or mixed-script titles intact.
     * </p>
     *
     * @param text
     *            raw Tesseract output, may be {@code null}
     * @return text with inter-CJK spaces removed, or the original value if
     *         {@code text} is {@code null}
     */
    public static String removeInterCjkSpaces(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll(
                "(?<=[\\u3040-\\u309F\\u30A0-\\u30FF\\u3000-\\u9FFF\\uF900-\\uFAFF])\\s+"
                        + "(?=[\\u3040-\\u309F\\u30A0-\\u30FF\\u3000-\\u9FFF\\uF900-\\uFAFF])",
                "");
    }

    /**
     * Delegates to
     * {@link ImageAnalysisService#detectDifficultyFromBand(BufferedImage)}.
     *
     * @param diffBand
     *            difficulty-band image
     * @return detected difficulty string
     * @throws ImageCropNotParsed
     *             when the band cannot be classified
     */
    public static String detectDifficultyFromBand(BufferedImage diffBand) throws ImageCropNotParsed {
        return ImageAnalysisService.detectDifficultyFromBand(diffBand);
    }
}
