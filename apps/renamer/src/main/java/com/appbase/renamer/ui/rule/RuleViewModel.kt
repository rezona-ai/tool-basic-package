/**
 * [INPUT]: 依赖 data 的 RenamePlanner / Renamer / HistoryStore，依赖 :core:storage 的 FileStore（CoreServices.files.listDir）
 * [OUTPUT]: 对外提供 RuleViewModel、FileEntry
 * [POS]: ui/rule 的状态与业务：当前文件夹、文件列表、勾选、规则参数、排序、预览、执行进度。
 *        执行放在 viewModelScope，离开页面也不会中断；完成后记入 HistoryStore 并回调批次 id
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.rule

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.core.CoreServices
import com.appbase.core.storage.files
import com.appbase.renamer.data.HistoryStore
import com.appbase.renamer.data.PlanItem
import com.appbase.renamer.data.RenameOp
import com.appbase.renamer.data.RenamePlanner
import com.appbase.renamer.data.RenameRule
import com.appbase.renamer.data.Renamer
import com.appbase.renamer.data.RuleType
import com.appbase.renamer.data.SortOrder
import com.appbase.renamer.data.SourceFile
import kotlinx.coroutines.launch

/** 预览列表的一行：文件、是否勾选、勾选时的新名字和问题 */
data class FileEntry(val file: SourceFile, val selected: Boolean, val plan: PlanItem?)

class RuleViewModel(val type: RuleType) : ViewModel() {

    var folder by mutableStateOf<String?>(null)
        private set
    var files by mutableStateOf<List<SourceFile>?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var rule by mutableStateOf(RenameRule(type))
    var sort by mutableStateOf(if (type == RuleType.DATE) SortOrder.MODIFIED else SortOrder.NAME)

    /** 执行中：已完成步数 / 总步数 */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    val entries: List<FileEntry> by derivedStateOf {
        val all = files ?: return@derivedStateOf emptyList()
        val ordered = RenamePlanner.sorted(all, sort)
        val chosen = ordered.filter { it.name in selected }
        val others = all.filter { it.name !in selected }.map { it.name }
        val plan = RenamePlanner.plan(chosen, rule, others).associateBy { it.oldName }
        ordered.map { FileEntry(it, it.name in selected, plan[it.name]) }
    }

    /** 选中且名字会变的 */
    val changes: List<PlanItem> by derivedStateOf { entries.mapNotNull { it.plan }.filter { it.changed } }
    val problems: Int by derivedStateOf { changes.count { it.problem != null } }

    fun chooseFolder(path: String) {
        folder = path
        files = null
        loadError = null
        viewModelScope.launch {
            val items = CoreServices.files.listDir(path)
            if (items == null) {
                loadError = "Can't read this folder. Choose it again or pick another one."
                files = emptyList()
            } else {
                files = items.filter { !it.isDir }.map { SourceFile(it.name, it.modified) }
                selected = files!!.map { it.name }.toSet()
            }
        }
    }

    fun toggle(name: String) {
        selected = if (name in selected) selected - name else selected + name
    }

    fun selectAll(all: Boolean) {
        selected = if (all) files.orEmpty().map { it.name }.toSet() else emptySet()
    }

    fun run(onDone: (batchId: Long) -> Unit) {
        val dir = folder ?: return
        val ops = changes.filter { it.problem == null }.map { RenameOp(it.oldName, it.newName) }
        if (ops.isEmpty() || progress != null) return
        progress = 0 to ops.size * 2
        viewModelScope.launch {
            val outcome = Renamer.run(dir, ops) { done, total -> progress = done to total }
            val id = HistoryStore.add(dir, type, outcome.done, outcome.failed.size)
            progress = null
            chooseFolder(dir)
            onDone(id)
        }
    }
}
