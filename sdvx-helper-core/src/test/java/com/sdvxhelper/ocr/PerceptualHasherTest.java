package com.sdvxhelper.ocr;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PerceptualHasher}.
 */
class PerceptualHasherTest {

    private final PerceptualHasher hasher = new PerceptualHasher();

    @Test
    void hashReturnsSixteenCharHexString() {
        BufferedImage img = solidImage(100, 100, Color.WHITE);
        String hash = hasher.hash(img);
        Assertions.assertEquals(25, hash.length());
        Assertions.assertTrue(hash.matches("[0-9a-f]+"), "Expected lowercase hex: " + hash);
    }

    @Test
    void identicalImagesSameHash() {
        BufferedImage img1 = solidImage(80, 80, Color.RED);
        BufferedImage img2 = solidImage(80, 80, Color.RED);
        Assertions.assertEquals(hasher.hash(img1), hasher.hash(img2));
    }

    @Test
    void veryDifferentImagesHighHammingDistance() {
        // Solid-color images hash identically in aHash (every pixel equals the mean),
        // so we use two structurally opposite half-and-half images instead.
        // "left-black / right-white" vs "left-white / right-black" should differ
        // maximally.
        String h1 = hasher.hash(halfImage(80, 80, Color.BLACK, Color.WHITE));
        String h2 = hasher.hash(halfImage(80, 80, Color.WHITE, Color.BLACK));
        int dist = hasher.hammingDistance(h1, h2);
        Assertions.assertTrue(dist > 10, "Expected high distance for inverted half images, got " + dist);
    }

    @Test
    void identicalHashesZeroHammingDistance() {
        String h = hasher.hash(solidImage(50, 50, Color.BLUE));
        Assertions.assertEquals(0, hasher.hammingDistance(h, h));
    }

    @Test
    void hammingDistanceMismatchLengthThrows() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> hasher.hammingDistance("ffff", "ff"));
    }

    @Test
    void isSimilarBelowThreshold() {
        String h = hasher.hash(solidImage(50, 50, Color.GREEN));
        Assertions.assertTrue(hasher.isSimilar(h, h, 0));
    }

    @Test
    void hexRoundTripConsistent() {
        BufferedImage img = createCheckerboard(64, 64);
        String h1 = hasher.hash(img);
        String h2 = hasher.hash(img);
        Assertions.assertEquals(h1, h2);
    }

    // -------------------------------------------------------------------------
    // Tests using real jacket image resource
    // -------------------------------------------------------------------------

    @Test
    void realJacketHashIsValidHex() throws IOException {
        BufferedImage jacket = loadTestResource("4fd1e379df6e0ec7e3a049d03.png");
        String hash = hasher.hash(jacket);
        Assertions.assertEquals(25, hash.length(), "Hash must be exactly 25 hex characters for HASH_SIZE=10");
        Assertions.assertTrue(hash.matches("[0-9a-f]{25}"), "Hash must be lowercase hex: " + hash);
    }

    @Test
    void realJacketHashIsStableAcrossMultipleCalls() throws IOException {
        BufferedImage jacket = loadTestResource("4fd1e379df6e0ec7e3a049d03.png");
        String hash1 = hasher.hash(jacket);
        String hash2 = hasher.hash(jacket);
        Assertions.assertEquals(hash1, hash2, "Same image hashed twice must produce the same result");
    }

    @Test
    void realJacketIsSimilarToItself() throws IOException {
        BufferedImage jacket = loadTestResource("4fd1e379df6e0ec7e3a049d03.png");
        String hash = hasher.hash(jacket);
        Assertions.assertEquals(0, hasher.hammingDistance(hash, hash),
                "Hamming distance from a hash to itself must be 0");
        Assertions.assertTrue(hasher.isSimilar(hash, hash, 0));
    }

    @Test
    void realJacketDissimilarFromSyntheticSolidImage() throws IOException {
        // A real photo jacket has high visual entropy and should differ greatly from
        // a uniform solid-colour synthetic image even after 8×8 downscaling.
        BufferedImage jacket = loadTestResource("4fd1e379df6e0ec7e3a049d03.png");
        String jacketHash = hasher.hash(jacket);
        // Solid white → all bits 1 (all pixels ≥ mean), real jacket will have mixed
        // bits
        String solidWhiteHash = hasher.hash(solidImage(262, 262, Color.WHITE));
        int dist = hasher.hammingDistance(jacketHash, solidWhiteHash);
        Assertions.assertTrue(dist > 5,
                "Real jacket must differ significantly from solid white (distance=" + dist + ")");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Loads an image from the test classpath resources. */
    private static BufferedImage loadTestResource(String filename) throws IOException {
        InputStream is = PerceptualHasherTest.class.getResourceAsStream("/" + filename);
        Assertions.assertNotNull(is, "Test resource not found on classpath: " + filename);
        return ImageIO.read(is);
    }

    /**
     * Creates an image whose left half is {@code left} and right half is
     * {@code right}.
     */
    private static BufferedImage halfImage(int w, int h, Color left, Color right) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(left);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(right);
        g.fillRect(w / 2, 0, w - w / 2, h);
        g.dispose();
        return img;
    }

    private static BufferedImage solidImage(int w, int h, Color color) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static BufferedImage createCheckerboard(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFFFFFF : 0x000000);
            }
        }
        return img;
    }
}
