package com.sdvxhelper.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * JavaFX entry point for the OCR Reporter maintainer tool.
 *
 * <p>Allows maintainers to register unknown jacket hashes and update the music list.
 * Replaces {@code ocr_reporter.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class OcrReporterApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(OcrReporterApp.class);

    /**
     * Application entry point.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        log.info("Starting OCR Reporter");
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/ocr_reporter.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find ocr_reporter.fxml on classpath");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("SDVX OCR Reporter");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        log.info("OCR Reporter shutting down");
    }
}
