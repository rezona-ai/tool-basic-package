package com.orbitclean.app.data

/** Orbit Clean 的扫描类别：清理三类（冗余/大文件/重复）+ 按类型浏览 */
enum class FileKind(val title: String, val subtitle: String) {
    REDUNDANT("Redundant Files", "Remove leftover and unused files"),
    LARGE("Large Files", "Find and manage big files"),
    DUPLICATE("Duplicate Files", "Detect and delete copies"),
    RECENT("Recent Files", "Files added in the last 7 days"),
    IMAGES("Images", ""),
    VIDEOS("Videos", ""),
    AUDIO("Audios", ""),
    DOCS("Docs", ""),
    ZIPS("Zips", ""),
}
