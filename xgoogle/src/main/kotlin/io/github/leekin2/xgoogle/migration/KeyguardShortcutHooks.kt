package io.github.leekin2.xgoogle.migration

import io.github.leekin2.xgoogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect as XposedHelpers
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks

/**
 * HyperOS lock screen shortcuts: XGoogle publishes its entries through the
 * "miui_keyguard_shortcuts" meta-data; this admits them past the picker's white list.
 * The tags keep their first names: HyperOS stores the user's picks by tag.
 * The shortcut code ships in com.miui.aod and is also loaded into SystemUI, which
 * clears any saved shortcut that is not white-listed.
 */
internal object KeyguardShortcutHooks {
    const val AOD_PACKAGE = "com.miui.aod"
    private const val COOGLE_PACKAGE = "io.github.leekin2.xapps"
    private const val DATA_UTILS = "com.miui.keyguard.shortcuts.utils.DataUtils"
    private val TAGS = listOf("coogle_assistant", "coogle_lens", "coogle_wallet", "coogle_home")

    /** com.miui.aod: the shortcut classes are in the app's own loader. */
    fun install(classLoader: ClassLoader) {
        hookWhiteList(classLoader)
    }

    private const val HOME_PANEL = "com.google.android.apps.chromecast.app/.systemcontrol.panel.GoogleHomePanelActivity"

    /**
     * The Home controls shortcut opens Google Home's panel over the lock screen, and HyperOS leaves
     * it open, so it is back over the lock screen the next time the phone wakes. Close it when the
     * screen goes off, as Pixel's controls do.
     */
    private fun closeHomePanelOnScreenOff() {
        val key = "keyguard-shortcuts:home-panel"
        if (!installedHooks.add(key)) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val context = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as android.content.Context
                context.registerReceiver(object : android.content.BroadcastReceiver() {
                    override fun onReceive(c: android.content.Context, intent: android.content.Intent) {
                        runCatching {
                            val panel = android.content.ComponentName.unflattenFromString(HOME_PANEL)
                            @Suppress("DEPRECATION")
                            val tasks = c.getSystemService(android.app.ActivityManager::class.java).getRunningTasks(10)
                            val service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
                            val remove = service.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                            for (task in tasks) if (task.topActivity == panel || task.baseActivity == panel) remove.invoke(service, task.taskId)
                        }.onFailure { log("Google Home panel not closed", it) }
                    }
                }, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF), android.content.Context.RECEIVER_EXPORTED)
            }.onFailure { installedHooks.remove(key); log("Failed to watch the screen for the Home panel", it) }
        }
    }

    /**
     * At boot SystemUI can check the saved shortcuts against its white list before XGoogle has
     * added its entries to it, and then resets them to none. Keep a saved XGoogle shortcut when
     * SystemUI tries to blank it; changes made in the shortcut picker (another process) still apply.
     */
    private fun keepShortcutsAtBoot() {
        val key = "keyguard-shortcuts:keep-at-boot"
        if (!installedHooks.add(key)) return
        runCatching {
            val put = android.provider.Settings.Secure::class.java.getMethod("putStringForUser",
                android.content.ContentResolver::class.java, String::class.java, String::class.java, Int::class.javaPrimitiveType)
            XposedBridge.hookMethod(put, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args[1] as? String ?: return
                    if (name != "keyguard_shortcut_left" && name != "keyguard_shortcut_right") return
                    if (!io.github.leekin2.xgoogle.FeatureConfig.enabled(io.github.leekin2.xgoogle.FeaturePolicy.QUICK_ENTRIES)) return
                    val value = param.args[2] as? String
                    if (value != null && value.contains("packageName")) return
                    val resolver = param.args[0] as? android.content.ContentResolver ?: return
                    val saved = android.provider.Settings.Secure.getString(resolver, name) ?: return
                    if (!saved.contains(COOGLE_PACKAGE)) return
                    log("Kept the XGoogle lock screen shortcut ($name) that SystemUI tried to reset")
                    param.result = true
                }
            })
        }.onFailure { installedHooks.remove(key); log("Failed to keep lock screen shortcuts at boot", it) }
    }

    /** SystemUI: catch the loader created for MIUIAod.apk. */
    fun installInSystemUi() {
        closeHomePanelOnScreenOff()
        keepShortcutsAtBoot()
        val key = "keyguard-shortcuts:aod-loader"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllConstructors(
                dalvik.system.BaseDexClassLoader::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dexPath = param.args.firstOrNull() as? String ?: return
                        if (!dexPath.contains("MIUIAod")) return
                        val loader = param.thisObject as? ClassLoader ?: return
                        hookWhiteList(loader)
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to watch for the HyperOS lock screen shortcut loader", it)
        }
    }

    private fun hookWhiteList(classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists(DATA_UTILS, classLoader) ?: return
        val key = "keyguard-shortcuts:white-list@${System.identityHashCode(classLoader)}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "loadWhiteItems",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val items = param.result as? MutableList<Any?> ?: return
                        // DataUtils.buildUniqueTag joins package and tag with "-".
                        TAGS.map { "$COOGLE_PACKAGE-$it" }
                            .filterNot(items::contains)
                            .forEach(items::add)
                    }
                },
            )
            log("Admitted XGoogle entries to the HyperOS lock screen shortcut list")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to admit XGoogle lock screen shortcuts", it)
        }
    }
}
