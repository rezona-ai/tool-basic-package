/**
 * [INPUT]: 依赖 SharedPreferences + org.json（Android 自带），依赖 Renamer 的 RenameOp
 * [OUTPUT]: 对外提供 Batch、HistoryStore（最近的改名批次：增、取、标记已撤销，变化以 StateFlow 推给页面）
 * [POS]: data 的改名记录：每批存文件夹路径、规则名、成功改名的「旧名 → 新名」，用于结果页展示和撤销。
 *        只存在应用私有存储，不上传；最多保留 [HistoryStore.MAX_BATCHES] 批
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Batch(
    val id: Long,
    val folder: String,
    val rule: RuleType,
    val ops: List<RenameOp>,
    val failed: Int,
    val createdAt: Long,
    val undone: Boolean = false,
)

object HistoryStore {
    const val MAX_BATCHES = 20

    private const val PREFS = "rename_history"
    private const val KEY = "batches"

    private lateinit var appContext: Context
    private val _batches = MutableStateFlow<List<Batch>>(emptyList())

    /** 最新的在前 */
    val batches: StateFlow<List<Batch>> = _batches.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        _batches.value = load()
    }

    fun get(id: Long): Batch? = _batches.value.firstOrNull { it.id == id }

    fun add(folder: String, rule: RuleType, ops: List<RenameOp>, failed: Int): Long {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (_batches.value.maxOfOrNull { it.id } ?: 0L) + 1)
        save((listOf(Batch(id, folder, rule, ops, failed, now)) + _batches.value).take(MAX_BATCHES))
        return id
    }

    fun markUndone(id: Long) = save(_batches.value.map { if (it.id == id) it.copy(undone = true) else it })

    private fun save(list: List<Batch>) {
        _batches.value = list
        val json = JSONArray()
        list.forEach { b ->
            val ops = JSONArray()
            b.ops.forEach { ops.put(JSONObject().put("from", it.oldName).put("to", it.newName)) }
            json.put(
                JSONObject().put("id", b.id).put("folder", b.folder).put("rule", b.rule.name).put("ops", ops)
                    .put("failed", b.failed).put("at", b.createdAt).put("undone", b.undone),
            )
        }
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, json.toString()) }
    }

    private fun load(): List<Batch> = runCatching {
        val json = JSONArray(appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            val ops = o.getJSONArray("ops")
            Batch(
                id = o.getLong("id"),
                folder = o.getString("folder"),
                rule = RuleType.valueOf(o.getString("rule")),
                ops = List(ops.length()) { j -> ops.getJSONObject(j).let { RenameOp(it.getString("from"), it.getString("to")) } },
                failed = o.optInt("failed"),
                createdAt = o.getLong("at"),
                undone = o.optBoolean("undone"),
            )
        }
    }.getOrDefault(emptyList())
}
