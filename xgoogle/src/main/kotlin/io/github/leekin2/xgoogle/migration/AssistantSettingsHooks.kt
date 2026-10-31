// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantFeatureHooks.hookNativeAssistantFeatureRoute
import io.github.leekin2.xgoogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.leekin2.xgoogle.migration.AssistantPowerHooks.isPowerAssistantEnabled
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SETTINGS_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT
import io.github.leekin2.xgoogle.migration.AssistantRuntime.findContext
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

internal object AssistantSettingsHooks {
    /** Contextual-search settings visibility. */
    fun hookSettings(classLoader: ClassLoader) {
        val nativeCircleEnabled = LspConfig.isAssistantNativeCircleEnabledXposed()

        if (nativeCircleEnabled) {
            hookContextualSearchSystemFeature(SETTINGS_PACKAGE, classLoader)
            hookSettingsCircleToSearchFeature(classLoader)
        }
        hookNativeAssistantFeatureRoute(SETTINGS_PACKAGE, classLoader)
        hookSettingsBreenoSpeechFeature(classLoader)
        hookSettingsPowerMenuDetails(classLoader)
    }
    private fun hookSettingsPowerMenuDetails(classLoader: ClassLoader) {
        val controllerClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.convenient.PowerMenuPreferenceController",
            classLoader,
        )
        controllerClass?.declaredMethods
            ?.filter { it.name == "lambda\$displayPreference\$0" }
            ?.forEach { method ->
                val key = "settings:power-menu-details:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val context = findContext(param.thisObject) ?: return
                                openNativePowerMenuSettings(context)
                                param.result = null
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to connect the power-menu details page", it)
                }
            }

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
                log("Failed to bridge $methodName to the C17 power setting", it)
            }
        }
        log("Connected the inert C17 power-menu details entry to its native fragment")
    }
    private fun openNativePowerMenuSettings(context: Context) {
        val intent = Intent().apply {
            component = ComponentName(SETTINGS_PACKAGE, "com.android.settings.SubSettings")
            putExtra(
                ":settings:show_fragment",
                "com.android.settings.gestures.PowerMenuSettings",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
    private fun hookSettingsCircleToSearchFeature(classLoader: ClassLoader) {
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.utils.CustomizeFeatureUtils",
            classLoader,
        ) ?: return
        val key = "settings:native-circle-feature"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isSupportCircleToSearch",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                            param.result = true
                        }
                    }
                },
            )
            log("Enabled the ColorOS 17 Circle to Search settings category")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to enable the ColorOS 17 Circle to Search settings category", it)
        }
    }
    private fun hookSettingsBreenoSpeechFeature(classLoader: ClassLoader) {
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.utils.FeatureUtils",
            classLoader,
        ) ?: return
        val key = "settings:disable-breeno-speech-feature"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isSupportSpeechAssist",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                },
            )
            log("Disabled the conflicting Breeno navigation assistant setting")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to disable the Breeno navigation assistant setting", it)
        }
    }
}
