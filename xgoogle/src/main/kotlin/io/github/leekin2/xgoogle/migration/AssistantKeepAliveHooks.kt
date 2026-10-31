package io.github.leekin2.xgoogle.migration

import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantRuntime.GOOGLE_APP_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

/**
 * HyperOS "one-key clean" (clear all recents / memory cleanup) force-stops the Google app,
 * including the process hosting the voice interaction service. A force-stopped package is
 * not rebound, so the power key and Hey Google stay dead until Google is opened again.
 * HyperOS spares its own assistant; give the Google assistant process the same treatment
 * while the rest of the Google app is still cleaned normally.
 */
internal object AssistantKeepAliveHooks {
    private const val INTERACTOR = "$GOOGLE_APP_PACKAGE:interactor"

    fun install(classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists("com.android.server.am.ProcessCleanerBase", classLoader) ?: return
        val key = "keepalive:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "killOnce",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val app = param.args.firstOrNull() ?: return
                        val processName = runCatching {
                            XposedHelpers.getObjectField(app, "processName") as? String
                        }.getOrNull() ?: return
                        if (processName != GOOGLE_APP_PACKAGE && !processName.startsWith("$GOOGLE_APP_PACKAGE:")) return
                        // The voice interaction service and its sandboxed Hey Google detector;
                        // neither is restarted by the system once killed.
                        if (processName == INTERACTOR || processName.contains("hotword_detector")) {
                            param.result = null
                            return
                        }
                        // killOnce(app, policy, reason, canForceStop, ...): kill this process only,
                        // never force-stop the package (that would take the assistant down too).
                        if (param.args.size > 3 && param.args[3] is Boolean) param.args[3] = false
                    }
                },
            )
            log("Kept the Google assistant process out of HyperOS one-key clean")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to protect the Google assistant from HyperOS one-key clean", it)
        }
    }
}
