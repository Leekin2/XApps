package io.github.leekin2.xsecurity.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ReportOff
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HourglassDisabled
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UpdateDisabled
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.leekin2.xsecurity.Features as F

/** One switch of the home page. */
class Item(val key: String, val icon: ImageVector)

/**
 * A value a switch takes while it is on, picked from a list: stored as an int under [key]; the
 * row shows the [pick] text title and the formatted value.
 */
class Choice(val key: String, val pick: String, val values: List<Int>, val default: Int, val format: (Int, AppLanguage) -> String)

private fun minutes(v: Int, l: AppLanguage): String = when {
    v < 60 -> "$v " + when (l) { AppLanguage.TR -> "sn"; AppLanguage.RU -> "с"; AppLanguage.ZH -> "秒"; else -> "s" }
    else -> "${v / 60} " + when (l) { AppLanguage.TR -> "dk"; AppLanguage.RU -> "мин"; AppLanguage.ZH -> "分钟"; else -> "min" }
}

private fun dp(v: Int, @Suppress("UNUSED_PARAMETER") l: AppLanguage) = "$v dp"

private fun glowName(v: Int, l: AppLanguage): String = when (l) {
    AppLanguage.TR -> listOf("Mavi", "Camgöbeği", "Yeşil", "Çivit", "Turuncu", "Pembe", "Mor", "Kırmızı", "Deniz mavisi", "Sarı")
    AppLanguage.RU -> listOf("Синий", "Голубой", "Зелёный", "Индиго", "Оранжевый", "Розовый", "Фиолетовый", "Красный", "Бирюзовый", "Жёлтый")
    AppLanguage.ES -> listOf("Azul", "Cian", "Verde", "Índigo", "Naranja", "Rosa", "Morado", "Rojo", "Verde azulado", "Amarillo")
    AppLanguage.ZH -> listOf("蓝色", "青色", "绿色", "靛蓝", "橙色", "粉色", "紫色", "红色", "蓝绿", "黄色")
    else -> listOf("Blue", "Cyan", "Green", "Indigo", "Orange", "Pink", "Purple", "Red", "Teal", "Yellow")
}.getOrElse(v) { "$v" }

private fun perfName(v: Int, l: AppLanguage): String = when (l) {
    AppLanguage.TR -> mapOf(1 to "Dengeli", 2 to "Dengeli + amiral gemisi efektleri", 3 to "Amiral gemisi")
    AppLanguage.RU -> mapOf(1 to "Сбалансированный", 2 to "Сбалансированный + эффекты", 3 to "Флагман")
    AppLanguage.ES -> mapOf(1 to "Equilibrado", 2 to "Equilibrado + efectos", 3 to "Gama alta")
    AppLanguage.ZH -> mapOf(1 to "均衡", 2 to "均衡 + 旗舰效果", 3 to "旗舰")
    else -> mapOf(1 to "Balanced", 2 to "Balanced + flagship effects", 3 to "Flagship")
}[v] ?: "$v"

private fun stateName(v: Int, l: AppLanguage): String = when (v) {
    3 -> "Doze"
    4 -> when (l) { AppLanguage.TR -> "Doze askıda (Xiaomi)"; AppLanguage.RU -> "Doze с приостановкой (стандарт)"; AppLanguage.ES -> "Doze en suspensión (de serie)"; AppLanguage.ZH -> "Doze 挂起（默认）"; else -> "Doze suspend (stock)" }
    else -> when (l) { AppLanguage.TR -> "Her zaman açık (yalnız LCD; OLED'de yanma yapar)"; AppLanguage.RU -> "Всегда включён (только LCD; выжигает OLED)"; AppLanguage.ES -> "Siempre encendida (solo LCD; quema OLED)"; AppLanguage.ZH -> "常亮（仅 LCD；OLED 会烧屏）"; else -> "Always on (LCD only; burns in OLED)" }
}

private fun songName(v: Int, l: AppLanguage): String = when (l) {
    AppLanguage.TR -> listOf("Yalnızca yazı", "Hap ve yuvarlak kapak", "Kare ve kare kapak")
    AppLanguage.RU -> listOf("Только текст", "Таблетка с круглой обложкой", "Квадрат с обложкой")
    AppLanguage.ES -> listOf("Solo texto", "Píldora con portada redonda", "Cuadrado con portada")
    AppLanguage.ZH -> listOf("仅文字", "胶囊 + 圆形封面", "圆角方形 + 封面")
    else -> listOf("Text only", "Pill with round cover", "Rounded square with cover")
}.getOrElse(v) { "$v" }

/** Turkish puts the sign first (%80); English, Russian, Spanish and Chinese write 80%. */
private fun percent(v: Int, l: AppLanguage) = when (l) {
    AppLanguage.TR -> "%$v"
    AppLanguage.RU, AppLanguage.ES -> "$v %"
    else -> "$v%"
}

val CHOICES: Map<String, Choice> = mapOf(
    F.CHARGE_LIMIT to Choice(F.CHARGE_LIMIT_PERCENT, PICK_CHARGE_LIMIT, (60..100 step 5).toList(), 80, ::percent),
    F.AOD_TIMEOUT to Choice(F.AOD_TIMEOUT_SECONDS, PICK_AOD_TIMEOUT, listOf(10, 30, 60, 120, 300, 600, 1800), 60, ::minutes),
    F.AOD_BATTERY_CUTOFF to Choice(F.AOD_BATTERY_CUTOFF_PERCENT, PICK_AOD_CUTOFF, listOf(5, 10, 15, 20, 25, 30, 40, 50), 15, ::percent),
    F.AOD_GLOW to Choice(F.AOD_GLOW_COLOR, PICK_AOD_GLOW, (0..9).toList(), 0, ::glowName),
    F.AOD_CHARGING to Choice(F.AOD_CHARGING_POS, PICK_AOD_POSITION, listOf(40, 80, 120, 160, 200), 120, ::dp),
    F.AOD_FLAGSHIP to Choice(F.AOD_PERF_LEVEL, PICK_AOD_PERF, listOf(1, 2, 3), 3, ::perfName),
    F.AOD_SCREEN_STATE to Choice(F.AOD_SCREEN_STATE_VALUE, PICK_AOD_STATE, listOf(4, 3, 2), 4, ::stateName),
    F.AOD_NOW_PLAYING to Choice(F.AOD_NOW_PLAYING_STYLE, PICK_AOD_SONG, listOf(0, 1, 2), 0, ::songName),
    F.AOD_BURNIN to Choice(F.AOD_BURNIN_SECONDS, PICK_AOD_BURNIN, listOf(5, 10, 20, 30, 60, 120), 10, ::minutes),
)

/** A folding card of the home page, with its badge color. */
class Section(val id: String, val icon: ImageVector, val color: Color, val items: List<Item>)

/** The XAod panel shows the always-on display sections; XSecurity shows the rest. */
fun sectionsFor(state: UiState): List<Section> = SECTIONS.filter { it.id.startsWith("aod_") == state.aodOnly }

/** Home page order: Find Device and the updater first, then the most used groups. */
val SECTIONS = listOf(
    Section("finddevice", Icons.Rounded.LocationOn, Color(0xFF2F9E6E), listOf(
        Item(F.FIND_DEVICE, Icons.Rounded.LocationOn),
        Item(F.FIND_DEVICE_GOOGLE_MAP, Icons.Rounded.Map))),
    Section("updater", Icons.Rounded.SystemUpdate, Color(0xFF3482FF), listOf(
        Item(F.UPDATER_NO_AUTO_DIALOG, Icons.Rounded.UpdateDisabled),
        Item(F.UPDATER_OTA_VALIDATE, Icons.Rounded.SystemUpdate))),
    Section("charge", Icons.Rounded.BatteryChargingFull, Color(0xFF2EB872), listOf(
        Item(F.CHARGE_PROTECT, Icons.Rounded.BatteryChargingFull),
        Item(F.CHARGE_LIMIT, Icons.Rounded.BatteryChargingFull),
        Item(F.SMART_CHARGE, Icons.Rounded.Bolt),
        Item(F.SUPER_WIRELESS, Icons.Rounded.WifiTethering))),
    Section("games", Icons.Rounded.SportsEsports, Color(0xFFE5484D), listOf(
        Item(F.GAME_FPS, Icons.Rounded.Speed),
        Item(F.GAME_LOCK_MAX_FPS, Icons.Rounded.Speed),
        Item(F.FPS_THERMAL_OFF, Icons.Rounded.Speed),
        Item(F.JOYOSE_NO_FC, Icons.Rounded.SportsEsports),
        Item(F.POWER_SAVE_NO_CPU_CAP, Icons.Rounded.Bolt),
        Item(F.POWERKEEPER_TASK_BOOST, Icons.Rounded.Bolt),
        Item(F.POWERKEEPER_MENU, Icons.Rounded.Tune),
        Item(F.GAME_KEEP_ENABLED, Icons.Rounded.SportsEsports),
        Item(F.KASHI_GAME_ALL, Icons.Rounded.SportsEsports),
        Item(F.PANEL_GLASS_BLUR, Icons.Rounded.Tune),
        Item(F.GAME_MEDIA, Icons.Rounded.Movie),
        Item(F.GAME_SHADE_BUTTON, Icons.Rounded.NotificationsOff),
        Item(F.GAME_SESSION_SUMMARY, Icons.Rounded.Timer),
        Item(F.GAME_TOAST_OFF, Icons.Rounded.NotificationsOff),
        Item(F.GAME_CENTER_ADS_OFF, Icons.Rounded.Block),
        Item(F.JOYOSE_GPU, Icons.Rounded.DeveloperBoard),
        Item(F.JOYOSE_CLOUD_OFF, Icons.Rounded.CloudOff))),
    Section("network", Icons.Rounded.CellTower, Color(0xFF8E4EC6), listOf(
        Item(F.IMS_ICONS, Icons.Rounded.SimCard),
        Item(F.SIM_MENUS, Icons.Rounded.SimCard),
        Item(F.NET_APP_CONTROLS, Icons.Rounded.Wifi),
        Item(F.NET_QUOTA_KEEP, Icons.Rounded.SignalCellularAlt),
        Item(F.NET_HOTSPOT_KEEP, Icons.Rounded.Wifi),
        Item(F.DAILY_DATA_LIMIT, Icons.Rounded.DataUsage),
        Item(F.DATA_USAGE, Icons.Rounded.DataUsage))),
    Section("battery", Icons.Rounded.BatteryStd, Color(0xFFF2A11B), listOf(
        Item(F.BATTERY_RANKING, Icons.Rounded.Insights),
        Item(F.BATTERY_SCREEN_TIME, Icons.Rounded.Schedule),
        Item(F.BATTERY_HEALTH, Icons.Rounded.BatteryStd),
        Item(F.BATTERY_LIVE, Icons.Rounded.Insights),
        Item(F.KASHI_POWER_ALL, Icons.Rounded.Bolt),
        Item(F.POWER_SAVE_REFRESH, Icons.Rounded.Speed),
        Item(F.POWER_SAVE_BRIGHTNESS, Icons.Rounded.Tune),
        Item(F.POWER_SAVE_BOOT, Icons.Rounded.PowerSettingsNew),
        Item(F.LOW_BATTERY_QUIET, Icons.Rounded.NotificationsOff))),
    Section("media", Icons.Rounded.Movie, Color(0xFFE0558E), listOf(
        Item(F.KASHI_VIDEO_ALL, Icons.Rounded.Movie),
        Item(F.VIDEO_SLEEP_LOCK, Icons.Rounded.Timer),
        Item(F.VIDEO_NO_HEADSUP, Icons.Rounded.NotificationsOff),
        Item(F.VIDEO_MUSIC_RADAR, Icons.Rounded.Movie),
        Item(F.VIDEO_SCREEN_OFF_SOUND, Icons.Rounded.VolumeUp),
        Item(F.KASHI_CALL_ALL, Icons.Rounded.VideoCall),
        Item(F.VOLUME_SAFETY_OFF, Icons.Rounded.VolumeUp),
        Item(F.NOTIFY_SHORT_VIBRATION, Icons.Rounded.Vibration))),
    Section("system", Icons.Rounded.Info, Color(0xFF6E7787), listOf(
        Item(F.FLAG_SECURE, Icons.Rounded.Screenshot),
        Item(F.SCREENSHOT_MAX_QUALITY, Icons.Rounded.Screenshot),
        Item(F.SCREENSHOT_NO_CROP_LIMIT, Icons.Rounded.Crop),
        Item(F.KASHI_SYSTEM_ALL, Icons.Rounded.Info),
        Item(F.KASHI_ADS_OFF, Icons.Rounded.Block),
        Item(F.APP_INFO, Icons.Rounded.Info),
        Item(F.APP_ICON_LAUNCH, Icons.Rounded.TouchApp),
        Item(F.APP_BATCH, Icons.Rounded.Tune),
        Item(F.FORCE_CN_PERM, Icons.Rounded.Security),
        Item(F.CRASH_LOGS, Icons.Rounded.ReportOff),
        Item(F.SIDEBAR_CLIPBOARD, Icons.Rounded.ContentPaste),
        Item(F.FREEFORM_MOTION_CUES, Icons.Rounded.ViewSidebar),
        Item(F.PERM_NO_WAIT, Icons.Rounded.HourglassDisabled),
        Item(F.APPLOCK_SCRAMBLE, Icons.Rounded.Keyboard),
        Item(F.LOCKSCREEN_PIN_SCRAMBLE, Icons.Rounded.Keyboard),
        Item(F.EXTEND_UNLOCK, Icons.Rounded.LockOpen),
        Item(F.CLOCK_SECONDS, Icons.Rounded.Schedule),
        Item(F.FBO, Icons.Rounded.Storage),
        Item(F.CHAIN_START_ONCE, Icons.Rounded.Link),
        Item(F.SIM_SUCCESS_DIALOG_OFF, Icons.Rounded.SimCard),
        Item(F.ROOT_CHECK_OFF, Icons.Rounded.Security),
        Item(F.LOCK_SCORE, Icons.Rounded.Security),
        Item(F.USB_INSTALL_CHECK_OFF, Icons.Rounded.Usb),
        Item(F.DARK_MODE_ALL_APPS, Icons.Rounded.DarkMode),
        Item(F.CLIPBOARD_WHITELIST, Icons.Rounded.ContentPaste),
        Item(F.ENHANCED_KEYBOARD, Icons.Rounded.Keyboard),
        Item(F.APPLIST_UPLOAD_OFF, Icons.Rounded.CloudOff))),
    Section("privacy", Icons.Rounded.CloudOff, Color(0xFF4F6BD8), listOf(
        Item(F.PRIVACY_OPERATOR, Icons.Rounded.CellTower),
        Item(F.PRIVACY_TELECOM, Icons.Rounded.CellTower),
        Item(F.PRIVACY_MISIGHT, Icons.Rounded.CloudOff),
        Item(F.PRIVACY_AD_USAGE, Icons.Rounded.Block))),
    Section("messages", Icons.Rounded.Sms, Color(0xFF2E9C86), listOf(
        Item(F.MMS_ADS, Icons.Rounded.Block),
        Item(F.MMS_FRAUD, Icons.Rounded.Warning),
        Item(F.MMS_OVERSEAS, Icons.Rounded.Public),
        Item(F.MMS_OLD_DEVICE, Icons.Rounded.PhoneAndroid),
        Item(F.MMS_COPY_CODE, Icons.Rounded.ContentCopy))),
    Section("launcher", Icons.Rounded.Home, Color(0xFF658BDA), listOf(
        Item(F.HOME_PRESTART, Icons.Rounded.Speed),
        Item(F.HOME_REPORT, Icons.Rounded.ReportOff))),
    Section("store", Icons.Rounded.Storefront, Color(0xFF1FA2A6), listOf(
        Item(F.STORE_FORCE_CHOOSER, Icons.Rounded.Storefront),
        Item(F.STORE_GETAPPS_MINI, Icons.Rounded.PictureInPictureAlt))),
    Section("theme", Icons.Rounded.Palette, Color(0xFFD6409F), listOf(
        Item(F.THEME_THIRD_PARTY, Icons.Rounded.Palette),
        Item(F.THEME_FIX_RESET, Icons.Rounded.Lock))),
    Section("aod_look", Icons.Rounded.Palette, Color(0xFF5B5BD6), listOf(
        Item(F.AOD_ALL, Icons.Rounded.DarkMode),
        Item(F.AOD_EDITOR_UNLOCK, Icons.Rounded.Palette),
        Item(F.AOD_FULLSCREEN, Icons.Rounded.PhoneAndroid),
        Item(F.AOD_FLAGSHIP, Icons.Rounded.Speed),
        Item(F.AOD_GRAYSCALE, Icons.Rounded.DarkMode),
        Item(F.AOD_LUNAR, Icons.Rounded.Schedule))),
    Section("aod_show", Icons.Rounded.Timer, Color(0xFF2F9E6E), listOf(
        Item(F.AOD_TIMEOUT, Icons.Rounded.Timer),
        Item(F.AOD_BATTERY_CUTOFF, Icons.Rounded.BatteryStd),
        Item(F.AOD_DARK_OFF, Icons.Rounded.DarkMode),
        Item(F.AOD_SMART_SENSOR, Icons.Rounded.TouchApp),
        Item(F.AOD_STANDARD_SENSORS, Icons.Rounded.TouchApp),
        Item(F.AOD_BURNIN, Icons.Rounded.Tune),
        Item(F.AOD_SCREEN_STATE, Icons.Rounded.PhoneAndroid))),
    Section("aod_extras", Icons.Rounded.Insights, Color(0xFFE0558E), listOf(
        Item(F.AOD_KITS, Icons.Rounded.Insights),
        Item(F.AOD_NOW_PLAYING, Icons.Rounded.Movie),
        Item(F.AOD_CHARGING, Icons.Rounded.BatteryChargingFull),
        Item(F.AOD_GLOW, Icons.Rounded.NotificationsOff))),
    Section("corepatch", Icons.Rounded.DeveloperBoard, Color(0xFF997744),
        F.CORE_PATCH.map { Item(it, Icons.Rounded.DeveloperBoard) }),
)

/**
 * How the screen looks. [hyper] picks the HyperOS (Miuix) style, otherwise Material You.
 * [theme]: 0 follows the system, 1 light, 2 dark. [monet] and [blur] belong to the HyperOS style.
 */
data class Look(val hyper: Boolean = true, val theme: Int = 0, val monet: Boolean = false, val blur: Boolean = false)

data class UiState(
    /** null while LSPosed has not answered yet. */
    val connected: Boolean? = null,
    val values: Map<String, Boolean> = emptyMap(),
    val language: AppLanguage = AppLanguage.TR,
    val look: Look = Look(),
    val settingsOpen: Boolean = false,
    val restarting: Boolean = false,
    /** Whether the home screen launcher icon is currently hidden. */
    val iconHidden: Boolean = false,
    /** How many apps are on the background clipboard list. */
    val clipboardApps: Int = 0,
    /** Chosen values of the switches that take one (see CHOICES), by value key. */
    val ints: Map<String, Int> = emptyMap(),
    /** True on the XAod panel (always-on display options only). */
    val aodOnly: Boolean = false,
    /** Native-only HyperOS launchers do not run the Java launcher hook entry. */
    val nativeLauncher: Boolean = false,
)

fun featureAvailable(state: UiState, key: String): Boolean =
    !state.nativeLauncher || key !in setOf(F.HOME_PRESTART, F.HOME_REPORT)

fun featureCanChange(state: UiState, key: String): Boolean =
    state.connected != false && (featureAvailable(state, key) || state.values[key] == true)

/** Everything the screens can ask for. */
class Actions(
    val onChange: (String, Boolean) -> Unit,
    val onRestart: () -> Unit,
    val onLanguage: (AppLanguage) -> Unit,
    val onLook: (Look) -> Unit,
    val onSettings: (Boolean) -> Unit,
    val onLsposed: () -> Unit,
    /** Hides or shows the home screen launcher icon. */
    val onHideIcon: (Boolean) -> Unit,
    /** Opens the picker for apps allowed to read the clipboard in the background. */
    val onPickClipboardApps: () -> Unit,
    /** Opens Google's Extend Unlock page, hidden on HyperOS. */
    val onOpenExtendUnlock: () -> Unit,
    /** Opens the value picker of a switch that takes one (see CHOICES). */
    val onPickChoice: (String) -> Unit = {},
    /** Opens the lock screen kits editor. */
    val onOpenKits: () -> Unit = {},
)
