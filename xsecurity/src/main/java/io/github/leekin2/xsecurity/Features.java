package io.github.leekin2.xsecurity;

import android.content.SharedPreferences;
import java.util.Set;

/** Switch keys shared by the settings screen and the hooks, with their defaults. */
public final class Features {
    public static final String GROUP = "xsecurity";

    // Battery
    public static final String BATTERY_RANKING = "battery_ranking";
    public static final String BATTERY_SCREEN_TIME = "battery_screen_time";
    public static final String BATTERY_HEALTH = "battery_health";
    public static final String CHARGE_PROTECT = "charge_protect";
    // Games
    public static final String GAME_LOCK_MAX_FPS = "game_lock_max_fps";
    public static final String GAME_FPS = "game_fps";
    public static final String GAME_KEEP_ENABLED = "game_keep_enabled";
    public static final String GT_HDR = "gt_hdr";
    public static final String GT_SUPER_RES = "gt_super_res";
    public static final String GT_DYNAMIC_PERF = "gt_dynamic_perf";
    public static final String GT_TOUCH = "gt_touch";
    public static final String GT_MI_TIME = "gt_mi_time";
    public static final String GT_EXTRAS = "gt_extras";
    public static final String GAME_MODES = "game_modes";
    public static final String GAME_CENTER_ADS_OFF = "game_center_ads_off";
    public static final String GAME_TOAST_OFF = "game_toast_off";
    public static final String GAME_MACRO = "game_macro_all_apps";
    public static final String GAME_NETWORK = "game_network_tools";
    public static final String JOYOSE_GPU = "joyose_gpu_tuner";
    public static final String JOYOSE_CLOUD_OFF = "joyose_cloud_off";
    public static final String JOYOSE_NO_FC = "joyose_no_fc";
    public static final String FPS_THERMAL_OFF = "fps_thermal_off";
    public static final String POWERKEEPER_MENU = "powerkeeper_hidden_menu";
    public static final String POWERKEEPER_TASK_BOOST = "powerkeeper_task_boost";
    // From kashi's Security Center mod
    public static final String VIDEO_SLEEP_LOCK = "video_sleep_lock";
    public static final String AOD_STANDARD_SENSORS = "aod_standard_sensors";
    public static final String AOD_SMART_SENSOR = "aod_smart_sensor";
    public static final String AOD_SCREEN_STATE = "aod_screen_state";
    public static final String AOD_PERF_LEVEL = "aod_perf_level";
    public static final String AOD_SCREEN_STATE_VALUE = "aod_screen_state_value";
    public static final String AOD_NOW_PLAYING_STYLE = "aod_now_playing_style";
    public static final String AOD_KITS = "aod_kits";
    /** Lock screen kits: order ("index:id" entries), shown on the AOD too, hidden while notifications show. */
    public static final String AOD_KIT_LIST = "aod_kit_list";
    public static final String AOD_KITS_ON_AOD = "aod_kits_on_aod";
    public static final String AOD_KITS_HIDE_NOTIF = "aod_kits_hide_notif";
    /** Where the kits sit, in thousandths of the screen height; 0 = right under the clock. */
    public static final String AOD_KITS_Y = "aod_kits_y";
    /** Kit shape as in kashi's mod: false = pill & circle, true = rounded square & rectangle. */
    public static final String AOD_KITS_GRID = "aod_kits_grid";
    public static final String AOD_KITS_SQUARE = "aod_kits_square";
    public static final String SIDEBAR_CLIPBOARD = "sidebar_clipboard_history";
    public static final String PANEL_GLASS_BLUR = "kashi_panel_glass_blur";
    public static final String DAILY_DATA_LIMIT = "kashi_daily_data_limit";
    public static final String SCREENSHOT_MAX_QUALITY = "screenshot_max_quality";
    public static final String SCREENSHOT_NO_CROP_LIMIT = "screenshot_no_crop_limit";
    public static final String AOD_ALL = "aod_all_features";
    public static final String KASHI_ADS_OFF = "kashi_ads_off";
    public static final String KASHI_SYSTEM_ALL = "kashi_system_all";
    public static final String KASHI_POWER_ALL = "kashi_power_all";
    public static final String KASHI_CALL_ALL = "kashi_call_all";
    public static final String KASHI_VIDEO_ALL = "kashi_video_all";
    public static final String KASHI_GAME_ALL = "kashi_game_all";
    public static final String AOD_DARK_OFF = "aod_dark_off";
    public static final String AOD_NOW_PLAYING = "aod_now_playing";
    public static final String AOD_CHARGING = "aod_charging_info";
    public static final String AOD_GLOW = "aod_edge_glow";
    public static final String AOD_BURNIN = "aod_burnin";
    public static final String AOD_BATTERY_CUTOFF = "aod_battery_cutoff";
    public static final String AOD_TIMEOUT = "aod_timeout";
    public static final String AOD_FLAGSHIP = "aod_flagship_effects";
    public static final String AOD_FULLSCREEN = "aod_fullscreen";
    public static final String AOD_EDITOR_UNLOCK = "aod_editor_unlock";
    public static final String AOD_LUNAR = "aod_lunar_everywhere";
    public static final String AOD_GRAYSCALE = "aod_grayscale";
    public static final String AOD_TIMEOUT_SECONDS = "aod_timeout_seconds";
    public static final String AOD_BATTERY_CUTOFF_PERCENT = "aod_battery_cutoff_percent";
    public static final String AOD_BURNIN_SECONDS = "aod_burnin_seconds";
    public static final String AOD_GLOW_COLOR = "aod_glow_color";
    public static final String AOD_CHARGING_POS = "aod_charging_position";
    public static final String CRASH_LOGS = "crash_logs";
    public static final String BATTERY_LIVE = "battery_live_info";
    public static final String FORCE_CN_PERM = "force_cn_permission_editor";
    public static final String APP_BATCH = "app_batch_actions";
    public static final String APP_INFO_MORE = "app_info_more";
    public static final String GAME_MEDIA = "game_media_controls";
    public static final String GAME_SESSION_SUMMARY = "game_session_summary";
    public static final String GAME_SHADE_BUTTON = "game_shade_button";
    public static final String VIDEO_NO_HEADSUP = "video_no_heads_up";
    public static final String LOCK_SCORE = "lock_score_100";
    public static final String CHARGE_LIMIT = "charge_limit";
    /** The level charging stops at with CHARGE_LIMIT on (60-100, default 80). */
    public static final String CHARGE_LIMIT_PERCENT = "charge_limit_percent";
    // Battery (more)
    public static final String BATTERY_LIFE = "battery_life";
    public static final String POWER_EXTRAS = "power_extras";
    public static final String POWER_SAVE_REFRESH = "power_save_keep_refresh";
    public static final String POWER_SAVE_BRIGHTNESS = "power_save_keep_brightness";
    public static final String POWER_SAVE_BOOT = "power_save_keep_on_boot";
    public static final String POWER_SAVE_NO_CPU_CAP = "power_save_no_cpu_cap";
    // Charging
    public static final String SMART_CHARGE = "smart_charge_unlock";
    public static final String SUPER_WIRELESS = "super_wireless_charge";
    // Network
    public static final String NET_QUOTA_KEEP = "net_quota_keep_data";
    public static final String NET_HOTSPOT_KEEP = "net_hotspot_keep";
    public static final String NET_APP_CONTROLS = "network_app_controls";
    public static final String IMS_ICONS = "ims_global_icons";
    public static final String SIM_MENUS = "sim_volte_wfc_menus";
    // Video and calls
    public static final String VIDEO_THEATRE = "video_theatre";
    public static final String VIDEO_DOLBY = "video_dolby";
    public static final String VIDEO_MEMC = "video_memc";
    public static final String VIDEO_SUPER_RES = "video_super_res";
    public static final String VIDEO_CONTOURS = "video_contours";
    public static final String VIDEO_TEMP_LIMIT = "video_temp_limit";
    public static final String VIDEO_MUSIC_RADAR = "video_music_radar";
    public static final String VIDEO_TOOLBOX = "video_toolbox";
    public static final String CALL_TOOLBOX = "call_toolbox";
    public static final String CALL_NOISE_REDUCTION = "call_noise_reduction";
    public static final String PRIVACY_OPERATOR = "privacy_operator";
    public static final String PRIVACY_TELECOM = "privacy_telecom";
    public static final String PRIVACY_MISIGHT = "privacy_misight";
    public static final String PRIVACY_AD_USAGE = "privacy_ad_usage";
    public static final String VIDEO_SCREEN_OFF_SOUND = "video_screen_off_sound";
    public static final String FIND_DEVICE = "find_device_on";
    public static final String FIND_DEVICE_GOOGLE_MAP = "find_device_google_map";
    public static final String VOLUME_SAFETY_OFF = "volume_safety_off";
    /** A fix, so on until turned off; "_on" so no earlier install has stored it as off. */
    public static final String FREEFORM_MOTION_CUES = "freeform_motion_cues_fix_on";
    public static final String NOTIFY_SHORT_VIBRATION = "notify_short_vibration";
    public static final String CHAIN_START_ONCE = "chain_start_allow_once";
    // Privacy and system
    public static final String FLAG_SECURE = "flag_secure_off";
    public static final String PERM_NO_WAIT = "perm_no_wait";
    public static final String SECOND_SPACE = "second_space";
    public static final String WARNINGS = "warnings";
    public static final String FBO = "fbo";
    public static final String TOOLBOX = "toolbox_layout";
    public static final String DATA_USAGE = "data_usage_control_center";
    public static final String APP_INFO = "app_info_actions";
    public static final String APP_INFO_BUTTONS = "app_info_buttons";
    public static final String APP_ICON_LAUNCH = "app_info_icon_launch";
    public static final String MMS_ADS = "mms_disable_ad";
    public static final String MMS_FRAUD = "mms_disable_fraud_risk_tip";
    public static final String MMS_OVERSEAS = "mms_disable_overseas_risk_tip";
    public static final String MMS_OLD_DEVICE = "mms_im_old_device";
    public static final String MMS_COPY_CODE = "mms_verification_code_auto_copy";
    public static final String HOME_PRESTART = "home_other_disable_prestart";
    public static final String HOME_REPORT = "home_title_hide_report";
    public static final String DOCK = "dock_50_apps";
    public static final String CAR_SICKNESS = "car_sickness_relief";
    public static final String APPLOCK_SCRAMBLE = "applock_pin_scramble";
    public static final String LOCKSCREEN_PIN_SCRAMBLE = "lockscreen_pin_scramble";
    public static final String EXTEND_UNLOCK = "extend_unlock";
    public static final String CLOCK_SECONDS = "statusbar_clock_seconds";
    public static final String LOW_BATTERY_QUIET = "low_battery_quiet";
    public static final String SIM_SUCCESS_DIALOG_OFF = "sim_success_dialog_off";
    public static final String ROOT_CHECK_OFF = "root_check_off";
    public static final String USB_INSTALL_CHECK_OFF = "usb_install_check_off";
    // Clipboard (system_server) and Security's app-list upload
    public static final String CLIPBOARD_WHITELIST = "clipboard_whitelist";
    public static final String CLIPBOARD_APPS = "clipboard_whitelist_apps";
    public static final String ENHANCED_KEYBOARD = "enhanced_keyboard";
    public static final String APPLIST_UPLOAD_OFF = "applist_upload_off";
    // Display
    public static final String DARK_MODE_ALL_APPS = "dark_mode_all_apps";
    // App store redirects
    public static final String STORE_FORCE_CHOOSER = "store_force_chooser";
    public static final String STORE_GETAPPS_MINI = "store_getapps_detailmini";
    // Themes
    public static final String THEME_THIRD_PARTY = "theme_third_party";
    public static final String THEME_FIX_RESET = "theme_fix_reset";
    // Optional package-manager behaviour (Core Patch feature names).
    public static final String CP_DOWNGRADE = "corepatch_downgrade";
    public static final String CP_VERIFY = "corepatch_verification";
    public static final String CP_ARSC = "corepatch_resource_arsc";
    public static final String CP_DIGEST = "corepatch_digest";
    public static final String CP_EXACT = "corepatch_exact_signature";
    public static final String CP_PREVIOUS = "corepatch_previous_signatures";
    public static final String CP_HIDDEN = "corepatch_hidden_api";
    public static final String CP_SHARED = "corepatch_shared_uid";
    public static final String CP_AGENT = "corepatch_verification_agent";
    public static final String CP_BLOCK = "corepatch_vendor_block";
    public static final String CP_PERSISTENT = "corepatch_persistent_updates";
    public static final String CP_SYSTEM_UI_UID = "corepatch_systemui_keep_uid";
    public static final String CP_APK_PROTECTION_REMOVE = "corepatch_apk_protection_remove";
    public static final String CP_DISABLE_SIG_VERIFY = "corepatch_disable_sig_verify";
    public static final String[] CORE_PATCH = {CP_DOWNGRADE, CP_VERIFY, CP_ARSC, CP_DIGEST, CP_EXACT,
            CP_PREVIOUS, CP_HIDDEN, CP_SHARED, CP_AGENT, CP_BLOCK, CP_PERSISTENT, CP_SYSTEM_UI_UID,
            CP_APK_PROTECTION_REMOVE, CP_DISABLE_SIG_VERIFY};
    // System updater
    public static final String UPDATER_OTA_VALIDATE = "updater_ota_validate_off";
    public static final String UPDATER_NO_AUTO_DIALOG = "updater_no_auto_dialog";

    public static final String[] ALL = {
            FIND_DEVICE, FIND_DEVICE_GOOGLE_MAP,
            BATTERY_RANKING, BATTERY_SCREEN_TIME, BATTERY_HEALTH, CHARGE_PROTECT, BATTERY_LIFE, POWER_EXTRAS,
            SMART_CHARGE, SUPER_WIRELESS, POWER_SAVE_REFRESH, POWER_SAVE_BRIGHTNESS, POWER_SAVE_BOOT, POWER_SAVE_NO_CPU_CAP, POWERKEEPER_MENU, VIDEO_NO_HEADSUP, GAME_SHADE_BUTTON, GAME_SESSION_SUMMARY, GAME_MEDIA, APP_INFO_MORE, APP_BATCH, FORCE_CN_PERM, BATTERY_LIVE, CRASH_LOGS, AOD_GRAYSCALE, AOD_LUNAR, AOD_EDITOR_UNLOCK, AOD_FULLSCREEN, AOD_FLAGSHIP, AOD_TIMEOUT, AOD_BATTERY_CUTOFF, AOD_BURNIN, AOD_GLOW, AOD_CHARGING, AOD_NOW_PLAYING, AOD_DARK_OFF, KASHI_GAME_ALL, KASHI_VIDEO_ALL, KASHI_CALL_ALL, KASHI_POWER_ALL, KASHI_SYSTEM_ALL, KASHI_ADS_OFF, AOD_ALL, SIDEBAR_CLIPBOARD, PANEL_GLASS_BLUR, DAILY_DATA_LIMIT, SCREENSHOT_MAX_QUALITY, SCREENSHOT_NO_CROP_LIMIT, AOD_KITS, AOD_SCREEN_STATE, AOD_SMART_SENSOR, AOD_STANDARD_SENSORS, VIDEO_SLEEP_LOCK, CHARGE_LIMIT, LOCK_SCORE,
            GAME_LOCK_MAX_FPS, FPS_THERMAL_OFF, POWERKEEPER_TASK_BOOST, GAME_FPS, GAME_KEEP_ENABLED, GT_HDR, GT_SUPER_RES, GT_DYNAMIC_PERF, GT_TOUCH, GT_MI_TIME, GT_EXTRAS, GAME_MODES,
            GAME_CENTER_ADS_OFF, GAME_TOAST_OFF, GAME_MACRO, GAME_NETWORK, JOYOSE_GPU, JOYOSE_CLOUD_OFF, JOYOSE_NO_FC,
            NET_QUOTA_KEEP, NET_HOTSPOT_KEEP, NET_APP_CONTROLS, IMS_ICONS, SIM_MENUS,
            VIDEO_TOOLBOX, VIDEO_THEATRE, VIDEO_DOLBY, VIDEO_MEMC, VIDEO_SUPER_RES, VIDEO_CONTOURS, VIDEO_TEMP_LIMIT,
            VIDEO_MUSIC_RADAR, VIDEO_SCREEN_OFF_SOUND, CALL_TOOLBOX, CALL_NOISE_REDUCTION, FREEFORM_MOTION_CUES, VOLUME_SAFETY_OFF, NOTIFY_SHORT_VIBRATION,
            FLAG_SECURE, PERM_NO_WAIT, SECOND_SPACE, WARNINGS, FBO, TOOLBOX, DATA_USAGE, APP_INFO, DOCK, CAR_SICKNESS, APPLOCK_SCRAMBLE, LOW_BATTERY_QUIET,
            SIM_SUCCESS_DIALOG_OFF, ROOT_CHECK_OFF, USB_INSTALL_CHECK_OFF, LOCKSCREEN_PIN_SCRAMBLE, EXTEND_UNLOCK, CLOCK_SECONDS,
            APP_INFO_BUTTONS, APP_ICON_LAUNCH, MMS_ADS, MMS_FRAUD, MMS_OVERSEAS, MMS_OLD_DEVICE, MMS_COPY_CODE, HOME_PRESTART, HOME_REPORT,
            DARK_MODE_ALL_APPS, STORE_FORCE_CHOOSER, STORE_GETAPPS_MINI,
            CLIPBOARD_WHITELIST, ENHANCED_KEYBOARD, APPLIST_UPLOAD_OFF, CHAIN_START_ONCE,
            PRIVACY_OPERATOR, PRIVACY_TELECOM, PRIVACY_MISIGHT, PRIVACY_AD_USAGE,
            THEME_THIRD_PARTY, THEME_FIX_RESET,
            CP_DOWNGRADE, CP_VERIFY, CP_ARSC, CP_DIGEST, CP_EXACT, CP_PREVIOUS, CP_HIDDEN, CP_SHARED, CP_AGENT, CP_BLOCK, CP_PERSISTENT, CP_SYSTEM_UI_UID,
            CP_APK_PROTECTION_REMOVE, CP_DISABLE_SIG_VERIFY,
            UPDATER_OTA_VALIDATE, UPDATER_NO_AUTO_DIALOG
    };

    /**
     * Off until chosen: everything the modded Security app did not do (extras taken from HyperCeiler
     * that change how the phone behaves), plus optional package-manager and framework policies.
     */
    private static final Set<String> OFF_BY_DEFAULT = Set.of(
            VIDEO_MUSIC_RADAR,
            GAME_LOCK_MAX_FPS, FPS_THERMAL_OFF, JOYOSE_NO_FC, POWER_SAVE_NO_CPU_CAP, POWERKEEPER_MENU, POWERKEEPER_TASK_BOOST, VIDEO_NO_HEADSUP, GAME_SHADE_BUTTON, GAME_SESSION_SUMMARY, GAME_MEDIA, APP_INFO_MORE, APP_BATCH, FORCE_CN_PERM, BATTERY_LIVE, CRASH_LOGS, AOD_GRAYSCALE, AOD_LUNAR, AOD_EDITOR_UNLOCK, AOD_FULLSCREEN, AOD_FLAGSHIP, AOD_TIMEOUT, AOD_BATTERY_CUTOFF, AOD_BURNIN, AOD_GLOW, AOD_CHARGING, AOD_NOW_PLAYING, AOD_DARK_OFF, KASHI_GAME_ALL, KASHI_VIDEO_ALL, KASHI_CALL_ALL, KASHI_POWER_ALL, KASHI_SYSTEM_ALL, KASHI_ADS_OFF, AOD_ALL, SIDEBAR_CLIPBOARD, PANEL_GLASS_BLUR, DAILY_DATA_LIMIT, SCREENSHOT_MAX_QUALITY, SCREENSHOT_NO_CROP_LIMIT, AOD_KITS, AOD_SCREEN_STATE, AOD_SMART_SENSOR, AOD_STANDARD_SENSORS, VIDEO_SLEEP_LOCK, CHARGE_LIMIT, LOCK_SCORE,
            FIND_DEVICE_GOOGLE_MAP, VIDEO_SCREEN_OFF_SOUND, CALL_NOISE_REDUCTION, UPDATER_OTA_VALIDATE, UPDATER_NO_AUTO_DIALOG, JOYOSE_CLOUD_OFF, JOYOSE_GPU,
            APPLOCK_SCRAMBLE, LOCKSCREEN_PIN_SCRAMBLE, EXTEND_UNLOCK, CLOCK_SECONDS, LOW_BATTERY_QUIET, GAME_CENTER_ADS_OFF, GAME_TOAST_OFF, GAME_MACRO, GAME_NETWORK,
            APP_INFO_BUTTONS, APP_ICON_LAUNCH, MMS_ADS, MMS_FRAUD, MMS_OVERSEAS, MMS_OLD_DEVICE, MMS_COPY_CODE, HOME_PRESTART, HOME_REPORT,
            NET_QUOTA_KEEP, NET_HOTSPOT_KEEP, SMART_CHARGE, SUPER_WIRELESS, CAR_SICKNESS,
            SIM_SUCCESS_DIALOG_OFF, ROOT_CHECK_OFF, USB_INSTALL_CHECK_OFF, DARK_MODE_ALL_APPS,
            STORE_FORCE_CHOOSER, STORE_GETAPPS_MINI, THEME_THIRD_PARTY, THEME_FIX_RESET, FLAG_SECURE, VOLUME_SAFETY_OFF, NOTIFY_SHORT_VIBRATION,
            CLIPBOARD_WHITELIST, ENHANCED_KEYBOARD, APPLIST_UPLOAD_OFF, CHAIN_START_ONCE,
            PRIVACY_OPERATOR, PRIVACY_TELECOM, PRIVACY_MISIGHT, PRIVACY_AD_USAGE,
            POWER_SAVE_REFRESH, POWER_SAVE_BRIGHTNESS, POWER_SAVE_BOOT, GAME_KEEP_ENABLED, NET_APP_CONTROLS, IMS_ICONS, SIM_MENUS,
            CP_DOWNGRADE, CP_VERIFY, CP_ARSC, CP_DIGEST, CP_EXACT, CP_PREVIOUS, CP_HIDDEN, CP_SHARED, CP_AGENT, CP_BLOCK, CP_PERSISTENT, CP_SYSTEM_UI_UID,
            CP_APK_PROTECTION_REMOVE, CP_DISABLE_SIG_VERIFY);

    /**
     * A brand-new install starts with every switch off except the Find Device fix, which stays on
     * until the user turns it off; the user turns on the rest.
     * Existing installs keep their choices: {@link #legacyDefault} is frozen into their stored
     * settings once (see MainActivity), so this change never turns anything off for them.
     */
    public static boolean defaultValue(String key) {
        return FIND_DEVICE.equals(key) || FREEFORM_MOTION_CUES.equals(key);
    }

    /**
     * The pre-1.4 default (the modded Security app's features on, HyperCeiler extras off). Written
     * once into an existing install's settings so an update never resets what they already had.
     */
    public static boolean legacyDefault(String key) {
        return !OFF_BY_DEFAULT.contains(key);
    }

    private static volatile SharedPreferences preferences;

    static void attach(SharedPreferences value) { preferences = value; }

    /** A plain stored switch that is not one of the feature switches (see AOD_KITS_ON_AOD). */
    static boolean flag(String key, boolean byDefault) {
        SharedPreferences p = preferences;
        try { return p == null ? byDefault : p.getBoolean(key, byDefault); } catch (Throwable t) { return byDefault; }
    }

    static SharedPreferences preferences() { return preferences; }

    static boolean enabled(String key) {
        return read(preferences, key);
    }

    /** Master switch that controls consolidated sub-features so the UI stays clean with zero lost functionality. */
    private static String masterOf(String key) {
        return switch (key) {
            case GAME_MODES, GT_SUPER_RES, GT_HDR, GT_DYNAMIC_PERF, GT_TOUCH, GT_MI_TIME, GT_EXTRAS,
                    GAME_MACRO, GAME_NETWORK -> KASHI_GAME_ALL;
            case VIDEO_TOOLBOX, VIDEO_THEATRE, VIDEO_DOLBY, VIDEO_MEMC, VIDEO_SUPER_RES,
                    VIDEO_CONTOURS, VIDEO_TEMP_LIMIT -> KASHI_VIDEO_ALL;
            case CALL_TOOLBOX, CALL_NOISE_REDUCTION -> KASHI_CALL_ALL;
            case BATTERY_LIFE, POWER_EXTRAS -> KASHI_POWER_ALL;
            case TOOLBOX, DOCK, CAR_SICKNESS, SECOND_SPACE, WARNINGS -> KASHI_SYSTEM_ALL;
            case APP_INFO_MORE, APP_INFO_BUTTONS -> APP_INFO;
            default -> null;
        };
    }

    /** Reads a feature switch, delegating consolidated sub-features to their master switch. */
    public static boolean read(SharedPreferences p, String key) {
        if (p == null) return defaultValue(key);
        String master = masterOf(key);
        if (master != null) {
            if (p.contains(master)) return p.getBoolean(master, false);
            return p.getBoolean(key, false);
        }
        boolean fallback = FIND_DEVICE.equals(key) || FREEFORM_MOTION_CUES.equals(key);
        return p.getBoolean(key, fallback);
    }

    public static boolean isVideoOption(String key) {
        return switch (key) {
            case VIDEO_THEATRE, VIDEO_DOLBY, VIDEO_MEMC, VIDEO_SUPER_RES, VIDEO_CONTOURS, VIDEO_TEMP_LIMIT -> true;
            default -> false;
        };
    }

    /** A stored list of package names (empty when unset or settings are unavailable). */
    static Set<String> stringSet(String key) {
        SharedPreferences p = preferences;
        Set<String> value = p == null ? null : p.getStringSet(key, null);
        return value == null ? Set.of() : value;
    }

    /** A chosen value (see the UI CHOICES), or the default when unset. */
    static int intValue(String key, int byDefault) {
        SharedPreferences p = preferences;
        return p == null ? byDefault : p.getInt(key, byDefault);
    }

    /** Charging stop level for CHARGE_LIMIT, kept between 60 and 100. */
    static int chargeLimit() {
        SharedPreferences p = preferences;
        int value = p == null ? 80 : p.getInt(CHARGE_LIMIT_PERCENT, 80);
        return Math.max(60, Math.min(100, value));
    }

    /** True once the remote settings are attached; false when they could not be read (e.g. early system_server). */
    static boolean ready() {
        return preferences != null;
    }

    private Features() { }
}
