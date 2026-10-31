package io.github.leekin2.xgoogle.migration

import android.app.Activity
import android.content.Context
import android.provider.Settings
import io.github.leekin2.xgoogle.WalletPowerPolicy
import io.github.leekin2.xgoogle.migration.compat.ModernMethodHook
import io.github.leekin2.xgoogle.migration.compat.ModernReflect

/**
 * The China ROM's "Double press power button" screen (Security core) offers Mi Pay, Alipay and
 * WeChat but no wallet. Add Google Wallet as an option: it selects HyperOS's own wallet function
 * ("launch_QAW_wallet"), which WalletPowerHooks routes to Google Wallet.
 */
internal object PowerKeyWalletOption {
    const val PACKAGE = "com.miui.securitycore"
    private const val ACTIVITY = "com.miui.miinput.gesture.powerkey.DoubleClickPowerKeySettingsActivity"
    private const val KEY = "launch_QAW_wallet"
    private const val SETTING = "double_click_power_key"

    fun install(loader: ClassLoader) {
        runCatching {
            val activityClass = ModernReflect.findClass(ACTIVITY, loader)
            ModernReflect.findAndHookMethod(activityClass, "onCreate", android.os.Bundle::class.java,
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        tryAdd(activity, 0)
                    }
                },
            )
            HookLog.i("PowerKeyWalletOption", "Google Wallet offered for the power key")
        }.onFailure { HookLog.w("PowerKeyWalletOption", "Power key screen unavailable; no Google Wallet option", it) }
    }

    /** Xiaomi's list is built a moment after the screen opens; wait for it (up to about 2 s). */
    private fun tryAdd(activity: Activity, attempt: Int) {
        activity.window.decorView.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            if (!addOption(activity) && attempt < 10) tryAdd(activity, attempt + 1)
        }, 200)
    }

    /** False while Xiaomi's list is not there yet. */
    private fun addOption(activity: Activity): Boolean {
        if (!io.github.leekin2.xgoogle.FeatureConfig.enabled(io.github.leekin2.xgoogle.FeaturePolicy.WALLET)) return true
        return runCatching {
            val label = runCatching {
                activity.packageManager.getApplicationLabel(
                    activity.packageManager.getApplicationInfo(WalletPowerPolicy.WALLET_PACKAGE, 0))
            }.getOrNull() ?: return true // Google Wallet not installed
            val fragment = preferenceFragment(activity) ?: return false
            val find = fragment.javaClass.getMethod("findPreference", CharSequence::class.java)
            val none = find.invoke(fragment, "none") ?: return false
            val option = find.invoke(fragment, KEY) ?: run {
                val created = none.javaClass.getConstructor(Context::class.java).newInstance(activity)
                created.javaClass.getMethod("setKey", String::class.java).invoke(created, KEY)
                created.javaClass.getMethod("setTitle", CharSequence::class.java).invoke(created, label)
                // Same change handling as Xiaomi's own options: it saves the choice and unticks the rest.
                val setter = created.javaClass.methods.first { it.name == "setOnPreferenceChangeListener" && it.parameterCount == 1 }
                setter.invoke(created, listenerOf(none, setter.parameterTypes[0]))
                // Just above "None".
                val order = none.javaClass.getMethod("getOrder").invoke(none) as Int
                created.javaClass.getMethod("setOrder", Int::class.javaPrimitiveType).invoke(created, order)
                none.javaClass.getMethod("setOrder", Int::class.javaPrimitiveType).invoke(none, order + 1)
                addToGroup(parentOf(none) ?: return false, created)
                // The fragment unticks every option it knows; let it know this one.
                for (c in generateSequence(fragment.javaClass as Class<*>) { it.superclass }) for (f in c.declaredFields) {
                    if (f.type != java.util.ArrayList::class.java) continue
                    f.isAccessible = true
                    @Suppress("UNCHECKED_CAST") val list = f.get(fragment) as? java.util.ArrayList<Any?> ?: continue
                    if (list.contains(none) && !list.contains(created)) list.add(created)
                }
                created
            }
            // Xiaomi's screen does not know this choice and ticks another option; correct it.
            if (Settings.System.getString(activity.contentResolver, SETTING) == KEY) {
                for (c in generateSequence(fragment.javaClass as Class<*>) { it.superclass }) for (f in c.declaredFields) {
                    if (f.type != java.util.ArrayList::class.java) continue
                    f.isAccessible = true
                    val list = f.get(fragment) as? java.util.ArrayList<*> ?: continue
                    if (list.contains(none)) list.forEach { if (it != null) setChecked(it, it === option) }
                }
                setChecked(option, true)
            }
            HookLog.i("PowerKeyWalletOption", "Google Wallet option shown")
            true
        }.getOrElse { HookLog.w("PowerKeyWalletOption", "Google Wallet option not added", it); true }
    }

    /**
     * The fragment manager's methods are renamed and its fragment list is not public; look for the
     * added fragments in its fields (and one level into its fragment store).
     */
    private fun preferenceFragment(activity: Activity): Any? {
        val manager = activity.javaClass.getMethod("getSupportFragmentManager").invoke(activity) ?: return null
        fun pick(value: Any?): Any? {
            val items: Iterable<*> = when (value) {
                is Iterable<*> -> value
                is Map<*, *> -> value.values
                else -> return null
            }
            return items.firstOrNull { f -> f != null && f.javaClass.methods.any { it.name == "findPreference" } }
        }
        fun fields(target: Any) = generateSequence(target.javaClass as Class<*>) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.type.isPrimitive }
            .mapNotNull { f -> runCatching { f.isAccessible = true; f.get(target) }.getOrNull() }
        for (value in fields(manager)) {
            pick(value)?.let { return it }
            if (value.javaClass.name.startsWith("androidx.fragment")) for (inner in fields(value)) pick(inner)?.let { return it }
        }
        return null
    }

    private fun listenerOf(preference: Any, type: Class<*>): Any? {
        for (c in generateSequence(preference.javaClass as Class<*>) { it.superclass }) for (f in c.declaredFields) {
            if (f.type == type) { f.isAccessible = true; return f.get(preference) }
        }
        return null
    }

    private fun parentOf(preference: Any): Any? =
        runCatching { preference.javaClass.getMethod("getParent").invoke(preference) }.getOrNull()

    private fun setChecked(preference: Any, checked: Boolean) {
        runCatching { preference.javaClass.getMethod("setChecked", Boolean::class.javaPrimitiveType).invoke(preference, checked) }
    }

    /**
     * PreferenceGroup.addPreference is renamed in this app and shares its shape with the remove
     * methods. Removing a preference that is not in the group does nothing, so try each until the
     * new option belongs to the group.
     */
    private fun addToGroup(group: Any, preference: Any) {
        val prefType = generateSequence(preference.javaClass as Class<*>) { it.superclass }
            .first { it.name == "androidx.preference.Preference" }
        val groupType = generateSequence(group.javaClass as Class<*>) { it.superclass }
            .first { it.name == "androidx.preference.PreferenceGroup" }
        val parent = prefType.getMethod("getParent")
        for (m in groupType.declaredMethods) {
            if (java.lang.reflect.Modifier.isStatic(m.modifiers) || m.parameterTypes.size != 1 || m.parameterTypes[0] != prefType) continue
            if (m.returnType != Void.TYPE && m.returnType != java.lang.Boolean.TYPE) continue
            m.isAccessible = true
            m.invoke(group, preference)
            if (parent.invoke(preference) === group) return
        }
        throw NoSuchMethodException("PreferenceGroup.addPreference")
    }
}
