package io.github.leekin2.xgoogle.migration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import io.github.leekin2.xgoogle.migration.AssistantRuntime.installedHooks
import io.github.leekin2.xgoogle.migration.AssistantRuntime.log

/**
 * The lock screen Assistant shortcut should open Gemini like a long press of the power key does.
 * Only the system can start that assistant session; XGoogle's shortcut screen asks SystemUI for
 * it with this broadcast, which only apps signed like XGoogle may send. The Circle to Search tile
 * asks the same way: SystemUI closes the control center first, so the search sees the app below.
 */
internal object AssistantShortcutReceiver {
    const val ACTION = "io.github.leekin2.xgoogle.SHOW_ASSISTANT"
    const val CIRCLE_ACTION = "io.github.leekin2.xgoogle.CIRCLE_TO_SEARCH"
    const val PERMISSION = "io.github.leekin2.xgoogle.permission.SHOW_ASSISTANT"
    /** Long enough for the control center to finish closing before the screen is captured. */
    private const val CIRCLE_DELAY_MS = 400L
    private const val SHOW_SOURCE_ASSIST_GESTURE = 4

    fun install() {
        val key = "assistant-shortcut-receiver"
        if (!installedHooks.add(key)) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val context = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Context
                context.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        resultCode = if (intent.action == CIRCLE_ACTION) circleToSearch(c) else if (showAssistant()) 1 else 0
                    }
                }, IntentFilter(ACTION).apply { addAction(CIRCLE_ACTION) }, PERMISSION, null, Context.RECEIVER_EXPORTED)
            }.onFailure { installedHooks.remove(key); log("Assistant shortcut receiver unavailable", it) }
        }
    }

    private fun circleToSearch(context: Context): Int {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return 0
        runCatching {
            val statusBar = context.getSystemService("statusbar")
            statusBar.javaClass.getMethod("collapsePanels").invoke(statusBar)
        }.onFailure { log("Control center not collapsed", it) }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ContextualSearchHooks.triggerCircleToSearchC17()
        }, CIRCLE_DELAY_MS)
        return 1
    }

    private fun showAssistant(): Boolean = runCatching {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "voiceinteraction") as android.os.IBinder
        val service = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            .getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
        val show = service.javaClass.methods.first { it.name == "showSessionForActiveService" && it.parameterTypes.size == 5 }
        val args = Bundle().apply { putInt("invocation_type", 1) }
        show.invoke(service, args, SHOW_SOURCE_ASSIST_GESTURE, null, null, null) as? Boolean ?: true
    }.getOrElse { log("Assistant session not shown", it); false }
}
