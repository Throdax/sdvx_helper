package com.sdvxhelper.service;

/**
 * Thrown by
 * {@link ImageAnalysisService#detectDifficultyFromBand(java.awt.image.BufferedImage)}
 * when the supplied crop image cannot be analysed:
 * <ul>
 * <li>{@code diffBand} is {@code null}.</li>
 * <li>The image dimensions are too small to contain meaningful colour
 * data.</li>
 * </ul>
 *
 * <p>
 * Whether the frame originates from a genuine result screen is determined
 * separately by {@link ImageAnalysisService#isResultScreen}, which mirrors the
 * Python {@code GenSummary.is_result()} method. Callers must invoke
 * {@code isResultScreen} before passing any crop to
 * {@code detectDifficultyFromBand}.
 * </p>
 *
 * <p>
 * An unrecognised difficulty colour (one that does not match NOVICE, ADVANCED,
 * or EXHAUST) is <em>not</em> an error: the method returns {@code "APPEND"} in
 * that case, mirroring the Python {@code gen_summary.py} fall-through.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ImageCropNotParsed extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs the exception with a descriptive message.
     *
     * @param message
     *            human-readable description of why the crop could not be parsed
     */
    public ImageCropNotParsed(String message) {
        super(message);
    }

    /**
     * Constructs the exception with a descriptive message and an underlying cause.
     *
     * @param message
     *            human-readable description of why the crop could not be parsed
     * @param cause
     *            underlying exception that triggered this failure
     */
    public ImageCropNotParsed(String message, Throwable cause) {
        super(message, cause);
    }
}
