/**
 * [OUTPUT]: 对外提供 FileItem、FileGroup、FileType、StorageInfo
 * [POS]: core/storage 的文件模型，所有基于文件夹授权的品牌共用；业务分类（如清理类别）放在各品牌自己的代码里
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.storage

data class FileItem(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean = false,
    val childCount: Int = 0,
) {
    val type: FileType get() = if (isDir) FileType.FOLDER else FileType.of(name)
}

/** 列表分组；title 为 null 表示不显示分组头 */
data class FileGroup(val title: String?, val items: List<FileItem>) {
    val totalSize: Long get() = items.sumOf { it.size }
}

data class StorageInfo(val total: Long, val free: Long) {
    val used: Long get() = total - free
    val usedFraction: Float get() = if (total == 0L) 0f else used.toFloat() / total
}

enum class FileType {
    FOLDER, IMAGE, VIDEO, AUDIO, DOC, ARCHIVE, APK, OTHER;

    companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
        val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v")
        val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr")
        val DOC_EXTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub")
        val ARCHIVE_EXTS = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

        fun of(name: String): FileType = when (name.substringAfterLast('.', "").lowercase()) {
            in IMAGE_EXTS -> IMAGE
            in VIDEO_EXTS -> VIDEO
            in AUDIO_EXTS -> AUDIO
            in DOC_EXTS -> DOC
            in ARCHIVE_EXTS -> ARCHIVE
            "apk" -> APK
            else -> OTHER
        }
    }
}
