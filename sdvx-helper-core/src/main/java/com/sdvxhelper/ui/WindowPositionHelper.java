package com.sdvxhelper.ui;

import java.io.IOException;
import java.util.Map;
import javafx.stage.Stage;

import com.sdvxhelper.repository.SettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utility that restores a JavaFX {@link Stage}'s position from the
 * {@link SettingsRepository} and persists the final position when the window
 * closes.
 *
 * <p>
 * Each app module uses a different pair of setting keys (e.g. {@code lx}/
 * {@code ly} for SDVX Helper, {@code ocr_lx}/{@code ocr_ly} for OCR Reporter,
 * etc.). Callers pass in the key pair so the helper can operate generically.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class WindowPositionHelper {

    private static final Logger log = LoggerFactory.getLogger(WindowPositionHelper.class);

    private WindowPositionHelper() {
    }

    /**
     * Restores the stage's X/Y position from the given settings keys and wires up
     * an {@code onCloseRequest} handler that writes the current position back to
     * disk on close.
     *
     * @param stage
     *            the primary stage
     * @param repo
     *            settings repository to read/write
     * @param keyX
     *            setting key for window X position
     * @param keyY
     *            setting key for window Y position
     */
    public static void applyAndPersist(Stage stage, SettingsRepository repo, String keyX, String keyY) {
        Map<String, String> settings = repo.load();
        double x = parseDouble(settings.get(keyX), Double.NaN);
        double y = parseDouble(settings.get(keyY), Double.NaN);
        if (!Double.isNaN(x) && x != 0) {
            stage.setX(x);
        }
        if (!Double.isNaN(y) && y != 0) {
            stage.setY(y);
        }
        stage.setOnCloseRequest(_ -> saveCurrent(stage, repo, keyX, keyY));
    }

    private static void saveCurrent(Stage stage, SettingsRepository repo, String keyX, String keyY) {
        try {
            Map<String, String> s = repo.load();
            s.put(keyX, String.valueOf((int) stage.getX()));
            s.put(keyY, String.valueOf((int) stage.getY()));
            repo.save(s);
        } catch (IOException e) {
            log.warn("Failed to persist window position ({}, {}): {}", keyX, keyY, e.getMessage());
        }
    }

    private static double parseDouble(String s, double fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
