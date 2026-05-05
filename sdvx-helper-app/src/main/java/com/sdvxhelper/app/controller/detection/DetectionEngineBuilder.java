package com.sdvxhelper.app.controller.detection;

import java.util.Map;
import java.util.Objects;

import com.sdvxhelper.network.DiscordPresenceClient;
import com.sdvxhelper.service.ImageAnalysisService;

/**
 * Builder for {@link DetectionEngine}.
 *
 * <p>
 * All fields except {@code discordPresenceClient} are mandatory; the Discord
 * client is optional and defaults to {@code null} when Rich Presence is
 * disabled in settings.
 * </p>
 *
 * <pre>{@code
 * DetectionEngine engine = DetectionEngine.builder().listener(this).imageAnalysisService(imageAnalysisService)
 * 		.screenHandler(screenHandler).obsOverlayService(obsOverlayService).webhookDispatcher(webhookDispatcher)
 * 		.params(params).settings(settings).build();
 * }</pre>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class DetectionEngineBuilder {

    private DetectionListener listener;
    private ImageAnalysisService imageAnalysisService;
    private DiscordPresenceClient discordPresenceClient;
    private ScreenHandler screenHandler;
    private ObsOverlayService obsOverlayService;
    private WebhookDispatcher webhookDispatcher;
    private Map<String, String> params;
    private Map<String, String> settings;

    /**
     * Sets the UI callback target that receives detection results.
     *
     * @param listener
     *            the detection listener; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder listener(DetectionListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * Sets the image analysis service used to classify screen captures.
     *
     * @param imageAnalysisService
     *            the service; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder imageAnalysisService(ImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
        return this;
    }

    /**
     * Sets the optional Discord Rich Presence client.
     *
     * @param discordPresenceClient
     *            the client, or {@code null} if Discord integration is disabled
     * @return this builder
     */
    public DetectionEngineBuilder discordPresenceClient(DiscordPresenceClient discordPresenceClient) {
        this.discordPresenceClient = discordPresenceClient;
        return this;
    }

    /**
     * Sets the per-screen image processing handler.
     *
     * @param screenHandler
     *            the handler; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder screenHandler(ScreenHandler screenHandler) {
        this.screenHandler = screenHandler;
        return this;
    }

    /**
     * Sets the OBS text-source and scene service.
     *
     * @param obsOverlayService
     *            the service; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder obsOverlayService(ObsOverlayService obsOverlayService) {
        this.obsOverlayService = obsOverlayService;
        return this;
    }

    /**
     * Sets the Discord webhook dispatcher.
     *
     * @param webhookDispatcher
     *            the dispatcher; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder webhookDispatcher(WebhookDispatcher webhookDispatcher) {
        this.webhookDispatcher = webhookDispatcher;
        return this;
    }

    /**
     * Sets the detection parameters map.
     *
     * @param params
     *            the parameters; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder params(Map<String, String> params) {
        this.params = params;
        return this;
    }

    /**
     * Sets the application settings map.
     *
     * @param settings
     *            the settings; must not be {@code null}
     * @return this builder
     */
    public DetectionEngineBuilder settings(Map<String, String> settings) {
        this.settings = settings;
        return this;
    }

    /**
     * Validates all mandatory fields, constructs and returns a fully initialised
     * {@link DetectionEngine}.
     *
     * @return a new {@link DetectionEngine}
     * @throws NullPointerException
     *             if any mandatory field has not been set
     */
    public DetectionEngine build() {
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(imageAnalysisService, "imageAnalysisService must not be null");
        Objects.requireNonNull(screenHandler, "screenHandler must not be null");
        Objects.requireNonNull(obsOverlayService, "obsOverlayService must not be null");
        Objects.requireNonNull(webhookDispatcher, "webhookDispatcher must not be null");
        Objects.requireNonNull(params, "params must not be null");
        Objects.requireNonNull(settings, "settings must not be null");

        DetectionEngine engine = new DetectionEngine();
        engine.setListener(listener);
        engine.setImageAnalysisService(imageAnalysisService);
        engine.setDiscordPresenceClient(discordPresenceClient);
        engine.setScreenHandler(screenHandler);
        engine.setObsOverlayService(obsOverlayService);
        engine.setWebhookDispatcher(webhookDispatcher);
        engine.setParams(params);
        engine.setSettings(settings);
        engine.setStateHashes(DetectionEngine.buildStateHashes(params));
        return engine;
    }
}
