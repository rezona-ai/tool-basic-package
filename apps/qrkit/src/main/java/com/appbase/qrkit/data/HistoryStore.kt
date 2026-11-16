/**
 * [INPUT]: 依赖 QrContent，依赖 SharedPreferences + org.json（Android 自带）
 * [OUTPUT]: 对外提供 HistoryEntry、HistoryStore（最近生成 / 识别的二维码：增、删、清空、按 id 取，变化以 StateFlow 推给页面）
 * [POS]: data 的本地历史：只存二维码文本和时间，保存在应用私有存储，不上传；最多保留 [HistoryStore.MAX_ENTRIES] 条
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class HistoryKind { CREATED, SCANNED }

data class HistoryEntry(val id: Long, val kind: HistoryKind, val raw: String, val createdAt: Long) {
    val content: QrContent get() = QrContent.parse(raw)
}

object HistoryStore {
    const val MAX_ENTRIES = 50

    private const val PREFS = "qr_history"
    private const val KEY = "entries"

    private lateinit var appContext: Context
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    /** 最新的在前 */
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        _entries.value = load()
    }

    fun get(id: Long): HistoryEntry? = _entries.value.firstOrNull { it.id == id }

    /** 记一条，返回它的 id；同样的内容再次出现时挪到最前面 */
    fun add(kind: HistoryKind, raw: String): Long {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (_entries.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val entry = HistoryEntry(id, kind, raw, now)
        save((listOf(entry) + _entries.value.filterNot { it.raw == raw && it.kind == kind }).take(MAX_ENTRIES))
        return id
    }

    fun remove(id: Long) = save(_entries.value.filterNot { it.id == id })

    fun clear() = save(emptyList())

    private fun save(list: List<HistoryEntry>) {
        _entries.value = list
        val json = JSONArray()
        list.forEach { e ->
            json.put(JSONObject().put("id", e.id).put("kind", e.kind.name).put("raw", e.raw).put("at", e.createdAt))
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, json.toString()) }
    }

    private fun load(): List<HistoryEntry> = runCatching {
        val json = JSONArray(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            HistoryEntry(o.getLong("id"), HistoryKind.valueOf(o.getString("kind")), o.getString("raw"), o.getLong("at"))
        }
    }.getOrDefault(emptyList())
}
