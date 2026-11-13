/**
 * [INPUT]: 依赖 data/ScanRepository 的 results/ensureFresh/storageInfo，依赖 lifecycle 的 ViewModel
 * [OUTPUT]: 对外提供 CleanDoneViewModel、Recommend、StorageDelta、recommendsFor（纯函数，可单测）
 * [POS]: ui/done 的状态层，算出清理前后的用量对比和"继续清理"推荐，CleanDoneScreen 只负责渲染
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.done

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.StorageInfo
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.data.ScanRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 推荐顺序：越靠前越容易找到可清理的内容 */
private val RECOMMEND_ORDER = listOf(
    FileKind.REDUNDANT, FileKind.DUPLICATE, FileKind.LARGE, FileKind.RECENT, FileKind.IMAGES, FileKind.VIDEOS,
)

/** 一条推荐；[found]/[size] 为 null 表示尚未扫描完，卡片显示类别简介 */
data class Recommend(val kind: FileKind, val found: Int?, val size: Long?)

/** 清理前后的存储占用（0..1） */
data class StorageDelta(val before: Float, val after: Float) {
    companion object {
        fun of(now: StorageInfo, freed: Long): StorageDelta {
            if (now.total <= 0L) return StorageDelta(0f, 0f)
            val before = ((now.used + freed).toFloat() / now.total).coerceIn(0f, 1f)
            return StorageDelta(before, now.usedFraction)
        }
    }
}

/** 除刚清理的类别外取前 3 个；扫描到的内容为空时不显示数量 */
fun recommendsFor(kind: FileKind, results: Map<FileKind, List<FileGroup>>): List<Recommend> =
    RECOMMEND_ORDER.filter { it != kind }.take(3).map { k ->
        val items = results[k]?.flatMap { it.items }?.takeIf { it.isNotEmpty() }
        Recommend(k, items?.size, items?.sumOf { it.size })
    }

class CleanDoneViewModel(repo: ScanRepository, kind: FileKind, freed: Long) : ViewModel() {

    val storage: StorageDelta = StorageDelta.of(repo.store.storageInfo(), freed)

    val recommends: StateFlow<List<Recommend>> = repo.results
        .map { recommendsFor(kind, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, recommendsFor(kind, repo.results.value))

    init {
        // 预先扫描推荐项，卡片上显示"发现 xx 个文件"，提高点击率
        recommends.value.forEach { repo.ensureFresh(it.kind) }
    }
}
