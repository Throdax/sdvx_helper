package com.sdvxhelper.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;

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
     * Constructs a {@code TesseractOcr} instance with the default language set
     * ({@code jpn+eng}).
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
        this("jpn+eng");
    }

    /**
     * Constructs a {@code TesseractOcr} instance with a custom Tesseract language
     * string.
     *
     * <p>
     * Use {@code +} to combine multiple language packs, e.g.
     * {@code "jpn+eng+fra+ell"}. The required {@code .traineddata} files must be
     * present in the tessdata directory; missing packs are logged as warnings by
     * Tesseract and the engine degrades gracefully.
     * </p>
     *
     * @param language
     *            Tesseract language string (e.g. {@code "jpn+eng+fra+ell"})
     */
    public TesseractOcr(String language) {
        this(language, System.getProperty("TESSDATA_PREFIX", "resources/tessdata"));
    }

    /**
     * Constructs a {@code TesseractOcr} instance with a custom language string and
     * an explicit tessdata directory path. Use this overload in tests where
     * tessdata is downloaded to a known location (e.g. {@code target/tessdata})
     * rather than the application's {@code resources/tessdata} directory.
     *
     * @param language
     *            Tesseract language string (e.g. {@code "eng"})
     * @param tessdataDir
     *            path to the directory containing {@code .traineddata} files
     */
    public TesseractOcr(String language, String tessdataDir) {
        tess = new Tesseract();
        tess.setDatapath(tessdataDir);
        tess.setLanguage(language);
        tess.setPageSegMode(7);
        tess.setOcrEngineMode(1);
    }

    /**
     * Sets a Tesseract engine variable (e.g. {@code "tessedit_char_whitelist"}).
     *
     * <p>
     * Must be called <em>before</em> {@link #recognizeText(BufferedImage)} for the
     * variable to take effect on the next recognition pass.
     * </p>
     *
     * @param name
     *            Tesseract variable name
     * @param value
     *            value to set
     */
    public void setVariable(String name, String value) {
        tess.setVariable(name, value);
    }

    /**
     * Overrides the Tesseract page segmentation mode (PSM).
     *
     * <p>
     * The constructor defaults to PSM 7 (treat the image as a single text line).
     * For crops that contain a single word or token with no spaces — such as the VF
     * badge ({@code DD.DDD}) — PSM 8 (single word) produces more reliable results.
     * Must be called before {@link #recognizeText(BufferedImage)}.
     * </p>
     *
     * @param mode
     *            Tesseract PSM constant (e.g. {@code 7} for single line, {@code 8}
     *            for single word)
     */
    public void setPageSegMode(int mode) {
        tess.setPageSegMode(mode);
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

    /**
     * Recognises text from a PNG file on disk. Passing a {@link File} instead of a
     * {@link BufferedImage} avoids raster-offset issues that occur when the image
     * was produced by {@link BufferedImage#getSubimage} — Tesseract reads the file
     * fresh and always gets a contiguous pixel buffer.
     *
     * @param imageFile
     *            PNG file to analyse; must exist and be readable
     * @return recognised text with leading/trailing whitespace trimmed, or an empty
     *         string if recognition fails or the file is {@code null}
     */
    public String recognizeText(File imageFile) {
        if (imageFile == null) {
            return "";
        }
        try {
            String result = tess.doOCR(imageFile);
            return result != null ? result.strip() : "";
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed for '{}': {}", imageFile.getName(), e.getMessage());
            return "";
        }
    }

    /**
     * Recognises text from a specific rectangular region of a PNG file on disk.
     * Tesseract applies OCR only to the given region, so the caller does not need
     * to produce a separate crop image. This is the preferred overload for
     * extracting a known sub-region (e.g. the VF number) from a larger preprocessed
     * image.
     *
     * @param imageFile
     *            PNG file to analyse; must exist and be readable
     * @param region
     *            the {@link Rectangle} (x, y, width, height) within the image to
     *            restrict OCR to
     * @return recognised text with leading/trailing whitespace trimmed, or an empty
     *         string if recognition fails or either argument is {@code null}
     */
    public String recognizeText(File imageFile, Rectangle region) {
        if (imageFile == null || region == null) {
            return "";
        }
        try {
            String result = tess.doOCR(imageFile, region);
            return result != null ? result.strip() : "";
        } catch (TesseractException e) {
            log.warn("Tesseract OCR failed for '{}' region {}: {}", imageFile.getName(), region, e.getMessage());
            return "";
        }
    }
}
