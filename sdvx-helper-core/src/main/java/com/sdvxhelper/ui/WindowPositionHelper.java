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
     * <p>
     * Note: this sets {@code stage.setOnCloseRequest}. If the caller also needs to
     * set a close handler, use {@link #restore} and {@link #save} separately
     * instead.
     * </p>
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
        restore(stage, repo, keyX, keyY);
        stage.setOnCloseRequest(_ -> save(stage, repo, keyX, keyY));
    }

    /**
     * Restores the stage's X/Y position from the given settings keys without wiring
     * a close handler. Use in combination with {@link #save} when the caller needs
     * to supply its own {@code setOnCloseRequest}.
     *
     * @param stage
     *            the primary stage
     * @param repo
     *            settings repository to read position from
     * @param keyX
     *            setting key for window X position
     * @param keyY
     *            setting key for window Y position
     */
    public static void restore(Stage stage, SettingsRepository repo, String keyX, String keyY) {
        Map<String, String> settings = repo.load();
        double x = parseDouble(settings.get(keyX), Double.NaN);
        double y = parseDouble(settings.get(keyY), Double.NaN);
        if (!Double.isNaN(x) && x != 0) {
            stage.setX(x);
        }
        if (!Double.isNaN(y) && y != 0) {
            stage.setY(y);
        }
    }

    /**
     * Saves the stage's current X/Y position to the given settings keys. Designed
     * to be called from a close-request handler alongside other cleanup.
     *
     * @param stage
     *            the primary stage
     * @param repo
     *            settings repository to write to
     * @param keyX
     *            setting key for window X position
     * @param keyY
     *            setting key for window Y position
     */
    public static void save(Stage stage, SettingsRepository repo, String keyX, String keyY) {
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
