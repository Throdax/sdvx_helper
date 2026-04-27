package com.sdvxhelper.app.controller.model;

/**
 * View model for one row in the rival scores side-table.
 *
 * <p>
 * Holds a rival player's name, best score, and clear lamp for the currently
 * selected chart. Used by the {@link javafx.scene.control.TableView} binding in
 * {@code ScoreViewerController}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class RivalScoreRow {

    private final String rivalName;
    private final int score;
    private final String lamp;

    /**
     * Constructs a row for one rival score entry.
     *
     * @param rivalName
     *            the rival player's display name
     * @param score
     *            the rival's best score for the chart
     * @param lamp
     *            the clear lamp string (e.g. {@code "puc"}, {@code "clear"})
     */
    public RivalScoreRow(String rivalName, int score, String lamp) {
        this.rivalName = rivalName;
        this.score = score;
        this.lamp = lamp;
    }

    /**
     * Returns the rival player's display name.
     *
     * @return rival name
     */
    public String getRivalName() {
        return rivalName;
    }

    /**
     * Returns the rival's best score for this chart.
     *
     * @return score (0–10 000 000)
     */
    public int getScore() {
        return score;
    }

    /**
     * Returns the clear lamp string.
     *
     * @return lamp string
     */
    public String getLamp() {
        return lamp;
    }
}
