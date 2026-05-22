package com.sdvxhelper.app.controller.listeners;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

/**
 * Listens for row-index changes in the saved-files {@code TableView} and
 * notifies the controller with the newly selected {@link File}.
 *
 * @author Throdax
 * @since 2.0.0
 */
public class FileSelectionIndexListener implements ChangeListener<Number> {

    private List<File> imageFiles;
    private Consumer<File> onFileSelected;

    /**
     * @param imageFiles
     *            the backing list of result-screenshot files displayed in the table
     * @param onFileSelected
     *            callback invoked with the selected {@link File} whenever the
     *            selection changes to a valid index
     */
    public FileSelectionIndexListener(List<File> imageFiles, Consumer<File> onFileSelected) {
        this.imageFiles = imageFiles;
        this.onFileSelected = onFileSelected;
    }

    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        int index = newValue.intValue();
        if (index >= 0 && index < imageFiles.size()) {
            onFileSelected.accept(imageFiles.get(index));
        }
    }
}
