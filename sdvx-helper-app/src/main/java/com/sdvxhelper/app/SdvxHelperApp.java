package com.sdvxhelper.app;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.ui.controller.MainController;
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
 * JavaFX entry point for the main SDVX Helper application.
 *
 * <p>Provides the detection loop, OBS capture, and play-logging GUI.
 * Replaces {@code sdvx_helper.pyw}.</p>
 *
 * @author Throdax
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

    private Stage primaryStage;
    private MainController currentController;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        LocaleManager.getInstance().init(new SettingsRepository());
        LocaleManager.getInstance().localeProperty().addListener((obs, oldLocale, newLocale) ->
                Platform.runLater(() -> {
                    try {
                        rebuildScene(newLocale);
                    } catch (IOException e) {
                        log.error("Failed to rebuild scene after locale change", e);
                    }
                })
        );
        buildScene(LocaleManager.getInstance().getCurrentLocale());
        stage.setTitle("SDVX Helper");
        stage.show();
        log.info("SDVX Helper UI displayed");
    }

    @Override
    public void stop() {
        log.info("SDVX Helper shutting down");
        if (currentController != null) {
            currentController.cleanup();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void buildScene(Locale locale) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/main.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find main.fxml on classpath");
        }
        ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", locale);
        FXMLLoader loader = new FXMLLoader(fxmlUrl, bundle);
        Scene scene = new Scene(loader.load());
        URL cssUrl = getClass().getResource("/styles/light.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        currentController = loader.getController();
        primaryStage.setScene(scene);
    }

    private void rebuildScene(Locale locale) throws IOException {
        if (currentController != null) {
            currentController.cleanup();
        }
        buildScene(locale);
    }
}
