package com.appbase.core.ads

import com.appbase.core.common.ExternalLaunch

import android.os.SystemClock
import android.util.Log

/**
 * 全屏广告的频率与时机规则，与具体广告 SDK 无关（debug 测试广告也走这套规则，看到的时机与正式一致）。
 * 数值集中在这里，后续可改为服务端下发。
 */
object AdPolicy {
    /** 两次全屏广告（插屏、开屏）的最短间隔 */
    const val FULLSCREEN_INTERVAL_MS = 30_000L

    /** 每次会话的插屏上限（开屏不计入） */
    const val MAX_INTERSTITIALS_PER_SESSION = 8

    /** 功能页“处理中”动画的最短时长 */
    const val PROCESSING_MIN_MS = 2_500L

    /** 清理动画的最短时长 */
    const val CLEANING_MIN_MS = 2_000L

    /** 处理中 / 清理动画等待插屏加载的总时长上限（从动画开始算）；冷却期内不等待 */
    const val AD_WAIT_MAX_MS = 8_000L

    /** 返回、点通知进入等场景的过渡遮罩：至少停留 / 最多等待插屏加载 */
    const val MASK_MIN_MS = 800L
    const val MASK_MAX_MS = 1_500L

    /** 冷启动闪屏最多等待开屏广告 */
    const val COLD_START_WAIT_MS = 8_000L

    /** 热启动：离开超过这个时长再回来就弹开屏（只过滤瞬间切换） */
    const val WARM_START_MIN_BACKGROUND_MS = 3_000L

    private var lastFullscreenAt = 0L
    private var sessionInterstitials = 0

    /** 冷启动或热启动（开屏广告的触发点）时调用 */
    fun startSession() {
        sessionInterstitials = 0
    }

    /** 即将跳到 App 外部的页面时调用（点击广告），回来时不当作热启动；标记本身在 core/common 的 ExternalLaunch */
    fun markExternalLaunch() = ExternalLaunch.mark()

    fun canShowInterstitial(): Boolean {
        val sinceLast = SystemClock.elapsedRealtime() - lastFullscreenAt
        return !Ads.provider.fullscreenShowing &&
            sessionInterstitials < MAX_INTERSTITIALS_PER_SESSION &&
            (lastFullscreenAt == 0L || sinceLast >= FULLSCREEN_INTERVAL_MS)
    }

    /** 广告真正展示出来时由广告实现回调 */
    fun onFullscreenShown(placement: String) {
        Log.d("AdPolicy", "fullscreen shown: $placement")
        lastFullscreenAt = SystemClock.elapsedRealtime()
        if (!placement.startsWith(AdPlacement.APP_OPEN_PREFIX)) sessionInterstitials++
    }
}
