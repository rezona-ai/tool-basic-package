/**
 * [INPUT]: 依赖 core/storage 的 FileStore（listDir / search / deleteTree / transfer / rename / createFolder），依赖 lifecycle 的 ViewModel
 * [OUTPUT]: 对外提供 FileClipboard、BusyOp、BrowserMessage、BrowserViewModel
 * [POS]: features/cleaner 的文件浏览状态：目录内容、多选、搜索、复制 / 移动 / 删除 / 重命名 / 新建文件夹。
 *        提示用 BusyOp / BrowserMessage 返回，文案由品牌页面自己写
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.FileStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 待粘贴的条目：复制或移动时先放这里，进入目标目录后粘贴；跨目录页面共享 */
object FileClipboard {
    var paths by mutableStateOf<List<String>>(emptyList())
        private set
    var move by mutableStateOf(false)
        private set

    fun put(paths: List<String>, move: Boolean) {
        this.paths = paths
        this.move = move
    }

    fun clear() {
        paths = emptyList()
    }
}

/** 进行中的耗时操作，界面显示进度弹窗 */
enum class BusyOp { DELETE, COPY, MOVE }

/** 一次性提示，界面显示后调用 [BrowserViewModel.consumeMessage] */
sealed interface BrowserMessage {
    data class Deleted(val done: Int, val total: Int) : BrowserMessage
    data class Transferred(val move: Boolean, val done: Int, val total: Int) : BrowserMessage
    /** 已放进剪贴板，提示用户去目标文件夹粘贴 */
    data object Clipped : BrowserMessage
    data object Renamed : BrowserMessage
}

/** [dir] 为 [FileStore.ROOT] 时是文件管理首页（授权文件夹列表） */
class BrowserViewModel(private val repo: FileStore, val dir: String) : ViewModel() {

    var entries by mutableStateOf<List<FileItem>?>(null)
        private set
    var denied by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var busy by mutableStateOf<BusyOp?>(null)
        private set
    var message by mutableStateOf<BrowserMessage?>(null)
        private set

    /** null 表示不在搜索；results 为 null 表示搜索中 */
    var query by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<FileItem>?>(null)
        private set
    private var searchJob: Job? = null

    val visible: List<FileItem>? get() = if (query != null) results else entries
    val selectedItems: List<FileItem> get() = visible.orEmpty().filter { it.path in selected }

    fun reload() {
        viewModelScope.launch {
            val list = repo.listDir(dir)
            denied = list == null
            entries = list.orEmpty()
            query?.let { search(it, debounce = false) }
            prune()
        }
    }

    private fun prune() {
        val paths = visible.orEmpty().mapTo(HashSet()) { it.path }
        selected = selected intersect paths
    }

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun setAll(select: Boolean) {
        selected = if (select) visible.orEmpty().mapTo(HashSet()) { it.path } else emptySet()
    }

    fun openSearch() {
        selected = emptySet()
        query = ""
        results = emptyList()
    }

    fun closeSearch() {
        searchJob?.cancel()
        selected = emptySet()
        query = null
        results = null
    }

    fun search(q: String, debounce: Boolean = true) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) {
            results = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            results = null
            results = repo.search(dir, q)
            prune()
        }
    }

    fun consumeMessage() {
        message = null
    }

    fun delete() = runOp(BusyOp.DELETE) {
        val targets = selected.toList()
        val n = repo.deleteTree(targets)
        selected = emptySet()
        BrowserMessage.Deleted(n, targets.size)
    }

    /** 放入剪贴板，等用户到目标目录粘贴 */
    fun clip(move: Boolean) {
        FileClipboard.put(selected.toList(), move)
        selected = emptySet()
        message = BrowserMessage.Clipped
    }

    fun paste() {
        val paths = FileClipboard.paths
        val move = FileClipboard.move
        runOp(if (move) BusyOp.MOVE else BusyOp.COPY) {
            val n = repo.transfer(paths, dir, move)
            FileClipboard.clear()
            BrowserMessage.Transferred(move, n, paths.size)
        }
    }

    /** [done] 收到 null 表示成功，否则是系统给的错误原因（可能为空，界面用自己的兜底文案） */
    fun rename(item: FileItem, name: String, done: (Throwable?) -> Unit) {
        viewModelScope.launch {
            repo.rename(item.path, name)
                .onSuccess {
                    done(null)
                    selected = emptySet()
                    message = BrowserMessage.Renamed
                    reload()
                }
                .onFailure { done(it) }
        }
    }

    fun createFolder(name: String, done: (Throwable?) -> Unit) {
        viewModelScope.launch {
            repo.createFolder(dir, name)
                .onSuccess {
                    done(null)
                    reload()
                }
                .onFailure { done(it) }
        }
    }

    private fun runOp(op: BusyOp, block: suspend () -> BrowserMessage) {
        viewModelScope.launch {
            busy = op
            message = try {
                block()
            } finally {
                busy = null
            }
            reload()
        }
    }
}
