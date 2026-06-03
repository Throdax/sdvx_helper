package com.sdvxhelper.app.controller.detection;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;

/**
 * Callback interface for events fired by {@link DetectionEngine} back to the
 * JavaFX controller. All methods are called on a background thread;
 * implementors must wrap UI updates in {@code Platform.runLater()}.
 *
 * @author Throdax
 * @since 2.0.0
 */
public interface DetectionListener {

    /**
     * Called when a new play has been recorded (result or select-import).
     *
     * @param play
     *            the newly recorded play
     */
    void onPlayRecorded(OnePlayData play);

    /**
     * Called when the detected game state changes.
     *
     * @param mode
     *            new detection mode
     */
    void onModeChanged(DetectMode mode);

    /**
     * Called when the OBS connection status changes.
     *
     * @param status
     *            human-readable status string
     */
    void onObsStatusChanged(String status);

    /**
     * Called when OBS begins recording or streaming.
     *
     * <p>
     * Called on a background thread; implementors must wrap UI updates in
     * {@code Platform.runLater()}.
     * </p>
     *
     * @param outputType
     *            {@code "Recording"} or {@code "Streaming"}
     */
    void onObsOutputStarted(String outputType);

    /**
     * Called when OBS stops recording or streaming.
     *
     * <p>
     * Called on a background thread; implementors must wrap UI updates in
     * {@code Platform.runLater()}.
     * </p>
     *
     * @param outputType
     *            {@code "Recording"} or {@code "Streaming"}
     */
    void onObsOutputStopped(String outputType);

    /**
     * Called after a result screen is processed, reporting which capture steps
     * succeeded. Mirrors Python {@code self.window['*_icon'].update(visible=True)}.
     *
     * <p>
     * Called on a background thread; implementors must wrap UI updates in
     * {@code Platform.runLater()}.
     * </p>
     *
     * @param screenshotSaved
     *            {@code true} if the result screenshot was saved to
     *            {@code autosave_dir}
     * @param summaryGenerated
     *            {@code true} if {@code out/summary_full.png} and
     *            {@code out/summary_small.png} were updated
     * @param vfCaptured
     *            {@code true} if {@code out/vf_cur.png} and
     *            {@code out/class_cur.png} were written
     */
    void onResultCaptured(boolean screenshotSaved, boolean summaryGenerated, boolean vfCaptured);
}
