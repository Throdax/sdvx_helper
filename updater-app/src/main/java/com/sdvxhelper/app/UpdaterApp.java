package com.sdvxhelper.app;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.repository.SettingsRepository;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * JavaFX entry point for the SDVX Helper self-updater.
 *
 * <p>Checks GitHub for a newer release and downloads/installs the update.
 * Replaces {@code update.py}.</p>
 *
 * @author Throdax
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

    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        LocaleManager.getInstance().init(new SettingsRepository());
        LocaleManager.getInstance().localeProperty().addListener((obs, oldLocale, newLocale) ->
                Platform.runLater(() -> {
                    try {
                        buildScene(newLocale);
                    } catch (IOException e) {
                        log.error("Failed to rebuild scene after locale change", e);
                    }
                })
        );
        buildScene(LocaleManager.getInstance().getCurrentLocale());
        stage.setTitle("SDVX Helper Updater");
        stage.show();
        log.info("Updater UI displayed");
    }

    @Override
    public void stop() {
        log.info("Updater shutting down");
    }

    private void buildScene(Locale locale) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/updater.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find updater.fxml on classpath");
        }
        ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", locale);
        FXMLLoader loader = new FXMLLoader(fxmlUrl, bundle);
        Scene scene = new Scene(loader.load());
        URL cssUrl = getClass().getResource("/styles/light.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        primaryStage.setScene(scene);
    }
}
