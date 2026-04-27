package com.sdvxhelper.app.controller;

import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final String STOP_PREFIX = "[STOP]";
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    private OcrReporterHelper() {
        // utility class — not instantiable
    }

    /**
     * Extracts the last sequence of digits from an OCR text string. Returns
     * {@code "??"} when {@code text} is blank or contains no digits.
     *
     * <p>
     * If the text starts with {@code "[STOP]"} that prefix is stripped before
     * searching for digits.
     * </p>
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
     * pattern ({@code sdvx_*}) used by the main application, indicating it was
     * auto-saved from the result screen.
     *
     * @param filename
     *            file base name to test
     * @return {@code true} if the filename starts with {@code sdvx_}
     *         (case-insensitive)
     */
    public static boolean isResultFilename(String filename) {
        return filename != null && filename.toLowerCase().startsWith("sdvx_");
    }

    /**
     * Crops a region from {@code src} and scales it to the requested output size,
     * clamping the crop rectangle to image bounds to avoid exceptions.
     *
     * @param src
     *            source image
     * @param x
     *            crop origin x (clamped to image bounds)
     * @param y
     *            crop origin y (clamped to image bounds)
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
}
