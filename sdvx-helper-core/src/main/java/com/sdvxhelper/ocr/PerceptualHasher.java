package com.sdvxhelper.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Computes perceptual hashes for jacket and info-bar images.
 *
 * <p>Implements the average-hash (aHash) algorithm used by the Python
 * {@code imagehash.average_hash()} function:
 * <ol>
 *   <li>Resize the image to {@code HASH_SIZE × HASH_SIZE} pixels using bilinear interpolation.</li>
 *   <li>Convert to greyscale.</li>
 *   <li>Compute the mean pixel value.</li>
 *   <li>Produce a bit string: {@code 1} if pixel ≥ mean, {@code 0} otherwise.</li>
 *   <li>Pack the bit string into a hex string.</li>
 * </ol>
 *
 * <p>Two hashes can be compared via their Hamming distance to determine similarity.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class PerceptualHasher {

    private static final Logger log = LoggerFactory.getLogger(PerceptualHasher.class);

    /** Width and height of the reduced image used for hashing. */
    public static final int HASH_SIZE = 8;

    /**
     * Computes the average perceptual hash of the given image.
     *
     * @param image input image (any size or colour model)
     * @return lowercase hex hash string of length {@code HASH_SIZE * HASH_SIZE / 4}
     *         (e.g. 16 chars for HASH_SIZE=8)
     */
    public String hash(BufferedImage image) {
        // 1. Resize to HASH_SIZE × HASH_SIZE
        BufferedImage small = resize(image, HASH_SIZE, HASH_SIZE);

        // 2. Convert to greyscale and collect pixel values
        int total = HASH_SIZE * HASH_SIZE;
        int[] grey = new int[total];
        long sum = 0;
        for (int y = 0; y < HASH_SIZE; y++) {
            for (int x = 0; x < HASH_SIZE; x++) {
                int rgb = small.getRGB(x, y);
                // Greyscale: BT.601 luminance approximation
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >>  8) & 0xFF;
                int b =  rgb        & 0xFF;
                int lum = (r * 299 + g * 587 + b * 114) / 1000;
                grey[y * HASH_SIZE + x] = lum;
                sum += lum;
            }
        }

        // 3. Mean
        int mean = (int) (sum / total);

        // 4. Build bit string, then pack into hex
        StringBuilder hex = new StringBuilder(total / 4);
        for (int i = 0; i < total; i += 4) {
            int nibble = 0;
            for (int bit = 0; bit < 4; bit++) {
                if (grey[i + bit] >= mean) {
                    nibble |= (1 << (3 - bit));
                }
            }
            hex.append(Integer.toHexString(nibble));
        }
        return hex.toString();
    }

    /**
     * Computes the Hamming distance between two hex hash strings.
     *
     * <p>Hashes must be of equal length.  Lower distances indicate more similar images.</p>
     *
     * @param h1 first hex hash string
     * @param h2 second hex hash string
     * @return number of differing bits (0 = identical, max = {@code h1.length() * 4})
     * @throws IllegalArgumentException if the hashes have different lengths
     */
    public int hammingDistance(String h1, String h2) {
        if (h1.length() != h2.length()) {
            throw new IllegalArgumentException(
                    "Hash length mismatch: " + h1.length() + " vs " + h2.length());
        }
        int distance = 0;
        for (int i = 0; i < h1.length(); i++) {
            int diff = Integer.parseInt(h1.substring(i, i + 1), 16)
                     ^ Integer.parseInt(h2.substring(i, i + 1), 16);
            distance += Integer.bitCount(diff);
        }
        return distance;
    }

    /**
     * Returns {@code true} if two images are considered perceptually similar
     * (Hamming distance ≤ threshold).
     *
     * @param h1        first hash
     * @param h2        second hash
     * @param threshold maximum allowed Hamming distance (typically 5–10)
     * @return {@code true} if similar
     */
    public boolean isSimilar(String h1, String h2, int threshold) {
        return hammingDistance(h1, h2) <= threshold;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }
}
