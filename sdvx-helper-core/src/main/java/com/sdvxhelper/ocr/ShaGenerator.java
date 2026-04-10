package com.sdvxhelper.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.imageio.ImageIO;

/**
 * Computes SHA-256 hashes for jacket images.
 *
 * <p>Used to create stable identifiers for jacket images downloaded from the web or
 * captured from OBS.  Replaces the Python {@code SHAGenerator} class in
 * {@code sha_generator.py}.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public class ShaGenerator {

    private static final Logger log = LoggerFactory.getLogger(ShaGenerator.class);
    private static final String ALGORITHM = "SHA-256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Computes the SHA-256 hash of the given byte array.
     *
     * @param bytes input bytes
     * @return lowercase hex-encoded SHA-256 hash string
     */
    public String hash(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] digest = md.digest(bytes);
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Computes the SHA-256 hash of a {@link BufferedImage} by encoding it as PNG
     * before hashing.
     *
     * @param image image to hash
     * @return lowercase hex SHA-256 string
     * @throws IOException if the image cannot be encoded
     */
    public String hash(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return hash(baos.toByteArray());
    }

    /**
     * Computes the SHA-256 hash of a file.
     *
     * @param file input file
     * @return lowercase hex SHA-256 string
     * @throws IOException if the file cannot be read
     */
    public String hash(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2]     = HEX[v >>> 4];
            chars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(chars);
    }
}
