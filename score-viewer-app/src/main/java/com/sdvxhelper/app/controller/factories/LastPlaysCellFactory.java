package com.sdvxhelper.app.controller.factories;

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

    @Override
    public ListCell<OnePlayData> call(ListView<OnePlayData> playDataListView) {
        return new OnePlayDataListCell();
    }
}
