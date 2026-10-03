package com.sdvxhelper.app.controller.listeners;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;

import com.sdvxhelper.app.controller.model.WikiSongRow;

/**
 * Listens for selection changes in the BemaniWiki song table and populates the
 * title field with the selected song's title.
 *
 * @author Throdax
 * @since 2.0.0
 */
public class WikiSongSelectionListener implements ChangeListener<WikiSongRow> {

    private TextField titleField;
    private Runnable onStateChange;

    /**
     * @param titleField
     *            the text field that receives the selected song title
     * @param onStateChange
     *            callback invoked after the title field is updated so the
     *            register-button state can be refreshed
     */
    public WikiSongSelectionListener(TextField titleField, Runnable onStateChange) {
        this.titleField = titleField;
        this.onStateChange = onStateChange;
    }

    @Override
    public void changed(ObservableValue<? extends WikiSongRow> observable, WikiSongRow oldValue, WikiSongRow newValue) {
        if (newValue != null) {
            titleField.setText(newValue.getTitle());
            titleField.setStyle("-fx-text-fill: black; -fx-background-color: #f8f8f8;");
        }
        onStateChange.run();
    }
}
