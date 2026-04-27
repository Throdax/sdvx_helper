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
 * JavaFX entry point for the Play Log Sync tool.
 *
 * <p>
 * Reconciles screenshot files with the play log and exports XML for the OBS
 * overlays. Replaces {@code play_log_sync.py}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PlayLogSyncApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(PlayLogSyncApp.class);

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
        stage.setTitle("SDVX Play Log Sync " + VersionUtil.getVersion("sync"));
        WindowPositionHelper.applyAndPersist(stage, repo, "sync_lx", "sync_ly");
        stage.show();
        log.info("Play Log Sync UI displayed");
    }

    @Override
    public void stop() {
        log.info("Play Log Sync shutting down");
    }

    private void buildScene(Locale locale) throws IOException {
        URL fxmlUrl = getClass().getResource("/com/sdvxhelper/app/view/play_log_sync.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find play_log_sync.fxml on classpath");
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
