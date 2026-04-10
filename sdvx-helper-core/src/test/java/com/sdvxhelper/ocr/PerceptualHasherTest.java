package com.sdvxhelper.ocr;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link PerceptualHasher}.
 */
class PerceptualHasherTest {

    private final PerceptualHasher hasher = new PerceptualHasher();

    @Test
    void hashReturnsSixteenCharHexString() {
        BufferedImage img = solidImage(100, 100, Color.WHITE);
        String hash = hasher.hash(img);
        Assertions.assertEquals(16, hash.length());
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
        String h1 = hasher.hash(solidImage(80, 80, Color.BLACK));
        String h2 = hasher.hash(solidImage(80, 80, Color.WHITE));
        int dist = hasher.hammingDistance(h1, h2);
        Assertions.assertTrue(dist > 10, "Expected high distance for black vs white, got " + dist);
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
    // Helpers
    // -------------------------------------------------------------------------

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
