package com.sdvxhelper.app.controller.factories;

import java.time.format.DateTimeFormatter;
import javafx.scene.control.ListCell;

import com.sdvxhelper.model.OnePlayData;

/**
 * List cell for displaying one play data in the last plays list view.
 * 
 * @author Throdax
 * @since 2.0.0
 */
public class OnePlayDataListCell extends ListCell<OnePlayData> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    protected void updateItem(OnePlayData item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
        } else {
            String dateStr = item.getDate() != null ? item.getDate().format(DATE_FORMAT) : "";
            setText(String.format("%,d | %s | %s", item.getCurScore(), item.getLamp(), dateStr));
        }
    }

}
