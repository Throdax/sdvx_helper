package com.sdvxhelper.app.controller.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;

import com.sdvxhelper.app.controller.OcrReporterHelper;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.ImageCropNotParsed;
import com.sdvxhelper.util.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterates result-screenshot files, computes jacket hashes, matches them
 * against the music list, colorises rows, and optionally renames unprocessed
 * files to the title-encoded format.
 *
 * <p>
 * Mirrors Python's {@code do_coloring} / {@code do_coloring_missing} /
 * {@code color_file} logic in {@code ocr_reporter.py}. This service has no
 * JavaFX dependency; all UI side-effects are delivered through the
 * {@link ColorizerCallback} passed by the caller.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ColorizerService {

    private static final Logger log = LoggerFactory.getLogger(ColorizerService.class);

    private static final String REGISTERED_STYLE = "-fx-background-color: #dde0ff;";
    private static final String UNKNOWN_STYLE = "-fx-background-color: #dddddd;";
    private static final String ERROR_STYLE = "-fx-background-color: #ffe0e0;";

    private MusicListRepository musicListRepo;
    private PerceptualHasher hasher;
    private ImageAnalysisService imageAnalysisService;
    private Map<String, String> paramsMap;
    private ResourceBundle bundle;

    /**
     * @param musicListRepo
     *            hash-to-title lookup source
     * @param hasher
     *            perceptual hasher for jacket images
     * @param imageAnalysisService
     *            used for result-screen validation, lamp, score, and difficulty
     *            detection
     * @param paramsMap
     *            detection parameters from {@code params.json}
     * @param bundle
     *            i18n bundle used to build status messages
     */
    public ColorizerService(MusicListRepository musicListRepo, PerceptualHasher hasher,
            ImageAnalysisService imageAnalysisService, Map<String, String> paramsMap, ResourceBundle bundle) {
        this.musicListRepo = musicListRepo;
        this.hasher = hasher;
        this.imageAnalysisService = imageAnalysisService;
        this.paramsMap = paramsMap;
        this.bundle = bundle;
    }

    /**
     * Runs the colorize pass over {@code files} and reports all outcomes through
     * {@code callback}.
     *
     * <p>
     * This method is blocking and intended to be called from a background thread.
     * </p>
     *
     * @param files
     *            snapshot of the file list to process (must not be modified
     *            externally during the run)
     * @param missingOnly
     *            if {@code true}, only unprocessed {@code sdvx_YYYYMMDD_HHMMSS.png}
     *            files are examined
     * @param callback
     *            receives incremental updates and the final result
     */
    public void colorize(List<File> files, boolean missingOnly, ColorizerCallback callback) {
        if (musicListRepo == null) {
            log.warn("colorize: musicListRepo not initialised, cannot colorize {} files", files.size());
            return;
        }

        long startMs = System.currentTimeMillis();
        int total = files.size();
        int found = 0;
        int notFound = 0;

        for (int i = 0; i < total; i++) {
            File f = files.get(i);

            if (missingOnly && !OcrReporterHelper.isUnprocessedResultFilename(f.getName())) {
                continue;
            }
            if (!OcrReporterHelper.isResultFilename(f.getName())) {
                continue;
            }

            try {
                BufferedImage img = ImageIO.read(f);
                if (img == null) {
                    continue;
                }
                if (imageAnalysisService != null && !imageAnalysisService.isResultScreen(img, paramsMap)) {
                    log.debug("colorize: '{}' does not pass isResultScreen - skipping", f.getName());
                    continue;
                }

                BufferedImage jacketCrop = cropJacket(img);
                String hash = jacketCrop != null ? hasher.hash(jacketCrop) : hasher.hash(img);
                String[] match = musicListRepo.findByJacketHash(hash);

                if (match != null) {
                    String title = match[0];
                    String diff = (match[1] != null && !match[1].isBlank()) ? match[1] : "unk";

                    if (OcrReporterHelper.isUnprocessedResultFilename(f.getName())) {
                        processUnprocessedFile(f, img, title, diff, i, callback);
                    } else {
                        callback.onFileColorized(f.getName(), REGISTERED_STYLE);
                        callback.onLog("Found: " + title + " [" + diff.toUpperCase() + "] — " + f.getName());
                    }
                    found++;
                } else {
                    callback.onFileColorized(f.getName(), UNKNOWN_STYLE);
                    callback.onLog("Not found: " + f.getName());
                    notFound++;
                }
            } catch (IOException e) {
                log.debug("Colorize error for {}: {}", f.getName(), e.getMessage());
                callback.onLog("ERROR reading: " + f.getName());
            }

            int current = i + 1;
            if (current % 10 == 0 || current == total) {
                String statusMessage = buildProgressMessage(current, total);
                callback.onProgress(current, total, statusMessage);
            }
        }

        double elapsedSeconds = Math.round((System.currentTimeMillis() - startMs) / 10.0) / 100.0;
        callback.onComplete(found, notFound, elapsedSeconds);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void processUnprocessedFile(File f, BufferedImage img, String title, String diff, int fileIndex,
            ColorizerCallback callback) {
        try {
            String effectiveDiff = detectDifficultyForRename(img);
            String lamp = imageAnalysisService != null ? imageAnalysisService.detectLampOnResult(img, paramsMap) : "uc";
            int score = imageAnalysisService != null ? imageAnalysisService.getScoreOnResult(img, paramsMap) : 0;
            String scorePrefix = OcrReporterHelper.toScorePrefix(score);
            File renamed = renameResultFile(f, title, effectiveDiff, lamp, scorePrefix);
            if (renamed != null) {
                callback.onFileRenamed(fileIndex, renamed);
                callback.onFileColorized(renamed.getName(), REGISTERED_STYLE);
                callback.onLog("OCR: [" + effectiveDiff.toUpperCase() + "] " + lamp + " " + scorePrefix + "xxxx — "
                        + title + " → " + renamed.getName());
            } else {
                callback.onFileColorized(f.getName(), REGISTERED_STYLE);
                callback.onLog("OCR: [" + effectiveDiff.toUpperCase() + "] " + title + " (rename skipped)");
            }
        } catch (ImageCropNotParsed e) {
            log.error("colorize: cannot classify difficulty band for '{}': {}", f.getName(), e.getMessage());
            callback.onFileColorized(f.getName(), ERROR_STYLE);
            callback.onLog("ERROR [" + f.getName() + "]: " + e.getMessage());
        }
    }

    private BufferedImage cropJacket(BufferedImage img) {
        int sx = ParamUtils.getInt(paramsMap, "log_crop_jacket_sx", -1);
        int sy = ParamUtils.getInt(paramsMap, "log_crop_jacket_sy", -1);
        int w = ParamUtils.getInt(paramsMap, "log_crop_jacket_w", 0);
        int h = ParamUtils.getInt(paramsMap, "log_crop_jacket_h", 0);
        if (sx < 0 || sy < 0 || w <= 0 || h <= 0) {
            log.warn("cropJacket: jacket params not configured, falling back to full image hash");
            return null;
        }
        return OcrReporterHelper.cropAndScale(img, sx, sy, w, h, w, h);
    }

    private String detectDifficultyForRename(BufferedImage img) throws ImageCropNotParsed {
        int dSx = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sx", 55);
        int dSy = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sy", 870);
        int dW = ParamUtils.getInt(paramsMap, "log_crop_difficulty_w", 138);
        int dH = ParamUtils.getInt(paramsMap, "log_crop_difficulty_h", 30);
        BufferedImage diffBand = OcrReporterHelper.cropAndScale(img, dSx, dSy, dW, dH, dW, dH);
        return OcrReporterHelper.detectDifficultyFromBand(diffBand);
    }

    private File renameResultFile(File f, String title, String difficulty, String lamp, String scorePrefix) {
        String sanitized = OcrReporterHelper.sanitizeForFilename(title);
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120);
        }
        String timestamp = OcrReporterHelper.extractTimestampFromFilename(f.getName());
        String newName = "sdvx_" + sanitized + "_" + difficulty.toUpperCase() + "_" + lamp + "_" + scorePrefix + "_"
                + timestamp + ".png";
        File newFile = new File(f.getParent(), newName);
        if (newFile.equals(f)) {
            return f;
        }
        if (newFile.exists()) {
            log.debug("renameResultFile: target already exists, skipping: {}", newName);
            return null;
        }
        if (f.renameTo(newFile)) {
            log.info("Renamed: {} -> {}", f.getName(), newFile.getName());
            return newFile;
        }
        log.warn("renameResultFile: failed to rename {} to {}", f.getName(), newName);
        return null;
    }

    private String buildProgressMessage(int current, int total) {
        String base = bundle != null ? bundle.getString("message.coloring") : "Colorizing…";
        return base + " (" + current + "/" + total + ")";
    }
}
