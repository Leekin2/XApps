// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.leekin2.xgoogle.migration.ContextualSearchHooks.triggerCircleToSearch
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_UI_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.INVOCATION_TYPE_CIRCLE_TO_SEARCH
import io.github.leekin2.xgoogle.migration.AssistantRuntime.INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.findSystemContext
import io.github.leekin2.xgoogle.migration.AssistantRuntime.isGestureCircleToSearchEnabled
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks
import io.github.leekin2.xgoogle.migration.AssistantRuntime.systemContext

internal object AssistantSystemUiHooks {
    fun hookSystemUi(classLoader: ClassLoader) {
        hookSystemUiAttachRetry()
        hookSystemUiRoutes(classLoader)
    }
    /** The contextual-search feature gate and the assistant route. */
    private fun hookSystemUiRoutes(classLoader: ClassLoader) {
        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
            hookContextualSearchSystemFeature(SYSTEM_UI_PACKAGE, classLoader)
        }
        hookAssistManagerRoute(classLoader)
    }
    /** Circle to Search requests reach AssistManager.startAssist; send them to the native service. */
    private fun hookAssistManagerRoute(classLoader: ClassLoader) {
        val assistManagerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.assist.AssistManager",
            classLoader,
        ) ?: return
        val key = "systemui:native-assistant-route-scope"
        if (!installedHooks.add(key)) return
        runCatching {
            val activityManager = XposedHelpers.findField(assistManagerClass, "mActivityManager")
            require(ActivityManager::class.java.isAssignableFrom(activityManager.type))
            XposedBridge.hookAllMethods(
                assistManagerClass,
                "startAssist\$1",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = param.args.firstOrNull() as? Bundle ?: return
                        val type = bundle.getInt("invocation_type", 0)
                        if (type != INVOCATION_TYPE_CIRCLE_TO_SEARCH &&
                            type != INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE) return
                        if (!LspConfig.isAssistantNativeCircleEnabledXposed() ||
                            !isGestureCircleToSearchEnabled(systemContext ?: findSystemContext())) return
                        val manager = param.thisObject ?: return
                        val activity = activityManager.get(manager) as? ActivityManager ?: return
                        // Route every circle request before the assistant picker/metrics path.
                        // Keep native recognition, animation and lock-task restrictions intact.
                        if (activity.lockTaskModeState == 0 && triggerCircleToSearch()) {
                            param.result = null
                        }
                    }
                },
            )
            log("Hooked the SystemUI assistant route for Circle to Search")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook the SystemUI assistant route", it)
        }
    }
    private fun hookSystemUiAttachRetry() {
        val key = "systemui:application-attach"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        if (systemContext == null) {
                            systemContext = context.applicationContext ?: context
                        }
                        sequenceOf(
                            context.classLoader,
                            param.thisObject?.javaClass?.classLoader,
                            Thread.currentThread().contextClassLoader
                        ).filterNotNull().distinct().forEach { classLoader ->
                            hookSystemUiRoutes(classLoader)
                        }
                    }
                }
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to install SystemUI assistant retry", it)
        }
    }
}
