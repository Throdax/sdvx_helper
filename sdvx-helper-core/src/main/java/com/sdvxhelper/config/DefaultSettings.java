package com.sdvxhelper.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides the default values for all user-configurable settings.
 *
 * <p>Mirrors the Python {@code default_val} dictionary in {@code manage_settings.py}.
 * The {@link #getDefaults()} method returns an unmodifiable snapshot; the
 * {@code SettingsRepository} merges these defaults with any stored user settings
 * on load, ensuring forward-compatibility when new keys are added.</p>
 *
 * @author sdvx-helper
 * @since 2.0.0
 */
public final class DefaultSettings {

    private DefaultSettings() {
        // utility class -- not instantiable
    }

    /**
     * Returns an unmodifiable map containing all default setting values.
     *
     * <p>String-keyed entries match the JSON keys used in {@code settings.json}.</p>
     *
     * @return default settings map
     */
    public static Map<String, Object> getDefaults() {
        Map<String, Object> d = new LinkedHashMap<>();

        // Window position
        d.put("lx", 0);
        d.put("ly", 0);

        // OBS WebSocket connection
        d.put("host", "localhost");
        d.put("port", "4444");
        d.put("passwd", "");

        // Auto-save
        d.put("autosave_dir", "");
        d.put("autosave_always", false);
        d.put("autosave_interval", 60);
        d.put("play0_interval", 10);
        d.put("autosave_prewait", "0.0");
        d.put("detect_wait", 2.7);
        d.put("obs_source", "");
        d.put("orientation_top", "left");

        // Language
        d.put("ui_language", "jp");

        // OBS scene/source control – boot
        d.put("obs_enable_boot", Collections.emptyList());
        d.put("obs_disable_boot", Collections.emptyList());
        d.put("obs_scene_boot", "");

        // OBS scene/source control – select (0=start, 1=end)
        d.put("obs_enable_select0", Collections.emptyList());
        d.put("obs_disable_select0", Collections.emptyList());
        d.put("obs_scene_select", "");
        d.put("obs_enable_select1", Collections.emptyList());
        d.put("obs_disable_select1", Collections.emptyList());

        // OBS scene/source control – play
        d.put("obs_enable_play0", Collections.emptyList());
        d.put("obs_disable_play0", Collections.emptyList());
        d.put("obs_scene_play", "");
        d.put("obs_enable_play1", Collections.emptyList());
        d.put("obs_disable_play1", Collections.emptyList());

        // OBS scene/source control – result
        d.put("obs_enable_result0", Collections.emptyList());
        d.put("obs_disable_result0", Collections.emptyList());
        d.put("obs_scene_result", "");
        d.put("obs_enable_result1", Collections.emptyList());
        d.put("obs_disable_result1", Collections.emptyList());

        // OBS scene/source control – quit
        d.put("obs_enable_quit", Collections.emptyList());
        d.put("obs_disable_quit", Collections.emptyList());
        d.put("obs_scene_quit", "");

        // OBS text source names
        d.put("obs_txt_plays", "sdvx_helper_playcount");
        d.put("obs_txt_plays_header", "plays: ");
        d.put("obs_txt_plays_footer", "");
        d.put("obs_txt_vf_with_diff", "sdvx_helper_vf_with_diff");
        d.put("obs_txt_vf_header", "VF: ");
        d.put("obs_txt_vf_footer", "");
        d.put("obs_txt_playtime", "sdvx_helper_playtime");
        d.put("obs_txt_playtime_header", "playtime: ");
        d.put("obs_txt_playtime_footer", "");
        d.put("obs_txt_blastermax", "sdvx_helper_blastermax");
        d.put("alert_blastermax", false);
        d.put("obs_scene_collection", "");

        d.put("clip_lxly", false);

        // Webhooks
        d.put("webhook_player_name", "");
        d.put("webhook_names", Collections.emptyList());
        d.put("webhook_urls", Collections.emptyList());
        d.put("webhook_enable_pics", Collections.emptyList());
        d.put("webhook_playlist", Collections.emptyList());
        d.put("webhook_enable_lvs", Collections.emptyList());
        d.put("webhook_enable_lamps", Collections.emptyList());

        // Google Drive / rival
        d.put("get_rival_score", false);
        d.put("my_googledrive", "");
        d.put("rival_names", Collections.emptyList());
        d.put("rival_googledrive", Collections.emptyList());

        // Score import from select screen
        d.put("import_from_select", false);
        d.put("import_arcade_score", false);

        // RTA
        d.put("rta_target_vf", "20.000");

        // Misc
        d.put("ignore_rankD", true);
        d.put("auto_update", true);
        d.put("params_json", "resources/params.json");
        d.put("logpic_offset_time", 2);
        d.put("logpic_bg_alpha", 255);
        d.put("autoload_musiclist", true);
        d.put("player_name", "");
        d.put("save_on_capture", false);
        d.put("save_jacketimg", true);
        d.put("update_rival_on_result", false);
        d.put("always_update_vf", false);

        // Play-log sync sub-settings
        Map<String, String> pls = new LinkedHashMap<>();
        pls.put("play_log_path", "");
        pls.put("results_folder", "");
        pls.put("song_list", "");
        pls.put("dump_output_folder", "");
        d.put("play_log_sync", pls);

        // Debug / feature flags
        d.put("send_webhook", true);
        d.put("dbg_enable_output", true);
        d.put("discord_presence_enable", false);
        d.put("discord_presence_song_as_title", false);
        d.put("discord_presence_upload_jacket", false);
        d.put("discord_presence_ocr_titles", false);

        return Collections.unmodifiableMap(d);
    }
}
