/**
 * [INPUT]: 依赖 ./FolderAccess（授权文件夹、路径 ↔ 文档 Uri），依赖 Android 的 DocumentsContract
 * [OUTPUT]: 对外提供 FileStore（浏览、删除、重命名、新建、复制移动、搜索、整体索引、存储用量）、FileEntry、FileIndex、StoreChange
 * [POS]: core/storage 的文件读写入口，所有基于文件夹授权（SAF）的品牌共用：只访问用户授权的文件夹，不需要存储权限。
 *        业务扫描（清理类别、重复文件等）不在这里，由各品牌基于 [index] 自己筛选，并通过 [addListener] 跟随文件变化
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 一个文件或文件夹（[path] 是真实路径，读写时经 FolderAccess 换成文档 Uri） */
class FileEntry(val path: String, val name: String, val mime: String?, val size: Long, val modified: Long) {
    val isDir get() = mime == Document.MIME_TYPE_DIR
    val hidden get() = name.startsWith(".")
    fun toFileItem() = FileItem(path, name, size, modified)
}

/** 一次遍历全部授权文件夹的结果：所有文件 + 可以删除的空文件夹 */
class FileIndex(val files: List<FileEntry>, val emptyDirs: List<FileItem>, val at: Long)

/** 文件结构的变化，供上层缓存跟随 */
sealed interface StoreChange {
    /** 有文件被删除（[paths] 为已删除的条目） */
    data class Deleted(val paths: Set<String>) : StoreChange

    /** 目录结构变了（新建、重命名、复制移动、删除文件夹、授权变化），依赖遍历结果的缓存应全部作废 */
    data object Structure : StoreChange
}

class FileStore(context: Context) {

    val access = FolderAccess(context)
    private val resolver = context.contentResolver

    private val listeners = CopyOnWriteArrayList<(StoreChange) -> Unit>()

    private val indexLock = Mutex()
    @Volatile private var index: FileIndex? = null

    /** 监听文件变化；回调在发生变化的线程上同步执行 */
    fun addListener(listener: (StoreChange) -> Unit) {
        listeners += listener
    }

    private fun notify(change: StoreChange) = listeners.forEach { it(change) }

    /** 整机存储用量；外部存储模拟在 /data 上，读 /data 不需要任何权限 */
    fun storageInfo(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(total = stat.totalBytes, free = stat.availableBytes)
    }

    // ---------------------------------------------------------------- 文件夹授权

    fun addFolder(treeUri: Uri): Boolean = access.add(treeUri).also { if (it) afterChange() }

    fun removeFolder(grant: FolderAccess.Grant) {
        access.remove(grant)
        afterChange()
    }

    // ---------------------------------------------------------------- 浏览

    /**
     * 列出目录内容（不含隐藏项），返回 null 表示无法读取。
     * [ROOT] 是文件管理首页：整个内部存储已授权时列根目录，否则列出用户授权的各个文件夹
     */
    suspend fun listDir(path: String): List<FileItem>? = withContext(Dispatchers.IO) {
        if (path == ROOT) {
            val roots = access.roots().map { it.path }
            val top = roots.flatMap { root ->
                if (root == access.primaryRoot) children(root).orEmpty().filterNot { it.hidden }.map { it.toItem() } else listOf(dirItem(root))
            }
            return@withContext top.sortedWith(ORDER)
        }
        children(path)?.filterNot { it.hidden }?.map { it.toItem() }?.sortedWith(ORDER)
    }

    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { stat(path) != null }

    /** 删除文件（目录只删空目录），返回释放的字节数 */
    suspend fun delete(items: Collection<FileItem>): Long = withContext(Dispatchers.IO) {
        val deleted = items.filter { deleteDoc(it.path) }
        if (deleted.isNotEmpty()) {
            index = null
            notify(StoreChange.Deleted(deleted.mapTo(HashSet()) { it.path }))
        }
        deleted.sumOf { it.size }
    }

    // ---------------------------------------------------------------- 文件管理
    // 以下操作改动了文件结构：完成后作废索引并通知 Structure。媒体库由系统的存储 Provider 自行更新

    /** 删除文件或整个文件夹（含其中所有内容），返回成功删除的条目数 */
    suspend fun deleteTree(paths: Collection<String>): Int = withContext(Dispatchers.IO) {
        val deleted = paths.filter { deleteDoc(it) }
        if (deleted.isNotEmpty()) notify(StoreChange.Deleted(deleted.toSet()))
        afterChange()
        deleted.size
    }

    /** 重命名，成功返回新路径 */
    suspend fun rename(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        val name = newName.trim()
        val error = nameError(name)
        val target = parentOf(path) + "/" + name
        val uri = access.uriOf(path)
        when {
            error != null -> Result.failure(IllegalArgumentException(error))
            name == nameOf(path) -> Result.success(path)
            stat(target) != null -> Result.failure(IllegalArgumentException("A file with this name already exists"))
            uri == null -> Result.failure(IllegalStateException("Couldn't rename"))
            else -> {
                val renamed = runCatching { DocumentsContract.renameDocument(resolver, uri, name) }.getOrNull()
                if (renamed != null) {
                    afterChange()
                    Result.success(access.pathOf(DocumentsContract.getDocumentId(renamed)) ?: target)
                } else {
                    Result.failure(IllegalStateException("Couldn't rename"))
                }
            }
        }
    }

    suspend fun createFolder(parent: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        val error = nameError(trimmed)
        val parentUri = access.uriOf(parent)
        when {
            error != null -> Result.failure(IllegalArgumentException(error))
            stat("$parent/$trimmed") != null -> Result.failure(IllegalArgumentException("A folder with this name already exists"))
            parentUri == null -> Result.failure(IllegalStateException("Couldn't create folder"))
            else -> createDoc(parentUri, Document.MIME_TYPE_DIR, trimmed)
                ?.let { Result.success(access.pathOf(DocumentsContract.getDocumentId(it)) ?: "$parent/$trimmed") }
                .also { afterChange() }
                ?: Result.failure(IllegalStateException("Couldn't create folder"))
        }
    }

    /**
     * 复制或移动到 [dest]，重名时自动改名为 "name (1)"。返回成功的条目数；
     * 不能把文件夹放进它自己或它的子文件夹。移动优先由系统直接改路径，不行时复制后删除源文件。
     */
    suspend fun transfer(paths: Collection<String>, dest: String, move: Boolean): Int = withContext(Dispatchers.IO) {
        var ok = 0
        for (path in paths) {
            currentCoroutineContext().ensureActive()
            val src = stat(path) ?: continue
            if (StoragePaths.covers(path, dest)) continue
            val parent = parentOf(path)
            if (move && parent == dest) {
                ok++
                continue
            }
            val name = uniqueName(dest, src.name, src.isDir)
            val done = if (move) {
                (name == src.name && moveDoc(path, parent, dest)) || (copyTree(src, dest, name) && deleteDoc(path))
            } else {
                copyTree(src, dest, name)
            }
            if (done) ok++
        }
        afterChange()
        ok
    }

    /** 在 [dir]（[ROOT] 为全部授权文件夹）及其子文件夹里按文件名搜索（不区分大小写，跳过隐藏项） */
    suspend fun search(dir: String, query: String, limit: Int = SEARCH_LIMIT): List<FileItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val out = ArrayList<FileItem>()
        val starts = if (dir == ROOT) access.roots().map { it.path } else listOf(dir)
        for (start in starts) {
            walk(start, enterHidden = false) { _, kids ->
                for (d in kids) {
                    if (out.size >= limit) return@walk false
                    if (!d.hidden && d.name.contains(q, ignoreCase = true)) out += d.toItem()
                }
                true
            }
            if (out.size >= limit) break
        }
        out
    }

    private fun afterChange() {
        index = null
        notify(StoreChange.Structure)
    }

    // ---------------------------------------------------------------- 整体索引与读取（供上层扫描）

    /** 全部授权文件夹的索引；[INDEX_STALE_MS] 内复用，文件变化后重建 */
    suspend fun index(): FileIndex = indexLock.withLock {
        index?.takeIf { SystemClock.elapsedRealtime() - it.at < INDEX_STALE_MS } ?: buildIndex().also { index = it }
    }

    /** 下次 [index] 时重新遍历 */
    fun invalidateIndex() {
        index = null
    }

    fun openFileDescriptor(path: String): ParcelFileDescriptor? = access.uriOf(path)?.let { resolver.openFileDescriptor(it, "r") }

    fun openInputStream(path: String): InputStream? = access.uriOf(path)?.let { resolver.openInputStream(it) }

    private suspend fun buildIndex(): FileIndex {
        val files = ArrayList<FileEntry>()
        val emptyDirs = ArrayList<FileItem>()
        for (root in access.roots().map { it.path }) {
            walk(root, enterHidden = true) { dir, kids ->
                kids.filterTo(files) { !it.isDir }
                // 授权的文件夹本身、内部存储根下的 DCIM 等系统标准目录，即使为空也不能删
                if (kids.isEmpty() && dir != root && parentOf(dir) != access.primaryRoot && !isHiddenPath(dir)) {
                    emptyDirs += FileItem(dir, nameOf(dir), 0, 0, isDir = true)
                }
                true
            }
        }
        return FileIndex(files, emptyDirs, SystemClock.elapsedRealtime())
    }

    // ---------------------------------------------------------------- 文档操作（DocumentsContract）

    private fun children(dir: String): List<FileEntry>? {
        val tree = access.treeFor(dir) ?: return null
        val id = access.docIdOf(dir) ?: return null
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        return runCatching { resolver.query(uri, PROJECTION, null, null, null)?.use { c -> buildList { while (c.moveToNext()) c.toEntry()?.let { add(it) } } } }
            .onFailure { Log.w(TAG, "list $dir failed", it) }
            .getOrNull()
    }

    private fun stat(path: String): FileEntry? {
        val uri = access.uriOf(path) ?: return null
        return runCatching { resolver.query(uri, PROJECTION, null, null, null)?.use { c -> if (c.moveToFirst()) c.toEntry() else null } }.getOrNull()
    }

    private fun android.database.Cursor.toEntry(): FileEntry? {
        val path = getString(0)?.let(access::pathOf) ?: return null
        return FileEntry(path, getString(1) ?: nameOf(path), getString(2), if (isNull(3)) 0 else getLong(3), if (isNull(4)) 0 else getLong(4))
    }

    /** 文件夹带上子项数（不计隐藏项；-1 表示无法读取） */
    private fun FileEntry.toItem(): FileItem =
        if (isDir) dirItem(path, name, modified) else toFileItem()

    private fun dirItem(path: String, name: String = nameOf(path), modified: Long = 0): FileItem {
        val count = children(path)?.count { !it.hidden } ?: -1
        return FileItem(path, name, 0, modified, isDir = true, childCount = count)
    }

    private fun createDoc(parent: Uri, mime: String, name: String): Uri? =
        runCatching { DocumentsContract.createDocument(resolver, parent, mime, name) }.getOrNull()

    /** 文件夹连同内容一起删除 */
    private fun deleteDoc(path: String): Boolean {
        val uri = access.uriOf(path) ?: return false
        return runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
    }

    private fun moveDoc(path: String, parent: String, dest: String): Boolean {
        val src = access.uriOf(path) ?: return false
        val from = access.uriOf(parent) ?: return false
        val to = access.uriOf(dest) ?: return false
        return runCatching { DocumentsContract.moveDocument(resolver, src, from, to) }.getOrNull() != null
    }

    /** 把 [src] 复制到 [dest] 下并命名为 [name]；失败时删掉复制了一半的目标 */
    private suspend fun copyTree(src: FileEntry, dest: String, name: String): Boolean {
        val ok = runCatching { copyInto(src, dest, name) }.getOrDefault(false)
        if (!ok) deleteDoc("$dest/$name")
        return ok
    }

    private suspend fun copyInto(src: FileEntry, dest: String, name: String): Boolean {
        currentCoroutineContext().ensureActive()
        val destUri = access.uriOf(dest) ?: return false
        if (src.isDir) {
            val created = createDoc(destUri, Document.MIME_TYPE_DIR, name) ?: return false
            val path = access.pathOf(DocumentsContract.getDocumentId(created)) ?: return false
            return children(src.path)?.all { copyInto(it, path, it.name) } ?: false
        }
        val from = access.uriOf(src.path) ?: return false
        // octet-stream：系统不会按类型改动文件名或补扩展名
        val created = createDoc(destUri, "application/octet-stream", name) ?: return false
        resolver.openInputStream(from)!!.use { input ->
            resolver.openOutputStream(created, "w")!!.use { output -> input.copyTo(output, 256 * 1024) }
        }
        return true
    }

    private fun uniqueName(dest: String, name: String, isDir: Boolean): String {
        val taken = children(dest).orEmpty().mapTo(HashSet()) { it.name }
        if (name !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 && !isDir }
        val base = if (dot != null) name.substring(0, dot) else name
        val ext = if (dot != null) name.substring(dot) else ""
        var i = 1
        var candidate: String
        do candidate = "$base (${i++})$ext" while (candidate in taken)
        return candidate
    }

    /**
     * 深度优先遍历 [start] 下的目录（最多 [MAX_DEPTH] 层，跳过内部存储根下的 Android 目录）。
     * [onDir] 收到每个目录及其子项，返回 false 时停止遍历
     */
    private suspend fun walk(start: String, enterHidden: Boolean, onDir: (dir: String, kids: List<FileEntry>) -> Boolean) {
        val androidDir = access.primaryRoot + "/Android"
        val stack = ArrayDeque<Pair<String, Int>>()
        stack.addLast(start to 0)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (dir, depth) = stack.removeLast()
            val kids = children(dir) ?: continue
            if (!onDir(dir, kids)) return
            if (depth >= MAX_DEPTH) continue
            for (k in kids) {
                if (k.isDir && k.path != androidDir && (enterHidden || !k.hidden)) stack.addLast(k.path to depth + 1)
            }
        }
    }

    companion object {
        private const val TAG = "FileStore"

        /** 文件管理首页（授权文件夹列表）的路径 */
        const val ROOT = ""

        /** 索引复用时长：同一轮操作里各页面共用一次遍历 */
        const val INDEX_STALE_MS = 30_000L

        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        private val ORDER = compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() }
        private const val MAX_DEPTH = 12
        private const val SEARCH_LIMIT = 300

        fun nameOf(path: String): String = path.substringAfterLast('/')
        fun parentOf(path: String): String = path.substringBeforeLast('/', "")

        /** 路径里有以 . 开头的目录或文件名 */
        fun isHiddenPath(path: String): Boolean = "/." in path

        /** 文件名校验，合法时返回 null */
        fun nameError(name: String): String? = when {
            name.isEmpty() -> "Name can't be empty"
            name == "." || name == ".." -> "Invalid name"
            name.any { it == '/' || it == '\u0000' } -> "Name can't contain /"
            name.toByteArray().size > 255 -> "Name is too long"
            else -> null
        }
    }
}
