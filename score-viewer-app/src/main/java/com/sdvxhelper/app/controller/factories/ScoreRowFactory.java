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
 * When a row is selected the background is darkened by 25% to indicate
 * selection without losing the color context. Cells are forced to a transparent
 * background via {@link Platform#runLater} so the row background always shows
 * through; text fill is applied directly to cells for the same reason (JavaFX
 * CSS inheritance does not propagate {@code -fx-text-fill} from row to cell
 * automatically).
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreRowFactory extends TableRow<MusicInfo> {

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
        String[] colorText = computeColorAndText(item);
        if (colorText == null) {
            setStyle("");
            Platform.runLater(this::clearCellStyles);
            return;
        }
        String bgColor = isSelected() ? darken(colorText[0]) : colorText[0];
        String textFill = colorText[1];
        setStyle("-fx-background-color: " + bgColor + ";");
        final String cellStyle = "-fx-background-color: transparent; -fx-text-fill: " + textFill + ";";
        Platform.runLater(() -> {
            for (Node child : getChildrenUnmodifiable()) {
                child.setStyle(cellStyle);
            }
        });
    }

    private void clearCellStyles() {
        for (Node child : getChildrenUnmodifiable()) {
            child.setStyle("");
        }
    }

    /**
     * Returns {@code [bgColor, textFill]} for the given item under the current
     * color mode, or {@code null} when no color should be applied.
     */
    private String[] computeColorAndText(MusicInfo item) {
        String mode = scoreViewerController.getColorModeCombo().getValue();
        if (mode == null || "None".equals(mode)) {
            return null;
        }
        if ("By Difficulty".equals(mode)) {
            String diff = item.getDifficulty() == null ? "" : item.getDifficulty().toLowerCase();
            return switch (diff) {
                case "nov" -> new String[]{"#7979D4", "white"};
                case "adv" -> new String[]{"#E8B81C", "black"};
                case "exh" -> new String[]{"#BD5E5E", "white"};
                case "mxm", "inf", "grv", "hvn", "vvd", "xcd" -> new String[]{"#D6D6D6", "black"};
                default -> null;
            };
        }
        if ("By Lamp".equals(mode)) {
            String lamp = item.getBestLamp() == null ? "" : item.getBestLamp().toLowerCase();
            return switch (lamp) {
                case "puc"    -> new String[]{"#ffff66", "black"};
                case "uc"     -> new String[]{"#ffaaaa", "black"};
                case "hard"   -> new String[]{"#ffccff", "black"};
                case "clear"  -> new String[]{"#77ff77", "black"};
                case "failed" -> new String[]{"#aaaaaa", "black"};
                default -> null;
            };
        }
        return null;
    }

    /**
     * Darkens a hex color string by 25% to signal row selection.
     *
     * @param hex
     *            six-digit hex color prefixed with {@code #}, e.g. {@code "#E8B81C"}
     * @return darkened hex color string
     */
    private static String darken(String hex) {
        int color = Integer.parseInt(hex.substring(1), 16);
        int r = (int) ((color >> 16 & 0xFF) * 0.75);
        int g = (int) ((color >> 8 & 0xFF) * 0.75);
        int b = (int) ((color & 0xFF) * 0.75);
        return String.format("#%02x%02x%02x", r, g, b);
    }

}
