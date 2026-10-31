package io.github.leekin2.xgoogle.migration

import android.content.Intent
import io.github.leekin2.xgoogle.FeatureConfig
import io.github.leekin2.xgoogle.FeaturePolicy
import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log

/**
 * HyperOS leaves Google's Quick Share tile out of the control center on the China ROM
 * (IS_INTERNATIONAL_BUILD): the control center plugin leaves it out of the edit list, and
 * SystemUI never creates a stored one. With the GMS restriction removed Quick Share works,
 * so offer its tile as the global ROM does.
 */
internal object QuickShareTileHooks {
    private const val SPEC = "custom(com.google.android.gms/.nearby.sharing.SharingTileService)"
    private const val PLUGIN = "miui.systemui.plugin"

    fun install(loader: ClassLoader) {
        if (!installedHooks.add("quick-share-tile")) return
        runCatching { hookCreation(loader) }.onFailure { log("Quick Share tile not created on the China ROM", it) }
        runCatching { hookPluginLoader(loader) }.onFailure { log("Control center plugin loader not found", it) }
    }

    /** The stored tile: create it where HyperOS returns null for it. */
    private fun hookCreation(loader: ClassLoader) {
        val impl = XposedHelpers.findClass("com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractorImpl", loader)
        val tileSpec = XposedHelpers.findClass("com.android.systemui.qs.pipeline.shared.TileSpec", loader)
        val getSpec = tileSpec.getMethod("getSpec")
        val ext = impl.getDeclaredField("interactorExt").apply { isAccessible = true }
        val factoryField = ext.type.getDeclaredField("tileFactory").apply { isAccessible = true }
        val createTile = factoryField.type.getMethod("createTile", String::class.java)
        XposedBridge.hookMethod(impl.getDeclaredMethod("createTileSync", tileSpec), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.result != null || param.throwable != null) return
                if (getSpec.invoke(param.args[0]) != SPEC || !FeatureConfig.enabled(FeaturePolicy.GMS)) return
                val factory = factoryField.get(ext.get(param.thisObject)) ?: return
                param.result = createTile.invoke(factory, SPEC)
            }
        })
        log("Quick Share tile can be created in the control center")
    }

    /** SystemUI loads the control center plugin with a class loader of its own, unseen by LSPosed. */
    private fun hookPluginLoader(loader: ClassLoader) {
        val factory = XposedHelpers.findClass("com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", loader)
        val appInfo = factory.getDeclaredField("pluginAppInfo").apply { isAccessible = true }
        XposedBridge.hookMethod(factory.getDeclaredMethod("createClassLoader"), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val plugin = param.result as? ClassLoader ?: return
                if ((appInfo.get(param.thisObject) as? android.content.pm.ApplicationInfo)?.packageName == PLUGIN) installPlugin(plugin)
            }
        })
    }

    /** miui.systemui.plugin, loaded into SystemUI: its edit list leaves the tile out (filterNearby). */
    fun installPlugin(loader: ClassLoader) {
        if (!installedHooks.add("quick-share-tile-plugin")) return
        runCatching {
            val companion = XposedHelpers.findClass("miui.systemui.controlcenter.qs.customize.TileQueryHelper\$Companion", loader)
            XposedBridge.hookMethod(companion.getDeclaredMethod("filterNearby", String::class.java), object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == SPEC && FeatureConfig.enabled(FeaturePolicy.GMS)) param.result = false
                }
            })
            log("Quick Share tile offered in the control center edit list")
        }.onFailure { installedHooks.remove("quick-share-tile-plugin"); log("Quick Share tile not offered in the control center edit list", it) }
    }

    /**
     * system_server: Quick Share's "View downloads" (VIEW_DOWNLOADS) is pinned by HyperOS to its
     * download manager screen, which does not show the received files. Unpin it and offer a chooser,
     * so the File Manager (or any app the user picks) opens the Downloads folder. After HyperCeiler's
     * BypassForceDownloadui, written anew; limited to this action, as other start hooks share the path.
     */
    fun hookSystem(loader: ClassLoader) {
        if (!installedHooks.add("quick-share-downloads")) return
        runCatching {
            val controller = XposedHelpers.findClass("com.android.server.wm.ActivityStartController", loader)
            val hooks = XposedBridge.hookAllMethods(controller, "startActivityInPackage", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val index = param.args.indexOfFirst { it is Intent }
                    val intent = param.args.getOrNull(index) as? Intent ?: return
                    if (intent.action != VIEW_DOWNLOADS || !FeatureConfig.enabled(FeaturePolicy.GMS)) return
                    intent.setPackage(null)
                    param.args[index] = Intent.createChooser(intent, null)
                }
            })
            check(hooks.isNotEmpty()) { "ActivityStartController.startActivityInPackage was not found" }
            log("Quick Share \"View downloads\" opens a chooser instead of the download manager")
        }.onFailure { installedHooks.remove("quick-share-downloads"); log("Quick Share downloads redirect not fixed", it) }
    }

    private const val VIEW_DOWNLOADS = "android.intent.action.VIEW_DOWNLOADS"
}
