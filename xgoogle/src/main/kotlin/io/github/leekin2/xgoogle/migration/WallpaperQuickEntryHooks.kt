package io.github.leekin2.xgoogle.migration

import android.content.Context
import android.content.res.Resources
import dalvik.system.BaseDexClassLoader
import io.github.leekin2.xgoogle.migration.compat.ModernHookRegistry
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Adds the existing clock plugin models to its native editor callback. */
internal object WallpaperQuickEntryHooks {
    private var installed = false
    private val entryIds = listOf("assistant", "wallet", "qr_code_scanner", "home")

    fun install(loader: ClassLoader) {
        if (ModernReflect.findClassIfExists("d1.c", loader) != null && bind(loader)) return
        BaseDexClassLoader::class.java.declaredConstructors.forEach { constructor ->
            ModernHookRegistry.installCompat("quick-entry:plugin-loader:$constructor", constructor,
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args.none { it is String && it.contains("KeyguardClockBase") }) return
                        val pluginLoader = param.thisObject as? ClassLoader ?: return
                        if (bind(pluginLoader)) ModernHookRegistry.unhookPrefix("quick-entry:plugin-loader:")
                    }
                })
        }
    }

    @Synchronized
    private fun bind(loader: ClassLoader): Boolean {
        if (installed) return true
        return runCatching {
            val manager = ModernReflect.findClass("d1.c", loader)
            val model = ModernReflect.findClass("d1.a", loader)
            val callbackType = ModernReflect.findClass("b.j", loader)
            val constructor = manager.getDeclaredConstructor(Context::class.java, Context::class.java, callbackType)
            val modelConstructor = model.getDeclaredConstructor(String::class.java, String::class.java,
                Int::class.javaPrimitiveType, String::class.java, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val key = ModernReflect.findField(model, "a")
            val pluginContextField = ModernReflect.findField(manager, "b")
            val callbackField = ModernReflect.findField(manager, "c")
            val callbackKind = ModernReflect.findField(callbackType, "a")
            val walletState = ModernReflect.findField(manager, "g")
            val homeState = ModernReflect.findField(manager, "f")
            val stateEnabled = ModernReflect.findField(walletState.type, "f")
            val isSystemApp = ModernReflect.findMethodExact(ModernReflect.findClass("i5.d", loader),
                "i", arrayOf(Context::class.java, String::class.java))
            require(stateEnabled.type == Boolean::class.javaPrimitiveType)
            val invoke = ModernReflect.findMethodExact(callbackType, "invoke", arrayOf(Any::class.java))
            val titleIds = Collections.synchronizedMap(WeakHashMap<Resources, Map<String, Int>>())
            val owners = Collections.synchronizedMap(WeakHashMap<Any, WeakReference<Any>>())
            ModernHookRegistry.installCompat("quick-entry:clock-editor", constructor, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val owner = param.thisObject ?: return
                    val callback = callbackField.get(owner) ?: return
                    owners[callback] = WeakReference(owner)
                }
            })
            ModernHookRegistry.installCompat("quick-entry:clock-list", invoke, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val callback = param.thisObject ?: return
                    if (callbackKind.getInt(callback) != 3) return
                    val owner = owners[callback]?.get() ?: return
                    val original = param.args[0] as? List<*> ?: return
                    val present = HashSet<String>(original.size)
                    var insertionIndex = original.size
                    original.forEachIndexed { index, entry ->
                        val id = entry?.let { key.get(it) as? String } ?: return@forEachIndexed
                        present.add(id)
                        if (id == "add") insertionIndex = index
                    }
                    if (entryIds.all(present::contains)) return
                    val pluginContext = pluginContextField.get(owner) as Context
                    val resources = pluginContext.resources
                    val ids = titleIds[resources] ?: entryIds.associateWith { id ->
                        val name = if (id == "home") "base_ui_affordance_homecontrol" else "base_ui_affordance_$id"
                        resources.getIdentifier(name, "string", pluginContext.packageName).also {
                            require(it != 0) { "Missing native resource $name" }
                        }
                    }.also { titleIds[resources] = it }
                    val completed = ArrayList(original)
                    fun add(id: String, icon: String, enabled: Boolean = true, packageName: String = "") {
                        if (id in present) return
                        val entry = modelConstructor.newInstance(id, resources.getString(ids.getValue(id)), 0,
                            "${icon}_icon", "${icon}_new", packageName, true, enabled, 0)
                        completed.add(insertionIndex++, entry)
                    }
                    if ("assistant" !in present) {
                        val geminiIsSystemApp = isSystemApp.invoke(null, pluginContext, "com.google.android.apps.bard") == true
                        add("assistant", if (geminiIsSystemApp) "affordance_digital_assistant" else "affordance_assistant",
                            packageName = "com.google.android.apps.bard")
                    }
                    if ("wallet" !in present)
                        add("wallet", "affordance_pay", walletState.get(owner)?.let { stateEnabled.getBoolean(it) } ?: false)
                    add("qr_code_scanner", "affordance_scanner")
                    if ("home" !in present)
                        add("home", "affordance_homecontrol", homeState.get(owner)?.let { stateEnabled.getBoolean(it) } ?: false)
                    param.args[0] = completed
                }
            })
            installed = true
            HookLog.i("NativeQuickEntries", "Restored native wallpaper clock editor entries")
            true
        }.getOrElse {
            // A retry must never reuse one hook with another attempt's owner map.
            runCatching { ModernHookRegistry.unhook("quick-entry:clock-list") }
            runCatching { ModernHookRegistry.unhook("quick-entry:clock-editor") }
            HookLog.w("NativeQuickEntries", "Clock editor binding unavailable; original behavior retained", it)
            false
        }
    }
}
