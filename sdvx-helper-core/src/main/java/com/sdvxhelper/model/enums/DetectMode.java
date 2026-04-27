package com.sdvxhelper.model.enums;

/**
 * Represents the current game-state detected from the OBS capture feed.
 *
 * <p>
 * Maps to the Python {@code detect_mode} enum defined in
 * {@code sdvxh_classes.py}. The detection loop transitions through these states
 * as images are analysed.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public enum DetectMode {

    /** Detection not yet started or image not recognised. */
    INIT,

    /**
     * The song-commit (confirm) screen is visible — song has been chosen but play
     * not yet started.
     */
    DETECT,

    /** The song-select screen is currently visible. */
    SELECT,

    /** A song is actively being played. */
    PLAY,

    /** The result screen is displayed after a play. */
    RESULT
}
