package com.sdvxhelper.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShaGenerator}.
 */
class ShaGeneratorTest {

    private final ShaGenerator sha = new ShaGenerator();

    @Test
    void knownBytesSha() {
        // SHA-256("") =
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String result = sha.hash(new byte[0]);
        Assertions.assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result);
    }

    @Test
    void deterministic() {
        byte[] data = "hello world".getBytes();
        Assertions.assertEquals(sha.hash(data), sha.hash(data));
    }

    @Test
    void outputIsLowercaseHex64Chars() {
        String h = sha.hash("test".getBytes());
        Assertions.assertEquals(64, h.length());
        Assertions.assertTrue(h.matches("[0-9a-f]+"));
    }

    @Test
    void differentInputsDifferentHash() {
        Assertions.assertNotEquals(sha.hash("a".getBytes()), sha.hash("b".getBytes()));
    }

    @Test
    void hashBufferedImage() throws IOException {
        BufferedImage img1 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        // Both blank images should hash the same
        Assertions.assertEquals(sha.hash(img1), sha.hash(img2));
    }
}
