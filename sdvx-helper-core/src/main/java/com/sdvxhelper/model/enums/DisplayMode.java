package com.sdvxhelper.model.enums;

/**
 * Controls what is displayed on the Discord Rich Presence status.
 *
 * <p>Maps to the Python {@code DisplayMode} enum in {@code discord_presence.py}.</p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public enum DisplayMode {

    /** Show the current song title and difficulty. */
    SONG,

    /** Show total Volforce and session statistics. */
    VOLFORCE,

    /** Show nothing (presence hidden / disabled). */
    NONE
}
