package com.sdvxhelper.service;

/**
 * Thrown by
 * {@link ImageAnalysisService#detectDifficultyFromBand(java.awt.image.BufferedImage)}
 * when the difficulty band image is {@code null}, too small to analyse, or
 * produces RGB-channel sums that do not match any known difficulty colour
 * (NOVICE, ADVANCED, EXHAUST, or APPEND).
 *
 * <p>
 * The failed crop image is saved to {@code ./target/out/last_error_crop.png}
 * when running inside a test or IDE environment, and to
 * {@code ./out/last_error_crop.png} in a production deployment, so that the
 * operator can inspect the problematic region.
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
