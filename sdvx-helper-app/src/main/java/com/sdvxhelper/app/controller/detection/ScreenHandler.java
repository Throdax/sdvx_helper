package com.sdvxhelper.app.controller.detection;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.ocr.OcrUtils;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.TesseractOcr;
import com.sdvxhelper.service.CsvExportService;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.ImageCropNotParsed;
import com.sdvxhelper.service.SdvxPlayLogService;
import com.sdvxhelper.service.SummaryGeneratorService;
import com.sdvxhelper.service.XmlExportService;
import com.sdvxhelper.util.ParamUtils;
import com.sdvxhelper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles per-screen image analysis, file exports, and stateful Volforce
 * tracking for the detection loop.
 *
 * <p>
 * This class has no JavaFX dependency. The caller ({@link DetectionEngine}) is
 * responsible for dispatching results to the UI thread and triggering OBS and
 * webhook side-effects.
 * </p>
 *
 * <p>
 * Replaces the inline screen-handling methods of the Python {@code SDVXHelper}
 * class in {@code sdvx_helper.pyw}.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ScreenHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreenHandler.class);

    private ImageAnalysisService imageAnalysisService;
    private SdvxPlayLogService playLogService;
    private XmlExportService xmlExportService;
    private CsvExportService csvExportService;
    private SummaryGeneratorService summaryGeneratorService;
    private PerceptualHasher perceptualHasher;
    private Map<String, String> params;
    private Map<String, String> settings;

    private static final Pattern VF_NUMBER_PATTERN = Pattern.compile("(\\d+\\.\\d{3})");

    // Volforce capture state
    private String lastVfHash = null;
    private String lastVfNumber = null;
    private TesseractOcr vfOcr = null;
    private boolean genFirstVf = false;

    // Result tracking state
    private List<OnePlayData> preloadedPlays = new ArrayList<>();
    private List<OnePlayData> sessionPlays = new ArrayList<>();
    private List<Duration> sessionPlayTimestamps = new ArrayList<>();
    private double currentTotalVf = 0.0;
    private double previousTotalVf = 0.0;

    // Capture indicators — set by handleResultScreen, read by DetectionEngine
    private boolean lastScreenshotSaved = false;
    private boolean lastSummaryGenerated = false;

    /**
     * @param imageAnalysisService
     *            image analysis service
     * @param loggerService
     *            play log and music-info service
     * @param xmlExportService
     *            XML overlay export service
     * @param csvExportService
     *            CSV export service (for Google Drive sync)
     * @param summaryGeneratorService
     *            summary image compositor service
     * @param perceptualHasher
     *            perceptual hashing utility
     * @param params
     *            detection parameters map
     * @param settings
     *            application settings map
     */
    public ScreenHandler(ImageAnalysisService imageAnalysisService, SdvxPlayLogService loggerService,
            XmlExportService xmlExportService, CsvExportService csvExportService,
            SummaryGeneratorService summaryGeneratorService, PerceptualHasher perceptualHasher,
            Map<String, String> params, Map<String, String> settings) {
        this.imageAnalysisService = imageAnalysisService;
        this.playLogService = loggerService;
        this.xmlExportService = xmlExportService;
        this.csvExportService = csvExportService;
        this.summaryGeneratorService = summaryGeneratorService;
        this.perceptualHasher = perceptualHasher;
        this.params = params;
        this.settings = settings;
        this.currentTotalVf = loggerService.getTotalVfInt() / 1000.0;
    }

    // -------------------------------------------------------------------------
    // Settings/params refresh
    // -------------------------------------------------------------------------

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    // -------------------------------------------------------------------------
    // State accessors (used by DetectionEngine after processing)
    // -------------------------------------------------------------------------

    public double getCurrentTotalVf() {
        return currentTotalVf;
    }

    public double getPreviousTotalVf() {
        return previousTotalVf;
    }

    public List<OnePlayData> getSessionPlays() {
        return sessionPlays;
    }

    public List<Duration> getSessionPlayTimestamps() {
        return sessionPlayTimestamps;
    }

    /**
     * Stores plays loaded from the autosave directory at startup into a separate
     * history list.
     *
     * <p>
     * These stub entries (screenshot path only, no in-memory metadata) are kept
     * apart from {@code sessionPlays} so that the webhook playlist only contains
     * plays actually recorded during the current session. Every subsequent
     * {@link #handleResultScreen} call combines both lists when calling
     * {@link com.sdvxhelper.service.SummaryGeneratorService#generate}, so the
     * summary image always includes the full history.
     * </p>
     *
     * <p>
     * The caller supplies the list in oldest-first order (as returned by
     * {@link com.sdvxhelper.service.SummaryGeneratorService#generateFromResultsDir}).
     * </p>
     *
     * @param plays
     *            plays scanned from disk at startup; must not be {@code null}
     */
    public void addPreloadedPlays(List<OnePlayData> plays) {
        preloadedPlays.addAll(plays);
        log.debug("addPreloadedPlays: stored {} pre-loaded play(s) in history", plays.size());
    }

    /**
     * Regenerates {@code summary_full.png} and {@code summary_small.png} from the
     * current combined play list (preloaded + session plays).
     *
     * <p>
     * Mirrors Python {@code capture_summary_btn} which calls
     * {@code gen_summary.generate()} to rebuild the OBS overlay images on demand.
     * </p>
     *
     * @return {@code true} if the images were written successfully
     */
    public boolean regenerateSummary() {
        String resourcesDir = settings.getOrDefault("resources_dir", "resources");
        List<OnePlayData> allPlays = new ArrayList<>(preloadedPlays);
        allPlays.addAll(sessionPlays);
        return summaryGeneratorService.generate(allPlays, params, settings, resourcesDir);
    }

    public boolean wasLastScreenshotSaved() {
        return lastScreenshotSaved;
    }

    public boolean wasLastSummaryGenerated() {
        return lastSummaryGenerated;
    }

    // -------------------------------------------------------------------------
    // Result screen
    // -------------------------------------------------------------------------

    /**
     * Processes a result-screen capture: detects score/lamp, identifies the jacket,
     * records the play, saves PNG and overlay XML files.
     *
     * <p>
     * OBS text updates, Discord presence, and webhook sends are intentionally left
     * to the caller.
     * </p>
     *
     * @param frame
     *            full-frame capture of the result screen
     * @param songTimestamp
     *            elapsed time since OBS recording/streaming started at the point
     *            the song was detected, or {@code null} when no output was active
     * @param lastKnownDiff
     *            difficulty detected on the preceding select/detect screen via
     *            button brightness; used as the authoritative source since the
     *            result screen does not show difficulty buttons. May be
     *            {@code null} or blank, in which case the hash-index value is used
     *            as a fallback.
     * @return the recorded play, or {@code null} if processing failed
     */
    public OnePlayData handleResultScreen(BufferedImage frame, Duration songTimestamp, String lastKnownDiff) {
        if (frame == null) {
            log.debug("handleResultScreen: frame is null, skipping");
            return null;
        }
        if (!imageAnalysisService.isResultScreen(frame, params)) {
            log.warn("handleResultScreen: frame does not match result-screen layout - skipping");
            return null;
        }
        lastScreenshotSaved = false;
        lastSummaryGenerated = false;
        try {
            String lamp = detectLampFromFrame(frame);
            BufferedImage jacketCrop = cropJacketLog(frame);
            String[] identified = identifyResultJacket(jacketCrop);
            String title = identified != null ? identified[0] : "Unknown";
            String difficulty = (lastKnownDiff != null && !lastKnownDiff.isBlank())
                    ? lastKnownDiff
                    : (identified != null ? identified[1] : "exh");

            MusicInfo best = "Unknown".equals(title) ? null : playLogService.getBestFor(title, difficulty);
            int preScore = best != null ? best.getBestScore() : 0;
            int score = readScore(frame);

            String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            OnePlayData play = new OnePlayData(title, score, preScore, lamp, difficulty, dateStr);
            if ("Unknown".equals(title)) {
                log.info(
                        "Play not recorded to alllog.xml: song is unknown (not in musiclist). Register jacket in OCR Reporter and sync with play-log-sync-app.");
            } else {
                playLogService.pushPlay(play);
            }
            sessionPlays.add(play);
            sessionPlayTimestamps.add(songTimestamp);

            previousTotalVf = currentTotalVf;
            currentTotalVf = playLogService.getTotalVfInt() / 1000.0;

            String screenshotPath = saveResultFiles(frame, jacketCrop, title, difficulty, lamp, score);
            play.setScreenshotFile(screenshotPath);
            lastScreenshotSaved = true;

            writeResultXml(title, difficulty);
            saveGoogleDriveCsv();

            String resourcesDir = settings.getOrDefault("resources_dir", "resources");
            List<OnePlayData> allPlays = new ArrayList<>(preloadedPlays);
            allPlays.addAll(sessionPlays);
            lastSummaryGenerated = summaryGeneratorService.generate(allPlays, params, settings, resourcesDir);

            return play;
        } catch (IOException e) {
            log.error("handleResultScreen failed", e);
            return null;
        }
    }

    private String detectLampFromFrame(BufferedImage frame) {
        return imageAnalysisService.detectLampOnResult(frame, params);
    }

    private BufferedImage cropJacketLog(BufferedImage frame) {
        int jSx = ParamUtils.getInt(params, "log_crop_jacket_sx", 57);
        int jSy = ParamUtils.getInt(params, "log_crop_jacket_sy", 916);
        int jW = ParamUtils.getInt(params, "log_crop_jacket_w", 263);
        int jH = ParamUtils.getInt(params, "log_crop_jacket_h", 263);
        return safeCrop(frame, jSx, jSy, jW, jH);
    }

    private String[] identifyResultJacket(BufferedImage jacketCrop) {
        return imageAnalysisService.identifyJacket(jacketCrop);
    }

    private int readScore(BufferedImage frame) {
        return imageAnalysisService.getScoreOnResult(frame, params);
    }

    private String saveResultFiles(BufferedImage frame, BufferedImage jacketCrop, String title, String difficulty,
            String lamp, int score) throws IOException {
        String autosaveDir = settings.getOrDefault("autosave_dir", "out");
        File autosaveDirFile = new File(autosaveDir);
        if (!autosaveDirFile.exists()) {
            autosaveDirFile.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName;
        boolean isUnknown = "Unknown".equalsIgnoreCase(title) || title == null || title.isBlank();
        if (isUnknown) {
            fileName = "sdvx_" + timestamp + ".png";
        } else {
            String sanitizedTitle = StringUtils.sanitize(title);
            if (sanitizedTitle.length() > 120) {
                sanitizedTitle = sanitizedTitle.substring(0, 120);
            }
            String diff4 = difficulty.toUpperCase();
            String scoreStr = String.valueOf(score / 10000);
            fileName = "sdvx_" + sanitizedTitle + "_" + diff4 + "_" + lamp + "_" + scoreStr + "_" + timestamp + ".png";
        }

        File screenshotFile = new File(autosaveDir, fileName);
        ImageIO.write(frame, "png", screenshotFile);
        log.info("Result screenshot saved: {}", screenshotFile.getAbsolutePath());

        if ("true".equalsIgnoreCase(settings.get("save_jacketimg"))) {
            saveJacketFile(jacketCrop);
        }

        return screenshotFile.getAbsolutePath();
    }

    private void saveJacketFile(BufferedImage jacketCrop) {
        try {
            File jacketsDir = new File("jackets");
            jacketsDir.mkdirs();
            String hashStr = perceptualHasher.hash(jacketCrop);
            File jacketFile = new File(jacketsDir, hashStr + ".png");
            if (!jacketFile.exists()) {
                ImageIO.write(jacketCrop, "png", jacketFile);
                log.info("Jacket saved: {}", jacketFile.getAbsolutePath());
            } else {
                log.debug("Jacket already exists: {}", jacketFile.getName());
            }
        } catch (IOException e) {
            log.warn("Failed to save jacket file: {}", e.getMessage());
        }
    }

    private void writeResultXml(String title, String difficulty) {
        if (xmlExportService == null) {
            log.debug("writeResultXml: xmlExportService not available, skipping XML export");
            return;
        }
        try {
            List<OnePlayData> history = playLogService.getPlaysFor(title, difficulty);
            MusicInfo info = playLogService.getBestFor(title, difficulty);
            int lv = info != null ? info.getLvAsInt() : -1;
            xmlExportService.writeHistoryCurSong(history, lv, new File("out/history_cursong.xml"));
            xmlExportService.writeTotalVf(playLogService.getBestAllFumen(), playLogService.getTotalVfInt(),
                    new File("out/total_vf.xml"));
        } catch (IOException e) {
            log.debug("XML export failed: {}", e.getMessage());
        }
    }

    private void saveGoogleDriveCsv() {
        if (csvExportService == null) {
            log.debug("saveGoogleDriveCsv: csvExportService not available, skipping");
            return;
        }
        String myGdrive = settings.getOrDefault("my_googledrive", "").trim();
        if (myGdrive.isBlank()) {
            log.debug("saveGoogleDriveCsv: my_googledrive path not configured, skipping");
            return;
        }
        try {
            csvExportService.writeBestCsv(playLogService.getBestAllFumen(), new File(myGdrive, "sdvx_helper_best.csv"));
        } catch (IOException e) {
            log.debug("Could not save best CSV to Google Drive path: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Select screen
    // -------------------------------------------------------------------------

    /**
     * Processes a music-select screen: identifies the jacket and optionally imports
     * the displayed score when {@code import_from_select} is enabled.
     *
     * @param frame
     *            full-frame capture of the select screen
     * @return result with identified title/difficulty and optional imported play,
     *         or {@code null} if the jacket could not be identified
     */
    public SelectScreenResult handleSelectScreen(BufferedImage frame) {
        if (frame == null) {
            log.debug("handleSelectScreen: frame is null, skipping");
            return null;
        }
        try {
            int jSx = ParamUtils.getInt(params, "select_jacket_sx", 94);
            int jSy = ParamUtils.getInt(params, "select_jacket_sy", 242);
            int jW = ParamUtils.getInt(params, "select_jacket_w", 352);
            int jH = ParamUtils.getInt(params, "select_jacket_h", 352);
            String[] identified = imageAnalysisService.identifyJacket(frame, new Rectangle(jSx, jSy, jW, jH), "");
            if (identified == null) {
                log.debug("handleSelectScreen: jacket not identified, skipping");
                return null;
            }
            String title = identified[0];
            String difficulty = imageAnalysisService.detectDifficultyFromButtons(frame, params);
            writeRivalViewXml(title, difficulty);
            OnePlayData importedPlay = null;
            if ("true".equalsIgnoreCase(settings.get("import_from_select"))) {
                importedPlay = importScoreFromSelect(frame, title, difficulty);
            }
            return new SelectScreenResult(title, difficulty, importedPlay);
        } catch (IOException e) {
            log.error("handleSelectScreen failed", e);
            return null;
        }
    }

    private OnePlayData importScoreFromSelect(BufferedImage frame, String title, String difficulty) throws IOException {
        int score = imageAnalysisService.getScoreOnSelect(frame, params, Collections.emptyMap());
        if (score <= 0 || score > 10_000_000) {
            log.debug("importScoreFromSelect: score {} out of valid range, skipping", score);
            return null;
        }
        MusicInfo best = playLogService.getBestFor(title, difficulty);
        int preScore = best != null ? best.getBestScore() : 0;
        String bestLamp = best != null ? best.getBestLamp() : "failed";
        if (score <= preScore) {
            log.debug("importScoreFromSelect: score {} not better than preScore {}, skipping", score, preScore);
            return null;
        }
        OnePlayData play = new OnePlayData(title, score, preScore, bestLamp, difficulty,
                LocalDateTime.now().toString());
        playLogService.pushPlay(play);
        return play;
    }

    /**
     * Returns the chart level for the given title and difficulty, or {@code -1} if
     * the song is not in the play-log.
     *
     * @param title
     *            song title
     * @param difficulty
     *            difficulty string (e.g. {@code "nov"})
     * @return chart level, or {@code -1} when unknown
     */
    public int getLevelFor(String title, String difficulty) {
        MusicInfo info = playLogService.getBestFor(title, difficulty);
        return info != null ? info.getLvAsInt() : -1;
    }

    /**
     * Writes the rival-view XML for the given song and difficulty.
     *
     * @param title
     *            song title
     * @param diff
     *            difficulty string
     */
    public void writeRivalViewXml(String title, String diff) {
        if (xmlExportService == null) {
            log.debug("writeRivalViewXml: xmlExportService not available, skipping");
            return;
        }
        try {
            List<OnePlayData> history = playLogService.getPlaysFor(title, diff);
            xmlExportService.writeHistoryCurSong(history, -1, new File("out/history_cursong.xml"));
        } catch (IOException e) {
            log.debug("writeRivalViewXml failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Detect (song-commit) screen
    // -------------------------------------------------------------------------

    /**
     * Handles the DETECT screen using a frame captured <em>after</em> the
     * {@code detect_wait} delay has already elapsed in the caller.
     *
     * <p>
     * Mirrors Python {@code GenSummary.update_musicinfo()} /
     * {@code GenSummary.ocr_from_detect()} in {@code gen_summary.py}, which are
     * invoked only after {@code time.sleep(detect_wait)} and a fresh OBS capture in
     * the main loop. The caller ({@link DetectionEngine#processDetectMode}) is
     * responsible for the sleep and the re-capture.
     * </p>
     *
     * @param frame
     *            fresh frame captured after {@code detect_wait} seconds have
     *            elapsed
     * @return {@code {title, diff}} array if identified, {@code null} otherwise
     */
    public String[] handleDetectMode(BufferedImage frame) {
        updateMusicInfo(frame);

        int dSx = ParamUtils.getInt(params, "info_diff_sx", 917);
        int dSy = ParamUtils.getInt(params, "info_diff_sy", 1172);
        int dW = ParamUtils.getInt(params, "info_diff_w", 73);
        int dH = ParamUtils.getInt(params, "info_diff_h", 12);
        String detectedDiff = detectDiffFromBandOrFallback(frame, dSx, dSy, dW, dH, "exh");

        int jSx = ParamUtils.getInt(params, "select_jacket_sx", 94);
        int jSy = ParamUtils.getInt(params, "select_jacket_sy", 242);
        int jW = ParamUtils.getInt(params, "select_jacket_w", 352);
        int jH = ParamUtils.getInt(params, "select_jacket_h", 352);
        String[] identified = imageAnalysisService.identifyJacket(frame, new Rectangle(jSx, jSy, jW, jH), "");
        if (identified == null) {
            log.debug("handleDetectMode: jacket not identified, returning Unknown with detected diff '{}'",
                    detectedDiff);
            return new String[]{"Unknown", detectedDiff};
        }
        return new String[]{identified[0], detectedDiff};
    }

    private String detectDiffFromBandOrFallback(BufferedImage frame, int dSx, int dSy, int dW, int dH,
            String fallback) {
        try {
            BufferedImage diffBand = frame.getSubimage(dSx, dSy, dW, dH);
            String diff = ImageAnalysisService.detectDifficultyFromBand(diffBand);
            log.debug("handleDetectMode: detected difficulty from band: '{}'", diff);
            return diff;
        } catch (ImageCropNotParsed | java.awt.image.RasterFormatException e) {
            log.warn("handleDetectMode: could not read difficulty band, falling back to '{}' ({})", fallback,
                    e.getMessage());
            return fallback;
        }
    }

    /**
     * Saves 8 region crops from the detect screen to {@code out/select_*.png} for
     * OBS browser source overlays.
     *
     * @param frame
     *            full-frame detect screen capture
     */
    /**
     * Saves 8 region crops from the detect screen to {@code out/select_*.png} for
     * OBS browser source overlays.
     *
     * <p>
     * Mirrors Python {@code GenSummary.update_musicinfo()} in
     * {@code gen_summary.py:707}. All crops use the {@code info_*} param keys
     * (detect-screen info panel), not the {@code select_*} params (which are for
     * the small select-screen jacket only). The whole frame is also saved as
     * {@code select_whole.png}.
     * </p>
     *
     * @param frame
     *            full-frame detect screen capture
     */
    public void updateMusicInfo(BufferedImage frame) {
        File outDir = new File("out");
        outDir.mkdirs();
        // Each entry: {paramPrefix, outputName}
        // Param keys (e.g. info_jacket_sx) mirror Python's
        // get_detect_points('info_jacket')
        String[][] crops = {{"info_jacket", "jacket"}, {"info_title", "title"}, {"info_lv", "level"},
                {"info_diff", "difficulty"}, {"info_bpm", "bpm"}, {"info_ef", "effector"},
                {"info_illust", "illustrator"},};
        for (String[] c : crops) {
            saveMusicInfoCrop(frame, outDir, c[0], c[1]);
        }
        // Save full rotated frame as select_whole.png (mirrors Python line 724:
        // img.save('out/select_whole.png'))
        try {
            ImageIO.write(frame, "png", new File(outDir, "select_whole.png"));
        } catch (IOException e) {
            log.debug("updateMusicInfo: failed to save select_whole.png: {}", e.getMessage());
        }
    }

    private void saveMusicInfoCrop(BufferedImage frame, File outDir, String prefix, String name) {
        try {
            int x = ParamUtils.getInt(params, prefix + "_sx", 0);
            int y = ParamUtils.getInt(params, prefix + "_sy", 0);
            int w = ParamUtils.getInt(params, prefix + "_w", 100);
            int h = ParamUtils.getInt(params, prefix + "_h", 100);
            if (x == 0 && y == 0) {
                log.debug("saveMusicInfoCrop: coords not configured for '{}', skipping", name);
                return;
            }
            BufferedImage crop = safeCrop(frame, x, y, w, h);
            ImageIO.write(crop, "png", new File(outDir, "select_" + name + ".png"));
        } catch (IOException e) {
            log.debug("updateMusicInfo: failed to save {} crop: {}", name, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Volforce capture
    // -------------------------------------------------------------------------

    /**
     * Crops the Volforce and class-badge regions from the given frame, saves them
     * as PNG files for OBS browser sources, and skips unchanged frames using
     * perceptual hashing.
     *
     * @param frame
     *            full-frame capture from which to crop
     */
    /**
     * Crops the Volforce and class-badge regions from the given frame, saves them
     * as PNG files for OBS browser sources, and skips unchanged frames using
     * perceptual hashing.
     *
     * @param frame
     *            full-frame capture from which to crop
     * @return {@code true} if {@code vf_cur.png} and {@code class_cur.png} were
     *         written (i.e. the frame was bright enough and the content changed)
     */
    /**
     * Force-saves the Volforce and class regions from {@code frame}, bypassing the
     * pHash change-detection and brightness checks.
     *
     * <p>
     * Mirrors Python {@code capture_volforce()} called directly by the F4 button
     * handler — no hash comparison, always writes {@code vf_cur.png} and
     * {@code class_cur.png}.
     * </p>
     *
     * @param frame
     *            full-frame capture from which to crop
     */
    public void forceCaptureVolforce(BufferedImage frame) {
        if (frame == null) {
            log.debug("forceCaptureVolforce: frame is null, skipping");
            return;
        }
        try {
            BufferedImage vfCrop = cropVf(frame);
            BufferedImage classCrop = cropClass(frame);
            File outDir = new File("out");
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            ImageIO.write(vfCrop, "png", new File("out/vf_cur.png"));
            ImageIO.write(classCrop, "png", new File("out/class_cur.png"));
            lastVfHash = perceptualHasher.phash(vfCrop);
            if (!genFirstVf) {
                ImageIO.write(vfCrop, "png", new File("out/vf_pre.png"));
                ImageIO.write(classCrop, "png", new File("out/class_pre.png"));
                genFirstVf = true;
            }
            log.info("Force VF capture: vf_cur.png and class_cur.png saved");
        } catch (IOException e) {
            log.warn("forceCaptureVolforce failed: {}", e.getMessage());
        }
    }

    public boolean captureVolforce(BufferedImage frame) {
        if (frame == null) {
            log.debug("captureVolforce: frame is null, skipping");
            return false;
        }
        try {
            long pixelSum = computeTopLeftPixelSum(frame);
            int threshold = "true".equalsIgnoreCase(settings.get("save_on_capture")) ? 1_400_000 : 700_000;
            if (pixelSum < threshold && !"true".equalsIgnoreCase(settings.get("always_update_vf"))) {
                log.debug("captureVolforce: pixel brightness {} below threshold {}, skipping unchanged frame", pixelSum,
                        threshold);
                return false;
            }
            BufferedImage vfCrop = cropVf(frame);
            BufferedImage classCrop = cropClass(frame);

            boolean changed;
            if ("true".equalsIgnoreCase(settings.get("vf_ocr_enabled"))) {
                changed = hasVfChangedByOcr(vfCrop);
            } else {
                String vfHash = perceptualHasher.phash(vfCrop);
                changed = (lastVfHash == null) || (perceptualHasher.hammingDistance(vfHash, lastVfHash) > 2);
                lastVfHash = vfHash;
            }

            File outDir = new File("out");
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            if (changed) {
                ImageIO.write(vfCrop, "png", new File("out/vf_cur.png"));
                ImageIO.write(classCrop, "png", new File("out/class_cur.png"));
            }
            if (!genFirstVf) {
                ImageIO.write(vfCrop, "png", new File("out/vf_pre.png"));
                ImageIO.write(classCrop, "png", new File("out/class_pre.png"));
                genFirstVf = true;
            }
            return changed;
        } catch (IOException e) {
            log.warn("captureVolforce failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Determines whether the Volforce number in {@code vfCrop} has changed since
     * the last capture by running Tesseract OCR on the pre-processed crop and
     * comparing the extracted number to the stored {@link #lastVfNumber}.
     *
     * <p>
     * If OCR fails to produce a valid VF number ({@code \d+\.\d{3}}), the method
     * falls back to pHash comparison so a capture is never silently dropped.
     * </p>
     *
     * @param vfCrop
     *            already-cropped Volforce badge image
     * @return {@code true} if the VF number changed or could not be determined
     */
    private boolean hasVfChangedByOcr(BufferedImage vfCrop) {
        if (vfOcr == null) {
            vfOcr = new TesseractOcr("eng");
            vfOcr.setVariable("tessedit_char_whitelist", "0123456789.");
            log.info("captureVolforce: VF OCR engine initialised");
        }
        BufferedImage preprocessed = OcrUtils.preprocessForOcr(vfCrop);
        String raw = vfOcr.recognizeText(preprocessed);
        Matcher matcher = VF_NUMBER_PATTERN.matcher(raw != null ? raw : "");
        if (!matcher.find()) {
            log.warn("captureVolforce: OCR produced '{}' - no valid VF number found, falling back to pHash", raw);
            String vfHash = perceptualHasher.phash(vfCrop);
            boolean changed = (lastVfHash == null) || (perceptualHasher.hammingDistance(vfHash, lastVfHash) > 2);
            lastVfHash = vfHash;
            return changed;
        }
        String detectedNumber = matcher.group(1);
        boolean changed = !detectedNumber.equals(lastVfNumber);
        log.debug("captureVolforce OCR: detected='{}' last='{}' changed={}", detectedNumber, lastVfNumber, changed);
        lastVfNumber = detectedNumber;
        return changed;
    }

    private long computeTopLeftPixelSum(BufferedImage frame) {
        long sum = 0;
        for (int py = 0; py < Math.min(50, frame.getHeight()); py++) {
            for (int px = 0; px < Math.min(50, frame.getWidth()); px++) {
                int rgb = frame.getRGB(px, py);
                sum += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
            }
        }
        return sum;
    }

    private BufferedImage cropVf(BufferedImage frame) {
        int vfSx = ParamUtils.getInt(params, "vf_sx", 317);
        int vfSy = ParamUtils.getInt(params, "vf_sy", 1529);
        int vfW = ParamUtils.getInt(params, "vf_w", 96);
        int vfH = ParamUtils.getInt(params, "vf_h", 36);
        return safeCrop(frame, vfSx, vfSy, vfW, vfH);
    }

    private BufferedImage cropClass(BufferedImage frame) {
        int clSx = ParamUtils.getInt(params, "class_sx", 184);
        int clSy = ParamUtils.getInt(params, "class_sy", 1514);
        int clW = ParamUtils.getInt(params, "class_w", 118);
        int clH = ParamUtils.getInt(params, "class_h", 58);
        return safeCrop(frame, clSx, clSy, clW, clH);
    }

    // -------------------------------------------------------------------------
    // Blaster max check
    // -------------------------------------------------------------------------

    /**
     * Checks the blaster gauge region against the reference image. Plays an alert
     * WAV if the gauge is maxed and the setting is enabled.
     *
     * @param frame
     *            full-frame capture
     * @return {@code true} if the blaster gauge is at maximum
     */
    public boolean checkBlasterMax(BufferedImage frame) {
        if (frame == null) {
            log.debug("checkBlasterMax: frame is null, skipping");
            return false;
        }
        try {
            File refFile = new File("resources/images/blastermax.png");
            if (!refFile.exists()) {
                log.debug("checkBlasterMax: reference image not found at {}", refFile.getPath());
                return false;
            }
            int x = ParamUtils.getInt(params, "blastermax_sx", 0);
            int y = ParamUtils.getInt(params, "blastermax_sy", 0);
            int w = ParamUtils.getInt(params, "blastermax_w", 100);
            int h = ParamUtils.getInt(params, "blastermax_h", 50);
            if (x == 0 && y == 0) {
                log.debug("checkBlasterMax: blastermax_sx/sy not configured, skipping");
                return false;
            }
            BufferedImage region = safeCrop(frame, x, y, w, h);
            String regionHash = perceptualHasher.hash(region);
            BufferedImage refImg = ImageIO.read(refFile);
            String refHash = perceptualHasher.hash(refImg);
            boolean isMax = perceptualHasher.hammingDistance(regionHash, refHash) < 10;
            if (isMax && "true".equalsIgnoreCase(settings.get("alert_blastermax"))) {
                playBlasterMaxAlert();
            }
            return isMax;
        } catch (IOException e) {
            log.debug("checkBlasterMax failed: {}", e.getMessage());
            return false;
        }
    }

    private void playBlasterMaxAlert() {
        File wavFile = new File("resources/blastermax.wav");
        if (!wavFile.exists()) {
            log.warn("playBlasterMaxAlert: blastermax.wav not found at {}", wavFile.getPath());
            return;
        }
        try {
            javax.sound.sampled.AudioSystem.getClip()
                    .open(javax.sound.sampled.AudioSystem.getAudioInputStream(wavFile));
        } catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
            log.debug("Could not play blastermax.wav: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Detect-mode check
    // -------------------------------------------------------------------------

    /**
     * Checks if the given frame is the song-commit (detect) screen by comparing a
     * reference hash.
     *
     * @param frame
     *            full-frame capture
     * @return {@code true} if the detect screen is visible
     */
    public boolean isOnDetect(BufferedImage frame) {
        try {
            File refFile = new File("resources/images/ondetect.png");
            if (!refFile.exists()) {
                log.debug("isOnDetect: reference image not found at {}", refFile.getPath());
                return false;
            }
            int x = ParamUtils.getInt(params, "ondetect_sx", 240);
            int y = ParamUtils.getInt(params, "ondetect_sy", 1253);
            int w = ParamUtils.getInt(params, "ondetect_w", 170);
            int h = ParamUtils.getInt(params, "ondetect_h", 130);
            BufferedImage region = safeCrop(frame, x, y, w, h);
            String regionHash = perceptualHasher.hash(region);
            BufferedImage refImg = ImageIO.read(refFile);
            String refHash = perceptualHasher.hash(refImg);
            return perceptualHasher.hammingDistance(regionHash, refHash) < 10;
        } catch (IOException e) {
            log.debug("isOnDetect check failed: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Low-level image helpers
    // -------------------------------------------------------------------------

    /**
     * Crops a region from the source image, clamping coordinates to valid bounds to
     * avoid {@code RasterFormatException}.
     */
    static BufferedImage safeCrop(BufferedImage src, int x, int y, int w, int h) {
        int safeX = Math.max(0, Math.min(x, src.getWidth() - 1));
        int safeY = Math.max(0, Math.min(y, src.getHeight() - 1));
        int safeW = Math.max(1, Math.min(w, src.getWidth() - safeX));
        int safeH = Math.max(1, Math.min(h, src.getHeight() - safeY));
        return src.getSubimage(safeX, safeY, safeW, safeH);
    }
}
