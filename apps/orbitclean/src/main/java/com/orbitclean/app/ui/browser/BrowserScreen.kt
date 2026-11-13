/**
 * [INPUT]: 依赖 core/storage 的 FileStore（CoreServices.files）的 listDir/deleteTree/rename/createFolder/transfer/search，依赖 core/ui 的 FileRow/TopBar/AppCard，
 *          依赖 core/storage 的 openFile/shareFiles
 * [OUTPUT]: 对外提供 BrowserScreen、BrowserViewModel、FileClipboard
 * [POS]: ui/browser 的文件管理器（应用启动页）：首页顶部是 FilesOverview，下面是用户授权的文件夹 + "Add a folder"；逐级浏览，打开文件；长按进入多选，可复制/移动/重命名/分享/删除；
 *        新建文件夹、在当前目录下按文件名搜索。复制/移动先放入 FileClipboard，进入目标目录后粘贴
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.browser

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.CoreServices
import com.appbase.core.common.formatDate
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.FileStore
import com.appbase.core.storage.files
import com.appbase.core.storage.openFile
import com.appbase.core.storage.shareFiles
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.EmptyState
import com.appbase.core.ui.FileRow
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.LoadingState
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.theme.SheetBg
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

/** [dir] 为 [FileStore.ROOT] 时是文件管理首页 */
class BrowserViewModel(private val repo: FileStore, val dir: String) : ViewModel() {

    var entries by mutableStateOf<List<FileItem>?>(null)
        private set
    var denied by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 非空时显示进度弹窗（复制、移动、删除中） */
    var busy by mutableStateOf<String?>(null)
        private set

    /** 一次性提示，界面显示后调用 [consumeMessage] */
    var message by mutableStateOf<String?>(null)
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

    fun delete() = runOp("Deleting…") {
        val targets = selected.toList()
        val n = repo.deleteTree(targets)
        selected = emptySet()
        if (n == targets.size) "Deleted ${plural(n, "item")}" else "Deleted $n of ${targets.size} items"
    }

    /** 放入剪贴板，等用户到目标目录粘贴 */
    fun clip(move: Boolean) {
        FileClipboard.put(selected.toList(), move)
        selected = emptySet()
        message = "Open the destination folder, then tap Paste here"
    }

    fun paste() {
        val paths = FileClipboard.paths
        val move = FileClipboard.move
        runOp(if (move) "Moving…" else "Copying…") {
            val n = repo.transfer(paths, dir, move)
            FileClipboard.clear()
            val verb = if (move) "Moved" else "Copied"
            if (n == paths.size) "$verb ${plural(n, "item")}" else "$verb $n of ${paths.size} items"
        }
    }

    /** [done] 收到 null 表示成功，否则是错误提示（弹窗保持打开） */
    fun rename(item: FileItem, name: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            repo.rename(item.path, name)
                .onSuccess {
                    done(null)
                    selected = emptySet()
                    message = "Renamed"
                    reload()
                }
                .onFailure { done(it.message ?: "Couldn't rename") }
        }
    }

    fun createFolder(name: String, done: (String?) -> Unit) {
        viewModelScope.launch {
            repo.createFolder(dir, name)
                .onSuccess {
                    done(null)
                    reload()
                }
                .onFailure { done(it.message ?: "Couldn't create folder") }
        }
    }

    private fun runOp(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            busy = label
            message = try {
                block()
            } finally {
                busy = null
            }
            reload()
        }
    }
}

private sealed interface BrowserDialog {
    data object Delete : BrowserDialog
    data object NewFolder : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
}

/**
 * @param path 为空（[FileStore.ROOT]）时是文件管理首页：列出用户授权的文件夹（整个内部存储已授权时为根目录）
 * @param onBack 为 null 时不显示返回按钮（底部导航里的文件页）
 * @param onCategory 非空时在根目录顶部显示文件管理首页头部 [FilesOverview]（存储卡、按类型浏览、清理入口）
 * @param onManageFolders 首页底部 "Add a folder"：打开文件夹授权页
 */
@Composable
fun BrowserScreen(
    path: String,
    onBack: (() -> Unit)?,
    onOpenDir: (String) -> Unit,
    onCategory: ((FileKind) -> Unit)? = null,
    onCleanup: () -> Unit = {},
    onManageFolders: () -> Unit = {},
) {
    val repo = CoreServices.files
    val access = repo.access
    val context = LocalContext.current
    val vm: BrowserViewModel = viewModel(key = "browser:$path") { BrowserViewModel(repo, path) }
    val dir = vm.dir
    val isRoot = dir == FileStore.ROOT
    val grants by access.grants.collectAsState()
    // 首页列的是用户授权的各个文件夹（不是真实目录）：不能在这里新建、粘贴，也不能选中授权文件夹本身去改名/删除
    val folderList = isRoot && grants.none { it.path == access.primaryRoot }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }

    // 每次回到本页都重新读取：子目录里移动/删除过文件，返回时立即反映
    LifecycleResumeEffect(vm) {
        vm.reload()
        onPauseOrDispose { }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
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
                TextButton(onClick = { vm.setAll(!all) }) { Text(if (all) "Clear" else "Select all", color = Primary) }
            }
            searching -> SearchBar(vm.query.orEmpty(), onQuery = vm::search, onClose = vm::closeSearch)
            else -> TopBar(if (isRoot) "Files" else FileStore.nameOf(dir), onBack = onBack) {
                IconButton(onClick = vm::openSearch) { Icon(Icons.Rounded.Search, "Search") }
                if (!folderList) {
                    IconButton(onClick = { dialog = BrowserDialog.NewFolder }) { Icon(Icons.Rounded.CreateNewFolder, "New folder") }
                }
            }
        }
        // 文件管理首页：根目录顶部是存储卡 + 按类型浏览 + 清理入口，替代路径行。
        // 多选时也保留：中途移除再加回会让列表停在文件夹处，头部被滚出视野
        val overview = isRoot && onCategory != null && !searching
        val location = when {
            folderList -> "Your folders"
            isRoot -> "Internal storage"
            else -> access.display(dir)
        }
        if (!overview) {
            Text(
                if (searching) "Search in $location" else location,
                color = Primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (isRoot && !overview && !searching) {
            val storage = remember { repo.storageInfo() }
            Text(
                "${formatSize(storage.used)} used of ${formatSize(storage.total)} · ${formatSize(storage.free)} free",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            when {
                list == null -> LoadingState(if (searching) "Searching…" else "Loading…")
                searching && vm.query.isNullOrBlank() -> EmptyState("Type a file or folder name")
                searching && list.isEmpty() -> EmptyState("No matches")
                vm.denied -> EmptyState("Can't open this folder")
                list.isEmpty() && !folderList -> EmptyState("Empty folder")
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (overview) {
                        item(key = "overview") { FilesOverview(onCategory = onCategory!!, onCleanup = onCleanup) }
                    }
                    items(list, key = { it.path }) { item ->
                        val checked = item.path in vm.selected
                        FileRow(
                            item = item,
                            subtitle = when {
                                searching -> access.display(FileStore.parentOf(item.path))
                                !item.isDir -> "${formatSize(item.size)} · ${formatDate(item.modified)}"
                                item.childCount < 0 -> "Protected"
                                else -> plural(item.childCount, "item")
                            },
                            onClick = {
                                when {
                                    selecting -> vm.toggle(item.path)
                                    item.isDir -> onOpenDir(item.path)
                                    else -> openFile(context, item.path)
                                }
                            },
                            onLongClick = if (folderList && !searching) null else ({ vm.toggle(item.path) }),
                        ) {
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
                    if (folderList && !searching && !selecting) {
                        item(key = "add-folder") { AddFolderRow(onManageFolders) }
                    }
                    if (!selecting) {
                        item { Text("Tip: long-press to select, then copy, move, rename, share or delete", color = TextSecondary, fontSize = 12.sp) }
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
                title = { Text("Delete ${plural(items.size, "item")}?") },
                text = {
                    Text(
                        if (items.any { it.isDir }) {
                            "Folders are deleted together with everything inside them. This cannot be undone."
                        } else {
                            "${formatSize(items.sumOf { it.size })} will be permanently deleted. This cannot be undone."
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
            onConfirm = { name, error -> vm.createFolder(name) { e -> if (e == null) dialog = null else error(e) } },
        )
        is BrowserDialog.Rename -> NameDialog(
            title = "Rename",
            initial = d.item.name,
            confirm = "Rename",
            selectBase = !d.item.isDir,
            onDismiss = { dialog = null },
            onConfirm = { name, error -> vm.rename(d.item, name) { e -> if (e == null) dialog = null else error(e) } },
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
                Text("Add a folder", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Choose more folders to manage", color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text("Search files and folders", color = TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                }
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
    Row(
        Modifier
            .fillMaxWidth()
            .background(SheetBg)
            .drawBehind { drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(vertical = 6.dp),
    ) {
        BarAction(Icons.Rounded.ContentCopy, "Copy", true, onCopy)
        BarAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Move", true, onMove)
        BarAction(Icons.Rounded.DriveFileRenameOutline, "Rename", canRename, onRename)
        BarAction(Icons.Rounded.Share, "Share", canShare, onShare)
        BarAction(Icons.Rounded.Delete, "Delete", true, onDelete, tint = Danger)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Column(
        Modifier
            .weight(1f)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick)
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
        Modifier
            .fillMaxWidth()
            .background(SheetBg)
            .drawBehind { drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${if (FileClipboard.move) "Move" else "Copy"} ${plural(count, "item")}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Open a folder and paste here", color = TextSecondary, fontSize = 12.sp)
        }
        TextButton(onClick = onCancel) { Text("Cancel", color = TextSecondary) }
        Spacer(Modifier.width(4.dp))
        GradientButton("Paste here", Modifier.width(128.dp), height = 40.dp, onClick = onPaste)
    }
}

@Composable
private fun BusyDialog(label: String) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(label)
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
