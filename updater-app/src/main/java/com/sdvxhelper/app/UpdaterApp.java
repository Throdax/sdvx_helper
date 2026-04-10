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
 * JavaFX entry point for the SDVX Helper self-updater.
 *
 * <p>Checks GitHub for a newer release and downloads/installs the update.
 * Replaces {@code update.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class UpdaterApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(UpdaterApp.class);

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
        log.info("Starting Updater");
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/updater.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find updater.fxml on classpath");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("SDVX Helper Updater");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        log.info("Updater shutting down");
    }
}
