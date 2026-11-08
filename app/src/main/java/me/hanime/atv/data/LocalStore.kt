package me.hanime.atv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("hanime_app_store", Context.MODE_PRIVATE)

    var user: UserInfo?
        get() {
            val raw = prefs.getString(KEY_USER, null) ?: return null
            return runCatching {
                val o = JSONObject(raw)
                UserInfo(o.getString("id"), o.getString("name"), o.optString("avatar").ifBlank { null })
            }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_USER).apply()
            } else {
                prefs.edit().putString(
                    KEY_USER,
                    JSONObject()
                        .put("id", value.id)
                        .put("name", value.name)
                        .put("avatar", value.avatar ?: "")
                        .toString(),
                ).apply()
            }
        }

    var preferredQuality: Int
        get() = prefs.getInt(KEY_QUALITY, 0)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    /** 界面缩放系数（在电视密度归一化之后额外乘上去） */
    var uiScale: Float
        get() = prefs.getFloat(KEY_UI_SCALE, 1f).let { if (it <= 0f) 1f else it }
        set(value) = prefs.edit().putFloat(KEY_UI_SCALE, value).apply()

    /** 上次检查更新的时间，避免每次启动都打 GitHub API */
    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_CHECK, value).apply()

    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED_VERSION, null)
        set(value) = prefs.edit().putString(KEY_SKIPPED_VERSION, value).apply()

    var searchHistory: List<String>
        get() = prefs.getString(KEY_SEARCH, null)?.let { raw ->
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }
            }.getOrNull()
        }.orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SEARCH, JSONArray(value.take(12)).toString()).apply()
        }

    fun addSearchHistory(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        searchHistory = (listOf(kw) + searchHistory.filter { it != kw }).take(12)
    }

    fun history(): List<HistoryItem> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                HistoryItem(
                    code = o.optString("code"),
                    title = o.optString("title"),
                    cover = o.optString("cover"),
                    positionMs = o.optLong("position"),
                    durationMs = o.optLong("duration"),
                    updatedAt = o.optLong("updated"),
                )
            }.filter { it.code.isNotBlank() }
        }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
    }

    fun upsertHistory(item: HistoryItem) {
        val list = history().toMutableList()
        list.removeAll { it.code == item.code }
        list.add(0, item)
        val trimmed = list.take(60)
        val arr = JSONArray()
        trimmed.forEach { h ->
            arr.put(
                JSONObject()
                    .put("code", h.code)
                    .put("title", h.title)
                    .put("cover", h.cover)
                    .put("position", h.positionMs)
                    .put("duration", h.durationMs)
                    .put("updated", h.updatedAt),
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun historyFor(code: String): HistoryItem? = history().firstOrNull { it.code == code }

    fun removeHistory(code: String) {
        val list = history().filterNot { it.code == code }
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(
                JSONObject()
                    .put("code", h.code)
                    .put("title", h.title)
                    .put("cover", h.cover)
                    .put("position", h.positionMs)
                    .put("duration", h.durationMs)
                    .put("updated", h.updatedAt),
            )
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    companion object {
        private const val KEY_USER = "user"
        private const val KEY_QUALITY = "quality"
        private const val KEY_SEARCH = "search_history"
        private const val KEY_HISTORY = "history"
        private const val KEY_UI_SCALE = "ui_scale"
        private const val KEY_UPDATE_CHECK = "update_check_at"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
    }
}
