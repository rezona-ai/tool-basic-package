/**
 * [INPUT]: 依赖 :features:cleaner 的 CleanViewModel / FileKind / Cleaner，依赖 :core:ui 的 FileRow / TopBar / GradientButton / 状态页，
 *          依赖 data/Copy 的类别文案、./Cleaning 的 CleaningView
 * [OUTPUT]: 对外提供 CategoryScreen
 * [POS]: 单个类别的文件列表：按分组列出、勾选（冗余默认全选，重复默认每组留一份）、确认后删除 → 清理中 → 完成页。
 *        点一行切换勾选，长按用其他应用打开
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.common.formatDate
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.storage.openFile
import com.appbase.core.ui.EmptyState
import com.appbase.core.ui.FileRow
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.LoadingState
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.CleanResult
import com.appbase.feature.cleaner.state.CleanViewModel
import {{CODE_PACKAGE}}.data.Copy
import {{CODE_PACKAGE}}.ui.components.ConfirmDialog

@Composable
fun CategoryScreen(kind: FileKind, onBack: () -> Unit, onCleaned: (CleanResult) -> Unit) {
    val vm: CleanViewModel = viewModel(key = "kind-${kind.name}") { CleanViewModel(Cleaner.scans, listOf(kind)) }
    if (vm.cleaning) {
        CleaningView(vm, onCleaned)
        return
    }
    val all by vm.groups.collectAsState()
    val groups = all[kind]
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val count = vm.selectedIn(kind)

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(Copy.title(kind), onBack = onBack) {
            if (!groups.isNullOrEmpty()) {
                val allOn = count == vm.items(kind).size
                TextButton(onClick = { vm.setAll(!allOn) }) { Text(if (allOn) "None" else "All", color = Primary) }
            }
        }
        when {
            groups == null -> LoadingState("Looking through your folders…")
            groups.isEmpty() -> EmptyState("Nothing here right now")
            else -> {
                Box(Modifier.weight(1f)) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Text(Copy.blurb(kind), color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp)) }
                        groups.forEach { g ->
                            g.title?.let { t ->
                                item(key = "h-$t") {
                                    Text(t, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                                }
                            }
                            items(g.items, key = { it.path }) { file ->
                                FileRow(
                                    item = file,
                                    subtitle = "${formatSize(file.size)} · ${formatDate(file.modified)}",
                                    onClick = { vm.toggle(file.path) },
                                    onLongClick = { if (!file.isDir) openFile(context, file.path) },
                                ) {
                                    Checkbox(
                                        checked = file.path in vm.selected,
                                        onCheckedChange = { vm.toggle(file.path) },
                                        colors = CheckboxDefaults.colors(checkedColor = Primary),
                                    )
                                }
                            }
                        }
                    }
                }
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    GradientButton(
                        if (count == 0) "Select files to remove" else "Remove ${plural(count, "file")} · ${formatSize(vm.selectedBytes)}",
                        enabled = count > 0,
                    ) { confirm = true }
                }
            }
        }
    }
    if (confirm) {
        val orphans = vm.fullySelectedDuplicateGroups()
        ConfirmDialog(
            title = "Remove ${plural(count, "file")}?",
            text = "They'll be deleted from your device." +
                if (orphans > 0) " $orphans duplicate ${if (orphans == 1) "set has" else "sets have"} every copy selected." else "",
            confirm = "Remove",
            onConfirm = {
                confirm = false
                vm.clean(CLEAN_ANIMATION_MS)
            },
            onDismiss = { confirm = false },
        )
    }
}
