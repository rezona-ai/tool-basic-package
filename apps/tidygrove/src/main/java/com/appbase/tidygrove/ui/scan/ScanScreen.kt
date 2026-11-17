/**
 * [INPUT]: 依赖 :features:cleaner 的 ScanViewModel / CleanViewModel / Cleaner，依赖 ui/scene 的 CleanScene、ui/clean 的 CleaningView，
 *          依赖 data/Copy，依赖 :core:ui 的通用组件
 * [OUTPUT]: 对外提供 ScanScreen
 * [POS]: 扫描页（变体：onetap 一键）：结果按 ScanReveal 的节奏逐行揭晓、总数滚动；扫描三类可清理内容，结果出来后按类整块勾选（冗余、重复默认勾上，大文件默认不勾），
 *        一个按钮直接清理全部勾选内容 → 清理中 → 完成页；想细看某一类可以点“查看”进类别页。
 *        这里只在清理完成后弹插屏（CleaningView 里），扫描结果出来时不弹。接口在所有清理流程变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.CleanResult
import com.appbase.feature.cleaner.state.CleanViewModel
import com.appbase.feature.cleaner.state.ScanViewModel
import com.appbase.tidygrove.data.Copy
import com.appbase.tidygrove.ui.clean.CLEAN_ANIMATION_MS
import com.appbase.tidygrove.ui.clean.CleaningView
import com.appbase.tidygrove.ui.components.ConfirmDialog
import com.appbase.tidygrove.ui.components.icon
import com.appbase.tidygrove.ui.components.tint
import com.appbase.tidygrove.ui.motion.enterUp
import com.appbase.tidygrove.ui.scene.CleanScene
import com.appbase.tidygrove.ui.scene.ScenePhase

@Composable
fun ScanScreen(onBack: () -> Unit, onOpenKind: (FileKind) -> Unit, onCleaned: (Set<FileKind>, CleanResult) -> Unit) {
    // 先建扫描（清空旧结果重新扫），再建勾选状态，后者从空结果开始逐类套用默认勾选
    val scan: ScanViewModel = viewModel { ScanViewModel(Cleaner.scans, createSavedStateHandle()) }
    val clean: CleanViewModel = viewModel(key = "onetap") { CleanViewModel(Cleaner.scans, FileKind.CLEANABLE) }
    // 删除后勾选会随文件消失而清空，所以在确认那一刻记下清理了哪些类别
    var cleanedKinds by remember { mutableStateOf(emptySet<FileKind>()) }
    if (clean.cleaning) {
        CleaningView(clean) { onCleaned(cleanedKinds, it) }
        return
    }
    val ui by scan.ui.collectAsState()
    val reveal = rememberScanReveal(ui)
    clean.groups.collectAsState().value // 结果变化时刷新勾选数
    var confirm by remember { mutableStateOf(false) }
    val selectedBytes = clean.selectedBytes
    val selectedCount = clean.selected.size

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Sweep", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CleanScene(
                if (reveal.done) ScenePhase.DONE else ScenePhase.SCANNING,
                reveal.sceneProgress,
                Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatSize(if (reveal.done) selectedBytes else (ui.junkBytes * reveal.ease).toLong()), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(if (reveal.done) "to clear" else "looking around", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                ui.rows.forEachIndexed { i, row ->
                    val rowDone = reveal.rowDone(i)
                    val kind = row.kind
                    val total = row.count ?: 0
                    val picked = clean.selectedIn(kind)
                    AppCard(Modifier.fillMaxWidth().enterUp(i)) {
                        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(kind.icon(), kind.tint())
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Copy.title(kind), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    when {
                                        !rowDone -> "Looking…"
                                        total == 0 -> "All clear"
                                        else -> "$picked of ${plural(total, "file")} picked · ${formatSize(row.size ?: 0)}"
                                    },
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                            when {
                                !rowDone -> RowStatus(false, Modifier.padding(12.dp))
                                total > 0 -> {
                                    TextButton(onClick = { onOpenKind(kind) }) { Text("Look", color = Primary) }
                                    Checkbox(
                                        checked = picked > 0,
                                        onCheckedChange = { clean.setKind(kind, it) },
                                        colors = CheckboxDefaults.colors(checkedColor = Primary),
                                    )
                                }
                                // 这一类没找到东西：也给一个对勾，表示查过了
                                else -> RowStatus(true, Modifier.padding(12.dp))
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            GradientButton(
                when {
                    !reveal.done -> "Looking…"
                    selectedCount == 0 -> "Pick something to clear"
                    else -> "Clear ${formatSize(selectedBytes)}"
                },
                enabled = reveal.done && selectedCount > 0,
            ) { confirm = true }
        }
    }
    if (confirm) {
        val orphans = clean.fullySelectedDuplicateGroups()
        ConfirmDialog(
            title = "Clear ${plural(selectedCount, "file")}?",
            text = "${formatSize(selectedBytes)} will be removed from this phone for good." +
                if (orphans > 0) " $orphans duplicate ${if (orphans == 1) "set has" else "sets have"} every copy picked." else "",
            confirm = "Clear",
            onConfirm = {
                confirm = false
                cleanedKinds = FileKind.CLEANABLE.filter { clean.selectedIn(it) > 0 }.toSet()
                clean.clean(CLEAN_ANIMATION_MS)
            },
            onDismiss = { confirm = false },
        )
    }
}
