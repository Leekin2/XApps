// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.leekin2.xgoogle.migration

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Field

@SuppressLint("StaticFieldLeak")
internal object AssistantRuntime {
    private const val TAG = "AssistantBridge-Assistant"
    const val SYSTEM_PACKAGE = "system"
    const val ANDROID_PACKAGE = "android"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val LAUNCHER_PACKAGE = "com.android.launcher"
    const val SETTINGS_PACKAGE = "com.android.settings"
    const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    const val GOOGLE_ASSISTANT_APP_FEATURE =
        "com.android.systemui.google_assistant_supported"
    const val CONTEXTUAL_SEARCH_SYSTEM_FEATURE =
        "com.google.android.feature.CONTEXTUAL_SEARCH"
    const val BREENO_SPEECH_FEATURE = "oplus.software.speech_assist_for_breeno"
    const val SECURE_ASSISTANT = "assistant"
    const val SECURE_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
    const val SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP =
        "disable_google_asssist_power_wakeup"
    const val SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT =
        "quick_turn_on_voice_assistant"
    const val SECURE_ASSISTANT_TUTORIAL_STARTED = "op_assist_started"
    private const val SECURE_GESTURE_CIRCLE_TO_SEARCH_ENABLED =
        "circle_to_search_enable_navi"
    private const val SECURE_GESTURE_CORNER_ASSISTANT_ENABLED =
        "circle_to_search_corner_assist_enable_navi"
    const val ASSISTANT_ROLE = "android.app.role.ASSISTANT"
    const val VOICE_INTERACTION_SERVICE_ACTION =
        "android.service.voice.VoiceInteractionService"
    const val BIND_VOICE_INTERACTION_PERMISSION =
        "android.permission.BIND_VOICE_INTERACTION"
    const val INVOCATION_TYPE_GESTURE_CORNER_ASSISTANT = 1
    const val INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS = 6
    const val INVOCATION_TYPE_CIRCLE_TO_SEARCH = 8
    const val INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE = 1000
    private val contextFieldCache = ConcurrentHashMap<Class<*>, Field>()
    fun findContext(source: Any?): Context? {
        val direct = source as? Context
        if (direct != null) return direct
        if (source != null) {
            contextFieldCache[source.javaClass]?.let { field ->
                (runCatching { field.get(source) }.getOrNull() as? Context)?.let { return it }
            }
        }
        return findFieldValue(source, Context::class.java) ?: findSystemContext()
    }
    private fun <T> findFieldValue(source: Any?, type: Class<T>, depth: Int = 0): T? {
        if (source == null || depth > 2) return null
        val cls = source.javaClass
        var current: Class<*>? = cls
        while (current != null) {
            for (field in current.declaredFields) {
                val value = runCatching {
                    field.isAccessible = true
                    field.get(source)
                }.getOrNull()
                if (type.isInstance(value)) {
                    if (depth == 0 && type == Context::class.java) contextFieldCache[cls] = field
                    return type.cast(value)
                }
                if (field.name == "this$0") {
                    findFieldValue(value, type, depth + 1)?.let { return it }
                }
            }
            current = current.superclass
        }
        return null
    }
    fun findSystemContext(): Context? {
        systemContext?.let { return it }
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val thread = activityThreadClass
                .getMethod("currentActivityThread")
                .invoke(null) ?: return@runCatching null
            activityThreadClass
                .getMethod("getSystemContext")
                .invoke(thread) as? Context
        }.onSuccess { context ->
            if (context != null) systemContext = context
        }.getOrNull()
    }
    fun resolveDefaultAssistant(context: Context): ComponentName? {
        val resolver = context.contentResolver
        val assistComponent = Settings.Secure.getString(resolver, SECURE_ASSISTANT)
            ?.takeIf(String::isNotBlank)
            ?.let(ComponentName::unflattenFromString)
        val voiceInteractionComponent = Settings.Secure.getString(
            resolver,
            SECURE_VOICE_INTERACTION_SERVICE,
        )
            ?.takeIf(String::isNotBlank)
            ?.let(ComponentName::unflattenFromString)
        return assistComponent ?: voiceInteractionComponent
    }
    fun isGestureCircleToSearchEnabled(context: Context?): Boolean {
        context ?: return false
        return Settings.Secure.getInt(
            context.contentResolver,
            SECURE_GESTURE_CIRCLE_TO_SEARCH_ENABLED,
            1,
        ) == 1
    }
    fun isGestureCornerAssistantEnabled(context: Context?): Boolean {
        context ?: return false
        return Settings.Secure.getInt(
            context.contentResolver,
            SECURE_GESTURE_CORNER_ASSISTANT_ENABLED,
            1,
        ) == 1
    }
    fun resolveCurrentUserId(): Int {
        return runCatching {
            ActivityManager::class.java.getDeclaredMethod("getCurrentUser").apply {
                isAccessible = true
            }.invoke(null) as Int
        }.getOrDefault(Process.myUid() / 100_000)
    }
    fun extractUserId(value: Any?): Int? {
        if (value == null) return null
        listOf("getUserIdentifier", "getIdentifier").forEach { methodName ->
            val result = runCatching {
                value.javaClass.getMethod(methodName).invoke(value) as? Int
            }.getOrNull()
            if (result != null) return result
        }
        return runCatching {
            val userHandle = value.javaClass.getMethod("getUserHandle").invoke(value)
                ?: return@runCatching null
            userHandle.javaClass.getMethod("getIdentifier").invoke(userHandle) as? Int
        }.getOrNull()
    }
    fun log(message: String, throwable: Throwable? = null) {
        HookLog.i(TAG, message, throwable)
    }
    val installedHooks = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    var systemContext: Context? = null
}
