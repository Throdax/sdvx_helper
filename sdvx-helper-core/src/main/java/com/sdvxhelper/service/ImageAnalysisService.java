package com.sdvxhelper.service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
     * Perceptual hashes for result-screen lamp reference images (lamp_puc.png,
     * lamp_uc.png, lamp_clear.png, lamp_failed.png). Used by
     * {@link #detectLampOnResult} to mirror Python's {@code comp_images} approach
     * in {@code gen_summary.py}.
     */
    private final Map<String, String> resultLampHashes = new HashMap<>();

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
        loadResultLampTemplate("puc");
        loadResultLampTemplate("uc");
        loadResultLampTemplate("clear");
        loadResultLampTemplate("failed");
        log.info("Loaded {} large, {} small, {} bestscore, {} select digit templates, {} result lamp templates",
                largeDigitTemplates.size(), smallDigitTemplates.size(), bestScoreTemplates.size(),
                selectScoreTemplates.size(), resultLampHashes.size());
    }

    private void loadTemplate(File file, Map<Character, String> map, char key) {
        try (InputStream is = openResource(file.getPath())) {
            if (is == null) {
                log.warn("loadTemplate: template file not found at {} (file system or classpath), skipping",
                        file.getPath());
                return;
            }
            BufferedImage img = ImageIO.read(is);
            if (img != null) {
                map.put(key, hasher.hash(img));
            }
        } catch (IOException e) {
            log.debug("Could not load template {}: {}", file.getName(), e.getMessage());
        }
    }

    private void loadLampTemplate(String name) {
        String path = "resources/images/select_lamp_" + name + ".png";
        try (InputStream is = openResource(path)) {
            if (is == null) {
                log.warn("loadTemplate: template file not found at {} (file system or classpath), skipping", path);
                return;
            }
            BufferedImage img = ImageIO.read(is);
            if (img != null) {
                selectLampHashes.put(name, hasher.hash(img));
            }
        } catch (IOException e) {
            log.debug("Could not load lamp template {}: {}", name, e.getMessage());
        }
    }

    /**
     * Loads a result-screen lamp reference image ({@code lamp_{name}.png} from
     * {@code resources/images/}) and stores its perceptual hash in
     * {@link #resultLampHashes}. Mirrors the reference images used by Python's
     * {@code comp_images} in {@code gen_summary.py}.
     *
     * @param name
     *            lamp name ({@code "puc"}, {@code "uc"}, {@code "clear"},
     *            {@code "failed"})
     */
    private void loadResultLampTemplate(String name) {
        String path = "resources/images/lamp_" + name + ".png";
        try (InputStream is = openResource(path)) {
            if (is == null) {
                log.debug("loadResultLampTemplate: file not found at {} (file system or classpath), skipping", path);
                return;
            }
            BufferedImage img = ImageIO.read(is);
            if (img != null) {
                resultLampHashes.put(name, hasher.hash(img));
            }
        } catch (IOException e) {
            log.debug("Could not load result lamp template {}: {}", name, e.getMessage());
        }
    }

    /**
     * Opens a resource stream for the given relative path. Tries the file system
     * first (production deployment where {@code resources/} sits next to the JAR),
     * then falls back to the classpath (test context and JAR-only deployments).
     *
     * <p>
     * The classpath fallback strips the leading {@code "resources/"} segment
     * because Maven places {@code src/main/resources/images/foo.png} at
     * {@code /images/foo.png} on the classpath, not at
     * {@code /resources/images/foo.png}.
     * </p>
     *
     * @param relPath
     *            relative path such as {@code "resources/images/lamp_uc.png"}
     * @return open {@link InputStream}, or {@code null} if not found anywhere
     */
    private InputStream openResource(String relPath) {
        File f = new File(relPath);
        if (f.exists()) {
            try {
                return new FileInputStream(f);
            } catch (IOException e) {
                log.debug("openResource: could not open file {}: {}", relPath, e.getMessage());
            }
        }
        // Normalise to forward slashes before classpath lookup.
        // On Windows, File.getPath() returns back-slashes (e.g.
        // "resources\images\foo.png"),
        // so the startsWith check must use the normalised form.
        // "resources/images/foo.png" → "/images/foo.png"
        // "resources\\images\\foo.png" → "/images/foo.png"
        String normalised = relPath.replace('\\', '/');
        String classpathPath = normalised.startsWith("resources/")
                ? "/" + normalised.substring("resources/".length())
                : "/" + normalised;
        InputStream is = getClass().getResourceAsStream(classpathPath);
        if (is != null) {
            log.debug("openResource: loaded {} from classpath as {}", relPath, classpathPath);
        }
        return is;
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

    /**
     * Reads the current score from the result screen using 8 digit regions (4
     * large: {@code result_score_large_{0-3}} + 4 small:
     * {@code result_score_small_{4-7}}) defined in {@code params.json}.
     *
     * <p>
     * Mirrors Python {@code GenSummary.get_score()} for the current-score digits.
     * </p>
     *
     * @param frame
     *            full-frame capture of the result screen
     * @param params
     *            detection parameters from {@code params.json}
     * @return detected current score (0–10 000 000), or {@code 0} on failure
     */
    public int getScoreOnResult(BufferedImage frame, Map<String, String> params) {
        if (largeDigitTemplates.isEmpty() || smallDigitTemplates.isEmpty()) {
            return 0;
        }
        ScoreDetector largeDetector = new ScoreDetector(largeDigitTemplates);
        ScoreDetector smallDetector = new ScoreDetector(smallDigitTemplates);
        StringBuilder digitBuffer = new StringBuilder();
        try {
            for (int i = 0; i < 4; i++) {
                int sx = ParamUtils.getInt(params, "result_score_large_" + i + "_sx", 0);
                int sy = ParamUtils.getInt(params, "result_score_large_" + i + "_sy", 0);
                int dw = ParamUtils.getInt(params, "result_score_large_" + i + "_w", 52);
                int dh = ParamUtils.getInt(params, "result_score_large_" + i + "_h", 51);
                if (sx == 0 && sy == 0) {
                    digitBuffer.append('0');
                    continue;
                }
                BufferedImage digitImg = crop(frame, sx, sy, dw, dh);
                digitBuffer.append(largeDetector.detectDigit(digitImg));
            }
            for (int i = 4; i < ScoreDetector.DIGIT_COUNT; i++) {
                int sx = ParamUtils.getInt(params, "result_score_small_" + i + "_sx", 0);
                int sy = ParamUtils.getInt(params, "result_score_small_" + i + "_sy", 0);
                int dw = ParamUtils.getInt(params, "result_score_small_" + i + "_w", 32);
                int dh = ParamUtils.getInt(params, "result_score_small_" + i + "_h", 31);
                if (sx == 0 && sy == 0) {
                    digitBuffer.append('0');
                    continue;
                }
                BufferedImage digitImg = crop(frame, sx, sy, dw, dh);
                digitBuffer.append(smallDetector.detectDigit(digitImg));
            }
            String raw = digitBuffer.toString().replace("?", "0");
            return Integer.parseInt(raw);
        } catch (NumberFormatException | java.awt.image.RasterFormatException | ArithmeticException e) {
            log.warn("getScoreOnResult failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Detects the difficulty from a difficulty-band crop by summing the R, G, B
     * channels over the left 70 columns (the coloured badge area) and comparing the
     * normalised sums against the thresholds from Python {@code GenSummary.ocr()}
     * ({@code gen_summary.py:581–591}).
     *
     * <p>
     * Raw sums are normalised to a 70×30 = 2100-pixel reference count so Python's
     * original threshold values can be applied verbatim regardless of the actual
     * image resolution:
     * </p>
     * <ul>
     * <li>NOV — blue dominant: rSum &lt; 190 000, gSum &lt; 180 000, bSum &gt; 300
     * 000</li>
     * <li>ADV — yellow: rSum &gt; 300 000, gSum &gt; 260 000, bSum &lt; 180
     * 000</li>
     * <li>EXH — red: rSum &gt; 300 000, gSum &lt; 180 000, bSum &lt; 180 000</li>
     * </ul>
     *
     * <p>
     * When no threshold matches (e.g. a black/uniform image), the crop is saved to
     * {@code ./target/out/last_error_crop.png} (test / IDE) or
     * {@code ./out/last_error_crop.png} (production) and an
     * {@link ImageCropNotParsed} exception is thrown so callers can surface the
     * failure without silently producing a wrong filename.
     * </p>
     *
     * @param diffBand
     *            difficulty-band image at any resolution
     * @return detected difficulty string ({@code "nov"}, {@code "adv"},
     *         {@code "exh"})
     * @throws ImageCropNotParsed
     *             if {@code diffBand} is {@code null}, too small to analyse, or its
     *             colour sums do not match any known difficulty
     */
    public static String detectDifficultyFromBand(BufferedImage diffBand) throws ImageCropNotParsed {
        if (diffBand == null) {
            throw new ImageCropNotParsed("Difficulty band image is null — no crop was produced");
        }
        if (diffBand.getWidth() < 2 || diffBand.getHeight() < 2) {
            throw new ImageCropNotParsed("Difficulty band image is too small to analyse (" + diffBand.getWidth() + "×"
                    + diffBand.getHeight() + " px)");
        }
        int analysisW = Math.min(70, diffBand.getWidth());
        int analysisH = diffBand.getHeight();
        long rSum = 0;
        long gSum = 0;
        long bSum = 0;
        for (int y = 0; y < analysisH; y++) {
            for (int x = 0; x < analysisW; x++) {
                int rgb = diffBand.getRGB(x, y);
                rSum += (rgb >> 16) & 0xFF;
                gSum += (rgb >> 8) & 0xFF;
                bSum += rgb & 0xFF;
            }
        }
        // Normalise to the 70×30 = 2100-pixel reference used by Python.
        long pixels = (long) analysisW * analysisH;
        long rT = rSum * 2100L / pixels;
        long gT = gSum * 2100L / pixels;
        long bT = bSum * 2100L / pixels;
        if (rT < 190000L && gT < 180000L && bT > 300000L) {
            return "nov";
        }
        if (rT > 300000L && gT > 260000L && bT < 180000L) {
            return "adv";
        }
        if (rT > 300000L && gT < 180000L && bT < 180000L) {
            return "exh";
        }
        // No threshold matched — save the crop for diagnostics and throw.
        String msg = String.format("Difficulty band colour does not match any known difficulty "
                + "(rT=%d, gT=%d, bT=%d) — crop saved to %s", rT, gT, bT, saveErrorCrop(diffBand));
        log.error(msg);
        throw new ImageCropNotParsed(msg);
    }

    /**
     * Saves {@code crop} to {@code last_error_crop.png} in the resolved output
     * directory and returns the absolute path of the saved file for use in
     * exception messages.
     *
     * <p>
     * Output directory selection:
     * </p>
     * <ul>
     * <li>{@code target/out/} — when {@code target/test-classes/} exists (test or
     * IDE run).</li>
     * <li>{@code out/} — production deployment (no {@code target/} directory).</li>
     * </ul>
     *
     * @param crop
     *            image to persist; ignored if {@code null}
     * @return absolute path of the written file, or a placeholder string if saving
     *         failed
     */
    private static String saveErrorCrop(BufferedImage crop) {
        if (crop == null) {
            return "(crop was null)";
        }
        File outDir = resolveErrorOutputDir();
        outDir.mkdirs();
        File dest = new File(outDir, "last_error_crop.png");
        try {
            ImageIO.write(crop, "png", dest);
            return dest.getAbsolutePath();
        } catch (IOException e) {
            log.warn("saveErrorCrop: could not write {}: {}", dest.getAbsolutePath(), e.getMessage());
            return dest.getAbsolutePath() + " (write failed: " + e.getMessage() + ")";
        }
    }

    /**
     * Returns the output directory to use for diagnostic artefacts.
     *
     * <ul>
     * <li>If {@code target/test-classes/} exists the JVM is running inside a
     * Maven/IDE test environment → {@code target/out/}.</li>
     * <li>Otherwise a production deployment is assumed → {@code out/}.</li>
     * </ul>
     *
     * @return resolved output {@link File} (not yet created)
     */
    static File resolveErrorOutputDir() {
        if (new File("target/test-classes").isDirectory()) {
            return new File("target/out");
        }
        return new File("out");
    }

    /**
     * Detects the clear lamp from the result screen by cropping the lamp region
     * ({@code lamp_sx/sy/w/h} from {@code params.json}) and comparing the crop
     * against reference images using perceptual hash — mirroring Python's
     * {@code comp_images} approach in {@code gen_summary.py}.
     *
     * <p>
     * Primary strategy: find the closest match in {@link #resultLampHashes} (loaded
     * from {@code resources/images/lamp_*.png}) by Hamming distance with a
     * threshold of 10 bits. Falls back to RGB-average thresholds when no reference
     * images are loaded or the best Hamming distance exceeds the threshold.
     * </p>
     *
     * @param frame
     *            full-frame capture of the result screen
     * @param params
     *            detection parameters from {@code params.json}
     * @return detected lamp string — one of {@code "puc"}, {@code "uc"},
     *         {@code "clear"}, {@code "hard"}, {@code "exh"},
     *         {@code "class_clear"}, {@code "failed"} — or {@code "uc"} as a safe
     *         default when the region is not configured or detection fails
     */
    public String detectLampOnResult(BufferedImage frame, Map<String, String> params) {
        int lsx = ParamUtils.getInt(params, "lamp_sx", -1);
        int lsy = ParamUtils.getInt(params, "lamp_sy", -1);
        int lw = ParamUtils.getInt(params, "lamp_w", 230);
        int lh = ParamUtils.getInt(params, "lamp_h", 50);
        if (lsx < 0 || lsy < 0) {
            return "uc";
        }
        try {
            BufferedImage lampRegion = crop(frame, lsx, lsy, lw, lh);

            // Primary: perceptual-hash comparison against reference lamp images.
            // Mirrors Python: comp_images(parts['lamp_crop'],
            // Image.open('resources/images/lamp_*.png'))
            if (!resultLampHashes.isEmpty()) {
                String lampHash = hasher.hash(lampRegion);
                String bestName = null;
                int bestDist = Integer.MAX_VALUE;
                for (Map.Entry<String, String> entry : resultLampHashes.entrySet()) {
                    int dist = hasher.hammingDistance(lampHash, entry.getValue());
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestName = entry.getKey();
                    }
                }
                if (bestName != null && bestDist < 10) {
                    // When the lamp icon matches lamp_clear.png, the gauge colour
                    // distinguishes between HARD, MAXXIVE (exh), NORMAL and CLASS clears.
                    // Mirrors gen_summary.py:355-366.
                    if ("clear".equals(bestName)) {
                        String fromGauge = detectLampFromGauge(frame, params);
                        log.debug("detectLampOnResult: 'clear' hash match dist={}, gauge → '{}'", bestDist, fromGauge);
                        return fromGauge;
                    }
                    log.debug("detectLampOnResult: hash match '{}' dist={}", bestName, bestDist);
                    return bestName;
                }
                log.debug("detectLampOnResult: best hash match '{}' dist={} >= threshold, falling back to RGB avg",
                        bestName, bestDist);
            }

            // Fallback: RGB-average thresholds (select-screen approach).
            return detectLamp(lampRegion);
        } catch (java.awt.image.RasterFormatException | ArithmeticException e) {
            log.debug("detectLampOnResult: lamp crop failed: {}", e.getMessage());
            return "uc";
        }
    }

    /**
     * Disambiguates a {@code "clear"} lamp result by analysing the gauge colour,
     * exactly mirroring Python {@code gen_summary.py:356–366}.
     *
     * <p>
     * When the lamp region hash matches {@code lamp_clear.png}, the gauge bar's
     * colour encodes the actual clear type:
     * </p>
     * <ul>
     * <li>{@code rSum + gSum + bSum > 800 000} — very bright gauge → {@code "exh"}
     * (MAXXIVE / EX-HARD clear)</li>
     * <li>{@code rSum < gSum} — green dominant → {@code "clear"} (normal
     * clear)</li>
     * <li>{@code gSum > 200 000} — moderate green → {@code "class_clear"}</li>
     * <li>otherwise → {@code "hard"} (hard-gauge clear)</li>
     * </ul>
     *
     * @param frame
     *            full result-screen image
     * @param params
     *            detection parameters from {@code params.json}; must contain
     *            {@code gauge_sx / gauge_sy / gauge_w / gauge_h}
     * @return refined lamp string
     */
    private String detectLampFromGauge(BufferedImage frame, Map<String, String> params) {
        int gsx = ParamUtils.getInt(params, "gauge_sx", -1);
        int gsy = ParamUtils.getInt(params, "gauge_sy", -1);
        int gw = ParamUtils.getInt(params, "gauge_w", 100);
        int gh = ParamUtils.getInt(params, "gauge_h", 16);
        if (gsx < 0 || gsy < 0) {
            log.debug("detectLampFromGauge: gauge coords not configured, defaulting to 'clear'");
            return "clear";
        }
        try {
            BufferedImage gauge = crop(frame, gsx, gsy, gw, gh);
            long rSum = 0;
            long gSum = 0;
            long bSum = 0;
            for (int y = 0; y < gauge.getHeight(); y++) {
                for (int x = 0; x < gauge.getWidth(); x++) {
                    int rgb = gauge.getRGB(x, y);
                    rSum += (rgb >> 16) & 0xFF;
                    gSum += (rgb >> 8) & 0xFF;
                    bSum += rgb & 0xFF;
                }
            }
            log.debug("detectLampFromGauge: rSum={} gSum={} bSum={} total={}", rSum, gSum, bSum, rSum + gSum + bSum);
            if (rSum + gSum + bSum > 800_000L) {
                return "exh";
            }
            if (rSum < gSum) {
                return "clear";
            }
            if (gSum > 200_000L) {
                return "class_clear";
            }
            return "hard";
        } catch (java.awt.image.RasterFormatException e) {
            log.debug("detectLampFromGauge: gauge crop failed: {}", e.getMessage());
            return "clear";
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
