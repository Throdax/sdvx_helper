package com.sdvxhelper.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides the default values for all user-configurable settings.
 *
 * <p>
 * Mirrors the Python {@code default_val} dictionary in
 * {@code manage_settings.py}. The {@link #getDefaults()} method returns an
 * unmodifiable snapshot; the {@code SettingsRepository} merges these defaults
 * with any stored user settings on load, ensuring forward-compatibility when
 * new keys are added.
 * </p>
 *
 * @author Throdax
 * @since 2.0.0
 */
public final class DefaultSettings {

    private DefaultSettings() {
        // utility class -- not instantiable
    }

    /**
     * Returns an unmodifiable map containing all default setting values.
     *
     * <p>
     * String-keyed entries match the JSON keys used in {@code settings.json}.
     * </p>
     *
     * @return default settings map
     */
    public static Map<String, String> getDefaults() {
        Map<String, String> defaults = new LinkedHashMap<>();

        // Window position (sdvx_helper app)
        defaults.put("lx", "0");
        defaults.put("ly", "0");

        // Window position (per-app keys for other modules)
        defaults.put("ocr_lx", "0");
        defaults.put("ocr_ly", "0");
        defaults.put("score_lx", "0");
        defaults.put("score_ly", "0");
        defaults.put("sync_lx", "0");
        defaults.put("sync_ly", "0");
        defaults.put("updater_lx", "0");
        defaults.put("updater_ly", "0");

        // OBS WebSocket connection
        defaults.put("host", "localhost");
        defaults.put("port", "4444");
        defaults.put("passwd", "");

        // Auto-save
        defaults.put("autosave_dir", "");
        defaults.put("autosave_always", "false");
        defaults.put("autosave_interval", "60");
        defaults.put("play0_interval", "10");
        defaults.put("autosave_prewait", "0.0");
        defaults.put("detect_wait", "2.7");
        defaults.put("obs_source", "");
        defaults.put("orientation", "top");
        defaults.put("orientation_top", "top");

        // Language
        defaults.put("ui_language", "jp");

        // OBS scene/source control – boot
        defaults.put("obs_enable_boot", Collections.emptyList().toString());
        defaults.put("obs_disable_boot", Collections.emptyList().toString());
        defaults.put("obs_scene_boot", "");

        // OBS scene/source control – select (0=start, 1=end)
        defaults.put("obs_enable_select0", Collections.emptyList().toString());
        defaults.put("obs_disable_select0", Collections.emptyList().toString());
        defaults.put("obs_scene_select", "");
        defaults.put("obs_enable_select1", Collections.emptyList().toString());
        defaults.put("obs_disable_select1", Collections.emptyList().toString());

        // OBS scene/source control – play
        defaults.put("obs_enable_play0", Collections.emptyList().toString());
        defaults.put("obs_disable_play0", Collections.emptyList().toString());
        defaults.put("obs_scene_play", "");
        defaults.put("obs_enable_play1", Collections.emptyList().toString());
        defaults.put("obs_disable_play1", Collections.emptyList().toString());

        // OBS scene/source control – result
        defaults.put("obs_enable_result0", Collections.emptyList().toString());
        defaults.put("obs_disable_result0", Collections.emptyList().toString());
        defaults.put("obs_scene_result", "");
        defaults.put("obs_enable_result1", Collections.emptyList().toString());
        defaults.put("obs_disable_result1", Collections.emptyList().toString());

        // OBS scene/source control – quit
        defaults.put("obs_enable_quit", Collections.emptyList().toString());
        defaults.put("obs_disable_quit", Collections.emptyList().toString());
        defaults.put("obs_scene_quit", "");

        // OBS text source names
        defaults.put("obs_txt_plays", "sdvx_helper_playcount");
        defaults.put("obs_txt_plays_header", "plays: ");
        defaults.put("obs_txt_plays_footer", "");
        defaults.put("obs_txt_vf_with_diff", "sdvx_helper_vf_with_diff");
        defaults.put("obs_txt_vf_header", "VF: ");
        defaults.put("obs_txt_vf_footer", "");
        defaults.put("obs_txt_playtime", "sdvx_helper_playtime");
        defaults.put("obs_txt_playtime_header", "playtime: ");
        defaults.put("obs_txt_playtime_footer", "");
        defaults.put("obs_txt_blastermax", "sdvx_helper_blastermax");
        defaults.put("alert_blastermax", "false");
        defaults.put("obs_scene_collection", "");

        defaults.put("clip_lxly", "false");

        // Webhooks
        defaults.put("webhook_player_name", "");
        defaults.put("webhook_reg_url", "");
        defaults.put("webhook_names", Collections.emptyList().toString());
        defaults.put("webhook_urls", Collections.emptyList().toString());
        defaults.put("webhook_enable_pics", Collections.emptyList().toString());
        defaults.put("webhook_playlist", Collections.emptyList().toString());
        defaults.put("webhook_enable_lvs", Collections.emptyList().toString());
        defaults.put("webhook_enable_lamps", Collections.emptyList().toString());

        // Google Drive / rival
        defaults.put("get_rival_score", "false");
        defaults.put("my_googledrive", "");
        defaults.put("rival_names", Collections.emptyList().toString());
        defaults.put("rival_googledrive", Collections.emptyList().toString());

        // Score import from select screen
        defaults.put("import_from_select", "false");
        defaults.put("import_arcade_score", "false");

        // RTA
        defaults.put("rta_target_vf", "20.000");

        // Misc
        defaults.put("ignore_rankD", "true");
        defaults.put("auto_update", "true");
        defaults.put("params_json", "resources/params.json");
        defaults.put("logpic_offset_time", "2");
        defaults.put("logpic_bg_alpha", "255");
        defaults.put("autoload_musiclist", "true");
        defaults.put("player_name", "");
        defaults.put("save_on_capture", "false");
        defaults.put("save_jacketimg", "true");
        defaults.put("update_rival_on_result", "false");
        defaults.put("always_update_vf", "false");

        // Play-log sync sub-settings
        Map<String, String> playLogSyncDefaults = new LinkedHashMap<>();
        playLogSyncDefaults.put("play_log_path", "");
        playLogSyncDefaults.put("results_folder", "");
        playLogSyncDefaults.put("song_list", "");
        playLogSyncDefaults.put("dump_output_folder", "");
        defaults.put("play_log_sync", playLogSyncDefaults.toString());

        // Debug / feature flags
        defaults.put("send_webhook", "true");
        defaults.put("dbg_enable_output", "true");
        defaults.put("discord_presence_enable", "false");
        defaults.put("discord_presence_song_as_title", "false");
        defaults.put("discord_presence_upload_jacket", "false");
        defaults.put("discord_presence_ocr_titles", "false");

        return Collections.unmodifiableMap(defaults);
    }
}
