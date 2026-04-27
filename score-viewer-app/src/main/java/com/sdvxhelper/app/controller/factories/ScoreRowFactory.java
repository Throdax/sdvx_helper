package com.sdvxhelper.app.controller.factories;

import javafx.scene.control.TableRow;

import com.sdvxhelper.app.controller.ScoreViewerController;
import com.sdvxhelper.model.MusicInfo;

/**
 * Custom TableRow for the score viewer table, which colors rows based on the
 * selected color mode (difficulty or lamp).
 * 
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreRowFactory extends TableRow<MusicInfo> {

    private ScoreViewerController scoreViewerController;

    public ScoreRowFactory(ScoreViewerController scoreViewerController) {
        this.scoreViewerController = scoreViewerController;
    }

    @Override
    protected void updateItem(MusicInfo item, boolean empty) {
        super.updateItem(item, empty);
        setStyle(item == null || empty ? "" : computeStyle(item));
    }

    private String computeStyle(MusicInfo item) {
        String mode = scoreViewerController.getColorModeCombo().getValue();
        if (mode == null || "None".equals(mode)) {
            return "";
        }

        if ("By Difficulty".equals(mode)) {
            String diff = item.getDifficulty() == null ? "" : item.getDifficulty().toLowerCase();
            return switch (diff) {
                case "nov" -> "-fx-background-color: #7979D4; -fx-text-fill: white;";
                case "adv" -> "-fx-background-color: #E8B81C; -fx-text-fill: white;";
                case "exh" -> "-fx-background-color: #BD5E5E; -fx-text-fill: white;";
                case "mxm", "inf", "grv", "hvn", "vvd", "xcd" -> "-fx-background-color: #D6D6D6; -fx-text-fill: white;";
                default -> "";
            };
        }
        if ("By Lamp".equals(mode)) {
            String lamp = item.getBestLamp() == null ? "" : item.getBestLamp().toLowerCase();
            return switch (lamp) {
                case "puc" -> "-fx-background-color: #ffff66; -fx-text-fill: black;";
                case "uc" -> "-fx-background-color: #ffaaaa; -fx-text-fill: black;";
                case "hard" -> "-fx-background-color: #ffccff; -fx-text-fill: black;";
                case "clear" -> "-fx-background-color: #77ff77; -fx-text-fill: black;";
                case "failed" -> "-fx-background-color: #aaaaaa; -fx-text-fill: black;";
                default -> "";
            };
        }
        return "";
    }

}
