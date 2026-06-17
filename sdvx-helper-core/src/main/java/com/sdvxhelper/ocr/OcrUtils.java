package com.sdvxhelper.ocr;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Stateless image pre-processing utilities shared across all OCR pipelines.
 *
 * <p>
 * Extracted from {@code OcrReporterHelper} (ocr-reporter-app) so that
 * {@code sdvx-helper-app} modules can use the same preprocessing without
 * creating a cross-module dependency on the reporter UI layer.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class OcrUtils {

    /**
     * Utility class — not meant to be instantiated.
     */
    private OcrUtils() {
    }

    /**
     * Pre-processes an image crop for Tesseract OCR using a 3× scale factor.
     *
     * <p>
     * Delegates to {@link #preprocessForOcr(BufferedImage, int)} with
     * {@code scaleFactor = 3}, which is appropriate for most SDVX UI crops. Use the
     * overload directly when a larger scale is needed (e.g. the VF badge, which
     * benefits from 5×).
     * </p>
     *
     * @param src
     *            source image to pre-process
     * @return pre-processed image ready for Tesseract, or {@code null} if
     *         {@code src} is {@code null}
     */
    public static BufferedImage preprocessForOcr(BufferedImage src) {
        return preprocessForOcr(src, 3);
    }

    /**
     * Pre-processes an image crop for Tesseract OCR with a caller-specified scale
     * factor.
     *
     * <p>
     * Three steps are applied in order:
     * </p>
     * <ol>
     * <li><b>Scale up {@code scaleFactor}×</b> with bicubic interpolation —
     * Tesseract accuracy improves significantly when characters are at least 60–90
     * px tall. Use a higher factor (e.g. 5) for small crops such as the VF
     * badge.</li>
     * <li><b>Invert + threshold</b> — SDVX screens use light text on a dark
     * background; Tesseract prefers dark text on a white background, so bright
     * pixels (text) become black and dark pixels (background) become white.</li>
     * <li><b>White padding</b> (20 px on every side) — Tesseract needs a small
     * margin around text to avoid clipping ascenders/descenders.</li>
     * </ol>
     *
     * @param src
     *            source image to pre-process
     * @param scaleFactor
     *            integer scale multiplier applied to both dimensions before
     *            thresholding; must be &gt; 0
     * @return pre-processed image ready for Tesseract, or {@code null} if
     *         {@code src} is {@code null}
     */
    public static BufferedImage preprocessForOcr(BufferedImage src, int scaleFactor) {
        if (src == null) {
            return null;
        }

        int scaledW = src.getWidth() * scaleFactor;
        int scaledH = src.getHeight() * scaleFactor;
        BufferedImage scaled = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB);
        Graphics2D gScale = scaled.createGraphics();
        gScale.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gScale.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gScale.drawImage(src, 0, 0, scaledW, scaledH, null);
        gScale.dispose();

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
}
