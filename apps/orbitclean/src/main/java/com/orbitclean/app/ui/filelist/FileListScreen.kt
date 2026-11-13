/**
 * [INPUT]: 依赖 data/ScanRepository 的 results/delete，依赖 core/ui 的 FileRow、ui/components 的 SaturnProgressView，依赖 core/ads 的插屏与 Banner
 * [OUTPUT]: 对外提供 FileListViewModel、FileListScreen、CleanResult
 * [POS]: ui/filelist 的文件列表与删除流程：勾选 → 确认 → 土星清理动画（光环脉动 + 螺旋粒子）→ 插屏 → 完成页
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.filelist

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdBanner
import com.appbase.core.ads.AdPlacement
import com.appbase.core.ads.AdPolicy
import com.appbase.core.ads.showInterstitial
import com.appbase.core.common.formatDate
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.openFile
import com.appbase.core.ui.EmptyState
import com.appbase.core.ui.FileRow
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.LoadingState
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.data.ScanRepository
import com.orbitclean.app.ui.Placements
import com.orbitclean.app.ui.components.PlainScreen
import com.orbitclean.app.ui.components.ProgressScene
import com.orbitclean.app.ui.components.SaturnProgressView
import com.orbitclean.app.ui.components.splitSize
import com.orbitclean.app.ui.components.waitForAd
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CleanResult(val freed: Long, val count: Int)

class FileListViewModel(private val repo: ScanRepository, val kind: FileKind) : ViewModel() {

    val groups: StateFlow<List<FileGroup>?> = repo.results
        .map { it[kind] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.results.value[kind])

    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 清理动画阶段（删除中 + 插屏期间）为 true */
    var cleaning by mutableStateOf(false)
        private set
    var cleaningCount by mutableStateOf(0)
        private set
    /** 本次要删除的总字节数，清理动画据此按进度显示"已释放" */
    var cleaningBytes by mutableStateOf(0L)
        private set
    var cleanStartedAt = 0L
        private set

    /** 删除完成且动画播够最短时长后给出结果，界面据此弹插屏并跳转完成页 */
    var cleanResult by mutableStateOf<CleanResult?>(null)
        private set

    private var defaultsApplied = false

    init {
        repo.ensureFresh(kind)
        viewModelScope.launch {
            groups.collect { g ->
                if (g == null) return@collect
                selected = if (!defaultsApplied) {
                    defaultsApplied = true
                    defaultSelection(g)
                } else {
                    // 删除后剔除已不存在的路径
                    selected intersect g.flatMap { it.items }.mapTo(HashSet()) { it.path }
                }
            }
        }
    }

    /** 冗余文件默认全选；重复文件默认保留每组第一个（最早的） */
    private fun defaultSelection(groups: List<FileGroup>): Set<String> = when (kind) {
        FileKind.REDUNDANT -> groups.flatMap { it.items }.mapTo(HashSet()) { it.path }
        FileKind.DUPLICATE -> groups.flatMap { it.items.drop(1) }.mapTo(HashSet()) { it.path }
        else -> emptySet()
    }

    fun allItems(): List<FileItem> = groups.value.orEmpty().flatMap { it.items }

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun setAll(select: Boolean) {
        selected = if (select) allItems().mapTo(HashSet()) { it.path } else emptySet()
    }

    /** 重复文件中所有副本都被勾选的组数（删除后一份都不剩） */
    fun fullySelectedGroups(): Int =
        if (kind != FileKind.DUPLICATE) 0 else groups.value.orEmpty().count { g -> g.items.all { it.path in selected } }

    /** 删除选中项；清理动画至少播放 [AdPolicy.CLEANING_MIN_MS]，期间界面禁止返回 */
    fun clean() {
        val targets = allItems().filter { it.path in selected }
        cleaningCount = targets.size
        cleaningBytes = targets.sumOf { it.size }
        cleanStartedAt = SystemClock.elapsedRealtime()
        cleaning = true
        viewModelScope.launch {
            val freed = repo.delete(targets)
            val count = targets.count { !repo.store.exists(it.path) }
            delay(AdPolicy.CLEANING_MIN_MS - (SystemClock.elapsedRealtime() - cleanStartedAt))
            cleanResult = CleanResult(freed, count)
        }
    }

    fun finishClean() {
        cleaning = false
        cleanResult = null
    }
}

@Composable
fun FileListScreen(kind: FileKind, onBack: () -> Unit, onCleaned: (CleanResult) -> Unit) {
    val vm: FileListViewModel = viewModel(key = kind.name) {
        FileListViewModel(App.instance.scans, kind)
    }
    val groups by vm.groups.collectAsState()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var confirm by remember { mutableStateOf(false) }

    // 清理流程：清理动画（删除完成后继续等插屏加载）→ 插屏 → 完成页
    val cleanProgress = remember { Animatable(0f) }
    LaunchedEffect(vm.cleaning) {
        if (vm.cleaning) {
            cleanProgress.snapTo(0f)
            cleanProgress.animateTo(0.9f, tween(AdPolicy.CLEANING_MIN_MS.toInt()))
        }
    }
    val result = vm.cleanResult
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        waitForAd(cleanProgress, AdPolicy.AD_WAIT_MAX_MS - (SystemClock.elapsedRealtime() - vm.cleanStartedAt))
        showInterstitial(activity, Placements.CLEAN_DONE_INTERSTITIAL) {
            vm.finishClean()
            onCleaned(result)
        }
    }
    if (vm.cleaning) {
        BackHandler { }
        val (value, unit) = splitSize((vm.cleaningBytes * cleanProgress.value).toLong())
        SaturnProgressView(
            title = "Cleaning",
            kicker = "Freeing space",
            value = value,
            unit = unit,
            caption = "Removing ${plural(vm.cleaningCount, "item")}…",
            progress = cleanProgress.value,
            scene = ProgressScene.CLEAN,
            label = kind.title,
            footer = "Please keep the app open",
        )
        return
    }

    val all = groups.orEmpty().flatMap { it.items }
    val selectedItems = all.filter { it.path in vm.selected }
    val selectedSize = selectedItems.sumOf { it.size }
    val allSelected = all.isNotEmpty() && selectedItems.size == all.size

    PlainScreen(bottomInset = true) {
        Column(Modifier.fillMaxSize()) {
            TopBar(kind.title, onBack = onBack) {
                if (all.isNotEmpty()) {
                    TextButton(onClick = { vm.setAll(!allSelected) }) {
                        Text(if (allSelected) "Clear" else "Select all", color = Primary)
                    }
                }
            }
            when {
                groups == null -> LoadingState("Scanning…")
                all.isEmpty() -> EmptyState("Nothing found")
                else -> {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "${plural(all.size, "item")} · ${formatSize(all.sumOf { it.size })}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                        groups.orEmpty().forEach { group ->
                            val title = group.title
                            if (title != null) {
                                item(key = "h:$title:${group.items.first().path}") {
                                    Text(
                                        title,
                                        modifier = Modifier.padding(top = 12.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                            items(group.items, key = { it.path }) { item ->
                                val checked = item.path in vm.selected
                                FileRow(
                                    item = item,
                                    subtitle = if (item.isDir) "Empty folder" else "${formatSize(item.size)} · ${formatDate(item.modified)}",
                                    onClick = { vm.toggle(item.path) },
                                    onLongClick = if (item.isDir) null else ({ openFile(context, item.path) }),
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { vm.toggle(item.path) },
                                        colors = CheckboxDefaults.colors(checkedColor = Primary),
                                    )
                                }
                            }
                        }
                        item { Text("Tip: long-press a file to open it", color = TextSecondary, fontSize = 12.sp) }
                    }
                    Column(Modifier.padding(16.dp)) {
                        AdBanner(Placements.FILE_LIST_BANNER)
                        Spacer(Modifier.height(12.dp))
                        GradientButton(
                            text = "Delete ${selectedItems.size} · ${formatSize(selectedSize)}",
                            enabled = selectedItems.isNotEmpty(),
                            onClick = { confirm = true },
                        )
                    }
                }
            }
        }

    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${plural(selectedItems.size, "item")}?") },
            text = {
                val noCopyLeft = vm.fullySelectedGroups()
                Column {
                    Text("${formatSize(selectedSize)} will be permanently deleted. This cannot be undone.")
                    if (noCopyLeft > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Warning: every copy of ${plural(noCopyLeft, "file")} is selected. No copy will be kept.",
                            color = Danger,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    vm.clean()
                }) { Text("Delete", color = Primary) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = TextSecondary) } },
        )
    }
}
