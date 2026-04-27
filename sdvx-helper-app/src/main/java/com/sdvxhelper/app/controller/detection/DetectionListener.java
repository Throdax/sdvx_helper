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
     * Called when the currently selected song title or difficulty changes.
     *
     * @param title
     *            song title
     * @param diff
     *            difficulty string (e.g. "exh")
     */
    void onTitleAndDiffChanged(String title, String diff);

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
}
