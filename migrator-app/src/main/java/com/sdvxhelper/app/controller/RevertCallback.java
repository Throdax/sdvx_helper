package com.sdvxhelper.app.controller;

/**
 * Listener interface that {@link RevertService} calls back on the JavaFX
 * Application Thread to report revert progress to the UI.
 *
 * <p>
 * All methods are invoked on the JavaFX Application Thread so that the
 * controller can safely update UI nodes without additional
 * {@link javafx.application.Platform#runLater} wrappers.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public interface RevertCallback {

    /**
     * Called whenever a line of diagnostic output is available so the UI can append
     * it to its log text area.
     *
     * @param message
     *            the log line to display; never {@code null}
     */
    void onLog(String message);

    /**
     * Called once the revert operation has finished, whether successfully or with
     * errors.
     *
     * @param success
     *            {@code true} if the revert completed without fatal errors;
     *            {@code false} if a fatal I/O error occurred
     */
    void onComplete(boolean success);
}
