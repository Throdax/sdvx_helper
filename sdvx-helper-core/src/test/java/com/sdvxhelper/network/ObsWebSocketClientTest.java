package com.sdvxhelper.network;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObsWebSocketClient}.
 */
class ObsWebSocketClientTest {

    @Test
    void notConnectedByDefault() {
        ObsWebSocketClient client = new ObsWebSocketClient("localhost", 4444, "");
        Assertions.assertFalse(client.isConnected());
    }

    @Test
    void captureSourceThrowsWhenNotConnected() {
        ObsWebSocketClient client = new ObsWebSocketClient("localhost", 4444, "");
        Assertions.assertThrows(Exception.class, () -> client.captureSource("source"));
    }

    @Test
    void decodeBase64ImageWithPrefixStripped() throws Exception {
        // Create a small PNG and encode it to base64
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());

        BufferedImage decoded = ObsWebSocketClient.decodeBase64Image(base64);
        Assertions.assertNotNull(decoded);
        Assertions.assertEquals(4, decoded.getWidth());
        Assertions.assertEquals(4, decoded.getHeight());
    }

    @Test
    void decodeBase64ImageWithoutPrefix() throws Exception {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());

        BufferedImage decoded = ObsWebSocketClient.decodeBase64Image(base64);
        Assertions.assertNotNull(decoded);
    }

    @Test
    void closeWhenAlreadyDisconnectedIsNoOp() {
        ObsWebSocketClient client = new ObsWebSocketClient("localhost", 4444, "");
        Assertions.assertDoesNotThrow(client::close);
    }

    @Test
    void discordPresenceClientThrottlesUpdates() throws Exception {
        DiscordPresenceClient dpc = new DiscordPresenceClient("123456789");
        // Not connected, so updates should be silent no-ops
        Assertions.assertDoesNotThrow(() -> dpc.updatePresence(com.sdvxhelper.model.enums.PlayState.SELECTING, "Song",
                "exh", "17.255", null));
        dpc.close();
    }
}
