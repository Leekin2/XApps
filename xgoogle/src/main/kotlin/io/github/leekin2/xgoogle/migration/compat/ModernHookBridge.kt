// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration.compat

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/**
 * ONextBox's independently designed adapter from its before/after callbacks to
 * the API 102 interceptor chain.
 */
internal object ModernHookBridge {
    private val installingHandles = ThreadLocal<MutableList<XposedInterface.HookHandle>>()

    /** Roll back an incomplete group; bookkeeping runs only during installation. */
    fun <T> atomic(block: () -> T): T {
        val parent = installingHandles.get()
        val handles = mutableListOf<XposedInterface.HookHandle>()
        installingHandles.set(handles)
        try {
            val result = block()
            parent?.addAll(handles)
            return result
        } catch (error: Throwable) {
            handles.asReversed().forEach { handle ->
                runCatching { handle.unhook() }.exceptionOrNull()?.let { failure ->
                    if (failure !== error) error.addSuppressed(failure)
                }
            }
            throw error
        } finally {
            if (parent == null) installingHandles.remove() else installingHandles.set(parent)
        }
    }

    fun hookMethod(
        executable: Executable,
        callback: ModernMethodHook,
    ): XposedInterface.HookHandle {
        executable.isAccessible = true
        val module = ModernHookRuntime.requireModule()
        return module
            .hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain -> intercept(chain, callback) }
            .also { installingHandles.get()?.add(it) }
    }

    fun hookAllMethods(
        hookClass: Class<*>,
        methodName: String,
        callback: ModernMethodHook,
    ): Set<XposedInterface.HookHandle> = atomic {
        hookClass.declaredMethods
            .asSequence()
            .filter { it.name == methodName }
            .map { hookMethod(it, callback) }
            .toSet()
    }

    fun hookAllConstructors(
        hookClass: Class<*>,
        callback: ModernMethodHook,
    ): Set<XposedInterface.HookHandle> = atomic {
        hookClass.declaredConstructors
            .asSequence()
            .map { hookMethod(it, callback) }
            .toSet()
    }

    private fun intercept(
        chain: XposedInterface.Chain,
        callback: ModernMethodHook,
    ): Any? {
        val param = ModernMethodHook.MethodHookParam(
            method = chain.executable,
            thisObject = chain.thisObject,
            args = chain.args.toTypedArray(),
        )

        val beforeSucceeded = runCallback("before", chain.executable) {
            callback.beforeHookedMethod(param)
        }
        if (!beforeSucceeded) param.resetAfterBeforeFailure()

        var result: Any? = null
        var throwable: Throwable? = null
        if (param.returnEarly) {
            result = param.result
            throwable = param.throwable
        } else {
            try {
                result = chain.proceed(param.args)
            } catch (error: Throwable) {
                throwable = error
            }
        }

        param.beginAfter(result, throwable)
        val originalOutcome = param.snapshotOutcome()
        val afterSucceeded = runCallback("after", chain.executable) {
            callback.afterHookedMethod(param)
        }
        if (!afterSucceeded) param.restoreOutcome(originalOutcome)
        return param.resultOrThrow()
    }

    private inline fun runCallback(
        phase: String,
        executable: Executable,
        block: () -> Unit,
    ): Boolean {
        return runCatching(block).fold(
            onSuccess = { true },
            onFailure = { error ->
                ModernHookRuntime.log(
                    priority = android.util.Log.ERROR,
                    tag = "AssistantBridge-LSP",
                    message = "$phase callback failed: ${executable.declaringClass.name}#${executable.name}",
                    throwable = error,
                )
                false
            },
        )
    }
}

