package com.sdvxhelper.service;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sdvxhelper.model.enums.DetectMode;
import com.sdvxhelper.ocr.PerceptualHasher;
import com.sdvxhelper.repository.MusicListRepository;

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

    private final PerceptualHasher hasher;
    private final MusicListRepository musicListRepo;

    /**
     * Constructs the service.
     *
     * @param musicListRepo repository providing the jacket-hash index
     */
    public ImageAnalysisService(MusicListRepository musicListRepo) {
        this.hasher = new PerceptualHasher();
        this.musicListRepo = musicListRepo;
    }

    /**
     * Determines the current game state from a full-frame capture by testing a set
     * of known reference image crops.
     *
     * <p>
     * This is a placeholder implementation; the real detection uses a region
     * defined in {@code params.json} to crop a reference area, computes its
     * perceptual hash, and compares against known state hashes. The detection
     * pipeline should be populated in the concrete OBS-capture integration.
     * </p>
     *
     * @param frame        full-frame {@link BufferedImage} captured from OBS
     * @param stateHashes  map from {@link DetectMode} to its reference hash string
     * @param regionParams map of parameter keys to numeric region values from
     *                     params.json
     * @return detected {@link DetectMode}
     */
    public DetectMode detectMode(BufferedImage frame, Map<DetectMode, String> stateHashes, Map<String, Object> regionParams) {
        // The detection region for the mode indicator (configurable via params.json)
        try {
            int x = toInt(regionParams.get("mode_x"));
            int y = toInt(regionParams.get("mode_y"));
            int w = toInt(regionParams.get("mode_w"));
            int h = toInt(regionParams.get("mode_h"));

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

        } catch (Exception e) {
            log.warn("detectMode failed: {}", e.getMessage());
            return DetectMode.INIT;
        }
    }

    /**
     * Identifies the song currently shown on the jacket area of the select screen.
     *
     * @param frame        full-frame capture
     * @param jacketRegion crop rectangle for the jacket area (in pixels)
     * @param difficulty   difficulty string to look up in the jacket-hash index
     * @return {@code String[]{title, difficulty}} if found, or {@code null}
     */
    public String[] identifyJacket(BufferedImage frame, Rectangle jacketRegion, String difficulty) {
        try {
            BufferedImage jacketCrop = crop(frame, jacketRegion.x, jacketRegion.y, jacketRegion.width, jacketRegion.height);
            String hash = hasher.hash(jacketCrop);
            return musicListRepo.findByJacketHash(hash);
        } catch (Exception e) {
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
     * @param lampRegionImage cropped image of the lamp indicator area
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

        // Heuristic lamp detection based on dominant colour channel.
        // Thresholds approximate those used in the Python implementation.
        if (rAvg > LAMP_THRESHOLD && gAvg > LAMP_THRESHOLD && bAvg > LAMP_THRESHOLD)
            return "puc";
        if (rAvg > LAMP_THRESHOLD && gAvg > LAMP_THRESHOLD)
            return "uc";
        if (gAvg > LAMP_THRESHOLD)
            return "clear";
        if (rAvg > LAMP_THRESHOLD)
            return "hard";
        if (bAvg > LAMP_THRESHOLD)
            return "exh";
        return "failed";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Crops a region from the source image, guarding against out-of-bounds errors.
     *
     * @param src source image
     * @param x   top-left x coordinate of the crop rectangle
     * @param y   top-left y coordinate of the crop rectangle
     * @param w   width of the crop rectangle
     * @param h   height of the crop rectangle
     * @return cropped image, or a smaller region if the requested rectangle exceeds bounds
     */
    private static BufferedImage crop(BufferedImage src, int x, int y, int w, int h) {
        // Guard against out-of-bounds
        int safeX = Math.clamp(x, 0, src.getWidth() - 1);
        int safeY = Math.clamp(y, 0, src.getHeight() - 1);
        int safeW = Math.min(w, src.getWidth() - safeX);
        int safeH = Math.min(h, src.getHeight() - safeY);
        
        return src.getSubimage(safeX, safeY, safeW, safeH);
    }

    /**
     * Converts an object to an integer, handling both numeric types and strings.
     * @param val
     * @return
     */
    private static int toInt(Object val) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        
        return Integer.parseInt(String.valueOf(val));
    }
}
