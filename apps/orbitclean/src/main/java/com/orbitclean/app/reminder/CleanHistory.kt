/**
 * [INPUT]: 依赖 core/common 的 Reminders.prefs（与提醒开关共用一个 SharedPreferences 文件，沿用旧键名）
 * [OUTPUT]: 对外提供 CleanHistory（markCleaned / lastCleanAt）
 * [POS]: reminder 的清理记录：删除文件后记下时间，ReminderWorker 据此判断"很久没清理"
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.reminder

import android.content.Context
import androidx.core.content.edit
import com.appbase.core.common.Reminders

object CleanHistory {
    private const val KEY_LAST_CLEAN = "last_clean_at"

    fun markCleaned(context: Context) = Reminders.prefs(context).edit { putLong(KEY_LAST_CLEAN, System.currentTimeMillis()) }

    /** 从未清理过时按安装时间算，避免刚装好就提醒“很久没清理” */
    fun lastCleanAt(context: Context): Long = Reminders.prefs(context).getLong(KEY_LAST_CLEAN, 0).takeIf { it > 0 }
        ?: context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
}
