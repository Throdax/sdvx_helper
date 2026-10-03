package com.sdvxhelper.app.controller.detection;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.sdvxhelper.network.ObsWebSocketClient;
import com.sdvxhelper.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages all OBS WebSocket text-source and scene-switching updates that the
 * detection loop needs to perform.
 *
 * <p>
 * All public methods silently no-op when the OBS client is not connected, so
 * callers never need to guard on connection state.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public class ObsOverlayService {

    private static final Logger log = LoggerFactory.getLogger(ObsOverlayService.class);

    private ObsWebSocketClient obsClient;
    private Map<String, String> settings;

    /**
     * @param settings
     *            live settings map; call {@link #setSettings} to refresh
     */
    public ObsOverlayService(Map<String, String> settings) {
        this.settings = settings;
    }

    public void setObsClient(ObsWebSocketClient obsClient) {
        this.obsClient = obsClient;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    public ObsWebSocketClient getObsClient() {
        return obsClient;
    }

    // -------------------------------------------------------------------------
    // Source / scene control
    // -------------------------------------------------------------------------

    /**
     * Enables/disables OBS sources and switches scenes for a given state name (e.g.
     * "boot", "play0", "select1", "result0", "quit").
     *
     * @param name
     *            state name used to look up {@code obs_scene_*},
     *            {@code obs_enable_*} and {@code obs_disable_*} settings
     */
    public void controlSources(String name) {
        if (!isConnected()) {
            log.debug("OBS overlay skipped (not connected): controlSources({})", name);
            return;
        }
        String nameCommon = stripTransitionSuffix(name);
        String scene = settings.getOrDefault("obs_scene_" + nameCommon, "");
        if (!scene.isBlank()) {
            try {
                obsClient.setCurrentScene(scene);
            } catch (IOException e) {
                log.debug("controlSources: setCurrentScene failed for {}: {}", name, e.getMessage());
            }
        }
        applySourceVisibility("obs_disable_" + name, scene, false);
        applySourceVisibility("obs_enable_" + name, scene, true);
    }

    private void applySourceVisibility(String settingKey, String scene, boolean visible) {
        List<String> sources = StringUtils.parseListSetting(settings.getOrDefault(settingKey, "[]"));
        for (String sourceName : sources) {
            if (!sourceName.isBlank() && !scene.isBlank()) {
                try {
                    obsClient.setSourceVisible(scene, sourceName, visible);
                } catch (IOException e) {
                    log.debug("applySourceVisibility ({}) for {} failed: {}", visible, sourceName, e.getMessage());
                }
            }
        }
    }

    private String stripTransitionSuffix(String name) {
        if (name.isEmpty()) {
            return name;
        }
        char last = name.charAt(name.length() - 1);
        if (last == '0' || last == '1') {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    /**
     * Sets a named OBS text source to the given value.
     *
     * @param sourceName
     *            OBS text source name
     * @param text
     *            value to set
     */
    public void setTextSource(String sourceName, String text) {
        if (!isConnected()) {
            log.debug("OBS overlay skipped (not connected): setTextSource({})", sourceName);
            return;
        }
        try {
            obsClient.setTextSourceValue(sourceName, text);
        } catch (IOException e) {
            log.debug("setTextSource failed for {}: {}", sourceName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Specialised text-source updaters
    // -------------------------------------------------------------------------

    /**
     * Updates the OBS play-count text source.
     *
     * @param playCount
     *            current session play count
     */
    public void updatePlaysText(int playCount) {
        if (!isConnected()) {
            log.debug("OBS overlay skipped (not connected): updatePlaysText");
            return;
        }
        String src = settings.getOrDefault("obs_txt_plays", "sdvx_helper_playcount");
        String header = settings.getOrDefault("obs_txt_plays_header", "plays: ");
        String footer = settings.getOrDefault("obs_txt_plays_footer", "");
        setTextSource(src, header + playCount + footer);
    }

    /**
     * Updates the OBS playtime text source.
     *
     * @param pt
     *            accumulated play duration
     */
    public void updatePlaytime(Duration pt) {
        if (!isConnected()) {
            log.debug("OBS overlay skipped (not connected): updatePlaytime");
            return;
        }
        String src = settings.getOrDefault("obs_txt_playtime", "sdvx_helper_playtime");
        String header = settings.getOrDefault("obs_txt_playtime_header", "playtime: ");
        long totalSeconds = pt.getSeconds();
        String formatted = String.format("%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60,
                totalSeconds % 60);
        setTextSource(src, header + formatted);
    }

    /**
     * Updates the OBS VF+delta text source.
     *
     * @param currentVf
     *            current total VF
     * @param previousVf
     *            previous total VF (before last play)
     */
    public void updateVfText(double currentVf, double previousVf) {
        if (!isConnected()) {
            log.debug("OBS overlay skipped (not connected): updateVfText");
            return;
        }
        String src = settings.getOrDefault("obs_txt_vf_with_diff", "sdvx_helper_vf_with_diff");
        String header = settings.getOrDefault("obs_txt_vf_header", "VF: ");
        String footer = settings.getOrDefault("obs_txt_vf_footer", "");
        double delta = currentVf - previousVf;
        String text = String.format("%s%.3f (%+.3f)%s", header, currentVf, delta, footer);
        setTextSource(src, text);
    }

    /**
     * Updates the RTA Volforce text source.
     *
     * @param vf
     *            current RTA VF value
     */
    public void updateRtaVf(double vf) {
        setTextSource("sdvx_helper_rta_vf", String.format("%.3f", vf));
    }

    /**
     * Resets the RTA Volforce text source to {@code "0.000"}.
     */
    public void resetRtaVf() {
        setTextSource("sdvx_helper_rta_vf", "0.000");
    }

    /**
     * Updates the blaster-max text source.
     *
     * @param isMax
     *            whether the blaster gauge is at maximum
     * @param textSourceName
     *            OBS text source name to update
     */
    public void updateBlasterMax(boolean isMax, String textSourceName) {
        String value = isMax ? "BLASTER GAUGE MAX!!" : "";
        setTextSource(textSourceName, value);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isConnected() {
        return obsClient != null && obsClient.isConnected();
    }
}
