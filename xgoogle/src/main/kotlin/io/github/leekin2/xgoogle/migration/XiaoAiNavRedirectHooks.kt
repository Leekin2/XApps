package io.github.leekin2.xgoogle.migration

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import io.github.leekin2.xgoogle.FeatureConfig
import io.github.leekin2.xgoogle.FeaturePolicy
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.ContextualSearchHooks.triggerCircleToSearch
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

/**
 * HyperOS 4: the native (Rust) launcher hands the nav-handle long press to XiaoAi's
 * VoiceService as ACTION_ASSIST, like the double tap and the side swipe-and-hold. Each gesture has its
 * own setting: on answers it with Circle to Search, off leaves it untouched to XiaoAi (screen
 * recognition, the intent ball or whatever the system sets). A request without a trigger stays with XiaoAi.
 */
internal object XiaoAiNavRedirectHooks {
    const val XIAOAI_PACKAGE = "com.miui.voiceassist"
    private const val START_NOT_STICKY = 2
    /** The launcher names the gesture: NavLongPress, NavDoubleClick or SideSlideHold. */
    private const val TRIGGER_TYPE = "triggerType"
    private const val LONG_PRESS = "NavLongPress"
    private const val DOUBLE_TAP = "NavDoubleClick"
    private const val SIDE_HOLD = "SideSlideHold"
    /** The side hold sends the ball press, then its release or cancel under the same trigger. */
    private const val FUNCTION_KEY = "voice_assist_function_key"
    private const val BALL_PRESS = "home_intentional_ball"
    private const val TRIGGER_FROM = "triggerFrom"
    private const val LAUNCHER = "MiuiHome"
    private val VOICE_SERVICE = ComponentName(XIAOAI_PACKAGE, "com.xiaomi.voiceassistant.VoiceService")

    @Volatile
    private var lastRedirectAt = 0L

    private enum class Route { OPEN, SWALLOW }

    /**
     * OPEN for a gesture set to Circle to Search, SWALLOW for the side hold's release and cancel,
     * null to leave the request to XiaoAi.
     */
    private fun route(intent: Intent): Route? = when (intent.getStringExtra(TRIGGER_TYPE)) {
        LONG_PRESS -> if (FeatureConfig.enabled(FeaturePolicy.CIRCLE_LONG_PRESS)) Route.OPEN else null
        DOUBLE_TAP -> if (FeatureConfig.enabled(FeaturePolicy.CIRCLE_DOUBLE_TAP)) Route.OPEN else null
        SIDE_HOLD -> when {
            !FeatureConfig.enabled(FeaturePolicy.CIRCLE_SIDE_HOLD) -> null
            intent.getStringExtra(FUNCTION_KEY).let { it == null || it == BALL_PRESS } -> Route.OPEN
            else -> Route.SWALLOW
        }
        else -> null
    }

    /**
     * A short tick as Circle to Search opens, like on Pixel. It follows the touch feedback setting
     * unless "always vibrate" is on; then it is sent as hardware feedback, which Android does not
     * silence with touch feedback.
     */
    private fun tick(context: android.content.Context?) {
        context ?: return
        runCatching {
            val always = FeatureConfig.enabled(FeaturePolicy.CIRCLE_VIBRATE)
            if (!always && android.provider.Settings.System.getInt(context.contentResolver,
                    android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
            val vibrator = context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator ?: return
            val effect = android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK)
            if (always) vibrator.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            else vibrator.vibrate(effect)
        }.onFailure { log("Circle to Search haptic unavailable", it) }
    }

    fun install(classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists(
            "com.xiaomi.voiceassistant.VoiceService",
            classLoader,
        ) ?: return
        val key = "xiaoai:nav-redirect:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.atomic {
                val targets = cls.declaredMethods.filter {
                    it.name == "onStartCommand" && it.parameterTypes.size == 3
                }
                check(targets.isNotEmpty()) { "VoiceService.onStartCommand was not found" }
                targets.forEach { method ->
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                                val intent = param.args.firstOrNull() as? Intent ?: return
                                if (intent.action != Intent.ACTION_ASSIST) return
                                // Only the gestures set to Circle to Search; XiaoAi keeps the rest.
                                val route = route(intent) ?: return
                                // The launcher can deliver the same press twice.
                                val now = SystemClock.elapsedRealtime()
                                if (route == Route.SWALLOW || now - lastRedirectAt < 1000) {
                                    param.result = START_NOT_STICKY
                                    return
                                }
                                lastRedirectAt = now
                                if (triggerCircleToSearch()) {
                                    param.result = START_NOT_STICKY
                                    tick(param.thisObject as? android.content.Context)
                                }
                            }
                        },
                    )
                }
            }
            log("Hooked the HyperOS nav-handle XiaoAi route")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook the HyperOS nav-handle XiaoAi route", it)
        }
    }

    /**
     * system_server: answer the launcher's request before it reaches XiaoAi. HyperOS often stops
     * XiaoAi in the background, and starting its processes first made Circle to Search open about
     * half a second late. The XiaoAi hook above stays for any request that gets past this one.
     */
    fun hookSystem(classLoader: ClassLoader) {
        val key = "xiaoai:nav-redirect:system"
        if (!installedHooks.add(key)) return
        runCatching {
            val ams = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)
            val targets = ams.declaredMethods.filter { method ->
                method.name == "startService" && method.returnType == ComponentName::class.java &&
                    Intent::class.java in method.parameterTypes
            }
            check(targets.isNotEmpty()) { "ActivityManagerService.startService was not found" }
            XposedBridge.atomic {
                targets.forEach { method ->
                    val index = method.parameterTypes.indexOf(Intent::class.java)
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val intent = param.args[index] as? Intent ?: return
                            if (intent.action != Intent.ACTION_ASSIST || intent.component != VOICE_SERVICE) return
                            if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                            // Only the launcher's gestures set to Circle to Search; XiaoAi keeps everything else.
                            val route = runCatching {
                                if (intent.getStringExtra(TRIGGER_FROM) == LAUNCHER) route(intent) else null
                            }.getOrNull() ?: return
                            // The launcher sends the same press twice.
                            val now = SystemClock.elapsedRealtime()
                            if (route == Route.SWALLOW || now - lastRedirectAt < 1000) {
                                param.result = VOICE_SERVICE
                                return
                            }
                            if (!triggerCircleToSearch()) return
                            lastRedirectAt = now
                            param.result = VOICE_SERVICE
                            tick(AssistantRuntime.findSystemContext())
                        }
                    })
                }
            }
            log("Nav-handle long press opens Circle to Search from the system, without waiting for XiaoAi")
        }.onFailure {
            installedHooks.remove(key)
            log("System nav-handle route unavailable; the XiaoAi route is kept", it)
        }
    }
}
