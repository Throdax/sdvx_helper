package com.sdvxhelper.app.controller.factories;

import java.util.List;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;

import com.sdvxhelper.model.OnePlayData;

/**
 * Cell factory for the last plays list view.
 *
 * @author Throdax
 * @since 2.0.0
 */
public class LastPlaysCellFactory implements Callback<ListView<OnePlayData>, ListCell<OnePlayData>> {

    private List<OnePlayData> allPlays;

    /**
     * Constructs a factory whose cells will look up play numbers from the full
     * sorted play log list.
     *
     * @param allPlays
     *            the complete sorted play log (from {@code PlayLog.getPlays()});
     *            used to derive a global play index for each cell
     */
    public LastPlaysCellFactory(List<OnePlayData> allPlays) {
        this.allPlays = allPlays;
    }

    @Override
    public ListCell<OnePlayData> call(ListView<OnePlayData> playDataListView) {
        return new OnePlayDataListCell(allPlays);
    }
}
