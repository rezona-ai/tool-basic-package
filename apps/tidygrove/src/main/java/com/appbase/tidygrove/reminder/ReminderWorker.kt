/**
 * [INPUT]: 依赖 :features:cleaner 的 ReminderRules（挑哪条提醒），依赖 :core:common 的 Reminders（发通知），依赖 data/Copy 的提醒文案
 * [OUTPUT]: 对外提供 ReminderWorker
 * [POS]: Tidy Grove 的定时提醒：规则在功能层，这里只把 ReminderReason 翻成本包的通知文案
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.appbase.core.common.Reminders
import com.appbase.feature.cleaner.data.ReminderRules
import com.appbase.tidygrove.data.Copy

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(Reminders.KEY_FORCE, false)
        val reason = ReminderRules.check(applicationContext, force) ?: return Result.success()
        val (title, text) = Copy.reminder(reason)
        Reminders.show(applicationContext, title, text)
        return Result.success()
    }
}
