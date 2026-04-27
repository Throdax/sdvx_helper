package com.sdvxhelper.app.controller.detection;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.sdvxhelper.model.MusicInfo;
import com.sdvxhelper.model.OnePlayData;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.service.CsvExportService;
import com.sdvxhelper.service.ImageAnalysisService;
import com.sdvxhelper.service.SdvxLoggerService;
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
    private SdvxLoggerService loggerService;
    private XmlExportService xmlExportService;
    private CsvExportService csvExportService;
    private PerceptualHasher perceptualHasher;
    private Map<String, String> params;
    private Map<String, String> settings;

    // Volforce capture state
    private String lastVfHash = null;
    private boolean genFirstVf = false;

    // Result tracking state
    private List<OnePlayData> sessionPlays = new ArrayList<>();
    private double currentTotalVf = 0.0;
    private double previousTotalVf = 0.0;

    /**
     * @param imageAnalysisService
     *            image analysis service
     * @param loggerService
     *            play log and music-info service
     * @param xmlExportService
     *            XML overlay export service
     * @param csvExportService
     *            CSV export service (for Google Drive sync)
     * @param perceptualHasher
     *            perceptual hashing utility
     * @param params
     *            detection parameters map
     * @param settings
     *            application settings map
     */
    public ScreenHandler(ImageAnalysisService imageAnalysisService, SdvxLoggerService loggerService,
            XmlExportService xmlExportService, CsvExportService csvExportService, PerceptualHasher perceptualHasher,
            Map<String, String> params, Map<String, String> settings) {
        this.imageAnalysisService = imageAnalysisService;
        this.loggerService = loggerService;
        this.xmlExportService = xmlExportService;
        this.csvExportService = csvExportService;
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
     * @return the recorded play, or {@code null} if processing failed
     */
    public OnePlayData handleResultScreen(BufferedImage frame) {
        if (frame == null) {
            log.debug("handleResultScreen: frame is null, skipping");
            return null;
        }
        try {
            String lamp = detectLampFromFrame(frame);
            BufferedImage jacketCrop = cropJacketLog(frame);
            String[] identified = identifyResultJacket(frame);
            String title = identified != null ? identified[0] : "Unknown";
            String difficulty = identified != null ? identified[1] : "exh";

            MusicInfo best = loggerService.getBestFor(title, difficulty);
            int preScore = best != null ? best.getBestScore() : 0;
            int score = readScore(frame);

            OnePlayData play = new OnePlayData(title, score, preScore, lamp, difficulty,
                    LocalDateTime.now().toString());
            loggerService.pushPlay(play);
            sessionPlays.add(play);

            previousTotalVf = currentTotalVf;
            currentTotalVf = loggerService.getTotalVfInt() / 1000.0;

            saveResultFiles(frame, jacketCrop, title, difficulty, lamp, score);
            writeResultXml(title, difficulty);
            saveGoogleDriveCsv();
            captureVolforce(frame);

            return play;
        } catch (IOException e) {
            log.error("handleResultScreen failed", e);
            return null;
        }
    }

    private String detectLampFromFrame(BufferedImage frame) {
        int sx = ParamUtils.getInt(params, "lamp_sx", 630);
        int sy = ParamUtils.getInt(params, "lamp_sy", 930);
        int lw = ParamUtils.getInt(params, "lamp_w", 230);
        int lh = ParamUtils.getInt(params, "lamp_h", 50);
        return imageAnalysisService.detectLamp(safeCrop(frame, sx, sy, lw, lh));
    }

    private BufferedImage cropJacketLog(BufferedImage frame) {
        int jSx = ParamUtils.getInt(params, "log_crop_jacket_sx", 57);
        int jSy = ParamUtils.getInt(params, "log_crop_jacket_sy", 916);
        int jW = ParamUtils.getInt(params, "log_crop_jacket_w", 263);
        int jH = ParamUtils.getInt(params, "log_crop_jacket_h", 263);
        return safeCrop(frame, jSx, jSy, jW, jH);
    }

    private String[] identifyResultJacket(BufferedImage frame) {
        int jacketSx = ParamUtils.getInt(params, "info_jacket_sx", 237);
        int jacketSy = ParamUtils.getInt(params, "info_jacket_sy", 387);
        int jacketW = ParamUtils.getInt(params, "info_jacket_w", 607);
        int jacketH = ParamUtils.getInt(params, "info_jacket_h", 607);
        return imageAnalysisService.identifyJacket(frame, new Rectangle(jacketSx, jacketSy, jacketW, jacketH), "");
    }

    private int readScore(BufferedImage frame) {
        int score = 0;
        for (int i = 0; i < 4; i++) {
            score = score * 10 + digitAt(frame, "result_score_large_" + i, 52, 51);
        }
        for (int i = 4; i < 8; i++) {
            score = score * 10 + digitAt(frame, "result_score_small_" + i, 32, 31);
        }
        return score;
    }

    private void saveResultFiles(BufferedImage frame, BufferedImage jacketCrop, String title, String difficulty,
            String lamp, int score) throws IOException {
        String autosaveDir = settings.getOrDefault("autosave_dir", "out");
        File autosaveDirFile = new File(autosaveDir);
        if (!autosaveDirFile.exists()) {
            autosaveDirFile.mkdirs();
        }
        String diff4 = difficulty.toUpperCase();
        String score4 = String.format("%04d", score / 10000);
        String timestamp = LocalDateTime.now().toString().replace(":", "-").replace(".", "-");
        String fileName = "sdvx_" + StringUtils.sanitize(title) + "_" + diff4 + "_" + lamp + "_" + score4 + "_"
                + timestamp + ".png";
        ImageIO.write(frame, "png", new File(autosaveDir, fileName));
        if ("true".equalsIgnoreCase(settings.get("save_jacketimg"))) {
            ImageIO.write(jacketCrop, "png", new File(autosaveDir, StringUtils.sanitize(title) + "_jacket.png"));
        }
    }

    private void writeResultXml(String title, String difficulty) {
        if (xmlExportService == null) {
            log.debug("writeResultXml: xmlExportService not available, skipping XML export");
            return;
        }
        try {
            List<OnePlayData> history = loggerService.getPlaysFor(title, difficulty);
            MusicInfo info = loggerService.getBestFor(title, difficulty);
            int lv = info != null ? info.getLvAsInt() : -1;
            xmlExportService.writeHistoryCurSong(history, lv, new File("out/history_cursong.xml"));
            xmlExportService.writeTotalVf(loggerService.getBestAllFumen(), loggerService.getTotalVfInt(),
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
            csvExportService.writeBestCsv(loggerService.getBestAllFumen(), new File(myGdrive, "sdvx_helper_best.csv"));
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
            String difficulty = identified[1];
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
        MusicInfo best = loggerService.getBestFor(title, difficulty);
        int preScore = best != null ? best.getBestScore() : 0;
        String bestLamp = best != null ? best.getBestLamp() : "failed";
        if (score <= preScore) {
            log.debug("importScoreFromSelect: score {} not better than preScore {}, skipping", score, preScore);
            return null;
        }
        OnePlayData play = new OnePlayData(title, score, preScore, bestLamp, difficulty,
                LocalDateTime.now().toString());
        loggerService.pushPlay(play);
        return play;
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
            List<OnePlayData> history = loggerService.getPlaysFor(title, diff);
            xmlExportService.writeHistoryCurSong(history, -1, new File("out/history_cursong.xml"));
        } catch (IOException e) {
            log.debug("writeRivalViewXml failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Detect (song-commit) screen
    // -------------------------------------------------------------------------

    /**
     * Handles the DETECT screen: sleeps {@code detect_wait} seconds, saves info
     * crops, then identifies the jacket.
     *
     * @param frame
     *            frame at the time detect was triggered
     * @param latestFrame
     *            more recent frame to use for identification (may be same)
     * @return {@code {title, diff}} array if identified, {@code null} otherwise
     */
    public String[] handleDetectMode(BufferedImage frame, BufferedImage latestFrame) {
        double detectWait = ParamUtils.parseDoubleParam(settings.get("detect_wait"), 2.7);
        try {
            Thread.sleep((long) (detectWait * 1000));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
        BufferedImage freshFrame = latestFrame != null ? latestFrame : frame;
        updateMusicInfo(freshFrame);
        int jSx = ParamUtils.getInt(params, "select_jacket_sx", 94);
        int jSy = ParamUtils.getInt(params, "select_jacket_sy", 242);
        int jW = ParamUtils.getInt(params, "select_jacket_w", 352);
        int jH = ParamUtils.getInt(params, "select_jacket_h", 352);
        return imageAnalysisService.identifyJacket(freshFrame, new Rectangle(jSx, jSy, jW, jH), "");
    }

    /**
     * Saves 8 region crops from the detect screen to {@code out/select_*.png} for
     * OBS browser source overlays.
     *
     * @param frame
     *            full-frame detect screen capture
     */
    public void updateMusicInfo(BufferedImage frame) {
        File outDir = new File("out");
        outDir.mkdirs();
        String[][] crops = {{"select_jacket", "jacket"}, {"select_title", "title"}, {"select_level", "level"},
                {"select_difficulty", "difficulty"}, {"select_bpm", "bpm"}, {"select_effector", "effector"},
                {"select_illustrator", "illustrator"}, {"select_whole", "whole"},};
        for (String[] c : crops) {
            saveMusicInfoCrop(frame, outDir, c[0], c[1]);
        }
    }

    private void saveMusicInfoCrop(BufferedImage frame, File outDir, String prefix, String name) {
        try {
            int x = ParamUtils.getInt(params, prefix + "_sx", 0);
            int y = ParamUtils.getInt(params, prefix + "_sy", 0);
            int w = ParamUtils.getInt(params, prefix + "_w", 100);
            int h = ParamUtils.getInt(params, prefix + "_h", 100);
            if (x == 0 && y == 0) {
                log.debug("saveMusicInfoCrop: coords not configured for crop '{}', skipping", name);
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
    public void captureVolforce(BufferedImage frame) {
        if (frame == null) {
            log.debug("captureVolforce: frame is null, skipping");
            return;
        }
        try {
            long pixelSum = computeTopLeftPixelSum(frame);
            int threshold = "true".equalsIgnoreCase(settings.get("save_on_capture")) ? 1_400_000 : 700_000;
            if (pixelSum < threshold && !"true".equalsIgnoreCase(settings.get("always_update_vf"))) {
                log.debug("captureVolforce: pixel brightness {} below threshold {}, skipping unchanged frame", pixelSum,
                        threshold);
                return;
            }
            BufferedImage vfCrop = cropVf(frame);
            BufferedImage classCrop = cropClass(frame);
            String vfHash = perceptualHasher.hash(vfCrop);
            boolean changed = (lastVfHash == null) || (perceptualHasher.hammingDistance(vfHash, lastVfHash) > 2);
            lastVfHash = vfHash;

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
        } catch (IOException e) {
            log.warn("captureVolforce failed: {}", e.getMessage());
        }
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

    private int digitAt(BufferedImage frame, String keyPrefix, int defaultW, int defaultH) {
        int sx = ParamUtils.getInt(params, keyPrefix + "_sx", 0);
        int sy = ParamUtils.getInt(params, keyPrefix + "_sy", 0);
        int w = ParamUtils.getInt(params, keyPrefix + "_w", defaultW);
        int h = ParamUtils.getInt(params, keyPrefix + "_h", defaultH);
        if (sx == 0 && sy == 0) {
            return 0;
        }
        safeCrop(frame, sx, sy, w, h);
        return 0;
    }

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
