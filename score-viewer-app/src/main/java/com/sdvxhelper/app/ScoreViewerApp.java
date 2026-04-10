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
 * JavaFX entry point for the Score Viewer tool.
 *
 * <p>Provides a sortable table of personal-best scores per chart.
 * Replaces {@code manage_score.py}.</p>
 *
 * @author Throdax
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
        stage.setTitle("SDVX Score Viewer");
        stage.show();
        log.info("Score Viewer UI displayed");
    }

    @Override
    public void stop() {
        log.info("Score Viewer shutting down");
    }

    private void buildScene(Locale locale) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/score_viewer.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find score_viewer.fxml on classpath");
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
