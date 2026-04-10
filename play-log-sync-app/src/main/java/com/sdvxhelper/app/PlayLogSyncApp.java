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
 * JavaFX entry point for the Play Log Sync tool.
 *
 * <p>Reconciles screenshot files with the play log and exports XML for
 * the OBS overlays.  Replaces {@code play_log_sync.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class PlayLogSyncApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(PlayLogSyncApp.class);

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
        log.info("Starting Play Log Sync");
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/play_log_sync.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find play_log_sync.fxml on classpath");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("SDVX Play Log Sync");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        log.info("Play Log Sync shutting down");
    }
}
