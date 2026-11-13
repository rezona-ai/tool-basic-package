/**
 * [INPUT]: 依赖 Android 的 SAF（DocumentsContract、持久化 Uri 授权）
 * [OUTPUT]: 对外提供 FolderAccess（已授权文件夹、路径 ↔ 文档 Uri 换算）、StoragePaths（纯字符串换算，可单测）
 * [POS]: data 的存储访问层：应用不申请任何存储权限，只读写用户在系统文件夹选择器里选中的文件夹；
 *        FileItem.path 仍是真实路径（标识、显示、导航都用它），读写时由这里换成文档 Uri
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FolderAccess(private val context: Context) {

    /** 一个已授权的文件夹：[path] 为真实路径，[treeUri] 为系统选择器返回的授权 */
    data class Grant(val path: String, val treeUri: Uri)

    /** 内部存储根目录，如 /storage/emulated/0 */
    val primaryRoot: String = Environment.getExternalStorageDirectory().path

    private val _grants = MutableStateFlow(load())
    val grants: StateFlow<List<Grant>> = _grants.asStateFlow()

    fun hasAny(): Boolean = _grants.value.isNotEmpty()

    fun refresh() {
        _grants.value = load()
    }

    /** 收下选择器返回的文件夹授权（重启后仍有效）；不是本机存储（如云盘）时返回 false */
    fun add(treeUri: Uri): Boolean {
        if (treeUri.authority != AUTHORITY || !DocumentsContract.isTreeUri(treeUri)) return false
        val ok = runCatching { context.contentResolver.takePersistableUriPermission(treeUri, RW) }.isSuccess
        refresh()
        return ok
    }

    fun remove(grant: Grant) {
        runCatching { context.contentResolver.releasePersistableUriPermission(grant.treeUri, RW) }
        refresh()
    }

    /** 顶层授权文件夹：被另一个授权包含的不再单独列出 */
    fun roots(): List<Grant> {
        val all = _grants.value
        return all.filter { g -> all.none { o -> o.path != g.path && StoragePaths.covers(o.path, g.path) } }
    }

    /** 整个内部存储都已授权（Android 10 及以下可以选根目录） */
    fun coversPrimaryRoot(): Boolean = _grants.value.any { it.path == primaryRoot }

    /** 路径对应的文档 Uri；不在任何授权文件夹里时返回 null */
    fun uriOf(path: String): Uri? {
        val tree = treeFor(path) ?: return null
        val id = docIdOf(path) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(tree, id)
    }

    /** 列子项用：目录对应的授权 Uri（取最外层授权，使整棵子树都可访问） */
    fun treeFor(path: String): Uri? =
        _grants.value.filter { StoragePaths.covers(it.path, path) }.minByOrNull { it.path.length }?.treeUri

    fun pathOf(docId: String): String? = StoragePaths.pathOf(docId, primaryRoot)

    fun docIdOf(path: String): String? = StoragePaths.docIdOf(path, primaryRoot)

    /** 给用户看的位置，如 "Internal storage/DCIM/Camera" */
    fun display(path: String): String = StoragePaths.display(path, primaryRoot)

    private fun load(): List<Grant> = context.contentResolver.persistedUriPermissions
        .filter { it.isReadPermission && it.isWritePermission && it.uri.authority == AUTHORITY && DocumentsContract.isTreeUri(it.uri) }
        .mapNotNull { p -> pathOf(DocumentsContract.getTreeDocumentId(p.uri))?.let { Grant(it, p.uri) } }
        .distinctBy { it.path }
        .sortedBy { it.path.lowercase() }

    companion object {
        /** 系统自带的本机存储 DocumentsProvider */
        const val AUTHORITY = "com.android.externalstorage.documents"
        private const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** 选择器初始位置：内部存储下的相对路径，空串为根目录 */
        fun initialUri(relative: String): Uri =
            DocumentsContract.buildDocumentUri(AUTHORITY, "${StoragePaths.PRIMARY}:$relative")
    }
}

/**
 * 本机存储的文档 ID 与路径互换：
 * "primary:DCIM/Camera" ↔ "<内部存储根>/DCIM/Camera"，"1A2B-3C4D:Music" ↔ "/storage/1A2B-3C4D/Music"
 */
object StoragePaths {
    const val PRIMARY = "primary"

    fun pathOf(docId: String, primaryRoot: String): String? {
        val colon = docId.indexOf(':')
        if (colon <= 0) return null
        val volume = docId.substring(0, colon)
        val relative = docId.substring(colon + 1).trim('/')
        val base = if (volume == PRIMARY) primaryRoot else "/storage/$volume"
        return if (relative.isEmpty()) base else "$base/$relative"
    }

    fun docIdOf(path: String, primaryRoot: String): String? {
        val p = path.trimEnd('/')
        if (covers(primaryRoot, p)) return "$PRIMARY:" + p.removePrefix(primaryRoot).removePrefix("/")
        val rest = p.removePrefix("/storage/").takeIf { it != p } ?: return null
        val volume = rest.substringBefore('/')
        if (volume.isEmpty() || volume == "emulated" || volume == "self") return null
        return "$volume:" + rest.substringAfter('/', "")
    }

    /** [path] 是 [parent] 本身或在它里面 */
    fun covers(parent: String, path: String): Boolean = path == parent || path.startsWith("$parent/")

    fun display(path: String, primaryRoot: String): String = when {
        covers(primaryRoot, path) -> "Internal storage" + path.removePrefix(primaryRoot)
        path.startsWith("/storage/") -> "SD card/" + path.removePrefix("/storage/").substringAfter('/', "")
        else -> path
    }.trimEnd('/')
}
