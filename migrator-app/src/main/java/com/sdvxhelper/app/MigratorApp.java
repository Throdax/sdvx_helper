package com.sdvxhelper.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import com.sdvxhelper.app.controller.MigratorController;
import com.sdvxhelper.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point for the SDVX Helper - Puni Edition Migrator application.
 *
 * <p>
 * This single-window application guides the user through a one-shot migration
 * from the existing SDVX Helper installation to the Puni Edition distribution.
 * It backs up the existing files, extracts the new distribution ZIP, converts
 * pickle data files to XML via the bundled Python script, and cleans up the
 * dist ZIP afterwards.
 * </p>
 *
 * @author Filipe Cristino
 * @since 2.0.0
 */
public class MigratorApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MigratorApp.class);

    /**
     * Application entry point.
     *
     * @param args
     *            command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/migrator.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find migrator.fxml on classpath");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Scene scene = new Scene(loader.load());
        URL cssUrl = getClass().getResource("/styles/light.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        MigratorController controller = loader.getController();
        controller.setStage(stage);
        stage.setTitle("SDVX Helper - Puni Edition Migrator " + VersionUtil.getVersion("migrator"));
        applyIcon(stage);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        log.info("Migrator UI displayed");
    }

    private void applyIcon(Stage stage) {
        try (InputStream iconStream = getClass().getResourceAsStream("/icon.png")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                log.warn("icon.png not found on classpath, window icon will not be set");
            }
        } catch (IOException e) {
            log.warn("Failed to load window icon: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        log.info("Migrator shutting down");
        System.exit(0);
    }
}
