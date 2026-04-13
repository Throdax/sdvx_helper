package com.sdvxhelper.app;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point for the OCR Reporter maintainer tool.
 *
 * <p>
 * Allows maintainers to register unknown jacket hashes and update the music
 * list. Replaces {@code ocr_reporter.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class OcrReporterApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(OcrReporterApp.class);

    /**
     * Application entry point.
     *
     * @param args
     *            command-line arguments (unused)
     */
    public static void main(String[] args) {
        launch(args);
    }

    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        LocaleManager.getInstance().init(new SettingsRepository());
        LocaleManager.getInstance().localeProperty()
                .addListener((obs, oldLocale, newLocale) -> Platform.runLater(() -> {
                    try {
                        buildScene(newLocale);
                    } catch (IOException e) {
                        log.error("Failed to rebuild scene after locale change", e);
                    }
                }));
        buildScene(LocaleManager.getInstance().getCurrentLocale());
        stage.setTitle("SDVX OCR Reporter");
        stage.show();
        log.info("OCR Reporter UI displayed");
    }

    @Override
    public void stop() {
        log.info("OCR Reporter shutting down");
    }

    private void buildScene(Locale locale) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/ocr_reporter.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find ocr_reporter.fxml on classpath");
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
