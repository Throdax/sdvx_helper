package com.sdvxhelper.app.controller.listeners;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import com.sdvxhelper.app.controller.OcrReporterController;

public class TextFilterChangeListener implements ChangeListener<String> {

    private OcrReporterController ocrReporterController;

    public TextFilterChangeListener(OcrReporterController ocrReporterController) {
        this.ocrReporterController = ocrReporterController;
    }

    public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
        if (ocrReporterController.getFilteredWikiSongs() != null) {

            String query;
            if (newValue == null) {
                query = "";
            } else {
                query = newValue.toLowerCase();
            }
            ocrReporterController.getFilteredWikiSongs()
                    .setPredicate(music -> query.isBlank() || music.getTitle().toLowerCase().contains(query));
        }
    }
}
