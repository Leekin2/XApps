// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantVoiceHooks.refreshLiveAssistantBinding
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT
import io.github.leekin2.xgoogle.migration.AssistantRuntime.findContext
import io.github.leekin2.xgoogle.migration.AssistantRuntime.resolveDefaultAssistant
import io.github.leekin2.xgoogle.migration.AssistantRuntime.resolveCurrentUserId
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks
import io.github.leekin2.xgoogle.migration.AssistantRuntime.systemContext

internal object AssistantPowerHooks {
    /** Power-key assistant routing. */
    fun hookNativePowerLongPress(packageName: String, classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists(
            "com.android.server.policy.PhoneWindowManagerExtImpl",
            classLoader,
        ) ?: return
        val key = "$packageName:native-power-long-press:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.atomic {
                val targets = cls.declaredMethods
                    .filter { method ->
                        method.name == "startSpeech" &&
                            method.parameterTypes.contentEquals(
                                arrayOf(
                                    Integer.TYPE,
                                    Integer.TYPE,
                                    java.lang.Long.TYPE,
                                ),
                            )
                    }
                check(targets.isNotEmpty()) { "C17 startSpeech(int, int, long) was not found" }
                targets.forEach { method ->
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val source = param.thisObject ?: return
                                val context = findContext(source) ?: return
                                applyC17NativePowerConfiguration(source, context)
                            }
                        },
                    )
                }
                XposedBridge.hookAllMethods(
                    cls,
                    "isSpeechDisabled",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val context = findContext(param.thisObject) ?: systemContext ?: return
                            param.result = !isPowerAssistantEnabled(context)
                        }
                    },
                )
                XposedBridge.hookAllMethods(
                    cls,
                    "registerSettingsForOplusLocked",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val source = param.thisObject ?: return
                            val context = findContext(source) ?: systemContext ?: return
                            registerPowerSettingBridge(source, context)
                            applyC17NativePowerConfiguration(source, context)
                        }
                    },
                )
                log("Hooked the ColorOS 17 native power-button assistant entry")
            }
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook the ColorOS 17 native power-button assistant entry", it)
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun applyC17NativePowerConfiguration(source: Any, context: Context) {
        runCatching {
            XposedHelpers.setObjectField(source, "mSpeechAsssistForBreeno", false)
            XposedHelpers.setObjectField(
                source,
                "mhasGoogleAssistant",
                resolveDefaultAssistant(context) != null,
            )

            // C17 EXP stores 0=enabled, 1=disabled and -1=not configured/show guide.
            // Keep those values unchanged so the stock startSpeech() state machine remains intact.
            val internationalPowerState = Settings.Secure.getInt(
                context.contentResolver,
                SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
                -1,
            )
            val currentUser = XposedHelpers.getObjectField(source, "mCurrentUserId") as? Int ?: 0
            val enabledMap = XposedHelpers.getObjectField(source, "mSpeechEnabledMap")
                as? MutableMap<Any?, Any?>
            enabledMap?.set(currentUser, internationalPowerState)
        }.onFailure {
            log("Failed to select the ColorOS 17 native power assistant route", it)
        }
    }
    private fun registerPowerSettingBridge(source: Any, context: Context) {
        if (powerSettingBridgeObservers != null) return
        synchronized(this) {
            if (powerSettingBridgeObservers != null) return
            val resolver = context.contentResolver
            val handler = Handler(Looper.getMainLooper())
            val domesticObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    syncPowerChoiceFromDomesticSetting(source, context)
                }
            }
            val internationalObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    syncPowerChoiceFromInternationalSetting(source, context)
                }
            }
            resolver.registerContentObserver(
                Settings.System.getUriFor(SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT),
                false,
                domesticObserver,
            )
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP),
                false,
                internationalObserver,
            )
            powerSettingBridgeObservers = listOf(domesticObserver, internationalObserver)
            lastObservedPowerAssistantChoice = Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != 0

            if (Settings.Secure.getInt(
                    resolver,
                    SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
                    -1,
                ) == -1
            ) {
                syncPowerChoiceFromDomesticSetting(source, context)
            } else {
                syncPowerChoiceFromInternationalSetting(source, context)
            }
            log("Connected the ColorOS power-button UI to the international assistant setting")
        }
    }
    private fun syncPowerChoiceFromDomesticSetting(source: Any, context: Context) {
        val resolver = context.contentResolver
        val domesticValue = Settings.System.getInt(
            resolver,
            SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
            1,
        )
        val assistantEnabled = domesticValue != 0
        val previousAssistantEnabled = lastObservedPowerAssistantChoice
        lastObservedPowerAssistantChoice = assistantEnabled
        val targetInternationalValue = if (assistantEnabled) 0 else 1
        val currentInternationalValue = Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )
        if (currentInternationalValue != targetInternationalValue) {
            Settings.Secure.putInt(
                resolver,
                SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
                targetInternationalValue,
            )
        }
        applyC17NativePowerConfiguration(source, context)
        if (assistantEnabled && previousAssistantEnabled == false) {
            refreshLiveAssistantBinding(context, resolveCurrentUserId())
        }
    }
    private fun syncPowerChoiceFromInternationalSetting(source: Any, context: Context) {
        val resolver = context.contentResolver
        val internationalValue = Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )
        if (internationalValue == -1) return
        val targetDomesticValue = if (internationalValue == 1) 0 else 1
        if (Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != targetDomesticValue
        ) {
            Settings.System.putInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                targetDomesticValue,
            )
        }
        applyC17NativePowerConfiguration(source, context)
    }
    fun isPowerAssistantEnabled(context: Context?): Boolean {
        context ?: return false
        val resolver = context.contentResolver
        return when (Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )) {
            1 -> false
            0 -> true
            else -> Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != 0
        }
    }
    @Volatile
    private var powerSettingBridgeObservers: List<ContentObserver>? = null
    @Volatile
    private var lastObservedPowerAssistantChoice: Boolean? = null
}
