// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.content.Context
import android.provider.Settings
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.leekin2.xgoogle.migration.AssistantPowerHooks.isPowerAssistantEnabled
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SETTINGS_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

internal object AssistantSettingsHooks {
    /** Contextual-search settings visibility and the power-key assistant setting. */
    fun hookSettings(classLoader: ClassLoader) {
        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
            hookContextualSearchSystemFeature(SETTINGS_PACKAGE, classLoader)
        }
        hookSettingsPowerMenuDetails(classLoader)
    }
    private fun hookSettingsPowerMenuDetails(classLoader: ClassLoader) {
        val utilsClass = XposedHelpers.findClassIfExists(
            "com.android.settings.gestures.PowerMenuSettingsUtils",
            classLoader,
        ) ?: return
        listOf(
            "isLongPressPowerSettingAvailable",
            "isLongPressPowerForAssistantEnabled",
            "setLongPressPowerForAssistant",
            "setLongPressPowerForPowerMenu",
        ).forEach { methodName ->
            val key = "settings:power-menu-utils:$methodName"
            if (!installedHooks.add(key)) return@forEach
            runCatching {
                XposedBridge.hookAllMethods(
                    utilsClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val context = param.args.firstOrNull() as? Context ?: return
                            when (methodName) {
                                "isLongPressPowerSettingAvailable" -> param.result = true
                                "isLongPressPowerForAssistantEnabled" ->
                                    param.result = isPowerAssistantEnabled(context)

                                "setLongPressPowerForAssistant" -> {
                                    writeNativePowerChoice(context, assistant = true)
                                    param.result = true
                                }

                                "setLongPressPowerForPowerMenu" -> {
                                    writeNativePowerChoice(context, assistant = false)
                                    param.result = true
                                }
                            }
                        }
                    },
                )
            }.onFailure {
                installedHooks.remove(key)
                log("Failed to bridge $methodName to the power-key assistant setting", it)
            }
        }
        log("Connected the power-menu settings to the power-key assistant setting")
    }
    private fun writeNativePowerChoice(context: Context, assistant: Boolean) {
        val resolver = context.contentResolver
        Settings.System.putInt(
            resolver,
            SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
            if (assistant) 1 else 0,
        )
        Settings.Secure.putInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            if (assistant) 0 else 1,
        )
        Settings.Global.putInt(
            resolver,
            "power_button_long_press",
            if (assistant) 5 else 1,
        )
        if (assistant) {
            Settings.Global.putInt(resolver, "key_chord_power_volume_up", 2)
        }
    }
}
