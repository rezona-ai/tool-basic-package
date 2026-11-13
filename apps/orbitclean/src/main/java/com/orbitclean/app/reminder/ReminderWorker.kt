package com.orbitclean.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.appbase.core.common.Reminders
import com.appbase.core.common.formatSize
import com.orbitclean.app.App
import java.util.concurrent.TimeUnit

/** 按优先级挑一条提醒：存储快满 > 垃圾较多 > 很久没清理；都不满足则不打扰 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val force = inputData.getBoolean(Reminders.KEY_FORCE, false)
        if (!Reminders.notificationsAllowed(context)) return Result.success()
        if (!force) {
            if (!Reminders.isEnabled(context)) return Result.success()
            // 每天最多中午、傍晚各一条；周期任务可能因系统调度漂移，避免两条挨得太近
            if (System.currentTimeMillis() - Reminders.lastNotifiedAt(context) < MIN_INTERVAL_MS) return Result.success()
        }

        val scans = App.instance.scans
        val storage = scans.store.storageInfo()
        val freePercent = ((1 - storage.usedFraction) * 100).toInt()
        val junk = scans.redundantSize()
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - CleanHistory.lastCleanAt(context))

        val (title, text) = when {
            freePercent < LOW_STORAGE_PERCENT ->
                "Storage almost full" to "Only $freePercent% left (${formatSize(storage.free)} free). Tap to free up space."
            junk >= JUNK_THRESHOLD ->
                "${formatSize(junk)} of leftover files found" to "APK installers, temp files and thumbnail caches can be cleaned."
            days >= STALE_DAYS ->
                "Time for a clean-up" to "It's been $days days since your last clean. Tap to scan your phone."
            force ->
                "Test reminder" to "Junk ${formatSize(junk)} · $freePercent% free · last clean $days days ago"
            else -> return Result.success()
        }
        Reminders.show(context, title, text)
        return Result.success()
    }

    private companion object {
        const val LOW_STORAGE_PERCENT = 10
        const val JUNK_THRESHOLD = 50L * 1024 * 1024
        const val STALE_DAYS = 2
        val MIN_INTERVAL_MS = TimeUnit.HOURS.toMillis(5)
    }
}
