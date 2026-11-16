/**
 * [INPUT]: 依赖 core/storage 的 FileStore，依赖 ./data 的 ScanRepository / SystemInfoRepository / CleanHistory
 * [OUTPUT]: 对外提供 FileKind、GroupLabels、Cleaner（init、scans、system）
 * [POS]: features/cleaner 的入口：品牌应用在 Application.onCreate 里 CoreServices.init 之后调用 Cleaner.init，
 *        之后各页面的 ViewModel 从这里取仓库。模块里不含任何品牌文案，界面上看到的类别名、分组名都由品牌传入
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner

import android.content.Context
import com.appbase.core.storage.FileStore
import com.appbase.core.storage.StoreChange
import com.appbase.feature.cleaner.data.CleanHistory
import com.appbase.feature.cleaner.data.ScanRepository
import com.appbase.feature.cleaner.data.SystemInfoRepository

/** 扫描类别：前三个是可清理的（冗余、重复、大文件），其余用于按类型浏览。界面文案由品牌按 kind 自己定 */
enum class FileKind {
    REDUNDANT, DUPLICATE, LARGE, RECENT, IMAGES, VIDEOS, AUDIO, DOCS, ZIPS;

    companion object {
        /** 一键扫描覆盖的类别 */
        val CLEANABLE = listOf(REDUNDANT, DUPLICATE, LARGE)

        /** 按类型浏览的类别 */
        val BROWSABLE = listOf(RECENT, IMAGES, VIDEOS, AUDIO, DOCS, ZIPS)
    }
}

/** 冗余文件的分组名和重复文件组的标题（会显示在列表分组头上，属于品牌文案） */
interface GroupLabels {
    val apks: String
    val temps: String
    val thumbnails: String
    val emptyFolders: String

    /** 一组重复文件的标题，例如 "3 copies · 2.1 MB each" */
    fun duplicates(copies: Int, eachSize: Long): String
}

object Cleaner {
    lateinit var scans: ScanRepository
        private set
    lateinit var system: SystemInfoRepository
        private set

    val isReady: Boolean get() = ::scans.isInitialized

    /** CoreServices.init 之后调用；删除文件时记一次清理（提醒规则据此判断“很久没清理”） */
    fun init(context: Context, store: FileStore, labels: GroupLabels) {
        val app = context.applicationContext
        scans = ScanRepository(store, labels)
        system = SystemInfoRepository(app)
        store.addListener { if (it is StoreChange.Deleted) CleanHistory.markCleaned(app) }
    }
}
