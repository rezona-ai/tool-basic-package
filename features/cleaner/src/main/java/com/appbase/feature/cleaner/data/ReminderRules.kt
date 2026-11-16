/**
 * [INPUT]: 依赖 core/common 的 Reminders（开关、权限、上次提醒时间），依赖 ../Cleaner 的 scans、./CleanHistory
 * [OUTPUT]: 对外提供 ReminderReason、ReminderRules（decide 纯函数可单测；check 读取当前状态）
 * [POS]: features/cleaner 的提醒规则：按优先级挑一条（存储快满 > 垃圾较多 > 很久没清理），都不满足就不打扰。
 *        通知文案由品牌的 ReminderWorker 按 ReminderReason 自己写
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner.data

import android.content.Context
import com.appbase.core.common.Reminders
import com.appbase.feature.cleaner.Cleaner
import java.util.concurrent.TimeUnit

sealed interface ReminderReason {
    data class LowStorage(val freePercent: Int, val freeBytes: Long) : ReminderReason
    data class Junk(val bytes: Long) : ReminderReason
    data class Stale(val days: Long) : ReminderReason

    /** 设置页“测试提醒”：无论条件都发一条，带上当前数值 */
    data class Test(val junkBytes: Long, val freePercent: Int, val days: Long) : ReminderReason
}

object ReminderRules {
    const val LOW_STORAGE_PERCENT = 10
    const val JUNK_THRESHOLD = 50L * 1024 * 1024
    const val STALE_DAYS = 2L
    val MIN_INTERVAL_MS = TimeUnit.HOURS.toMillis(5)

    /** 纯规则 */
    fun decide(freePercent: Int, freeBytes: Long, junkBytes: Long, daysSinceClean: Long, force: Boolean): ReminderReason? = when {
        freePercent < LOW_STORAGE_PERCENT -> ReminderReason.LowStorage(freePercent, freeBytes)
        junkBytes >= JUNK_THRESHOLD -> ReminderReason.Junk(junkBytes)
        daysSinceClean >= STALE_DAYS -> ReminderReason.Stale(daysSinceClean)
        force -> ReminderReason.Test(junkBytes, freePercent, daysSinceClean)
        else -> null
    }

    /**
     * Worker 里调用：通知权限、开关、最小间隔都满足时才计算；[force] 为 true（设置页测试）时跳过开关和间隔。
     * 返回 null 表示这次不发
     */
    suspend fun check(context: Context, force: Boolean): ReminderReason? {
        if (!Reminders.notificationsAllowed(context)) return null
        if (!force) {
            if (!Reminders.isEnabled(context)) return null
            // 每天最多中午、傍晚各一条；周期任务可能因系统调度漂移，避免两条挨得太近
            if (System.currentTimeMillis() - Reminders.lastNotifiedAt(context) < MIN_INTERVAL_MS) return null
        }
        if (!Cleaner.isReady) return null
        val scans = Cleaner.scans
        val storage = scans.store.storageInfo()
        val freePercent = ((1 - storage.usedFraction) * 100).toInt()
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - CleanHistory.lastCleanAt(context))
        return decide(freePercent, storage.free, scans.redundantSize(), days, force)
    }
}
