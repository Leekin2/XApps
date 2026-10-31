// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.content.Context
import android.provider.Settings
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP
import io.github.leekin2.xgoogle.migration.AssistantRuntime.SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT

internal object AssistantPowerHooks {
    /** The power-key assistant choice: 0 = on, 1 = off, unset follows the quick voice assistant setting. */
    fun isPowerAssistantEnabled(context: Context?): Boolean {
        context ?: return false
        val resolver = context.contentResolver
        return when (Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )) {
            1 -> false
            0 -> true
            else -> Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != 0
        }
    }
}
