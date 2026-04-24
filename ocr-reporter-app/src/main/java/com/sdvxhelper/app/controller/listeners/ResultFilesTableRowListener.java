package com.sdvxhelper.app.controller.listeners;

import java.io.File;
import javafx.scene.control.TableRow;

import com.sdvxhelper.app.controller.OcrReporterController;

public class ResultFilesTableRowListener extends TableRow<File> {

    /**
     * 
     */
    private final OcrReporterController ocrReporterController;

    /**
     * @param ocrReporterController
     */
    public ResultFilesTableRowListener(OcrReporterController ocrReporterController) {
        this.ocrReporterController = ocrReporterController;
    }

    @Override
    protected void updateItem(File item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setStyle("");
        } else {
            setStyle(this.ocrReporterController.getFileColorMap().getOrDefault(item.getName(), ""));
        }
    }
}