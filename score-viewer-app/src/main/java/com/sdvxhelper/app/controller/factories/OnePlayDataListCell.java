package com.sdvxhelper.app.controller.factories;

import java.time.format.DateTimeFormatter;
import java.util.List;

import javafx.scene.control.ListCell;

import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.util.ScoreFormatter;

/**
 * List cell for displaying one play data in the last plays list view.
 *
 * <p>
 * Each row shows the global play index in the full play log, the score
 * (SDVX four-digit-group format), the lamp (uppercased, with "exh" rendered
 * as "MAXXIVE"), and the play date.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class OnePlayDataListCell extends ListCell<OnePlayData> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private List<OnePlayData> allPlays;

    /**
     * Constructs a cell that derives play numbers from the given list.
     *
     * @param allPlays
     *            the complete sorted play log; used to look up a global index
     *            for each entry displayed in this cell
     */
    public OnePlayDataListCell(List<OnePlayData> allPlays) {
        this.allPlays = allPlays;
    }

    @Override
    protected void updateItem(OnePlayData item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
        } else {
            int idx = allPlays != null ? allPlays.indexOf(item) : -1;
            String idxStr = idx >= 0 ? String.valueOf(idx) : "?";
            String lampDisplay = formatLamp(item.getLamp());
            String dateStr = item.getDate() != null ? item.getDate().format(DATE_FORMAT) : "";
            setText(idxStr + " - " + ScoreFormatter.formatScore(item.getCurScore())
                    + " | " + lampDisplay + " | " + dateStr);
        }
    }

    private static String formatLamp(String lamp) {
        if (lamp == null) {
            return "";
        }
        if ("exh".equalsIgnoreCase(lamp)) {
            return "MAXXIVE";
        }
        return lamp.toUpperCase();
    }

}
