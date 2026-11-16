/**
 * [INPUT]: 依赖 :features:cleaner 的 FileKind / GroupLabels / ReminderReason，依赖 :core:common 的 formatSize
 * [OUTPUT]: 对外提供 Copy（类别名与简介、冗余分组名、重复组标题、提醒文案）
 * [POS]: {{APP_NAME}} 的功能文案真相源：功能层不带任何文案，界面上的类别名、分组名、通知都从这里来。
 *        每个复刻包都要按自己的语气重写这里的全部英文（skill 第 7 步），不能照抄其他包
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.data

import com.appbase.core.common.formatSize
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.GroupLabels
import com.appbase.feature.cleaner.data.ReminderReason

object Copy {

    /** 类别名（列表标题、按钮） */
    fun title(kind: FileKind): String = when (kind) {
        FileKind.REDUNDANT -> "Leftovers"
        FileKind.DUPLICATE -> "Twin files"
        FileKind.LARGE -> "Space hogs"
        FileKind.RECENT -> "New this week"
        FileKind.IMAGES -> "Pictures"
        FileKind.VIDEOS -> "Clips"
        FileKind.AUDIO -> "Sound"
        FileKind.DOCS -> "Documents"
        FileKind.ZIPS -> "Archives"
    }

    /** 类别一句话说明（卡片副标题） */
    fun blurb(kind: FileKind): String = when (kind) {
        FileKind.REDUNDANT -> "Old installers, temp files and cached thumbnails"
        FileKind.DUPLICATE -> "Identical files stored more than once"
        FileKind.LARGE -> "Files over 50 MB, biggest first"
        FileKind.RECENT -> "Anything added in the last 7 days"
        FileKind.IMAGES -> "Photos and pictures"
        FileKind.VIDEOS -> "Recorded and saved videos"
        FileKind.AUDIO -> "Music, voice notes and recordings"
        FileKind.DOCS -> "PDFs, sheets, slides and text"
        FileKind.ZIPS -> "Zip, rar, 7z and other archives"
    }

    val groups = object : GroupLabels {
        override val apks = "Installer packages"
        override val temps = "Temporary and log files"
        override val thumbnails = "Cached thumbnails"
        override val emptyFolders = "Empty folders"
        override fun duplicates(copies: Int, eachSize: Long) = "$copies identical · ${formatSize(eachSize)} each"
    }

    /** 通知渠道名（系统设置里可见）和设置页提醒开关 */
    const val reminderChannel = "Tidy-up reminders"
    const val reminderTitle = "Tidy-up reminders"
    const val reminderSubtitle = "Let me know when storage runs low or leftovers build up"

    /** 提醒通知的标题和正文 */
    fun reminder(reason: ReminderReason): Pair<String, String> = when (reason) {
        is ReminderReason.LowStorage ->
            "Running out of room" to "${reason.freePercent}% free (${formatSize(reason.freeBytes)}). Open to see what can go."
        is ReminderReason.Junk ->
            "${formatSize(reason.bytes)} of leftovers" to "Installers, temp files and cached thumbnails are ready to remove."
        is ReminderReason.Stale ->
            "Quick tidy-up?" to "Your last tidy-up was ${reason.days} days ago. Tap to check your folders."
        is ReminderReason.Test ->
            "Test reminder" to "Leftovers ${formatSize(reason.junkBytes)} · ${reason.freePercent}% free · ${reason.days} days since last tidy-up"
    }
}
