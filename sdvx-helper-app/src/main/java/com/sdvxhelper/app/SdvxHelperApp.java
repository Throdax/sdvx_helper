package com.sdvxhelper.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import com.sdvxhelper.app.controller.MainController;
import com.sdvxhelper.app.controller.detection.DetectionEngine;
import com.sdvxhelper.i18n.LocaleManager;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.repository.SettingsRepository;
import com.sdvxhelper.ui.WindowPositionHelper;
import com.sdvxhelper.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point for the main SDVX Helper application.
 *
 * <p>
 * Provides the detection loop, OBS capture, and play-logging GUI. Replaces
 * {@code sdvx_helper.pyw}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class SdvxHelperApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(SdvxHelperApp.class);

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
    private MainController currentController;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;
        SettingsRepository repo = new SettingsRepository();
        LocaleManager.getInstance().init(repo);
        LocaleManager.getInstance().localeProperty().addListener((_, _, newLocale) -> Platform.runLater(() -> {
            try {
                rebuildScene(newLocale);
            } catch (IOException e) {
                log.error("Failed to rebuild scene after locale change", e);
            }
        }));
        buildScene(LocaleManager.getInstance().getCurrentLocale());
        applyIcon(stage);
        stage.setTitle("SDVX Helper " + VersionUtil.getVersion("helper"));
        WindowPositionHelper.applyAndPersist(stage, repo, "lx", "ly");
        stage.show();
        log.info("SDVX Helper UI displayed");
    }

    @Override
    public void stop() {
        log.info("SDVX Helper shutting down");
        if (currentController != null) {
            currentController.onWindowClose();
            currentController.cleanup();
        }
        // Force JVM exit after JavaFX has shut down. Third-party libraries such as
        // JNativeHook and obs-websocket-java/Jetty start non-daemon threads that
        // would otherwise keep the process alive after the window is closed.
        System.exit(0);
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
        List<OnePlayData> savedPlays = Collections.emptyList();
        String savedOutput = "";
        DetectMode savedMode = DetectMode.INIT;
        if (currentController != null) {
            savedPlays = currentController.getSessionLogSnapshot();
            savedOutput = currentController.getOutputText();
            DetectionEngine oldEngine = currentController.getDetectionEngine();
            if (oldEngine != null) {
                savedMode = oldEngine.getCurrentMode();
            }
            currentController.cleanup();
        }
        buildScene(locale);
        currentController.setInitialDetectMode(savedMode);
        currentController.restoreSessionData(savedPlays, savedOutput);
    }

    private void applyIcon(Stage stage) {
        try (InputStream iconStream = getClass().getResourceAsStream("/icon.ico")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                log.warn("icon.ico not found on classpath, window icon will not be set");
            }
        } catch (IOException e) {
            log.warn("Failed to load window icon: {}", e.getMessage());
        }
    }
}
