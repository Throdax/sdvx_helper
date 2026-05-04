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
import com.sdvxhelper.ui.WindowPositionHelper;
import com.sdvxhelper.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point for the Score Viewer tool.
 *
 * <p>
 * Provides a sortable table of personal-best scores per chart. Replaces
 * {@code manage_score.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ScoreViewerApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(ScoreViewerApp.class);

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
        SettingsRepository repo = new SettingsRepository();
        LocaleManager.getInstance().init(repo);

        LocaleManager.getInstance().localeProperty().addListener((_, _, newLocale) -> Platform.runLater(() -> {
            try {
                buildScene(newLocale);
            } catch (IOException e) {
                log.error("Failed to rebuild scene after locale change", e);
            }
        }));
        buildScene(LocaleManager.getInstance().getCurrentLocale());
        stage.setTitle("SDVX Score Viewer " + VersionUtil.getVersion("manager"));
        WindowPositionHelper.applyAndPersist(stage, repo, "score_lx", "score_ly");
        stage.show();
        log.info("Score Viewer UI displayed");
    }

    @Override
    public void stop() {
        log.info("Score Viewer shutting down");
        System.exit(0);
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
