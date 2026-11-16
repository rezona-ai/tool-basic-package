/**
 * [INPUT]: 依赖 :features:cleaner 的 FileKind / GroupLabels / ReminderReason，依赖 :core:common 的 formatSize
 * [OUTPUT]: 对外提供 Copy（类别名与简介、冗余分组名、重复组标题、提醒文案）
 * [POS]: Tidy Grove 的功能文案真相源：功能层不带任何文案，界面上的类别名、分组名、通知都从这里来。
 *        语气是“打理一片小树林”：类别名用园艺比喻，副标题用直白的话说清楚到底是什么文件
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.data

import com.appbase.core.common.formatSize
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.GroupLabels
import com.appbase.feature.cleaner.data.ReminderReason

object Copy {

    /** 类别名（列表标题、按钮） */
    fun title(kind: FileKind): String = when (kind) {
        FileKind.REDUNDANT -> "Weeds"
        FileKind.DUPLICATE -> "Doubles"
        FileKind.LARGE -> "Big branches"
        FileKind.RECENT -> "Fresh growth"
        FileKind.IMAGES -> "Photos"
        FileKind.VIDEOS -> "Movies"
        FileKind.AUDIO -> "Audio"
        FileKind.DOCS -> "Documents"
        FileKind.ZIPS -> "Compressed"
    }

    /** 类别一句话说明（卡片副标题）：比喻之外用直白的话说清楚 */
    fun blurb(kind: FileKind): String = when (kind) {
        FileKind.REDUNDANT -> "Leftover installers, scratch logs and preview caches"
        FileKind.DUPLICATE -> "The same file saved twice or more, first copy kept"
        FileKind.LARGE -> "Anything over 50 MB, largest at the top"
        FileKind.RECENT -> "Added during the past 7 days"
        FileKind.IMAGES -> "Camera shots, screenshots and saved pictures"
        FileKind.VIDEOS -> "Clips you recorded or downloaded"
        FileKind.AUDIO -> "Songs, voice memos and recordings"
        FileKind.DOCS -> "PDFs, spreadsheets, slides and notes"
        FileKind.ZIPS -> "Zip, rar, 7z and other packed files"
    }

    val groups = object : GroupLabels {
        override val apks = "App installers (.apk)"
        override val temps = "Scratch and log files"
        override val thumbnails = "Preview thumbnails"
        override val emptyFolders = "Folders with nothing inside"
        override fun duplicates(copies: Int, eachSize: Long) = "$copies matching copies · ${formatSize(eachSize)} apiece"
    }

    /** 通知渠道名（系统设置里可见）和设置页提醒开关 */
    const val reminderChannel = "Grove check-ins"
    const val reminderTitle = "Check-in reminders"
    const val reminderSubtitle = "A nudge when space gets tight or weeds start piling up"

    /** 提醒通知的标题和正文 */
    fun reminder(reason: ReminderReason): Pair<String, String> = when (reason) {
        is ReminderReason.LowStorage ->
            "Your phone is getting crowded" to "Only ${reason.freePercent}% (${formatSize(reason.freeBytes)}) left. A quick sweep can make room."
        is ReminderReason.Junk ->
            "${formatSize(reason.bytes)} of weeds to pull" to "Old installers, scratch files and preview caches have piled up."
        is ReminderReason.Stale ->
            "Time for a check-in" to "It's been ${reason.days} days since your last sweep. Take a look?"
        is ReminderReason.Test ->
            "Test check-in" to "Weeds ${formatSize(reason.junkBytes)} · ${reason.freePercent}% free · last sweep ${reason.days} days ago"
    }
}
