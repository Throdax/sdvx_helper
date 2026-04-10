package com.sdvxhelper.model.enums;

/**
 * Represents the active screen / panel shown in the main application window.
 *
 * <p>Maps to the Python {@code gui_mode} enum defined in {@code sdvxh_classes.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public enum GuiMode {

    /** Initial splash / loading state before the main UI is ready. */
    INIT,

    /** The main detection and monitoring panel. */
    MAIN,

    /** The application settings panel. */
    SETTING,

    /** The OBS source/scene control panel. */
    OBS_CONTROL,

    /** The Discord / webhook configuration panel. */
    WEBHOOK,

    /** The Google Drive integration panel. */
    GOOGLEDRIVE
}
