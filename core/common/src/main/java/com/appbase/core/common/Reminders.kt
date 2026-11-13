package com.appbase.core.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.appbase.core.CoreServices
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 本地提醒框架：按 [com.appbase.core.ReminderSetup.times] 每天定时运行品牌提供的 Worker，由 Worker 决定发不发、发什么（[show]）。
 * 这里只管开关、通知权限、频率记录、通知渠道和点击跳转；没有配置 reminder 的品牌调用这些方法时什么也不做
 */
object Reminders {
    /** 点击提醒通知打开 Activity 时带的 action，品牌据此跳到对应页面 */
    const val ACTION_OPEN_FROM_REMINDER = "core.action.OPEN_FROM_REMINDER"
    const val KEY_FORCE = "force"

    private const val PREFS = "reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_NOTIFIED = "last_notified_at"
    private const val KEY_PERMISSION_ASKED = "permission_asked"
    private const val CHANNEL_ID = "reminders"
    private const val NOTIFICATION_ID = 1001

    /** 品牌也可以把自己的提醒状态（如上次清理时间）存在这里 */
    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val setup get() = CoreServices.config.reminder

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) {
            schedule(context)
        } else {
            setup?.times?.keys?.forEach { WorkManager.getInstance(context).cancelUniqueWork(it) }
        }
    }

    /** 首页只主动申请一次通知权限，之后由设置页开关引导 */
    fun isPermissionAsked(context: Context) = prefs(context).getBoolean(KEY_PERMISSION_ASKED, false)

    fun setPermissionAsked(context: Context) = prefs(context).edit { putBoolean(KEY_PERMISSION_ASKED, true) }

    fun lastNotifiedAt(context: Context) = prefs(context).getLong(KEY_LAST_NOTIFIED, 0)

    fun notificationsAllowed(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** App 启动时调用；KEEP 保证已有计划不被重置 */
    fun schedule(context: Context) {
        val setup = setup ?: return
        if (!isEnabled(context)) return
        setup.times.forEach { (name, time) ->
            val request = PeriodicWorkRequest.Builder(setup.worker, 1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(time).toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    /** debug 用：立即跑一次，忽略开关和频率限制 */
    fun sendTestNow(context: Context) {
        val setup = setup ?: return
        val request = OneTimeWorkRequest.Builder(setup.worker).setInputData(workDataOf(KEY_FORCE to true)).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun show(context: Context, title: String, text: String) {
        val setup = setup ?: return
        if (!notificationsAllowed(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, setup.channelName, NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = Intent(context, setup.activity)
            .setAction(ACTION_OPEN_FROM_REMINDER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(setup.smallIcon)
            .setColor(setup.color)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs(context).edit { putLong(KEY_LAST_NOTIFIED, System.currentTimeMillis()) }
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var next = now.with(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
