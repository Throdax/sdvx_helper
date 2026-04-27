package com.sdvxhelper.ocr;

import java.awt.image.BufferedImage;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around Tess4J's {@link Tesseract} engine for recognising text
 * from image crops.
 *
 * <p>
 * Used by the OCR Reporter to auto-populate the song-title field from the
 * result-screen info strip, matching the Python {@code ocr_reporter.py}
 * Tesseract integration.
 * </p>
 *
 * <p>
 * Tesseract data files must be present on the classpath or at the path
 * configured via the {@code TESSDATA_PREFIX} system property. If Tesseract is
 * unavailable, {@link #recognizeText(BufferedImage)} returns an empty string
 * and logs a warning rather than throwing.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class TesseractOcr {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcr.class);

    private final Tesseract tess;

    /**
     * Constructs a {@code TesseractOcr} instance with default configuration.
     *
     * <p>
     * The Tesseract data directory is resolved (in priority order) from:
     * </p>
     * <ol>
     * <li>The {@code TESSDATA_PREFIX} system property</li>
     * <li>{@code resources/tessdata} relative to the working directory</li>
     * </ol>
     */
    public TesseractOcr() {
        tess = new Tesseract();
        String dataPath = System.getProperty("TESSDATA_PREFIX", "resources/tessdata");
        tess.setDatapath(dataPath);
        tess.setLanguage("jpn+eng");
        tess.setPageSegMode(7);
        tess.setOcrEngineMode(1);
    }

    /**
     * Recognises text from the given image region.
     *
     * @param image
     *            pre-cropped image to analyse
     * @return recognised text with leading/trailing whitespace trimmed, or an empty
     *         string if recognition fails
     */
    public String recognizeText(BufferedImage image) {
        if (image == null) {
            return "";
        }
        try {
            String result = tess.doOCR(image);
            return result != null ? result.strip() : "";
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed: {}", e.getMessage());
            return "";
        }
    }
}
