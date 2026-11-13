/**
 * [INPUT]: 依赖 Google Play In-App Review（review-ktx），依赖 core/ads 的 AdPolicy
 * [OUTPUT]: 对外提供 ReviewPrompt（recordClean / maybeAsk / shouldAsk）
 * [POS]: core/common 的应用内评分时机（各品牌在自己的"完成"时刻调用 recordClean + maybeAsk；Orbit Clean 在清理完成页）：用户第 MIN_CLEANS 次及以后成功清理、停在完成页时请求一次 Play 评分卡片，之后冷却 COOLDOWN_MS。
 *        按 Google 指南：不由按钮触发、弹出前不问任何问题（不问喜不喜欢、不预选星级）、不和广告挂钩；
 *        Play 自己还有次数配额，调用了也可能不弹，调用方不依赖结果。设置页 "Rate us" 仍然直接打开商店页
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.common

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

object ReviewPrompt {
    /** 第几次成功清理起才请求评分：第一次用户还没形成判断 */
    const val MIN_CLEANS = 2

    /** 请求过一次后，至少隔这么久才再请求 */
    const val COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000

    /** 完成页出现后等冲击波动画放完再请求 */
    const val DELAY_MS = 1_800L

    private const val PREFS = "review"
    private const val KEY_CLEANS = "successful_cleans"
    private const val KEY_ASKED_AT = "asked_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 纯规则，单测覆盖 */
    fun shouldAsk(cleans: Int, lastAskedAt: Long, now: Long): Boolean =
        cleans >= MIN_CLEANS && (lastAskedAt == 0L || now - lastAskedAt !in 0 until COOLDOWN_MS)

    /** 记一次成功清理，返回累计次数 */
    fun recordClean(context: Context): Int {
        val cleans = prefs(context).getInt(KEY_CLEANS, 0) + 1
        prefs(context).edit { putInt(KEY_CLEANS, cleans) }
        return cleans
    }

    /**
     * 满足规则且完成页仍在前台（没有广告或其他窗口挡着）时请求 Play 评分卡片。
     * 非 Play 安装的包、配额用完等情况下 Play 会静默不弹，这里吞掉所有异常。
     */
    suspend fun maybeAsk(activity: Activity, cleans: Int) {
        val prefs = prefs(activity)
        if (!shouldAsk(cleans, prefs.getLong(KEY_ASKED_AT, 0L), System.currentTimeMillis())) return
        if (activity.isFinishing || !activity.hasWindowFocus()) return
        val manager = ReviewManagerFactory.create(activity)
        val info = runCatching { manager.requestReview() }.getOrNull() ?: return
        if (activity.isFinishing || !activity.hasWindowFocus()) return
        prefs.edit { putLong(KEY_ASKED_AT, System.currentTimeMillis()) }
        // 评分卡片是 Play 的界面，回来时不当作热启动弹开屏广告
        ExternalLaunch.mark()
        runCatching { manager.launchReview(activity, info) }
    }
}
