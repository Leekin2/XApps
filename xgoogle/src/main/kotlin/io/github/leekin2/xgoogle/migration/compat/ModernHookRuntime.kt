// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration.compat

import android.util.Log
import io.github.libxposed.api.XposedModule

/** Process-local access to the API 102 module instance. */
internal object ModernHookRuntime {
    @Volatile
    private var module: XposedModule? = null

    fun attach(module: XposedModule) {
        this.module = module
    }

    fun requireModule(): XposedModule = checkNotNull(module) {
        "Modern Xposed runtime is not attached"
    }

    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val activeModule = module
        if (activeModule != null) {
            runCatching { activeModule.log(priority, tag, message, throwable) }
                .onSuccess { return }
        }
        when (priority) {
            Log.DEBUG -> Log.d(tag, message, throwable)
            Log.WARN -> Log.w(tag, message, throwable)
            Log.ERROR -> Log.e(tag, message, throwable)
            else -> Log.i(tag, message, throwable)
        }
    }
}

