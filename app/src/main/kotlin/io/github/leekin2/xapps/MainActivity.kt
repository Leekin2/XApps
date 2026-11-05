// HyperOS-style components from compose-miuix-ui/miuix (Apache-2.0).
package io.github.leekin2.xapps

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import io.github.leekin2.xapps.common.ServiceHub
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** One XApps part: its switch key, texts, icon and the screen holding its own settings. */
private class Part(val key: String, val title: Int, val summary: Int, val icon: ImageVector, val color: Color,
                   val settings: Class<out Activity>, val oldPackage: String)

private val PARTS = listOf(
    Part(Parts.GOOGLE, R.string.google, R.string.google_summary, Icons.Rounded.Search, Color(0xFF4285F4),
        io.github.leekin2.xgoogle.MainActivity::class.java, "io.github.leekin2.xgoogle"),
    Part(Parts.SECURITY, R.string.security, R.string.security_summary, Icons.Rounded.Shield, Color(0xFF34A853),
        io.github.leekin2.xsecurity.MainActivity::class.java, "io.github.leekin2.xsecurity"),
    Part(Parts.NOTIFY, R.string.notify, R.string.notify_summary, Icons.Rounded.Notifications, Color(0xFFFF8A00),
        io.github.leekin2.xnotify.SettingsActivity::class.java, "io.github.leekin2.xnotify"),
)

class MainActivity : ComponentActivity() {
    private var service by mutableStateOf<XposedService?>(null)
    private var switches by mutableStateOf(mapOf<String, Boolean>())
    private var oldInstalled by mutableStateOf(listOf<Part>())
    private var iconHidden by mutableStateOf(false)

    // The alias class is under the code namespace.
    private val launcherAlias get() = ComponentName(this, "io.github.leekin2.xapps.Launcher")

    private val listener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(value: XposedService) { runOnUiThread { bind(value) } }
        override fun onServiceDied(value: XposedService) { runOnUiThread { if (service == value) service = null } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceHub.current()?.let(::bind)
        ServiceHub.register(listener)
        setContent { Theme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        iconHidden = packageManager.getComponentEnabledSetting(launcherAlias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // Re-checked on return from an uninstall.
        oldInstalled = PARTS.filter { part ->
            runCatching { packageManager.getPackageInfo(part.oldPackage, 0) }.isSuccess
        }
    }

    /** Hides or shows the home screen icon; the app still opens from LSPosed when hidden. */
    private fun changeIconHidden(hidden: Boolean) {
        runCatching {
            packageManager.setComponentEnabledSetting(launcherAlias,
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
            iconHidden = hidden
        }
    }

    override fun onDestroy() {
        ServiceHub.unregister(listener)
        super.onDestroy()
    }

    private fun bind(value: XposedService) {
        service = value
        val prefs = runCatching { value.getRemotePreferences(Parts.GROUP) }.getOrNull()
        switches = PARTS.associate { it.key to (prefs?.getBoolean(it.key, true) ?: true) }
    }

    private fun change(key: String, on: Boolean) {
        val prefs = runCatching { service?.getRemotePreferences(Parts.GROUP) }.getOrNull() ?: return
        prefs.edit().putBoolean(key, on).apply()
        switches = switches + (key to on)
    }

    @Composable
    private fun Screen() {
        val bar = MiuixScrollBehavior()
        val connected = service != null
        Scaffold(topBar = { TopAppBar(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.xapps_subtitle), scrollBehavior = bar) }) { padding ->
            LazyColumn(
                Modifier.fillMaxHeight().overScrollVertical().nestedScroll(bar.nestedScrollConnection),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
                overscrollEffect = null
            ) {
                item { Status(connected) }
                if (oldInstalled.isNotEmpty()) item {
                    SmallTitle(stringResource(R.string.group_old), Modifier.padding(top = 6.dp))
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        oldInstalled.forEach { part ->
                            ArrowPreference(title = stringResource(part.title), summary = stringResource(R.string.old_summary),
                                startAction = { Tile(Icons.Rounded.Delete, Color(0xFFE53935)) },
                                onClick = { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + part.oldPackage))) })
                        }
                    }
                }
                item { SmallTitle(stringResource(R.string.group_parts), Modifier.padding(top = 6.dp)) }
                PARTS.forEach { part ->
                    item(key = part.key) {
                        Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            val on = switches[part.key] ?: true
                            SwitchPreference(checked = on, onCheckedChange = { change(part.key, it) },
                                title = stringResource(part.title), summary = stringResource(part.summary),
                                startAction = { Tile(part.icon, part.color) }, enabled = connected)
                            ArrowPreference(title = stringResource(R.string.open_settings),
                                startAction = { Tile(Icons.Rounded.Tune, null) }, enabled = on,
                                onClick = { startActivity(Intent(this@MainActivity, part.settings)) })
                        }
                    }
                }
                item(key = "icon") {
                    Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        SwitchPreference(checked = iconHidden, onCheckedChange = ::changeIconHidden,
                            title = stringResource(R.string.xapps_hide_icon), summary = stringResource(R.string.xapps_hide_icon_summary),
                            startAction = { Tile(Icons.Rounded.VisibilityOff, null) })
                    }
                }
                item {
                    Text(stringResource(R.string.xapps_restart_hint) + "\n" + stringResource(R.string.xapps_version, BuildConfig.VERSION_NAME),
                        Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                }
            }
        }
    }

    @Composable
    private fun Status(connected: Boolean) {
        val accent = if (connected) Color(0xFF34A853) else Color(0xFFE53935)
        Card(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 8.dp)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center) {
                    Icon(if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.HighlightOff, null, Modifier.size(28.dp), tint = accent)
                }
                Spacer(Modifier.width(16.dp))
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(if (connected) R.string.connected else R.string.not_connected),
                        fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text(stringResource(if (connected) R.string.connected_summary else R.string.not_connected_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp)
                }
            }
        }
    }

    /** Coloured rounded square with a white icon, or a plain icon when [color] is null. */
    @Composable
    private fun Tile(icon: ImageVector, color: Color?) {
        if (color == null) {
            Icon(icon, null, Modifier.padding(end = 14.dp).size(24.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            return
        }
        Box(Modifier.padding(end = 14.dp).size(38.dp).clip(RoundedCornerShape(12.dp)).background(color),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
        }
    }

    @Composable
    private fun Theme(content: @Composable () -> Unit) {
        val dark = isSystemInDarkTheme()
        val context = LocalContext.current
        LaunchedEffect(dark) {
            val activity = context as? Activity ?: return@LaunchedEffect
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        val controller = remember(dark) { ThemeController(if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight) }
        MiuixTheme(controller = controller, content = content)
    }
}
