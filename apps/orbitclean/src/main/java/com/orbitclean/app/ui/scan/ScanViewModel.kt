/**
 * [INPUT]: 依赖 data/ScanRepository 的 results/rescan/load，依赖 lifecycle 的 ViewModel/SavedStateHandle
 * [OUTPUT]: 对外提供 ScanViewModel、ScanUi、ScanRow、SCAN_KINDS、ScanUi.from（纯函数，可单测）
 * [POS]: ui/scan 的状态层，把仓库扫描结果折叠成扫描页需要的一份不可变快照，JunkScanScreen 只负责渲染
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.core.storage.FileGroup
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.data.ScanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 一键扫描覆盖的类别，顺序即列表顺序 */
val SCAN_KINDS = listOf(FileKind.REDUNDANT, FileKind.DUPLICATE, FileKind.LARGE)

/** 一个类别的扫描结果；[count]/[size] 为 null 表示仍在扫描 */
data class ScanRow(val kind: FileKind, val count: Int?, val size: Long?) {
    val done: Boolean get() = count != null
}

/** 扫描页的全部状态：每类一行 + 可直接清理的空间 */
data class ScanUi(val rows: List<ScanRow>, val junkBytes: Long) {
    val done: Boolean get() = rows.all { it.done }
    val itemCount: Int get() = rows.sumOf { it.count ?: 0 }

    companion object {
        /** 可直接清理 = 冗余文件全部 + 重复文件中除第一份以外的副本（大文件只展示，不计入） */
        fun from(results: Map<FileKind, List<FileGroup>>): ScanUi {
            val rows = SCAN_KINDS.map { kind ->
                val groups = results[kind]
                ScanRow(kind, groups?.sumOf { it.items.size }, groups?.sumOf { it.totalSize })
            }
            val junk = results[FileKind.REDUNDANT].orEmpty().sumOf { it.totalSize } +
                results[FileKind.DUPLICATE].orEmpty().sumOf { g -> g.items.drop(1).sumOf { it.size } }
            return ScanUi(rows, junk)
        }
    }
}

/**
 * 首次进入时清空旧结果重新扫描；进程被回收后恢复时（[SavedStateHandle] 里已有标记）
 * 只补扫缺失的类别，不丢掉已经展示过的结果。
 */
class ScanViewModel(repo: ScanRepository, state: SavedStateHandle) : ViewModel() {

    init {
        if (state.get<Boolean>(KEY_STARTED) == true) {
            SCAN_KINDS.forEach(repo::load)
        } else {
            SCAN_KINDS.forEach { repo.rescan(it) }
            state[KEY_STARTED] = true
        }
    }

    val ui: StateFlow<ScanUi> = repo.results
        .map(ScanUi::from)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanUi.from(repo.results.value))

    private companion object {
        const val KEY_STARTED = "scan_started"
    }
}
