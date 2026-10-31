// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration.compat

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.concurrent.ConcurrentHashMap

/** Owns modern hook handles so one-shot and feature-scoped hooks can be removed explicitly. */
internal object ModernHookRegistry {
    private val handles = ConcurrentHashMap<String, XposedInterface.HookHandle>()

    fun installInterceptor(
        key: String,
        executable: Executable,
        callback: XposedInterface.Hooker,
    ): XposedInterface.HookHandle {
        handles[key]?.let { return it }
        executable.isAccessible = true
        val installed = ModernHookRuntime.requireModule().hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(callback)
        return handles.putIfAbsent(key, installed)?.also { installed.unhook() } ?: installed
    }

    fun installCompat(
        key: String,
        executable: Executable,
        callback: ModernMethodHook,
    ): XposedInterface.HookHandle {
        handles[key]?.let { return it }
        val installed = ModernHookBridge.hookMethod(executable, callback)
        return handles.putIfAbsent(key, installed)?.also { installed.unhook() } ?: installed
    }

    fun unhook(key: String): Boolean {
        val handle = handles.remove(key) ?: return false
        handle.unhook()
        return true
    }

    fun unhookPrefix(prefix: String): Int {
        val keys = handles.keys.filter { it.startsWith(prefix) }
        keys.forEach(::unhook)
        return keys.size
    }
}

