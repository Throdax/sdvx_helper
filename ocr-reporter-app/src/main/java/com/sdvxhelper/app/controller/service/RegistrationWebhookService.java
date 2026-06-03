package com.sdvxhelper.app.controller.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;

import com.sdvxhelper.app.controller.OcrReporterHelper;
import com.sdvxhelper.config.SecretConfig;
import com.sdvxhelper.network.DiscordWebhookClient;
import com.sdvxhelper.util.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends Discord webhook messages for song registration events and the
 * end-of-session musiclist upload.
 *
 * <p>
 * Mirrors Python {@code ocr_reporter.py} {@code send_webhook} (lines 293–314)
 * and the close handler behaviour. This service has no JavaFX dependency and is
 * safe to call from any thread.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class RegistrationWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationWebhookService.class);

    private SecretConfig secretConfig;
    private DiscordWebhookClient webhookClient;
    private ResourceBundle bundle;
    private Map<String, String> paramsMap;

    /**
     * @param secretConfig
     *            source of the webhook URL
     * @param webhookClient
     *            HTTP client for Discord webhook calls
     * @param bundle
     *            i18n bundle for message text
     * @param paramsMap
     *            detection parameters for image crop coordinates
     */
    public RegistrationWebhookService(SecretConfig secretConfig, DiscordWebhookClient webhookClient,
            ResourceBundle bundle, Map<String, String> paramsMap) {
        this.secretConfig = secretConfig;
        this.webhookClient = webhookClient;
        this.bundle = bundle;
        this.paramsMap = paramsMap;
    }

    /**
     * Sends a registration webhook for a newly registered song hash.
     *
     * <p>
     * Attaches two image crops ({@code info.png} and {@code difficulty.png}) when
     * {@code sourceFile} is available and readable.
     * </p>
     *
     * @param title
     *            song title
     * @param difficulty
     *            difficulty string (e.g. {@code "exh"})
     * @param hashJacket
     *            jacket perceptual hash
     * @param hashInfo
     *            info-region hash (may be blank)
     * @param sourceFile
     *            result-screen source image; {@code null} sends text-only
     */
    public void sendOnRegister(String title, String difficulty, String hashJacket, String hashInfo, File sourceFile) {
        String webhookUrl = secretConfig.getWebhookRegUrl();
        if (webhookUrl.isBlank()) {
            log.info("sendOnRegister: webhook.reg.url not configured, skipping");
            return;
        }

        String msg = buildRegisterMessage(title, difficulty, hashJacket, hashInfo);

        if (sourceFile == null) {
            log.debug("sendOnRegister: no source file - sending text-only message");
            webhookClient.sendMessage(webhookUrl, msg);
            return;
        }

        try {
            BufferedImage awtImage = ImageIO.read(sourceFile);
            if (awtImage == null) {
                log.warn("sendOnRegister: ImageIO could not decode '{}' - sending text-only message",
                        sourceFile.getName());
                webhookClient.sendMessage(webhookUrl, msg);
                return;
            }

            Map<String, byte[]> attachments = buildImageAttachments(awtImage);
            webhookClient.sendMessageWithMultipleImages(webhookUrl, msg, attachments);
            log.info("{} sent to Discord registration webhook", title);
        } catch (IOException e) {
            log.warn("sendOnRegister: failed to attach images, sending text only: {}", e.getMessage());
            webhookClient.sendMessage(webhookUrl, msg);
        }
    }

    /**
     * Sends the current {@code musiclist.xml} to the webhook at session end.
     *
     * <p>
     * Call is skipped silently when fewer than 2 songs were registered, when the
     * webhook URL is not configured, or when the XML file does not exist.
     * </p>
     *
     * @param sessionCount
     *            number of songs registered in this session
     * @param totalHashes
     *            total number of hashes in the music list
     */
    public void sendOnClose(int sessionCount, int totalHashes) {
        if (sessionCount <= 1) {
            log.info("sendOnClose: {} song(s) registered - skipping close webhook", sessionCount);
            return;
        }
        String webhookUrl = secretConfig != null ? secretConfig.getWebhookRegUrl() : "";
        if (webhookUrl.isBlank()) {
            log.debug("sendOnClose: webhook.reg.url not configured, skipping");
            return;
        }
        File musiclistFile = new File("resources/musiclist.xml");
        if (!musiclistFile.exists()) {
            log.debug("sendOnClose: musiclist.xml not found, skipping");
            return;
        }
        try {
            byte[] xmlBytes = Files.readAllBytes(Paths.get(musiclistFile.toURI()));
            String msg = "Session ended. Registered: " + sessionCount + ", total: " + totalHashes;
            webhookClient.sendMessageWithFile(webhookUrl, msg, xmlBytes, "musiclist.xml", "application/xml");
            log.info("Musiclist sent to Discord on close");
        } catch (IOException e) {
            log.warn("sendOnClose: failed to send musiclist: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildRegisterMessage(String title, String difficulty, String hashJacket, String hashInfo) {
        StringBuilder msg = new StringBuilder();
        msg.append(bundle.getString("webhook.ocr.title")).append(": **").append(title).append("**\n");
        msg.append(" - ").append(bundle.getString("webhook.ocr.hash.jacket")).append(": **").append(hashJacket)
                .append("**");
        if (!hashInfo.isBlank()) {
            msg.append(" - ").append(bundle.getString("webhook.ocr.hash.info")).append(": **").append(hashInfo)
                    .append("**");
        }
        msg.append(" (").append(bundle.getString("webhook.ocr.difficulty")).append(": **")
                .append(difficulty.toUpperCase()).append("**)");
        return msg.toString();
    }

    private Map<String, byte[]> buildImageAttachments(BufferedImage awtImage) throws IOException {
        int iSx = ParamUtils.getInt(paramsMap, "log_crop_info_sx", 0);
        int iSy = ParamUtils.getInt(paramsMap, "log_crop_info_sy", 0);
        int iW = ParamUtils.getInt(paramsMap, "log_crop_info_w", 260);
        int iH = ParamUtils.getInt(paramsMap, "log_crop_info_h", 65);
        BufferedImage infoFull = OcrReporterHelper.cropAndScale(awtImage, iSx, iSy, iW, iH, iW, iH);
        int subW = Math.min(260, infoFull.getWidth());
        int subH = Math.min(65, infoFull.getHeight());
        BufferedImage infoCrop = infoFull.getSubimage(0, 0, subW, subH);

        int dSx = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sx", 55);
        int dSy = ParamUtils.getInt(paramsMap, "log_crop_difficulty_sy", 870);
        int dW = ParamUtils.getInt(paramsMap, "log_crop_difficulty_w", 138);
        int dH = ParamUtils.getInt(paramsMap, "log_crop_difficulty_h", 30);
        BufferedImage diffBand = OcrReporterHelper.cropAndScale(awtImage, dSx, dSy, dW, dH, dW, dH);

        ByteArrayOutputStream baosInfo = new ByteArrayOutputStream();
        ImageIO.write(infoCrop, "png", baosInfo);
        ByteArrayOutputStream baosDiff = new ByteArrayOutputStream();
        ImageIO.write(diffBand, "png", baosDiff);

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("info.png", baosInfo.toByteArray());
        files.put("difficulty.png", baosDiff.toByteArray());
        return files;
    }
}
