package com.sdvxhelper.ocr;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Computes perceptual hashes for jacket and info-bar images.
 *
 * <p>
 * Implements the average-hash (aHash) algorithm used by the Python
 * {@code imagehash.average_hash(image, hash_size=10)} function, replicating
 * Pillow's exact behaviour so that hashes match the values stored in
 * {@code musiclist.xml}:
 * </p>
 * <ol>
 * <li>Convert the image to greyscale using Pillow's ITU-R 601 formula:
 * {@code L = (R*19595 + G*38470 + B*7471 + 32768) >> 16}.</li>
 * <li>Resize the greyscale image to {@code HASH_SIZE × HASH_SIZE} pixels using
 * a Lanczos-3 separable filter with Pillow's coordinate conventions.</li>
 * <li>Compute the float mean of all {@code HASH_SIZE²} pixels.</li>
 * <li>Produce a bit-string: {@code 1} if pixel {@code >} mean (strict, matching
 * Python's {@code pixels > avg}), {@code 0} otherwise.</li>
 * <li>Pack bits MSB-first into a hex string.</li>
 * </ol>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class PerceptualHasher {

    /** Width and height of the reduced image used for hashing. */
    public static final int HASH_SIZE = 10;

    /** Lanczos lobe count (a=3 → Lanczos-3). */
    private static final double LANCZOS_A = 3.0;

    /**
     * Computes the average perceptual hash of the given image, matching Python
     * {@code imagehash.average_hash(image, hash_size=10)}.
     *
     * @param image
     *            input image (any size or colour model)
     * @return lowercase hex hash string of length {@code HASH_SIZE²/4} (25 chars
     *         for HASH_SIZE=10)
     */
    public String hash(BufferedImage image) {
        int srcW = image.getWidth();
        int srcH = image.getHeight();

        // 1. Convert to greyscale using Pillow's ITU-R 601 formula
        int[] srcGrey = toGreyArray(image, srcW, srcH);

        // 2. Resize to HASH_SIZE × HASH_SIZE using Lanczos-3 (Pillow convention)
        double[] small = resizeLanczos(srcGrey, srcW, srcH, HASH_SIZE, HASH_SIZE);

        // 3. Float mean (matches numpy.mean — Python uses strict float arithmetic)
        double mean = 0;
        for (double v : small) {
            mean += v;
        }
        mean /= small.length;

        // 4. Build bit-string, pack into hex (pixel > mean, strict greater-than)
        StringBuilder hex = new StringBuilder(small.length / 4);
        for (int i = 0; i < small.length; i += 4) {
            int nibble = 0;
            for (int bit = 0; bit < 4; bit++) {
                if (small[i + bit] > mean) {
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
     * @param h1
     *            first hex hash string
     * @param h2
     *            second hex hash string
     * @return number of differing bits
     * @throws IllegalArgumentException
     *             if the hashes have different lengths
     */
    public int hammingDistance(String h1, String h2) {
        if (h1.length() != h2.length()) {
            throw new IllegalArgumentException("Hash length mismatch: " + h1.length() + " vs " + h2.length());
        }
        int distance = 0;
        for (int i = 0; i < h1.length(); i++) {
            int diff = Integer.parseInt(h1.substring(i, i + 1), 16) ^ Integer.parseInt(h2.substring(i, i + 1), 16);
            distance += Integer.bitCount(diff);
        }
        return distance;
    }

    /**
     * Returns {@code true} if two images are considered perceptually similar
     * (Hamming distance ≤ threshold).
     *
     * @param h1
     *            first hash
     * @param h2
     *            second hash
     * @param threshold
     *            maximum allowed Hamming distance (typically 3–5)
     * @return {@code true} if similar
     */
    public boolean isSimilar(String h1, String h2, int threshold) {
        return hammingDistance(h1, h2) <= threshold;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts an RGB image to a flat greyscale array using Pillow's ITU-R 601
     * formula (with rounding, matching {@code PIL.Image.convert('L')}).
     */
    private static int[] toGreyArray(BufferedImage img, int w, int h) {
        int[] grey = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                grey[y * w + x] = (r * 19595 + g * 38470 + b * 7471 + 0x8000) >> 16;
            }
        }
        return grey;
    }

    /**
     * Resizes a greyscale pixel array from {@code srcW×srcH} to {@code dstW×dstH}
     * using a separable Lanczos-3 filter that replicates Pillow's coordinate
     * convention:
     * <ul>
     * <li>Source center for output pixel {@code dx}:
     * {@code center = (dx + 0.5) * scaleX}</li>
     * <li>Kernel argument for source pixel {@code sx}:
     * {@code lanczos((sx + 0.5 - center) * filterScale)}</li>
     * <li>For downsampling {@code filterScale = 1 / scaleX}; for upsampling
     * {@code filterScale = 1} (no kernel widening).</li>
     * </ul>
     */
    private static double[] resizeLanczos(int[] src, int srcW, int srcH, int dstW, int dstH) {
        // --- Horizontal pass: srcW columns → dstW columns ---
        double scaleX = (double) srcW / dstW;
        double filterScaleX = scaleX > 1.0 ? 1.0 / scaleX : 1.0;
        double supportX = LANCZOS_A / filterScaleX;

        double[] tmp = new double[dstW * srcH];
        for (int y = 0; y < srcH; y++) {
            for (int dx = 0; dx < dstW; dx++) {
                double center = (dx + 0.5) * scaleX;
                int xMin = (int) Math.max(0, Math.ceil(center - supportX));
                int xMax = (int) Math.min(srcW - 1, Math.floor(center + supportX));

                double wSum = 0;
                double vSum = 0;
                for (int sx = xMin; sx <= xMax; sx++) {
                    double w = lanczos3((sx + 0.5 - center) * filterScaleX);
                    vSum += w * src[y * srcW + sx];
                    wSum += w;
                }
                tmp[y * dstW + dx] = wSum != 0 ? vSum / wSum : 0;
            }
        }

        // --- Vertical pass: srcH rows → dstH rows ---
        double scaleY = (double) srcH / dstH;
        double filterScaleY = scaleY > 1.0 ? 1.0 / scaleY : 1.0;
        double supportY = LANCZOS_A / filterScaleY;

        double[] out = new double[dstW * dstH];
        for (int dy = 0; dy < dstH; dy++) {
            double center = (dy + 0.5) * scaleY;
            int yMin = (int) Math.max(0, Math.ceil(center - supportY));
            int yMax = (int) Math.min(srcH - 1, Math.floor(center + supportY));

            for (int dx = 0; dx < dstW; dx++) {
                double wSum = 0;
                double vSum = 0;
                for (int sy = yMin; sy <= yMax; sy++) {
                    double w = lanczos3((sy + 0.5 - center) * filterScaleY);
                    vSum += w * tmp[sy * dstW + dx];
                    wSum += w;
                }
                out[dy * dstW + dx] = wSum != 0 ? vSum / wSum : 0;
            }
        }
        return out;
    }

    /**
     * Computes a DCT-based perceptual hash (pHash) of the given image, replicating
     * Python {@code imagehash.phash(image, hash_size=10, highfreq_factor=4)}.
     *
     * <p>
     * Algorithm:
     * <ol>
     * <li>Convert to greyscale using Pillow's ITU-R 601 formula.</li>
     * <li>Resize to {@code (HASH_SIZE * 4) x (HASH_SIZE * 4)} = 40x40 using
     * Lanczos-3.</li>
     * <li>Apply a 2-D DCT-II (separable row-then-column).</li>
     * <li>Extract the top-left {@code HASH_SIZE x HASH_SIZE} = 10x10 block of
     * low-frequency DCT coefficients.</li>
     * <li>Produce a bit-string: {@code 1} if coefficient is above the block's
     * median, {@code 0} otherwise.</li>
     * <li>Pack bits MSB-first into a hex string.</li>
     * </ol>
     * </p>
     *
     * <p>
     * The returned hex string is the same length as {@link #hash} (25 chars for
     * {@code HASH_SIZE=10}), so {@link #hammingDistance} works with both.
     * </p>
     *
     * @param image
     *            input image (any size or colour model)
     * @return lowercase hex pHash string of length {@code HASH_SIZE²/4}
     */
    public String phash(BufferedImage image) {
        int imgSize = HASH_SIZE * 4;
        int srcW = image.getWidth();
        int srcH = image.getHeight();

        int[] srcGrey = toGreyArray(image, srcW, srcH);
        double[] resized = resizeLanczos(srcGrey, srcW, srcH, imgSize, imgSize);

        double[][] pixels = new double[imgSize][imgSize];
        for (int r = 0; r < imgSize; r++) {
            for (int c = 0; c < imgSize; c++) {
                pixels[r][c] = resized[r * imgSize + c];
            }
        }

        double[][] dct = dct2d(pixels, imgSize, imgSize);

        double[] lowFreq = new double[HASH_SIZE * HASH_SIZE];
        for (int r = 0; r < HASH_SIZE; r++) {
            System.arraycopy(dct[r], 0, lowFreq, r * HASH_SIZE, HASH_SIZE);
        }

        double[] sorted = lowFreq.clone();
        Arrays.sort(sorted);
        double median = (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;

        StringBuilder hex = new StringBuilder(lowFreq.length / 4);
        for (int i = 0; i < lowFreq.length; i += 4) {
            int nibble = 0;
            for (int bit = 0; bit < 4; bit++) {
                if (lowFreq[i + bit] > median) {
                    nibble |= (1 << (3 - bit));
                }
            }
            hex.append(Integer.toHexString(nibble));
        }
        return hex.toString();
    }

    /**
     * Applies a separable 2-D DCT-II: first along each row, then along each column.
     */
    private static double[][] dct2d(double[][] input, int rows, int cols) {
        double[][] temp = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            dct1d(input[r], temp[r], cols);
        }
        double[][] out = new double[rows][cols];
        double[] colIn = new double[rows];
        double[] colOut = new double[rows];
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                colIn[r] = temp[r][c];
            }
            dct1d(colIn, colOut, rows);
            for (int r = 0; r < rows; r++) {
                out[r][c] = colOut[r];
            }
        }
        return out;
    }

    /**
     * Applies a 1-D DCT-II to {@code in} of length {@code n}, writing results to
     * {@code out}: {@code out[k] = sum_{x=0}^{n-1} in[x] * cos(PI*k*(x+0.5)/n)}.
     *
     * <p>
     * The scaling factor of 2 present in {@code scipy.fft.dct} is omitted because
     * the pHash comparison uses only the relative order of coefficients vs. their
     * median.
     * </p>
     */
    private static void dct1d(double[] in, double[] out, int n) {
        for (int k = 0; k < n; k++) {
            double sum = 0;
            for (int x = 0; x < n; x++) {
                sum += in[x] * Math.cos(Math.PI * k * (x + 0.5) / n);
            }
            out[k] = sum;
        }
    }

    /**
     * Lanczos-3 kernel: {@code sinc(x) * sinc(x/a)}, evaluated at {@code x} for
     * {@code a = 3}.
     */
    private static double lanczos3(double x) {
        if (x == 0.0) {
            return 1.0;
        }
        if (x <= -LANCZOS_A || x >= LANCZOS_A) {
            return 0.0;
        }
        double pix = Math.PI * x;
        return LANCZOS_A * Math.sin(pix) * Math.sin(pix / LANCZOS_A) / (pix * pix);
    }
}
