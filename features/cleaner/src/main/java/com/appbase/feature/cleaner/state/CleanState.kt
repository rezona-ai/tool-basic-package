/**
 * [INPUT]: 依赖 ../data 的 ScanRepository（results / ensureFresh / delete），依赖 lifecycle 的 ViewModel
 * [OUTPUT]: 对外提供 CleanResult、defaultSelection（纯函数）、CleanViewModel
 * [POS]: features/cleaner 的勾选与删除状态，一个或多个类别共用：分步清理时一次一个类别，一键清理时同时管多个类别。
 *        清理动画、插屏、跳转完成页属于界面节奏，由品牌页面处理
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner.state

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.FileItem
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.data.ScanRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次清理的结果：释放的字节数、实际删掉的条目数 */
data class CleanResult(val freed: Long, val count: Int)

/** 默认勾选：冗余文件全选；重复文件每组保留第一个（最早的）；其余类别不勾 */
fun defaultSelection(kind: FileKind, groups: List<FileGroup>): Set<String> = when (kind) {
    FileKind.REDUNDANT -> groups.flatMap { it.items }.mapTo(HashSet()) { it.path }
    FileKind.DUPLICATE -> groups.flatMap { it.items.drop(1) }.mapTo(HashSet()) { it.path }
    else -> emptySet()
}

class CleanViewModel(private val repo: ScanRepository, val kinds: List<FileKind>) : ViewModel() {

    /** 每个类别的分组；某个类别不在 map 里表示还在扫描 */
    val groups: StateFlow<Map<FileKind, List<FileGroup>>> = repo.results
        .map { r -> kinds.mapNotNull { k -> r[k]?.let { k to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, kinds.mapNotNull { k -> repo.results.value[k]?.let { k to it } }.toMap())

    val ready: Boolean get() = groups.value.size == kinds.size

    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 删除进行中（包括界面等插屏的那段）为 true，界面据此禁止返回 */
    var cleaning by mutableStateOf(false)
        private set
    var cleaningCount by mutableIntStateOf(0)
        private set
    /** 本次要删除的总字节数，清理动画据此按进度显示“已释放” */
    var cleaningBytes by mutableLongStateOf(0L)
        private set
    var cleanStartedAt = 0L
        private set

    /** 删除完成后给出结果，界面据此弹插屏并跳转完成页 */
    var cleanResult by mutableStateOf<CleanResult?>(null)
        private set

    private val defaultsApplied = HashSet<FileKind>()

    init {
        kinds.forEach(repo::ensureFresh)
        viewModelScope.launch {
            groups.collect { map ->
                val fresh = map.keys - defaultsApplied
                val alive = map.values.flatten().flatMap { it.items }.mapTo(HashSet()) { it.path }
                // 删除后剔除已不存在的路径；新扫完的类别套用默认勾选
                selected = (selected intersect alive) + fresh.flatMap { defaultSelection(it, map.getValue(it)) }
                defaultsApplied += fresh
            }
        }
    }

    fun items(kind: FileKind): List<FileItem> = groups.value[kind].orEmpty().flatMap { it.items }

    fun allItems(): List<FileItem> = kinds.flatMap(::items)

    val selectedBytes: Long get() = allItems().filter { it.path in selected }.sumOf { it.size }

    fun selectedIn(kind: FileKind): Int = items(kind).count { it.path in selected }

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun setAll(select: Boolean) {
        selected = if (select) allItems().mapTo(HashSet()) { it.path } else emptySet()
    }

    /** 一键清理里整类勾选 / 取消 */
    fun setKind(kind: FileKind, select: Boolean) {
        val paths = items(kind).mapTo(HashSet()) { it.path }
        selected = if (select) selected + paths else selected - paths
    }

    /** 重复文件中所有副本都被勾选的组数（删除后一份都不剩），确认弹窗里提醒 */
    fun fullySelectedDuplicateGroups(): Int =
        groups.value[FileKind.DUPLICATE].orEmpty().count { g -> g.items.all { it.path in selected } }

    /**
     * 删除选中项。[minMs] 是清理动画的最短时长（0 表示删完就出结果）；删除本身是真实进度，
     * 动画只负责把“正在删除”展示完整
     */
    fun clean(minMs: Long = 0L) {
        val targets = allItems().filter { it.path in selected }
        cleaningCount = targets.size
        cleaningBytes = targets.sumOf { it.size }
        cleanStartedAt = SystemClock.elapsedRealtime()
        cleaning = true
        viewModelScope.launch {
            val freed = repo.delete(targets)
            val count = targets.count { !repo.store.exists(it.path) }
            delay(minMs - (SystemClock.elapsedRealtime() - cleanStartedAt))
            cleanResult = CleanResult(freed, count)
        }
    }

    fun finishClean() {
        cleaning = false
        cleanResult = null
    }
}
