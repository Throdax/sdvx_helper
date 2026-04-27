package com.sdvxhelper.service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

import com.sdvxhelper.model.SelectResult;
import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.ocr.ScoreDetector;
import com.sdvxhelper.repository.MusicListRepository;
import com.sdvxhelper.util.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyses captured OBS frames to determine the current game state and song
 * identity.
 *
 * <p>
 * Decomposes the image-analysis portion of the Python {@code GenSummary} class
 * and the detection logic in {@code sdvx_helper.pyw}. The service uses
 * {@link PerceptualHasher} to compare jacket image crops against the music-list
 * database to identify the currently selected or playing song.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImageAnalysisService.class);

    /** Pixel-sum threshold for lamp detection. */
    private static final int LAMP_THRESHOLD = 200;

    private PerceptualHasher hasher;
    private MusicListRepository musicListRepo;

    /**
     * Perceptual hashes for result-screen large digits (result_score_l{0-9}.png).
     */
    private Map<Character, String> largeDigitTemplates = new HashMap<>();

    /**
     * Perceptual hashes for result-screen small digits (result_score_s{0-9}.png).
     */
    private Map<Character, String> smallDigitTemplates = new HashMap<>();

    /** Perceptual hashes for best-score digits (result_bestscore_{0-9}.png). */
    private Map<Character, String> bestScoreTemplates = new HashMap<>();

    /**
     * Perceptual hashes for select-screen score digits (select_score_s{0-9}.png).
     */
    private Map<Character, String> selectScoreTemplates = new HashMap<>();

    /**
     * Perceptual hashes for select-screen lamp (select_lamp_puc.png,
     * select_lamp_uc.png).
     */
    private Map<String, String> selectLampHashes = new HashMap<>();

    /**
     * Constructs the service and loads digit template hashes from
     * {@code resources/images/}.
     *
     * @param musicListRepo
     *            repository providing the jacket-hash index
     */
    public ImageAnalysisService(MusicListRepository musicListRepo) {
        this.hasher = new PerceptualHasher();
        this.musicListRepo = musicListRepo;
        loadDigitTemplates();
    }

    /**
     * Loads digit template images from {@code resources/images/} and pre-computes
     * their perceptual hashes. Missing files are silently skipped.
     */
    private void loadDigitTemplates() {
        for (int i = 0; i < 10; i++) {
            char digit = (char) ('0' + i);
            loadTemplate(new File("resources/images/result_score_l" + i + ".png"), largeDigitTemplates, digit);
            loadTemplate(new File("resources/images/result_score_s" + i + ".png"), smallDigitTemplates, digit);
            loadTemplate(new File("resources/images/result_bestscore_" + i + ".png"), bestScoreTemplates, digit);
            loadTemplate(new File("resources/images/select_score_s" + i + ".png"), selectScoreTemplates, digit);
        }
        loadLampTemplate("puc");
        loadLampTemplate("uc");
        log.info("Loaded {} large, {} small, {} bestscore, {} select digit templates", largeDigitTemplates.size(),
                smallDigitTemplates.size(), bestScoreTemplates.size(), selectScoreTemplates.size());
    }

    private void loadTemplate(File file, Map<Character, String> map, char key) {
        if (!file.exists()) {
            log.warn("loadTemplate: template file not found at {}, skipping", file.getPath());
            return;
        }
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                map.put(key, hasher.hash(img));
            }
        } catch (IOException e) {
            log.debug("Could not load template {}: {}", file.getName(), e.getMessage());
        }
    }

    private void loadLampTemplate(String name) {
        File file = new File("resources/images/select_lamp_" + name + ".png");
        if (!file.exists()) {
            log.warn("loadTemplate: template file not found at {}, skipping", file.getPath());
            return;
        }
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) {
                selectLampHashes.put(name, hasher.hash(img));
            }
        } catch (IOException e) {
            log.debug("Could not load lamp template {}: {}", name, e.getMessage());
        }
    }

    /**
     * Determines the current game state from a full-frame capture by testing a set
     * of known reference image crops.
     *
     * <p>
     * The detection uses a region defined in {@code params.json} to crop a
     * reference area, computes its perceptual hash, and compares against known
     * state hashes.
     * </p>
     *
     * @param frame
     *            full-frame {@link BufferedImage} captured from OBS
     * @param stateHashes
     *            map from {@link DetectMode} to its reference hash string
     * @param regionParams
     *            map of parameter keys to string values from params.json
     * @return detected {@link DetectMode}
     */
    public DetectMode detectMode(BufferedImage frame, Map<DetectMode, String> stateHashes,
            Map<String, String> regionParams) {
        try {
            int x = ParamUtils.getInt(regionParams, "ondetect_sx", ParamUtils.getInt(regionParams, "mode_x", 240));
            int y = ParamUtils.getInt(regionParams, "ondetect_sy", ParamUtils.getInt(regionParams, "mode_y", 1253));
            int w = ParamUtils.getInt(regionParams, "ondetect_w", ParamUtils.getInt(regionParams, "mode_w", 170));
            int h = ParamUtils.getInt(regionParams, "ondetect_h", ParamUtils.getInt(regionParams, "mode_h", 130));

            BufferedImage crop = crop(frame, x, y, w, h);
            String cropHash = hasher.hash(crop);

            DetectMode best = DetectMode.INIT;
            int bestDist = Integer.MAX_VALUE;

            for (Map.Entry<DetectMode, String> entry : stateHashes.entrySet()) {
                int dist = hasher.hammingDistance(cropHash, entry.getValue());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entry.getKey();
                }
            }
            log.debug("detectMode -> {} (hamming={})", best, bestDist);
            return best;

        } catch (java.awt.image.RasterFormatException | ArithmeticException | IllegalArgumentException e) {
            log.warn("detectMode failed: {}", e.getMessage());
            return DetectMode.INIT;
        }
    }

    /**
     * Identifies the song currently shown on the jacket area of the select screen.
     *
     * @param frame
     *            full-frame capture
     * @param jacketRegion
     *            crop rectangle for the jacket area (in pixels)
     * @param difficulty
     *            difficulty string to look up in the jacket-hash index
     * @return {@code String[]{title, difficulty}} if found, or {@code null}
     */
    public String[] identifyJacket(BufferedImage frame, Rectangle jacketRegion, String difficulty) {
        try {
            BufferedImage jacketCrop = crop(frame, jacketRegion.x, jacketRegion.y, jacketRegion.width,
                    jacketRegion.height);
            String hash = hasher.hash(jacketCrop);
            return musicListRepo.findByJacketHash(hash);
        } catch (java.awt.image.RasterFormatException | IllegalArgumentException e) {
            log.warn("identifyJacket failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Detects the lamp type from a result-screen crop using pixel-sum thresholding.
     *
     * <p>
     * The Python implementation sums the colour channels of a lamp-indicator region
     * and compares against pre-measured thresholds. This method accepts the
     * pre-cropped lamp region image.
     * </p>
     *
     * @param lampRegionImage
     *            cropped image of the lamp indicator area
     * @return detected lamp string ({@code "puc"}, {@code "uc"}, {@code "exh"},
     *         {@code "hard"}, {@code "clear"}, or {@code "failed"})
     */
    public String detectLamp(BufferedImage lampRegionImage) {
        long rSum = 0;
        long bSum = 0;
        long gSum = 0;

        int width = lampRegionImage.getWidth();
        int height = lampRegionImage.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = lampRegionImage.getRGB(x, y);
                rSum += (rgb >> 16) & 0xFF;
                gSum += (rgb >> 8) & 0xFF;
                bSum += rgb & 0xFF;
            }
        }

        int pixels = width * height;
        int rAvg = (int) (rSum / pixels);
        int gAvg = (int) (gSum / pixels);
        int bAvg = (int) (bSum / pixels);

        if (rAvg > LAMP_THRESHOLD && gAvg > LAMP_THRESHOLD && bAvg > LAMP_THRESHOLD) {
            return "puc";
        }
        if (rAvg > LAMP_THRESHOLD && gAvg > LAMP_THRESHOLD) {
            return "uc";
        }
        if (gAvg > LAMP_THRESHOLD) {
            return "clear";
        }
        if (rAvg > LAMP_THRESHOLD) {
            return "hard";
        }
        if (bAvg > LAMP_THRESHOLD) {
            return "exh";
        }
        return "failed";
    }

    /**
     * Reads the score displayed on the song-select screen by cropping eight digit
     * regions (four large + four small) defined in {@code params.json} and running
     * them through the {@link ScoreDetector} perceptual-hash pipeline.
     *
     * <p>
     * Mirrors the Python {@code GenSummary.get_score_on_select()} method.
     * </p>
     *
     * @param frame
     *            full-frame capture of the select screen
     * @param params
     *            detection parameters loaded from {@code params.json}
     * @param digitTemplates
     *            map from digit character ('0'–'9') to its perceptual hash string;
     *            supply an empty map to receive {@code 0} (unrecognised)
     * @return detected score in the range 0–10 000 000, or {@code 0} on failure
     */
    public int getScoreOnSelect(BufferedImage frame, Map<String, String> params,
            Map<Character, String> digitTemplates) {
        Map<Character, String> templates = digitTemplates.isEmpty() ? selectScoreTemplates : digitTemplates;
        ScoreDetector detector = new ScoreDetector(templates);
        StringBuilder digitBuffer = new StringBuilder();
        try {
            for (int i = 0; i < 4; i++) {
                int sx = ParamUtils.getInt(params, "select_score_large_" + i + "_sx", 0);
                int sy = ParamUtils.getInt(params, "select_score_large_" + i + "_sy", 0);
                int dw = ParamUtils.getInt(params, "select_score_large_" + i + "_w", 42);
                int dh = ParamUtils.getInt(params, "select_score_large_" + i + "_h", 40);
                BufferedImage digitImg = crop(frame, sx, sy, dw, dh);
                digitBuffer.append(detector.detectDigit(digitImg));
            }
            for (int i = 4; i < ScoreDetector.DIGIT_COUNT; i++) {
                int sx = ParamUtils.getInt(params, "select_score_small_" + i + "_sx", 0);
                int sy = ParamUtils.getInt(params, "select_score_small_" + i + "_sy", 0);
                int dw = ParamUtils.getInt(params, "select_score_small_" + i + "_w", 24);
                int dh = ParamUtils.getInt(params, "select_score_small_" + i + "_h", 24);
                BufferedImage digitImg = crop(frame, sx, sy, dw, dh);
                digitBuffer.append(detector.detectDigit(digitImg));
            }
            String raw = digitBuffer.toString().replace("?", "0");
            return Integer.parseInt(raw);
        } catch (NumberFormatException | java.awt.image.RasterFormatException | ArithmeticException e) {
            log.warn("getScoreOnSelect failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Extended select-screen analysis that returns score, lamp, and arcade flag,
     * matching Python {@code GenSummary.get_score_on_select()}.
     *
     * <p>
     * Lamp detection: uses perceptual hash against {@code select_lamp_puc.png} and
     * {@code select_lamp_uc.png} for PUC/UC; falls back to pixel-sum heuristics for
     * other lamps.
     * </p>
     *
     * @param frame
     *            full-frame capture of the select screen
     * @param params
     *            detection parameters from {@code params.json}
     * @return {@link SelectResult} with score, lamp, and arcade flag
     */
    public SelectResult getScoreOnSelectFull(BufferedImage frame, Map<String, String> params) {
        int score = getScoreOnSelect(frame, params, new HashMap<>());
        String lamp = detectLampFromParams(frame, params);
        boolean arcade = detectArcadeFromParams(frame, params);
        return new SelectResult(score, lamp, arcade);
    }

    private String detectLampFromParams(BufferedImage frame, Map<String, String> params) {
        String lamp = "clear";
        try {
            int lsx = ParamUtils.getInt(params, "select_lamp_sx", 0);
            int lsy = ParamUtils.getInt(params, "select_lamp_sy", 0);
            int lw = ParamUtils.getInt(params, "select_lamp_w", 50);
            int lh = ParamUtils.getInt(params, "select_lamp_h", 50);
            if (lsx > 0 || lsy > 0) {
                BufferedImage lampRegion = crop(frame, lsx, lsy, lw, lh);
                String lampHash = hasher.hash(lampRegion);
                String pucHash = selectLampHashes.get("puc");
                String ucHash = selectLampHashes.get("uc");
                if (pucHash != null && hasher.hammingDistance(lampHash, pucHash) < 10) {
                    lamp = "puc";
                } else if (ucHash != null && hasher.hammingDistance(lampHash, ucHash) < 10) {
                    lamp = "uc";
                } else {
                    lamp = detectLamp(lampRegion);
                }
            }
        } catch (java.awt.image.RasterFormatException | ArithmeticException e) {
            log.debug("Lamp detection failed: {}", e.getMessage());
        }
        return lamp;
    }

    private boolean detectArcadeFromParams(BufferedImage frame, Map<String, String> params) {
        try {
            int asx = ParamUtils.getInt(params, "select_arcade_sx", 0);
            int asy = ParamUtils.getInt(params, "select_arcade_sy", 0);
            int aw = ParamUtils.getInt(params, "select_arcade_w", 50);
            int ah = ParamUtils.getInt(params, "select_arcade_h", 50);
            if (asx > 0 || asy > 0) {
                BufferedImage arcadeRegion = crop(frame, asx, asy, aw, ah);
                long pixelSum = 0;
                for (int py = 0; py < arcadeRegion.getHeight(); py++) {
                    for (int px = 0; px < arcadeRegion.getWidth(); px++) {
                        int rgb = arcadeRegion.getRGB(px, py);
                        pixelSum += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                    }
                }
                return pixelSum > 10000;
            }
        } catch (java.awt.image.RasterFormatException | ArithmeticException e) {
            log.debug("Arcade detection failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Reads the previous best score from the result screen using 8 digit regions
     * ({@code result_bestscore_{0-7}}) defined in {@code params.json}.
     *
     * <p>
     * Mirrors Python {@code GenSummary.get_score()} for the bestscore regions.
     * </p>
     *
     * @param frame
     *            full-frame capture of the result screen
     * @param params
     *            detection parameters from {@code params.json}
     * @return detected best score (0–10 000 000), or {@code 0} on failure
     */
    public int getBestScore(BufferedImage frame, Map<String, String> params) {
        if (bestScoreTemplates.isEmpty()) {
            return 0;
        }
        ScoreDetector detector = new ScoreDetector(bestScoreTemplates);
        StringBuilder digitBuffer = new StringBuilder();
        try {
            for (int i = 0; i < ScoreDetector.DIGIT_COUNT; i++) {
                int sx = ParamUtils.getInt(params, "result_bestscore_" + i + "_sx", 0);
                int sy = ParamUtils.getInt(params, "result_bestscore_" + i + "_sy", 0);
                int dw = ParamUtils.getInt(params, "result_bestscore_" + i + "_w", 32);
                int dh = ParamUtils.getInt(params, "result_bestscore_" + i + "_h", 31);
                if (sx == 0 && sy == 0) {
                    digitBuffer.append('0');
                    continue;
                }
                BufferedImage digitImg = crop(frame, sx, sy, dw, dh);
                digitBuffer.append(detector.detectDigit(digitImg));
            }
            String raw = digitBuffer.toString().replace("?", "0");
            return Integer.parseInt(raw);
        } catch (NumberFormatException | java.awt.image.RasterFormatException | ArithmeticException e) {
            log.warn("getBestScore failed: {}", e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Crops a region from the source image, guarding against out-of-bounds errors.
     *
     * @param src
     *            source image
     * @param x
     *            top-left x coordinate of the crop rectangle
     * @param y
     *            top-left y coordinate of the crop rectangle
     * @param w
     *            width of the crop rectangle
     * @param h
     *            height of the crop rectangle
     * @return cropped image, or a smaller region if the requested rectangle exceeds
     *         bounds
     */
    private static BufferedImage crop(BufferedImage src, int x, int y, int w, int h) {
        int safeX = Math.clamp(x, 0, src.getWidth() - 1);
        int safeY = Math.clamp(y, 0, src.getHeight() - 1);
        int safeW = Math.min(w, src.getWidth() - safeX);
        int safeH = Math.min(h, src.getHeight() - safeY);
        return src.getSubimage(safeX, safeY, safeW, safeH);
    }
}
