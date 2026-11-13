/**
 * [INPUT]: 依赖 core/storage 的 FileStore（授权文件夹的索引、删除、变化通知），依赖 ./Models 的 FileKind
 * [OUTPUT]: 对外提供 ScanRepository（按 FileKind 扫描并缓存结果、删除后同步结果、后台提醒用的冗余文件大小）
 * [POS]: data 的 Orbit Clean 扫描层：各 FileKind 从 FileStore 的同一份索引里筛选，结果缓存在 [results]；
 *        文件被删除时从所有缓存里移除，目录结构变化时作废全部缓存，各页面直接订阅即可
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.data

import android.os.SystemClock
import android.util.Log
import com.appbase.core.common.formatSize
import com.appbase.core.storage.FileEntry
import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.FileIndex
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.FileStore
import com.appbase.core.storage.FileType
import com.appbase.core.storage.StoreChange
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanRepository(val store: FileStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 每个 kind 当前有效的扫描任务；只有它能写结果，被替换/取消的旧任务结果直接丢弃 */
    private val jobs = HashMap<FileKind, Job>()
    private val loadedAt = HashMap<FileKind, Long>()
    private val _results = MutableStateFlow<Map<FileKind, List<FileGroup>>>(emptyMap())

    /** 已完成扫描的结果；某个 kind 不在 map 里表示尚未扫描完成 */
    val results: StateFlow<Map<FileKind, List<FileGroup>>> = _results.asStateFlow()

    init {
        store.addListener { change ->
            when (change) {
                is StoreChange.Deleted -> prune(change.paths)
                StoreChange.Structure -> clearAll()
            }
        }
    }

    /** 尚无结果且没有进行中的扫描时开始扫描（进程被回收后恢复页面也靠它重新触发） */
    fun load(kind: FileKind) = synchronized(jobs) {
        if (kind !in _results.value && jobs[kind]?.isActive != true) start(kind)
    }

    /** 页面进入时调用：无结果则扫描；结果超过 [STALE_MS] 则后台刷新，刷新期间保留旧结果 */
    fun ensureFresh(kind: FileKind) = synchronized(jobs) {
        val age = loadedAt[kind]?.let { SystemClock.elapsedRealtime() - it }
        when {
            jobs[kind]?.isActive == true -> Unit
            kind !in _results.value -> start(kind)
            age != null && age > STALE_MS -> start(kind)
        }
    }

    /** 强制重新扫描；clear 为 true 时先清空旧结果，界面显示扫描中 */
    fun rescan(kind: FileKind, clear: Boolean = true) = synchronized(jobs) {
        if (clear) _results.update { it - kind }
        store.invalidateIndex()
        start(kind)
    }

    /** 删除文件，返回释放的字节数；结果缓存经 FileStore 的变化通知同步 */
    suspend fun delete(items: Collection<FileItem>): Long = store.delete(items)

    private fun start(kind: FileKind) {
        jobs[kind]?.cancel()
        jobs[kind] = scope.launch {
            val groups = try {
                scan(kind)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "scan $kind failed", e)
                emptyList()
            }
            val self = coroutineContext.job
            synchronized(jobs) {
                if (jobs[kind] === self) {
                    _results.update { it + (kind to groups) }
                    loadedAt[kind] = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /** 从所有缓存里移除已删除的条目；重复文件组只剩一个时整组移除 */
    private fun prune(deletedPaths: Set<String>) {
        _results.update { map ->
            map.mapValues { (kind, groups) ->
                groups.mapNotNull { g ->
                    val left = g.items.filterNot { it.path in deletedPaths }
                    when {
                        left.isEmpty() -> null
                        kind == FileKind.DUPLICATE && left.size < 2 -> null
                        else -> g.copy(items = left)
                    }
                }
            }
        }
    }

    private fun clearAll() = synchronized(jobs) {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        loadedAt.clear()
        _results.value = emptyMap()
    }

    // ---------------------------------------------------------------- 扫描

    /** 后台提醒用：扫一遍可清理垃圾的总大小，不写入结果缓存 */
    suspend fun redundantSize(): Long = withContext(Dispatchers.IO) {
        if (!store.access.hasAny()) 0L else redundant(store.index()).sumOf { it.totalSize }
    }

    private suspend fun scan(kind: FileKind): List<FileGroup> {
        val idx = store.index()
        // 分类只看普通文件：隐藏目录里的缓存、空文件只在冗余文件里出现
        val visible = idx.files.filter { it.size > 0 && !FileStore.isHiddenPath(it.path) }
        val byDate = compareByDescending<FileEntry> { it.modified }
        return when (kind) {
            FileKind.REDUNDANT -> redundant(idx)
            FileKind.LARGE -> single(visible.filter { it.size > LARGE_THRESHOLD }.sortedByDescending { it.size })
            FileKind.DUPLICATE -> duplicates(visible)
            FileKind.RECENT -> {
                val since = System.currentTimeMillis() - RECENT_DAYS * 24 * 3600 * 1000
                single(visible.filter { it.modified > since }.sortedWith(byDate).take(RECENT_LIMIT))
            }
            FileKind.IMAGES -> single(visible.filter { it.type() == FileType.IMAGE || it.mime?.startsWith("image/") == true }.sortedWith(byDate))
            FileKind.VIDEOS -> single(visible.filter { it.type() == FileType.VIDEO || it.mime?.startsWith("video/") == true }.sortedWith(byDate))
            FileKind.AUDIO -> single(visible.filter { it.type() == FileType.AUDIO || it.mime?.startsWith("audio/") == true }.sortedWith(byDate))
            FileKind.DOCS -> single(visible.filter { it.type() == FileType.DOC }.sortedWith(byDate))
            FileKind.ZIPS -> single(visible.filter { it.type() == FileType.ARCHIVE }.sortedWith(byDate))
        }
    }

    private fun FileEntry.type() = FileType.of(name)

    private fun single(docs: List<FileEntry>) =
        if (docs.isEmpty()) emptyList() else listOf(FileGroup(null, docs.map { it.toFileItem() }))

    /** 冗余文件：安装包、临时/日志文件、缩略图缓存和空文件夹 */
    private fun redundant(idx: FileIndex): List<FileGroup> {
        val apks = ArrayList<FileItem>()
        val temps = ArrayList<FileItem>()
        val thumbs = ArrayList<FileItem>()
        for (d in idx.files) {
            if (d.name == ".nomedia") continue
            val item = d.toFileItem()
            val ext = d.name.substringAfterLast('.', "").lowercase()
            when {
                ext == "apk" -> apks += item
                ext in TEMP_EXTS -> temps += item
                "/.thumbnails/" in d.path -> thumbs += item
            }
        }
        return listOf(
            FileGroup("APK installers", apks),
            FileGroup("Temp & log files", temps),
            FileGroup("Thumbnail cache", thumbs),
            FileGroup("Empty folders", idx.emptyDirs),
        ).filter { it.items.isNotEmpty() }
    }

    /**
     * 重复文件，三步筛选：
     * 1. 按大小分组；2. 首/中/尾采样哈希快速排除；3. 对仍然相同的文件做全量哈希确认。
     * 第 3 步保证只有内容完全一致才算重复，避免误删；只有少数候选文件需要完整读取。
     */
    private suspend fun duplicates(files: List<FileEntry>): List<FileGroup> {
        val groups = ArrayList<List<FileEntry>>()
        for (sameSize in files.filter { it.size > 1024 }.groupBy { it.size }.values) {
            if (sameSize.size < 2) continue
            currentCoroutineContext().ensureActive()
            val size = sameSize.first().size
            val sampled = sameSize.groupBy { sampleHash(it.path, size) }
            for ((key, docs) in sampled) {
                if (key == null || docs.size < 2) continue
                // 小文件的采样已覆盖全部内容，无需再算一遍
                val confirmed = if (size <= CHUNK * 3L) listOf(docs) else docs.groupBy { fullHash(it.path) }
                    .filterKeys { it != null }
                    .values
                confirmed.filterTo(groups) { it.size > 1 }
            }
        }
        return groups
            .sortedByDescending { group -> group.first().size * (group.size - 1) }
            .map { group ->
                // 最早的文件排第一，默认保留
                val items = group.sortedBy { it.modified }.map { it.toFileItem() }
                FileGroup("${items.size} copies · ${formatSize(items.first().size)} each", items)
            }
    }

    private fun sampleHash(path: String, size: Long): String? = runCatching {
        val md5 = MessageDigest.getInstance("MD5")
        store.openFileDescriptor(path)!!.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                val buf = ByteBuffer.allocate(CHUNK)
                val offsets = if (size <= CHUNK * 3L) listOf(0L) else listOf(0L, size / 2, size - CHUNK)
                for (offset in offsets) {
                    var position = offset
                    var remaining = if (offsets.size == 1) size else CHUNK.toLong()
                    while (remaining > 0) {
                        buf.clear()
                        buf.limit(minOf(remaining, CHUNK.toLong()).toInt())
                        val n = channel.read(buf, position)
                        if (n < 0) break
                        md5.update(buf.array(), 0, n)
                        position += n
                        remaining -= n
                    }
                }
            }
        }
        md5.digest().toHex()
    }.getOrNull()

    private fun fullHash(path: String): String? = runCatching {
        val md5 = MessageDigest.getInstance("MD5")
        store.openInputStream(path)!!.use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md5.update(buf, 0, n)
            }
        }
        md5.digest().toHex()
    }.getOrNull()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "ScanRepository"
        private const val LARGE_THRESHOLD = 50L * 1024 * 1024
        private const val RECENT_DAYS = 7L
        private const val RECENT_LIMIT = 500
        private const val CHUNK = 64 * 1024
        private const val STALE_MS = FileStore.INDEX_STALE_MS
        private val TEMP_EXTS = setOf("tmp", "temp", "log")
    }
}
