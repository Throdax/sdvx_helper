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
 * JavaFX entry point for the main SDVX Helper application.
 *
 * <p>Provides the detection loop, OBS capture, and play-logging GUI.
 * Replaces {@code sdvx_helper.pyw}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class SdvxHelperApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(SdvxHelperApp.class);

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
        log.info("Starting SDVX Helper");
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/main.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find main.fxml on classpath");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load());
        primaryStage.setTitle("SDVX Helper");
        primaryStage.setScene(scene);
        primaryStage.show();
        log.info("SDVX Helper UI displayed");
    }

    @Override
    public void stop() {
        log.info("SDVX Helper shutting down");
    }
}
