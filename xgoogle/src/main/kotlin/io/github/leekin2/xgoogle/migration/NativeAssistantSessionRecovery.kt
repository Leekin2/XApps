// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import io.github.leekin2.xgoogle.migration.compat.ModernReflect
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

/** Watches the native show callback without replacing a successful assistant invocation. */
internal object NativeAssistantSessionRecovery {
    private const val TAG = "AssistantBridge-Assistant"
    private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val CALLBACK_DESCRIPTOR =
        "com.android.internal.app.IVoiceInteractionSessionShowCallback"
    private const val SHOW_TIMEOUT_MS = 5_000L
    private const val BIND_TIMEOUT_MS = 2_000L
    private const val BIND_POLL_MS = 100L
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val replaying = ThreadLocal<Boolean>()
    private var pending: ShowAttempt? = null
    private data class CallbackTransactions(val shown: Int, val failed: Int)
    private val callbackBindings = ConcurrentHashMap<Class<*>, CallbackTransactions>()

    fun prepare(stub: Any, method: Method, args: Array<Any?>) {
        if (replaying.get() == true) return
        cancel()
        if (args.size != 5 || args[3] != null) return
        val bundle = args[0] as? Bundle ?: return
        val invocationType = bundle.getInt("invocation_type", 0)
        if (!enabled(invocationType)) return
        val component = currentComponent(stub) ?: return
        if (component.packageName != GOOGLE_PACKAGE) return
        val userId = ModernReflect.getObjectField(stub, "mCurUser") as? Int ?: return
        val savedArgs = args.copyOf().also { it[0] = Bundle(bundle) }
        val attempt = ShowAttempt(stub, method, savedArgs, component, userId, invocationType, 0)
        args[3] = attempt.callback
        handler.post { arm(attempt) }
    }

    fun finishRequest(callback: Any?, accepted: Boolean, error: Throwable?) {
        handler.post {
            val attempt = pending ?: return@post
            if (attempt.callback !== callback) return@post
            if (error != null) {
                attempt.cancel()
                pending = null
            } else if (!accepted) {
                attempt.complete(shown = false)
            }
        }
    }

    fun prepareCornerSession(impl: Any, bundle: Bundle?) {
        if (bundle?.getInt("invocation_type", 0) != 1 || !enabled(1)) return
        val component = ModernReflect.getObjectField(impl, "mComponent") as? ComponentName ?: return
        if (component.packageName != GOOGLE_PACKAGE) return
        val stub = ModernReflect.getObjectField(impl, "mServiceStub") ?: return
        synchronized(stub) {
            val session = ModernReflect.getObjectField(impl, "mActiveSession") ?: return
            val token = ModernReflect.getObjectField(session, "mToken") as? IBinder ?: return
            // A new corner invocation must not reuse Google's unresponsive cached session.
            // Keep the selected voice service bound and replace only its session connection.
            ModernReflect.callMethod(impl, "finishLocked", token, false)
        }
    }

    fun cancel() {
        handler.post {
            pending?.cancel()
            pending = null
        }
    }

    fun cancelSession(stub: Any, token: Any?) {
        if (token == null) return
        val matches = runCatching {
            synchronized(stub) {
                val impl = ModernReflect.getObjectField(stub, "mImpl") ?: return@synchronized false
                val session = ModernReflect.getObjectField(impl, "mActiveSession")
                    ?: return@synchronized false
                ModernReflect.getObjectField(session, "mToken") == token
            }
        }.getOrDefault(false)
        if (matches) cancel()
    }

    private fun enabled(invocationType: Int): Boolean = when (invocationType) {
        6 -> true // Native power-button integration follows the system assistant picker.
        1 -> LspConfig.isAssistantNativeCircleEnabledXposed()
        else -> false
    }

    private fun currentComponent(stub: Any): ComponentName? = runCatching {
        val impl = ModernReflect.getObjectField(stub, "mImpl") ?: return@runCatching null
        ModernReflect.getObjectField(impl, "mComponent") as? ComponentName
    }.getOrNull()

    private fun arm(attempt: ShowAttempt) {
        pending?.cancel()
        pending = attempt
        if (!attempt.completed.get()) handler.postDelayed(attempt.timeout, SHOW_TIMEOUT_MS)
    }

    private class ShowAttempt(
        val stub: Any,
        val method: Method,
        val args: Array<Any?>,
        val component: ComponentName,
        val userId: Int,
        val invocationType: Int,
        val retry: Int,
    ) {
        val completed = AtomicBoolean(false)
        val timeout = Runnable { complete(shown = false) }
        val callback: Any = createCallback(method.parameterTypes[3]) { complete(it) }

        fun cancel() {
            completed.set(true)
            handler.removeCallbacks(timeout)
        }

        fun complete(shown: Boolean) {
            if (!completed.compareAndSet(false, true)) return
            handler.post {
                handler.removeCallbacks(timeout)
                if (pending !== this) return@post
                if (shown) {
                    pending = null
                    if (retry > 0) HookLog.i(TAG, "Native Google assistant session recovered")
                } else if (retry == 0) {
                    recover()
                } else {
                    pending = null
                    HookLog.w(TAG, "Google assistant did not show after one binding recovery")
                }
            }
        }

        private fun stillSelected(): Boolean = runCatching {
            synchronized(stub) {
                val impl = ModernReflect.getObjectField(stub, "mImpl") ?: return@synchronized false
                val context = ModernReflect.getObjectField(impl, "mContext") as? Context
                    ?: return@synchronized false
                val selected = ModernReflect.callStaticMethod(
                    Settings.Secure::class.java,
                    "getStringForUser",
                    context.contentResolver,
                    "voice_interaction_service",
                    userId,
                ) as? String
                enabled(invocationType) &&
                    ModernReflect.getObjectField(stub, "mCurUser") == userId &&
                    currentComponent(stub) == component &&
                    selected?.let(ComponentName::unflattenFromString) == component &&
                    ModernReflect.getObjectField(stub, "mTemporarilyDisabled") != true
            }
        }.getOrDefault(false)

        private fun recover() {
            if (!stillSelected()) {
                pending = null
                return
            }
            val identity = Binder.clearCallingIdentity()
            try {
                // Recreate the same selected implementation; never change the Assistant Role
                // or force-stop the Google app and its unrelated services.
                synchronized(stub) {
                    ModernReflect.callMethod(stub, "switchImplementationIfNeeded", true)
                }
                HookLog.i(TAG, "Native Google show callback timed out/failed; rebuilding its binding")
                waitForBinding(SystemClock.uptimeMillis() + BIND_TIMEOUT_MS)
            } catch (error: Throwable) {
                pending = null
                HookLog.w(TAG, "Failed to recover the native Google assistant binding", error)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        private fun waitForBinding(deadline: Long) {
            if (pending !== this) return
            if (!stillSelected()) {
                pending = null
                return
            }
            val connected = runCatching {
                synchronized(stub) {
                    val impl = ModernReflect.getObjectField(stub, "mImpl")
                        ?: return@synchronized false
                    ModernReflect.getObjectField(impl, "mService") != null
                }
            }.getOrDefault(false)
            if (!connected) {
                if (SystemClock.uptimeMillis() < deadline) {
                    handler.postDelayed({ waitForBinding(deadline) }, BIND_POLL_MS)
                } else {
                    pending = null
                    HookLog.w(TAG, "Native Google assistant did not reconnect before the deadline")
                }
                return
            }
            val retryArgs = args.copyOf().also {
                it[0] = Bundle(args[0] as Bundle).apply {
                    putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                }
            }
            val next = ShowAttempt(stub, method, retryArgs, component, userId, invocationType, 1)
            retryArgs[3] = next.callback
            arm(next)
            val identity = Binder.clearCallingIdentity()
            replaying.set(true)
            try {
                method.isAccessible = true
                if (method.invoke(stub, *retryArgs) == false) next.complete(shown = false)
            } catch (error: Throwable) {
                next.cancel()
                pending = null
                HookLog.w(TAG, "Failed to retry the native assistant session", error)
            } finally {
                replaying.remove()
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    private fun createCallback(callbackClass: Class<*>, onComplete: (Boolean) -> Unit): Any {
        val binding = callbackBindings.computeIfAbsent(callbackClass) {
            val stubClass = Class.forName("$CALLBACK_DESCRIPTOR\$Stub", false, it.classLoader)
            fun transaction(name: String): Int = stubClass.getDeclaredField("TRANSACTION_$name")
                .apply { isAccessible = true }.getInt(null)
            CallbackTransactions(transaction("onShown"), transaction("onFailed"))
        }
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(CALLBACK_DESCRIPTOR)
                    return true
                }
                if (code == binding.shown || code == binding.failed) {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    onComplete(code == binding.shown)
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        return Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "onShown" -> onComplete(true)
                "onFailed" -> onComplete(false)
                "toString" -> "AssistantBridge-NativeAssistantShowCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }.also { binder.attachInterface(it as IInterface, CALLBACK_DESCRIPTOR) }
    }
}

