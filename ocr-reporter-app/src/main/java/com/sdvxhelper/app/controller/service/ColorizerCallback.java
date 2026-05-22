package com.sdvxhelper.app.controller.service;

import java.io.File;

/**
 * Callback interface for receiving incremental progress and results from
 * {@link ColorizerService#colorize(java.util.List, boolean, ColorizerCallback)}.
 *
 * <p>
 * All methods are called from the background thread that runs the colorize
 * loop. Implementations that update JavaFX controls must delegate to
 * {@code Platform.runLater}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public interface ColorizerCallback {

    /**
     * Invoked periodically during the colorize loop to report progress.
     *
     * @param current
     *            number of files processed so far
     * @param total
     *            total number of files
     * @param statusMessage
     *            human-readable progress description
     */
    void onProgress(int current, int total, String statusMessage);

    /**
     * Invoked for each file whose row color has been determined.
     *
     * @param filename
     *            file base name
     * @param cssStyle
     *            JavaFX inline style string to apply to the row
     */
    void onFileColorized(String filename, String cssStyle);

    /**
     * Invoked when a file has been successfully renamed.
     *
     * @param fileIndex
     *            index of the file in the original snapshot list
     * @param newFile
     *            the renamed {@link File}
     */
    void onFileRenamed(int fileIndex, File newFile);

    /**
     * Invoked to append a line to the application log.
     *
     * @param message
     *            log line (no trailing newline required)
     */
    void onLog(String message);

    /**
     * Invoked once when the colorize run finishes.
     *
     * @param found
     *            number of files whose hash was found in the music list
     * @param notFound
     *            number of files whose hash was not found
     * @param elapsedSeconds
     *            wall-clock duration of the run
     */
    void onComplete(int found, int notFound, double elapsedSeconds);
}
