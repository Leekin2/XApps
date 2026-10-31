// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.util.Log
import io.github.leekin2.xgoogle.migration.compat.ModernHookRuntime

/** Routes hook diagnostics directly to the modern Xposed framework. */
internal object HookLog {
    private const val LOGCAT_TAG = "AssistantBridge-LSP"

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        write(Log.WARN, tag, message, throwable)
    }

    private fun write(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val scopedTag = tag
            .removePrefix("AssistantBridge-")
            .removePrefix("ONextBox ")
            .trim()
            .ifBlank { "Hook" }
        val line = "[$scopedTag] $message"
        ModernHookRuntime.log(priority, LOGCAT_TAG, line, throwable)
    }
}

