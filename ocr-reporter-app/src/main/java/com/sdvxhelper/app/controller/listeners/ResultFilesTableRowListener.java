package com.sdvxhelper.app.controller.listeners;

import java.io.File;
import javafx.scene.control.TableRow;

import com.sdvxhelper.app.controller.OcrReporterController;

/**
 * Custom {@link TableRow} for the saved-files table that applies per-row
 * colorization from {@link OcrReporterController#getFileColorMap()} while
 * correctly honouring the selection and hover states.
 *
 * <p>
 * Plain {@code setStyle()} calls have higher CSS specificity than any
 * pseudo-class rule in the external stylesheet, so a naïve implementation
 * silently overrides {@code :selected} and {@code :hover} colours. This class
 * resolves the conflict by re-evaluating the effective style whenever the item,
 * selection, or hover state changes and writing the correct colour as an inline
 * style, keeping the priority consistent.
 * </p>
 */
public class ResultFilesTableRowListener extends TableRow<File> {

    private static final String SELECTED_STYLE = "-fx-background-color: #bbdefb; -fx-background-insets: 0;";
    private static final String SELECTED_HOVER_STYLE = "-fx-background-color: #90caf9; -fx-background-insets: 0;";
    private static final String HOVER_STYLE = "-fx-background-color: #e3f2fd; -fx-background-insets: 0;";

    private final OcrReporterController ocrReporterController;

    /**
     * @param ocrReporterController
     *            controller that owns the file-colour map
     */
    public ResultFilesTableRowListener(OcrReporterController ocrReporterController) {
        this.ocrReporterController = ocrReporterController;
        selectedProperty().addListener((_, _, _) -> refreshStyle());
        hoverProperty().addListener((_, _, _) -> refreshStyle());
    }

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        refreshStyle();
    }

    private void refreshStyle() {
        String rowStyle;
        boolean propagateWhiteText = false;

        if (isSelected() && isHover()) {
            rowStyle = SELECTED_HOVER_STYLE;
        } else if (isSelected()) {
            rowStyle = SELECTED_STYLE;
        } else if (isHover()) {
            rowStyle = HOVER_STYLE;
        } else if (isEmpty() || getItem() == null) {
            rowStyle = "";
        } else {
            rowStyle = ocrReporterController.getFileColorMap().getOrDefault(getItem().getName(), "");
            propagateWhiteText = rowStyle.contains("-fx-text-fill: white");
        }

        setStyle(rowStyle);

        // -fx-text-fill on a TableRow does not cascade to TableCell nodes because
        // Modena sets its own -fx-text-fill on .table-cell. Propagate explicitly
        // only for dark-background rows that require white text; clear it otherwise
        // so selected/hovered rows restore the default (dark) cell text.
        String cellTextStyle = propagateWhiteText ? "-fx-text-fill: white;" : "";
        for (javafx.scene.Node child : getChildrenUnmodifiable()) {
            child.setStyle(cellTextStyle);
        }
    }
}
