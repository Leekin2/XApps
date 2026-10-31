package io.github.leekin2.xgoogle.migration

import android.content.Context
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

/**
 * The car projection role is static: only the package named by the framework's
 * config_systemAutomotiveProjection may hold it, and the HyperOS CN ROM leaves that empty.
 * Without the role Android Auto lacks the projection permissions and cannot start in the car.
 * Name Android Auto as the default holder when the ROM names none, so the role controller
 * (part of service-permission.jar in system_server) grants the role itself on boot.
 */
internal object AutomotiveRoleHooks {
    private const val ROLE = "android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION"
    private const val ANDROID_AUTO = "com.google.android.projection.gearhead"

    fun install(classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists("com.android.role.controller.model.Role", classLoader) ?: return
        val key = "automotive-role:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "getDefaultHoldersAsUser",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val current = param.result as? List<*> ?: return
                        if (current.isNotEmpty()) return
                        val name = runCatching { XposedHelpers.callMethod(param.thisObject, "getName") as? String }
                            .getOrNull()
                        if (name != ROLE) return
                        val context = param.args.firstOrNull { it is Context } as? Context ?: return
                        val installed = runCatching { context.packageManager.getPackageInfo(ANDROID_AUTO, 0) }.isSuccess
                        if (installed) param.result = listOf(ANDROID_AUTO)
                    }
                },
            )
            log("Android Auto named as the car projection role holder")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to name Android Auto as the car projection role holder", it)
        }
    }
}
