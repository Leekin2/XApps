// Material component conventions follow tiann/KernelSU (GPL-3.0); the HyperOS style uses
// compose-miuix-ui/miuix (Apache-2.0); see NOTICE.txt.
package io.github.leekin2.xgoogle.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun XGoogleTheme(look: Look, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // The theme choice belongs to the HyperOS style; the Material screen always follows the system.
    val dark = when (if (look.hyper) look.theme else 0) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) {
        val activity = context as? Activity ?: return@LaunchedEffect
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    if (look.hyper) {
        // The stock HyperOS palette, or the wallpaper's colors (Monet) when chosen.
        val mode = if (look.monet) { if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight }
            else { if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light }
        val controller = remember(mode) { ThemeController(mode) }
        MiuixTheme(controller = controller, content = content)
    } else {
        MaterialExpressiveTheme(
            colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
