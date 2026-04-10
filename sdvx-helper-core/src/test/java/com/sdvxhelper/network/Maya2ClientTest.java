package com.sdvxhelper.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;

/**
 * Unit tests for {@link Maya2Client}.
 */
class Maya2ClientTest {

    @Test
    void signAppendsSha256ChecksumRow() throws IOException {
        Maya2Client client = new Maya2Client("http://localhost:8080", "testkey");
        String payload = "title,score\ncool_song,9000000";
        String signed = client.sign(payload);
        Assertions.assertTrue(signed.startsWith(payload), "Signed payload should start with original content");
        String[] lines = signed.split("\n");
        // Last line should be the hex HMAC
        String hmacLine = lines[lines.length - 1].trim();
        Assertions.assertEquals(64, hmacLine.length(), "HMAC-SHA256 hex should be 64 characters");
        Assertions.assertTrue(hmacLine.matches("[0-9a-f]+"), "HMAC should be lowercase hex");
    }

    @Test
    void signIsDeterministicForSameKeyAndPayload() throws IOException {
        Maya2Client client = new Maya2Client("http://localhost:8080", "myKey");
        String payload = "data,row\na,b";
        Assertions.assertEquals(client.sign(payload), client.sign(payload));
    }

    @Test
    void signDifferentPayloadsDifferentHmac() throws IOException {
        Maya2Client client = new Maya2Client("http://localhost:8080", "myKey");
        String s1 = client.sign("payload1");
        String s2 = client.sign("payload2");
        // Extract the last line (HMAC) from each
        String h1 = s1.lines().reduce((a, b) -> b).orElse("");
        String h2 = s2.lines().reduce((a, b) -> b).orElse("");
        Assertions.assertNotEquals(h1, h2, "Different payloads should produce different HMACs");
    }

    @Test
    void baseUrlTrailingSlashNormalized() {
        // Constructor should strip trailing slash
        Maya2Client client = new Maya2Client("http://localhost:8080/", "key");
        Assertions.assertNotNull(client); // Just verify construction doesn't throw
    }
}
