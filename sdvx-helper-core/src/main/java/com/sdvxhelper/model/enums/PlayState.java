package com.sdvxhelper.model.enums;

/**
 * Represents the player's activity state reported to Discord Rich Presence.
 *
 * <p>Maps to the Python {@code PlayStates} enum in {@code discord_presence.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public enum PlayState {

    /** Player is browsing the song-select screen. */
    SELECTING,

    /** Player is actively playing a song. */
    PLAYING,

    /** Player is viewing the result screen after a play. */
    RESULT,

    /** Application is idle (not connected to OBS or game not running). */
    IDLE
}
