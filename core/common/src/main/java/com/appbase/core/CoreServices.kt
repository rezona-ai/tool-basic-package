/**
 * [INPUT]: 由各品牌的 Application.onCreate 调用 [CoreServices.init]，传入从 BuildConfig / 品牌配置整理出的 [CoreConfig]
 * [OUTPUT]: 对外提供 CoreConfig、AdIds、ReminderSetup、CoreServices（config + context；文件读写 CoreServices.files 由 core/storage 扩展提供）
 * [POS]: core 的唯一入口：公共代码不直接引用任何品牌的 BuildConfig、R、Application 或 Activity，需要的值都从这里取
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core

import android.app.Activity
import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.work.ListenableWorker
import java.time.LocalTime

/** 广告位 ID。debug 包传 Google 测试广告位；MAX 字段保持 YOUR_ 前缀表示未配置 */
data class AdIds(
    val admobBanner: String,
    val admobInterstitial: String,
    val admobAppOpen: String,
    /** 只收测试广告的设备 ID，逗号分隔 */
    val admobTestDevices: String,
    val applovinSdkKey: String,
    val maxBanner: String,
    val maxInterstitial: String,
    val maxAppOpen: String,
)

/** 本地提醒：由品牌提供 Worker（决定发什么）、点击后打开的 Activity、通知图标和颜色 */
data class ReminderSetup(
    val worker: Class<out ListenableWorker>,
    val activity: Class<out Activity>,
    /** 系统通知设置里显示的渠道名 */
    val channelName: String,
    @DrawableRes val smallIcon: Int,
    @ColorInt val color: Int,
    /** 每天检查的时刻：key 是 WorkManager 唯一任务名 */
    val times: Map<String, LocalTime> = mapOf("noon_reminder" to LocalTime.of(12, 30), "daily_reminder" to LocalTime.of(19, 0)),
)

data class CoreConfig(
    val debug: Boolean,
    val appName: String,
    val versionName: String,
    val privacyUrl: String,
    val feedbackEmail: String,
    val ads: AdIds,
    /** Adjust App Token，空串表示不初始化 */
    val adjustToken: String,
    /** null 表示该品牌不发本地提醒 */
    val reminder: ReminderSetup?,
)

object CoreServices {
    lateinit var config: CoreConfig
        private set

    /** Application Context，init 之后可用 */
    lateinit var context: Context
        private set

    fun init(context: Context, config: CoreConfig) {
        this.context = context.applicationContext
        this.config = config
    }
}
