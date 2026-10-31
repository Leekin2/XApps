// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Process
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_UI_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.GOOGLE_APP_PACKAGE
import io.github.leekin2.xgoogle.migration.AssistantRuntime.findSystemContext
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks
import io.github.leekin2.xgoogle.migration.AssistantRuntime.systemContext

internal object ContextualSearchHooks {
    fun hookContextualSearch(packageName: String, classLoader: ClassLoader) {
        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
            hookContextualSearchSystemFeature(packageName, classLoader)
        }
        if (contextualSearchConfigId == null) {
            contextualSearchConfigId = findContextualSearchConfigId(classLoader)
        }

        XposedHelpers.findClassIfExists("com.android.server.SystemServer", classLoader)?.let { cls ->
            val key = "$packageName:contextual-search-config:${cls.name}"
            if (!installedHooks.add(key)) return@let
            runCatching {
                XposedHelpers.findAndHookMethod(
                    cls,
                    "deviceHasConfigString",
                    Context::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!isCircleFeatureEnabled()) return
                            val resId = param.args.getOrNull(1) as? Int ?: return
                            if (resId == contextualSearchConfigId) {
                                param.result = true
                            }
                        }
                    }
                )
            }.onFailure {
                installedHooks.remove(key)
                log("Failed to hook contextual search config check", it)
            }
        }

        XposedHelpers.findClassIfExists(
            "com.android.server.contextualsearch.ContextualSearchManagerService",
            classLoader
        )?.let { cls ->
            hookContextualSearchService(cls)
            cls.declaredClasses.forEach(::hookContextualSearchService)
        }
    }
    private fun hookContextualSearchService(cls: Class<*>) {
        val key = "contextual:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.atomic {
                cls.declaredMethods
                    .filter { it.name == "getContextualSearchPackageName" }
                    .forEach { method ->
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (isCircleFeatureEnabled()) {
                                        param.result = GOOGLE_APP_PACKAGE
                                    }
                                }
                            }
                        )
                    }
                cls.declaredMethods
                    .filter { it.name == "enforcePermission" }
                    .forEach { method ->
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (isCircleFeatureEnabled() &&
                                        isTrustedContextualSearchCaller()
                                    ) {
                                        param.result = null
                                    }
                                }
                            }
                        )
                    }
                cls.declaredMethods
                    .filter { it.name == "startContextualSearch" }
                    .forEach { method ->
                        XposedBridge.hookMethod(method, ContextualSearchIdentityHook)
                    }
            }
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook contextual search service ${cls.name}", it)
        }
    }
    private object ContextualSearchIdentityHook : XC_MethodHook() {
        private val identity = ThreadLocal<Long?>()

        override fun beforeHookedMethod(param: MethodHookParam) {
            if (!isCircleFeatureEnabled() || !isTrustedContextualSearchCaller()) return
            identity.set(Binder.clearCallingIdentity())
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            identity.get()?.let(Binder::restoreCallingIdentity)
            identity.remove()
        }
    }
    fun triggerCircleToSearchC17(): Boolean {
        val token = Binder.clearCallingIdentity()
        return runCatching {
            val api = contextualSearchApi
            // Cache only reflection members. Always resolve the current service Binder.
            val binder = api.getService.invoke(null, "contextual_search") as? IBinder ?: return@runCatching false
            val service = api.asInterface.invoke(null, binder)
                ?: return@runCatching false
            // A null ContextualSearchConfig selects the framework's DEFAULT_CONFIG.
            // Android 16 (HyperOS 3) has no config class, only startContextualSearch(int).
            if (api.withConfig) api.start.invoke(service, 1, null) else api.start.invoke(service, 1)
            true
        }.onFailure {
            log("Failed to trigger Circle to Search through the ColorOS 17 API", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }
    private class ContextualSearchApi {
        val getService = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
        val asInterface = Class.forName("android.app.contextualsearch.IContextualSearchManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
        private val manager = Class.forName("android.app.contextualsearch.IContextualSearchManager")
        val start = runCatching {
            manager.getMethod("startContextualSearch", Int::class.javaPrimitiveType,
                Class.forName("android.app.contextualsearch.ContextualSearchConfig"))
        }.getOrElse { manager.getMethod("startContextualSearch", Int::class.javaPrimitiveType) }
        val withConfig = start.parameterCount == 2
    }
    // A failed lazy initialization remains retryable when framework classes become available.
    private val contextualSearchApi by lazy { ContextualSearchApi() }
    private fun findContextualSearchConfigId(classLoader: ClassLoader): Int? {
        return runCatching {
            val cls = XposedHelpers.findClass("com.android.internal.R\$string", classLoader)
            XposedHelpers.getStaticIntField(cls, "config_defaultContextualSearchPackageName")
        }.getOrNull()
    }
    private fun isCircleFeatureEnabled(): Boolean {
        return LspConfig.isAssistantNativeCircleEnabledXposed()
    }
    private fun isTrustedContextualSearchCaller(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.SYSTEM_UID) return true
        return runCatching {
            val context = systemContext ?: findSystemContext() ?: return@runCatching false
            val packages = context.packageManager.getPackagesForUid(uid) ?: return@runCatching false
            // HyperOS routes the nav-handle long press through XiaoAi (see XiaoAiNavRedirectHooks).
            packages.contains(SYSTEM_UI_PACKAGE) ||
                packages.contains(XiaoAiNavRedirectHooks.XIAOAI_PACKAGE)
        }.getOrDefault(false)
    }
    @Volatile
    private var contextualSearchConfigId: Int? = null
}
