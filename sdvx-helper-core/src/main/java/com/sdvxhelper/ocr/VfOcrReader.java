package com.sdvxhelper.ocr;

import java.awt.Rectangle;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a Volforce value ({@code DD.DDD}) from a preprocessed VF badge image by
 * combining two {@link TesseractOcr} passes over the same number region.
 *
 * <p>
 * Empirical testing against real captured badge images (see {@code VfOcrTest})
 * showed that Tesseract's default LSTM engine (OEM 1) correctly counts the
 * number of integer digits but frequently misreads one or more of the three
 * decimal digits (e.g. {@code "12.234"} read as {@code "12.349"}), while the
 * legacy engine (OEM 0) reliably reads the decimal digits and the decimal point
 * but systematically fails to recognise the SDVX VF font's leading {@code "1"}
 * glyph, dropping it entirely (e.g. {@code "12.234"} read as {@code "2.234"}).
 * Combining the LSTM engine's integer-digit count with the legacy engine's
 * decimal digits consistently reproduces the correct value on both samples.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class VfOcrReader {

    private static final Logger log = LoggerFactory.getLogger(VfOcrReader.class);

    /**
     * Matches a VF-formatted number: one or more integer digits, a dot, and exactly
     * three decimal digits.
     */
    private static final Pattern VF_NUMBER_PATTERN = Pattern.compile("(\\d+)\\.(\\d{3})");

    /**
     * Tesseract character whitelist restricting recognition to digits and the
     * decimal point.
     */
    private static final String DIGIT_WHITELIST = "0123456789.";

    /**
     * OEM 0 — legacy engine, reliable on decimal digits but drops the leading "1"
     * glyph.
     */
    private final TesseractOcr legacyEngine;

    /**
     * OEM 1 — LSTM engine, reliable on integer-digit count but prone to
     * decimal-digit confusion.
     */
    private final TesseractOcr lstmEngine;

    /**
     * Constructs a {@code VfOcrReader} using the default Tesseract data directory
     * resolution (see {@link TesseractOcr#TesseractOcr(String)}).
     */
    public VfOcrReader() {
        this.legacyEngine = buildEngine(null, 0);
        this.lstmEngine = buildEngine(null, 1);
    }

    /**
     * Constructs a {@code VfOcrReader} using an explicit Tesseract data directory.
     * Use this overload in tests where tessdata is downloaded to a known location
     * (e.g. {@code target/tessdata}) rather than the application's
     * {@code resources/tessdata} directory.
     *
     * @param tessdataDir
     *            path to the directory containing {@code .traineddata} files
     */
    public VfOcrReader(String tessdataDir) {
        this.legacyEngine = buildEngine(tessdataDir, 0);
        this.lstmEngine = buildEngine(tessdataDir, 1);
    }

    /**
     * Builds a single-purpose {@link TesseractOcr} engine configured with the
     * digit+dot whitelist, PSM 8 (single word), and the requested OCR engine mode.
     *
     * @param tessdataDir
     *            tessdata directory, or {@code null} to use the default resolution
     * @param ocrEngineMode
     *            Tesseract OEM constant ({@code 0} legacy, {@code 1} LSTM)
     * @return the configured engine
     */
    private TesseractOcr buildEngine(String tessdataDir, int ocrEngineMode) {
        TesseractOcr engine = tessdataDir == null ? new TesseractOcr("eng") : new TesseractOcr("eng", tessdataDir);
        engine.setVariable("tessedit_char_whitelist", DIGIT_WHITELIST);
        engine.setPageSegMode(8);
        engine.setOcrEngineMode(ocrEngineMode);
        return engine;
    }

    /**
     * Reads the VF number from the given region of a preprocessed badge image by
     * running both the legacy and LSTM engines and reconciling their results.
     *
     * <p>
     * If both engines find a match and the legacy engine's integer part is exactly
     * one digit shorter than the LSTM engine's, the legacy decimal digits are
     * combined with the LSTM integer digits (the font-specific leading-digit drop
     * case). Otherwise the legacy engine's full match is preferred, since it is the
     * more reliable engine for the decimal digits whenever its integer-digit count
     * already looks complete. If only one engine finds a match, that engine's
     * result is used; if neither does, {@code null} is returned.
     * </p>
     *
     * @param imageFile
     *            the preprocessed badge PNG file to analyse
     * @param numberRegion
     *            the rectangular sub-region containing only the VF number
     * @return the recognised value formatted as {@code DD.DDD}, or {@code null} if
     *         neither engine produced a value matching the expected format
     */
    public String read(File imageFile, Rectangle numberRegion) {
        String legacyText = restoreMissingDot(legacyEngine.recognizeText(imageFile, numberRegion));
        String lstmText = restoreMissingDot(lstmEngine.recognizeText(imageFile, numberRegion));
        Matcher legacyMatch = VF_NUMBER_PATTERN.matcher(legacyText);
        Matcher lstmMatch = VF_NUMBER_PATTERN.matcher(lstmText);
        boolean legacyFound = legacyMatch.find();
        boolean lstmFound = lstmMatch.find();

        if (legacyFound && lstmFound && legacyMatch.group(1).length() == lstmMatch.group(1).length() - 1) {
            log.debug(
                    "VF OCR: legacy engine dropped leading digit, combining LSTM integer part '{}' with legacy decimal part '{}'",
                    lstmMatch.group(1), legacyMatch.group(2));
            return lstmMatch.group(1) + "." + legacyMatch.group(2);
        }
        if (legacyFound) {
            return legacyMatch.group(1) + "." + legacyMatch.group(2);
        }
        if (lstmFound) {
            return lstmMatch.group(1) + "." + lstmMatch.group(2);
        }
        return null;
    }

    /**
     * Recovers a missing decimal point from a raw Tesseract result when the period
     * pixel was erased by the preprocessing binary-threshold step.
     *
     * <p>
     * VF numbers always follow the pattern {@code ##.###} (two integer digits) or
     * {@code #.###} (one integer digit). When Tesseract returns a pure digit string
     * with no period, the point can be unambiguously reinserted: a 5-digit string
     * becomes {@code ##.###} and a 4-digit string becomes {@code #.###}. If the raw
     * string already contains a period, is {@code null}, or does not match either
     * pure-digit shape, it is returned unchanged (trimmed).
     * </p>
     *
     * @param raw
     *            the raw string returned by Tesseract (may be {@code null})
     * @return the normalised string with the period restored where possible, or the
     *         trimmed input if no recovery was needed or possible; never
     *         {@code null}
     */
    private static String restoreMissingDot(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.matches("\\d{5}")) {
            return trimmed.substring(0, 2) + "." + trimmed.substring(2);
        }
        if (trimmed.matches("\\d{4}")) {
            return trimmed.substring(0, 1) + "." + trimmed.substring(1);
        }
        return trimmed;
    }
}
