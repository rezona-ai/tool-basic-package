/**
 * [INPUT]: 依赖 ../data 的 ScanRepository（results / rescan / load / storageInfo），依赖 lifecycle 的 ViewModel / SavedStateHandle
 * [OUTPUT]: 对外提供 StorageViewModel、ScanRow、ScanUi（from 为纯函数，可单测）、ScanViewModel
 * [POS]: features/cleaner 的扫描与用量状态：把仓库结果折叠成扫描页要的一份不可变快照，界面只负责渲染
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.StorageInfo
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.data.ScanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 首页存储用量；页面回到前台时调用 [refresh]，清理后用量随之更新 */
class StorageViewModel(private val repo: ScanRepository) : ViewModel() {

    var storage: StorageInfo by mutableStateOf(repo.store.storageInfo())
        private set

    fun refresh() {
        storage = repo.store.storageInfo()
    }
}

/** 一个类别的扫描结果；[count] / [size] 为 null 表示仍在扫描 */
data class ScanRow(val kind: FileKind, val count: Int?, val size: Long?) {
    val done: Boolean get() = count != null
}

/** 扫描页的全部状态：每类一行 + 可直接清理的空间 */
data class ScanUi(val rows: List<ScanRow>, val junkBytes: Long) {
    val done: Boolean get() = rows.all { it.done }
    val itemCount: Int get() = rows.sumOf { it.count ?: 0 }

    /** 已完成扫描的类别占比（0..1），扫描页的进度 */
    val progress: Float get() = if (rows.isEmpty()) 1f else rows.count { it.done }.toFloat() / rows.size

    companion object {
        /** 可直接清理 = 冗余文件全部 + 重复文件中除第一份以外的副本（大文件只展示，不计入） */
        fun from(results: Map<FileKind, List<FileGroup>>, kinds: List<FileKind> = FileKind.CLEANABLE): ScanUi {
            val rows = kinds.map { kind ->
                val groups = results[kind]
                ScanRow(kind, groups?.sumOf { it.items.size }, groups?.sumOf { it.totalSize })
            }
            val junk = (if (FileKind.REDUNDANT in kinds) results[FileKind.REDUNDANT].orEmpty().sumOf { it.totalSize } else 0L) +
                (if (FileKind.DUPLICATE in kinds) results[FileKind.DUPLICATE].orEmpty().sumOf { g -> g.items.drop(1).sumOf { it.size } } else 0L)
            return ScanUi(rows, junk)
        }
    }
}

/**
 * 首次进入时清空旧结果重新扫描；进程被回收后恢复时（[SavedStateHandle] 里已有标记）只补扫缺失的类别。
 */
class ScanViewModel(
    repo: ScanRepository,
    state: SavedStateHandle,
    val kinds: List<FileKind> = FileKind.CLEANABLE,
) : ViewModel() {

    init {
        if (state.get<Boolean>(KEY_STARTED) == true) {
            kinds.forEach(repo::load)
        } else {
            kinds.forEach { repo.rescan(it) }
            state[KEY_STARTED] = true
        }
    }

    val ui: StateFlow<ScanUi> = repo.results
        .map { ScanUi.from(it, kinds) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanUi.from(repo.results.value, kinds))

    private companion object {
        const val KEY_STARTED = "scan_started"
    }
}
