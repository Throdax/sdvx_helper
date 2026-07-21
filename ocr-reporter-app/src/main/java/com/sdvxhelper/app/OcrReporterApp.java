package com.sdvxhelper.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import com.sdvxhelper.app.controller.OcrReporterController;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.ui.WindowPositionHelper;
import com.sdvxhelper.util.VersionUtil;
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
    private OcrReporterController controller;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        SettingsRepository repo = new SettingsRepository();
        LocaleManager.getInstance().init(repo);
        LocaleManager.getInstance().localeProperty().addListener((_, _, newLocale) -> Platform.runLater(() -> {
            try {
                buildScene(newLocale);
            } catch (IOException e) {
                log.error("Failed to rebuild scene after locale change", e);
            }
        }));
        try {
            buildScene(LocaleManager.getInstance().getCurrentLocale());
        } catch (IOException | RuntimeException e) {
            log.error("Fatal: could not build OCR Reporter scene - app will exit", e);
            Platform.exit();
            return;
        }
        stage.setTitle("SDVX OCR Reporter - Puni Edition " + VersionUtil.getVersion("ocr"));
        applyIcon(stage);
        // Restore saved position without wiring a handler so we can combine with
        // controller.onWindowClose() in a single setOnCloseRequest below.
        WindowPositionHelper.restore(stage, repo, "ocr_lx", "ocr_ly");

        // Only apply maximized state after restoring position,
        // otherwise the window will always open in the top-left corner on a single
        // monitor setup
        stage.setMaximized(true);

        stage.setOnCloseRequest(_ -> {
            WindowPositionHelper.save(stage, repo, "ocr_lx", "ocr_ly");
            if (controller != null) {
                controller.onWindowClose();
            }
        });
        dismissSplashScreen();
        stage.show();
        log.info("OCR Reporter UI displayed");
    }

    @Override
    public void stop() {
        log.info("OCR Reporter shutting down");
        System.exit(0);
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

    /**
     * Closes the AWT splash screen if one is active. The splash is shown by the JVM
     * before JavaFX initialises (declared via {@code SplashScreen-Image} in
     * {@code MANIFEST.MF}); it must be dismissed programmatically once the main
     * window is ready to display.
     */
    private void dismissSplashScreen() {
        java.awt.SplashScreen splash = java.awt.SplashScreen.getSplashScreen();
        if (splash != null && splash.isVisible()) {
            splash.close();
        }
    }

    private void buildScene(Locale locale) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/ocr_reporter.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find ocr_reporter.fxml on classpath");
        }
        ResourceBundle bundle = ResourceBundle.getBundle("i18n/messages", locale);
        FXMLLoader loader = new FXMLLoader(fxmlUrl, bundle);
        Scene scene = new Scene(loader.load());
        controller = loader.getController();
        URL cssUrl = getClass().getResource("/styles/light.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        primaryStage.setScene(scene);
    }
}
