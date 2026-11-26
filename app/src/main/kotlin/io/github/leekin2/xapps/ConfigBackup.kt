package io.github.leekin2.xapps

import io.github.libxposed.service.XposedService
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports and restores every XApps remote preference group (hub switches, XGoogle, XSecurity + XAod,
 * and XNotify) as one lossless, human-readable JSON file.
 */
internal object ConfigBackup {
    private const val FORMAT = "xapps-backup"
    private const val VERSION = 1
    private val GROUPS = listOf(Parts.GROUP, "xgoogle", "xsecurity", "settings")

    data class Result(val groups: Int, val entries: Int)

    fun defaultFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "xapps-backup-$stamp.json"
    }

    fun exportJson(service: XposedService): Pair<String, Result> {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))

        val groupsObj = JSONObject()
        var groupCount = 0
        var entryCount = 0
        for (name in GROUPS) {
            val prefs = runCatching { service.getRemotePreferences(name) }.getOrNull() ?: continue
            val all = runCatching { prefs.all }.getOrNull().orEmpty()
            val groupJson = JSONObject()
            for ((key, value) in all) {
                val encoded = encodeValue(value) ?: continue
                groupJson.put(key, encoded)
                entryCount++
            }
            groupsObj.put(name, groupJson)
            groupCount++
        }
        root.put("groups", groupsObj)
        return root.toString(2) to Result(groupCount, entryCount)
    }

    fun importJson(service: XposedService, raw: String): Result {
        val root = JSONObject(raw)
        require(root.optString("format") == FORMAT) { "Invalid backup file" }
        val groupsObj = root.optJSONObject("groups") ?: throw IllegalArgumentException("Missing groups")

        var groupCount = 0
        var entryCount = 0
        for (name in GROUPS) {
            val groupJson = groupsObj.optJSONObject(name) ?: continue
            val prefs = runCatching { service.getRemotePreferences(name) }.getOrNull() ?: continue
            val editor = prefs.edit().clear()
            val keys = groupJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = groupJson.optJSONObject(key) ?: continue
                if (applyValue(editor, key, item)) entryCount++
            }
            editor.commit()
            groupCount++
        }
        return Result(groupCount, entryCount)
    }

    private fun encodeValue(value: Any?): JSONObject? {
        val obj = JSONObject()
        when (value) {
            is Boolean -> { obj.put("t", "b"); obj.put("v", value) }
            is Int -> { obj.put("t", "i"); obj.put("v", value) }
            is Long -> { obj.put("t", "l"); obj.put("v", value) }
            is Float -> { obj.put("t", "f"); obj.put("v", value.toDouble()) }
            is String -> { obj.put("t", "s"); obj.put("v", value) }
            is Set<*> -> {
                val arr = JSONArray()
                value.filterIsInstance<String>().forEach(arr::put)
                obj.put("t", "ss")
                obj.put("v", arr)
            }
            else -> return null
        }
        return obj
    }

    private fun applyValue(editor: android.content.SharedPreferences.Editor, key: String, item: JSONObject): Boolean {
        return when (item.optString("t")) {
            "b" -> { editor.putBoolean(key, item.getBoolean("v")); true }
            "i" -> { editor.putInt(key, item.getInt("v")); true }
            "l" -> { editor.putLong(key, item.getLong("v")); true }
            "f" -> { editor.putFloat(key, item.getDouble("v").toFloat()); true }
            "s" -> { editor.putString(key, item.getString("v")); true }
            "ss" -> {
                val arr = item.optJSONArray("v") ?: JSONArray()
                val set = LinkedHashSet<String>(arr.length())
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                editor.putStringSet(key, set)
                true
            }
            else -> false
        }
    }
}
