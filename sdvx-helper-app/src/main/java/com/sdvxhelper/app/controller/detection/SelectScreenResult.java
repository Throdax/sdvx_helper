package com.sdvxhelper.app.controller.detection;

import com.sdvxhelper.model.OnePlayData;

/**
 * Data returned by
 * {@link ScreenHandler#handleSelectScreen(java.awt.image.BufferedImage)}.
 * Contains the identified song title/difficulty and an optional play that was
 * automatically imported when the {@code import_from_select} setting is
 * enabled.
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SelectScreenResult {

    private String title;
    private String diff;
    private OnePlayData importedPlay;

    /**
     * @param title
     *            identified song title
     * @param diff
     *            identified difficulty string
     * @param importedPlay
     *            play imported from the select screen, or {@code null}
     */
    public SelectScreenResult(String title, String diff, OnePlayData importedPlay) {
        this.title = title;
        this.diff = diff;
        this.importedPlay = importedPlay;
    }

    public String getTitle() {
        return title;
    }

    public String getDiff() {
        return diff;
    }

    public OnePlayData getImportedPlay() {
        return importedPlay;
    }
}
