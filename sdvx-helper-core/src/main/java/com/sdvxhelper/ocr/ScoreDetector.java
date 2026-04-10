package com.sdvxhelper.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Detects numeric scores from result-screen images using template perceptual hashing.
 *
 * <p>Replaces the Python digit-recognition logic in
 * {@code GenSummary.get_score()} / {@code get_score_on_select()} in
 * {@code gen_summary.py}.  A set of pre-computed digit template hashes is supplied
 * at construction; each digit region is cropped from the result image and compared
 * against all digit templates using Hamming distance.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class ScoreDetector {

    private static final Logger log = LoggerFactory.getLogger(ScoreDetector.class);

    /** Number of score digits (8 digits: {@code 00000000} – {@code 10000000}). */
    public static final int DIGIT_COUNT = 8;

    /** Maximum Hamming distance to consider a digit match. */
    private static final int MATCH_THRESHOLD = 10;

    private final PerceptualHasher hasher;

    /**
     * Pre-computed perceptual hashes for each digit (0–9).
     * Key: digit character ('0'–'9'), Value: hash string.
     */
    private final Map<Character, String> digitTemplates;

    /**
     * Constructs a score detector with the supplied digit template hashes.
     *
     * @param digitTemplates map from digit character to its perceptual hash
     */
    public ScoreDetector(Map<Character, String> digitTemplates) {
        this.hasher = new PerceptualHasher();
        this.digitTemplates = digitTemplates;
    }

    /**
     * Detects a single digit from a cropped digit image.
     *
     * @param digitImage cropped image of one score digit
     * @return detected digit character ('0'–'9'), or {@code '?'} if unrecognised
     */
    public char detectDigit(BufferedImage digitImage) {
        String candidateHash = hasher.hash(digitImage);
        char best = '?';
        int bestDistance = Integer.MAX_VALUE;

        for (Map.Entry<Character, String> entry : digitTemplates.entrySet()) {
            try {
                int dist = hasher.hammingDistance(candidateHash, entry.getValue());
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = entry.getKey();
                }
            } catch (IllegalArgumentException e) {
                log.warn("Hash length mismatch for digit template '{}': {}", entry.getKey(), e.getMessage());
            }
        }

        if (bestDistance > MATCH_THRESHOLD) {
            log.debug("No digit match found (best distance={}), returning '?'", bestDistance);
            return '?';
        }
        return best;
    }

    /**
     * Assembles an 8-digit score integer from a list of cropped digit images.
     *
     * <p>If any digit cannot be recognised, returns {@code -1} to signal failure.</p>
     *
     * @param digitImages list of 8 cropped digit images, left-to-right
     * @return detected score (0–10 000 000), or {@code -1} on recognition failure
     */
    public int detectScore(java.util.List<BufferedImage> digitImages) {
        if (digitImages.size() != DIGIT_COUNT) {
            log.warn("Expected {} digit images, got {}", DIGIT_COUNT, digitImages.size());
            return -1;
        }
        StringBuilder sb = new StringBuilder(DIGIT_COUNT);
        for (BufferedImage img : digitImages) {
            char d = detectDigit(img);
            if (d == '?') {
                log.debug("Digit recognition failed");
                return -1;
            }
            sb.append(d);
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse detected score '{}'", sb);
            return -1;
        }
    }
}
