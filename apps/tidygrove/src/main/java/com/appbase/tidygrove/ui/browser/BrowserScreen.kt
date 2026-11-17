/**
 * [INPUT]: 依赖 :features:cleaner 的 BrowserViewModel / FileClipboard / BusyOp / BrowserMessage，
 *          依赖 :core:storage 的 FileStore / openFile / shareFiles，依赖 :core:ui 的通用组件与颜色 token
 * [OUTPUT]: 对外提供 BrowserScreen
 * [POS]: 文件管理页：根目录列出用户授权的文件夹 + 添加文件夹；进入子目录浏览、打开文件；
 *        长按多选后复制 / 移动 / 重命名 / 分享 / 删除，新建文件夹，按名搜索。
 *        布局由 scaffold 选定（变体 browser：list 列表 / grid 网格），见 [GRID]
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.CoreServices
import com.appbase.core.common.formatDate
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.FileStore
import com.appbase.core.storage.FileType
import com.appbase.core.storage.files
import com.appbase.core.storage.openFile
import com.appbase.core.storage.shareFiles
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.EmptyState
import com.appbase.core.ui.FileRow
import com.appbase.core.ui.FileThumb
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.LoadingState
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.color
import com.appbase.core.ui.icon
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.CardBg
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.state.BrowserMessage
import com.appbase.feature.cleaner.state.BrowserViewModel
import com.appbase.feature.cleaner.state.BusyOp
import com.appbase.feature.cleaner.state.FileClipboard
import com.appbase.tidygrove.ui.motion.pressScale

/** true：网格（缩略图大块）；false：列表。由 scaffold 按变体 browser 写入 */
private const val GRID = true

private sealed interface BrowserDialog {
    data object Delete : BrowserDialog
    data object NewFolder : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
}

/**
 * @param path 为空（[FileStore.ROOT]）时是文件管理首页：列出用户授权的文件夹（整个内部存储已授权时为根目录）
 * @param onBack 为 null 时不显示返回按钮（作为底部导航的一页）
 * @param onManageFolders 首页底部“添加文件夹”：打开文件夹授权页
 */
@Composable
fun BrowserScreen(
    path: String,
    onBack: (() -> Unit)?,
    onOpenDir: (String) -> Unit,
    onManageFolders: () -> Unit = {},
) {
    val repo = CoreServices.files
    val access = repo.access
    val context = LocalContext.current
    val vm: BrowserViewModel = viewModel(key = "browser:$path") { BrowserViewModel(repo, path) }
    val isRoot = vm.dir == FileStore.ROOT
    val grants by access.grants.collectAsState()
    // 首页列的是授权文件夹本身（不是真实目录）：不能在这里新建、粘贴，也不能选中授权文件夹去改名 / 删除
    val folderList = isRoot && grants.none { it.path == access.primaryRoot }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }

    // 每次回到本页都重新读取：子目录里移动 / 删除过文件，返回时立即反映
    LifecycleResumeEffect(vm) {
        vm.reload()
        onPauseOrDispose { }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it.text(), Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val selecting = vm.selected.isNotEmpty()
    val searching = vm.query != null
    BackHandler(enabled = selecting || searching) {
        if (selecting) vm.setAll(false) else vm.closeSearch()
    }

    Column(Modifier.fillMaxSize()) {
        val list = vm.visible
        when {
            selecting -> TopBar("${vm.selected.size} selected", onBack = { vm.setAll(false) }) {
                val all = list.orEmpty().isNotEmpty() && vm.selected.size == list.orEmpty().size
                TextButton(onClick = { vm.setAll(!all) }) { Text(if (all) "Clear" else "All", color = Primary) }
            }
            searching -> SearchBar(vm.query.orEmpty(), onQuery = vm::search, onClose = vm::closeSearch)
            else -> TopBar(if (isRoot) "Your folders" else FileStore.nameOf(vm.dir), onBack = onBack) {
                IconButton(onClick = vm::openSearch) { Icon(Icons.Rounded.Search, "Search") }
                if (!folderList) {
                    IconButton(onClick = { dialog = BrowserDialog.NewFolder }) { Icon(Icons.Rounded.CreateNewFolder, "New folder") }
                }
            }
        }
        val location = when {
            folderList -> "Folders Tidy Grove can see"
            isRoot -> "Internal storage"
            else -> access.display(vm.dir)
        }
        Text(
            if (searching) "Looking in $location" else location,
            color = Primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            when {
                list == null -> LoadingState(if (searching) "Searching…" else "Loading…")
                searching && vm.query.isNullOrBlank() -> EmptyState("Type a few letters of the name")
                searching && list.isEmpty() -> EmptyState("No matches")
                vm.denied -> EmptyState("Android won't let us open this one")
                list.isEmpty() && !folderList -> EmptyState("Nothing in this folder yet")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(if (GRID && !folderList) 3 else 1),
                    contentPadding = PaddingValues(16.dp, 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.path }) { item ->
                        val subtitle = when {
                            searching -> access.display(FileStore.parentOf(item.path))
                            !item.isDir -> "${formatSize(item.size)} · ${formatDate(item.modified)}"
                            item.childCount < 0 -> "Protected"
                            else -> plural(item.childCount, "item")
                        }
                        val onClick = {
                            when {
                                selecting -> vm.toggle(item.path)
                                item.isDir -> onOpenDir(item.path)
                                else -> openFile(context, item.path)
                            }
                        }
                        val onLongClick = if (folderList && !searching) null else ({ vm.toggle(item.path) })
                        val checked = item.path in vm.selected
                        // 删除、粘贴、新建后其余项平滑补位
                        if (GRID && !folderList) {
                            FileTile(item, checked, selecting, onClick, onLongClick, Modifier.animateItem())
                        } else {
                            FileRow(item = item, subtitle = subtitle, modifier = Modifier.animateItem(), onClick = onClick, onLongClick = onLongClick) {
                                when {
                                    selecting -> Checkbox(
                                        checked = checked,
                                        onCheckedChange = { vm.toggle(item.path) },
                                        colors = CheckboxDefaults.colors(checkedColor = Primary),
                                    )
                                    item.isDir -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
                                }
                            }
                        }
                    }
                    if (folderList && !searching && !selecting) {
                        item(key = "add-folder", span = { GridItemSpan(maxLineSpan) }) { AddFolderRow(onManageFolders) }
                    }
                    if (!selecting) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("Tip: press and hold to pick files, then copy, move, rename, share or delete", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        when {
            selecting -> {
                val items = vm.selectedItems
                SelectionBar(
                    canRename = items.size == 1,
                    canShare = items.isNotEmpty() && items.none { it.isDir },
                    onCopy = { vm.clip(move = false) },
                    onMove = { vm.clip(move = true) },
                    onRename = { items.singleOrNull()?.let { dialog = BrowserDialog.Rename(it) } },
                    onShare = { shareFiles(context, items.map { it.path }) },
                    onDelete = { dialog = BrowserDialog.Delete },
                )
            }
            FileClipboard.paths.isNotEmpty() && !searching && !folderList -> PasteBar(onPaste = vm::paste, onCancel = FileClipboard::clear)
        }
    }

    vm.busy?.let { BusyDialog(it) }
    when (val d = dialog) {
        null -> Unit
        BrowserDialog.Delete -> {
            val items = vm.selectedItems
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = DialogBg,
                title = { Text("Delete ${plural(items.size, "item")}?") },
                text = {
                    Text(
                        if (items.any { it.isDir }) {
                            "Everything inside these folders goes too. There's no undo."
                        } else {
                            "${formatSize(items.sumOf { it.size })} will be removed for good."
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = null
                        vm.delete()
                    }) { Text("Delete", color = Danger) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel", color = TextSecondary) } },
            )
        }
        BrowserDialog.NewFolder -> NameDialog(
            title = "New folder",
            initial = "",
            confirm = "Create",
            onDismiss = { dialog = null },
            onConfirm = { name, error -> vm.createFolder(name) { e -> if (e == null) dialog = null else error(e.message ?: "Couldn't create the folder") } },
        )
        is BrowserDialog.Rename -> NameDialog(
            title = "Rename",
            initial = d.item.name,
            confirm = "Rename",
            selectBase = !d.item.isDir,
            onDismiss = { dialog = null },
            onConfirm = { name, error -> vm.rename(d.item, name) { e -> if (e == null) dialog = null else error(e.message ?: "Couldn't rename") } },
        )
    }
}

private fun BrowserMessage.text(): String = when (this) {
    is BrowserMessage.Deleted -> if (done == total) "Deleted ${plural(done, "item")}" else "Deleted $done of $total items"
    is BrowserMessage.Transferred -> {
        val verb = if (move) "Moved" else "Copied"
        if (done == total) "$verb ${plural(done, "item")}" else "$verb $done of $total items"
    }
    BrowserMessage.Clipped -> "Go to the destination folder and tap Paste"
    BrowserMessage.Renamed -> "Renamed"
}

/** 网格布局的一格：大缩略图 + 文件名；多选时右上角打勾 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(item: FileItem, checked: Boolean, selecting: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BrandTheme.style.corner(14.dp))
    Column(
        modifier
            .pressScale()
            .clip(shape)
            .background(CardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (item.type == FileType.IMAGE || item.type == FileType.VIDEO) {
                FileThumb(item, Modifier.fillMaxSize())
            } else {
                IconBadge(item.type.icon(), item.type.color(), 52.dp)
            }
            if (selecting) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    tint = if (checked) Primary else Line,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, color = TextPrimary)
        Text(
            if (item.isDir) plural(item.childCount.coerceAtLeast(0), "item") else formatSize(item.size),
            fontSize = 11.sp,
            color = TextSecondary,
        )
    }
}

@Composable
private fun AddFolderRow(onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Add, Primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Share another folder", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Let Tidy Grove look after it too", color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Name to look for", color = TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onQuery(query) }),
            colors = fieldColors(),
        )
    }
}

@Composable
private fun SelectionBar(
    canRename: Boolean,
    canShare: Boolean,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().background(DialogBg).padding(vertical = 6.dp)) {
        BarAction(Icons.Rounded.ContentCopy, "Copy", true, onCopy)
        BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Move", true, onMove)
        BarAction(Icons.Rounded.DriveFileRenameOutline, "Rename", canRename, onRename)
        BarAction(Icons.Rounded.Share, "Share", canShare, onShare)
        BarAction(Icons.Rounded.Delete, "Delete", true, onDelete, tint = Danger)
    }
}

@Composable
private fun RowScope.BarAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, tint: Color = TextPrimary) {
    Column(
        Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.35f)
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun PasteBar(onPaste: () -> Unit, onCancel: () -> Unit) {
    val count = FileClipboard.paths.size
    Row(
        Modifier.fillMaxWidth().background(DialogBg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${if (FileClipboard.move) "Move" else "Copy"} ${plural(count, "item")}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Head to a folder, then paste", color = TextSecondary, fontSize = 12.sp)
        }
        TextButton(onClick = onCancel) { Text("Cancel", color = TextSecondary) }
        Spacer(Modifier.width(4.dp))
        GradientButton("Paste", Modifier.width(112.dp), height = 40.dp, onClick = onPaste)
    }
}

@Composable
private fun BusyDialog(op: BusyOp) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { },
        containerColor = DialogBg,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    when (op) {
                        BusyOp.DELETE -> "Deleting…"
                        BusyOp.COPY -> "Copying…"
                        BusyOp.MOVE -> "Moving…"
                    },
                )
            }
        },
    )
}

/** 输入名称的弹窗；[onConfirm] 的第二个参数用于回传错误提示 */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    selectBase: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, error: (String) -> Unit) -> Unit,
) {
    // 重命名文件时默认选中扩展名之前的部分
    val end = if (selectBase) initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = {
        val e = FileStore.nameError(value.text.trim())
        if (e != null) error = e else onConfirm(value.text) { error = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = Danger) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text(confirm, color = Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Line,
    cursorColor = Primary,
)
