package com.sdvxhelper.app.controller;

/**
 * Listener interface that {@link MigrationService} calls back on the JavaFX
 * Application Thread to report migration progress to the UI.
 *
 * <p>
 * All methods are invoked on the JavaFX Application Thread so that the
 * controller can safely update {@link javafx.scene.control.ProgressBar},
 * {@link javafx.scene.control.Label}, and {@link javafx.scene.control.TextArea}
 * nodes without explicit {@link javafx.application.Platform#runLater} wrappers
 * in the implementation.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public interface MigrationCallback {

    /**
     * Called when a migration step is about to start, allowing the UI to update the
     * progress bar and the current-step label.
     *
     * @param step
     *            the step that is starting
     * @param stepIndex
     *            the zero-based index of this step
     * @param totalSteps
     *            the total number of steps in the migration sequence
     */
    void onStepStarted(MigrationStep step, int stepIndex, int totalSteps);

    /**
     * Called whenever a line of diagnostic output is available so the UI can append
     * it to its log text area.
     *
     * @param message
     *            the log line to display; never {@code null}
     */
    void onLog(String message);

    /**
     * Called once the entire migration sequence has finished, whether successfully
     * or with errors.
     *
     * @param success
     *            {@code true} if all mandatory steps completed without fatal
     *            errors; {@code false} if one or more steps could not be completed
     */
    void onComplete(boolean success);
}
