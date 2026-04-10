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
 * JavaFX entry point for the Score Viewer tool.
 *
 * <p>Provides a sortable table of personal-best scores per chart.
 * Replaces {@code manage_score.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class ScoreViewerApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(ScoreViewerApp.class);

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
        log.info("Starting Score Viewer");
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/score_viewer.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find score_viewer.fxml on classpath");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("SDVX Score Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        log.info("Score Viewer shutting down");
    }
}
