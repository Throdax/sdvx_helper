package com.sdvxhelper.app.controller.factories;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TableRow;

import com.sdvxhelper.app.controller.ScoreViewerController;
import com.sdvxhelper.model.MusicInfo;

/**
 * Custom TableRow for the score viewer table, which colors rows based on the
 * selected color mode (difficulty or lamp).
 *
 * <p>
 * When a row is selected, a 4-pixel blue stripe is painted on the left edge
 * using layered JavaFX backgrounds. Cells are forced transparent via
 * {@link Platform#runLater} so the row background shows through instead of
 * Modena's default selected-cell blue.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreRowFactory extends TableRow<MusicInfo> {

    private static final String SELECTION_COLOR = "#1565c0";

    private ScoreViewerController scoreViewerController;

    public ScoreRowFactory(ScoreViewerController scoreViewerController) {
        this.scoreViewerController = scoreViewerController;
        selectedProperty().addListener((obs, oldVal, newVal) -> refreshStyle());
        hoverProperty().addListener((obs, oldVal, newVal) -> refreshStyle());
    }

    @Override
    protected void updateItem(MusicInfo item, boolean empty) {
        super.updateItem(item, empty);
        refreshStyle();
    }

    private void refreshStyle() {
        MusicInfo item = getItem();
        if (item == null || isEmpty()) {
            setStyle("");
            Platform.runLater(this::clearCellStyles);
            return;
        }
        String style = computeStyle(item);
        setStyle(style);
        if (style.isEmpty()) {
            Platform.runLater(this::clearCellStyles);
        } else {
            Platform.runLater(this::applyTransparentCells);
        }
    }

    /**
     * Clears any inline cell style, restoring Modena's default behaviour.
     */
    private void clearCellStyles() {
        for (Node child : getChildrenUnmodifiable()) {
            child.setStyle("");
        }
    }

    /**
     * Makes every child cell transparent so the row's background colour
     * (including the selection stripe) shows through unobstructed.
     */
    private void applyTransparentCells() {
        for (Node child : getChildrenUnmodifiable()) {
            child.setStyle("-fx-background-color: transparent; -fx-text-fill: inherit;");
        }
    }

    private String computeStyle(MusicInfo item) {
        String mode = scoreViewerController.getColorModeCombo().getValue();
        if (mode == null || "None".equals(mode)) {
            return "";
        }

        String baseColor = null;
        String textFill = "black";

        if ("By Difficulty".equals(mode)) {
            String diff = item.getDifficulty() == null ? "" : item.getDifficulty().toLowerCase();
            switch (diff) {
                case "nov" -> { baseColor = "#7979D4"; textFill = "white"; }
                case "adv" -> { baseColor = "#E8B81C"; textFill = "white"; }
                case "exh" -> { baseColor = "#BD5E5E"; textFill = "white"; }
                case "mxm", "inf", "grv", "hvn", "vvd", "xcd" -> { baseColor = "#D6D6D6"; textFill = "white"; }
                default -> { }
            }
        } else if ("By Lamp".equals(mode)) {
            String lamp = item.getBestLamp() == null ? "" : item.getBestLamp().toLowerCase();
            switch (lamp) {
                case "puc"    -> { baseColor = "#ffff66"; textFill = "black"; }
                case "uc"     -> { baseColor = "#ffaaaa"; textFill = "black"; }
                case "exh"    -> { baseColor = "#ddaaff"; textFill = "black"; }
                case "hard"   -> { baseColor = "#ffccff"; textFill = "black"; }
                case "clear"  -> { baseColor = "#77ff77"; textFill = "black"; }
                case "failed" -> { baseColor = "#aaaaaa"; textFill = "black"; }
                default -> { }
            }
        }

        if (baseColor == null) {
            return "";
        }

        if (isSelected()) {
            return "-fx-background-color: " + SELECTION_COLOR + ", " + baseColor + "; "
                    + "-fx-background-insets: 0, 0 0 0 4; "
                    + "-fx-text-fill: " + textFill + ";";
        }
        return "-fx-background-color: " + baseColor + "; -fx-text-fill: " + textFill + ";";
    }

}
