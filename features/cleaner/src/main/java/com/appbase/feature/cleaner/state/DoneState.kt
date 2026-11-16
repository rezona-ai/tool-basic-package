/**
 * [INPUT]: 依赖 ../data 的 ScanRepository（results / ensureFresh / storageInfo），依赖 lifecycle 的 ViewModel
 * [OUTPUT]: 对外提供 StorageDelta、Recommend、recommendsFor（纯函数，可单测）、DoneViewModel
 * [POS]: features/cleaner 的完成页状态：清理前后的用量对比和“继续清理”推荐
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner.state

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

/** 默认推荐顺序：越靠前越容易找到可清理的内容 */
val DEFAULT_RECOMMEND_ORDER = listOf(
    FileKind.REDUNDANT, FileKind.DUPLICATE, FileKind.LARGE, FileKind.RECENT, FileKind.IMAGES, FileKind.VIDEOS,
)

/** 一条推荐；[found] / [size] 为 null 表示尚未扫描完或没有内容 */
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

/** 去掉刚清理过的类别后取前 [limit] 个；扫描到的内容为空时不显示数量 */
fun recommendsFor(
    cleaned: Set<FileKind>,
    results: Map<FileKind, List<FileGroup>>,
    order: List<FileKind> = DEFAULT_RECOMMEND_ORDER,
    limit: Int = 3,
): List<Recommend> =
    order.filter { it !in cleaned }.take(limit).map { k ->
        val items = results[k]?.flatMap { it.items }?.takeIf { it.isNotEmpty() }
        Recommend(k, items?.size, items?.sumOf { it.size })
    }

class DoneViewModel(
    repo: ScanRepository,
    cleaned: Set<FileKind>,
    freed: Long,
    order: List<FileKind> = DEFAULT_RECOMMEND_ORDER,
) : ViewModel() {

    val storage: StorageDelta = StorageDelta.of(repo.store.storageInfo(), freed)

    val recommends: StateFlow<List<Recommend>> = repo.results
        .map { recommendsFor(cleaned, it, order) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, recommendsFor(cleaned, repo.results.value, order))

    init {
        // 预先扫描推荐项，卡片上能显示“发现 xx 个文件”
        recommends.value.forEach { repo.ensureFresh(it.kind) }
    }
}
