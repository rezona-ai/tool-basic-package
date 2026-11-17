/**
 * [INPUT]: 依赖 :core:ads 的 AdProvider 接口（只用接口，不碰任何广告 SDK）
 * [OUTPUT]: 对外提供 NoAds
 * [POS]: 白包（brand.properties 的 ADS=off）用的广告 Provider：横幅不占位，插屏、开屏直接跳过，不弹授权表单，设置页没有隐私选项。
 *        和 core 的 PlaceholderAdProvider 不同，debug 包里也不画占位框、不弹提示，看到的就是用户拿到的样子
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove

import android.app.Activity
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.appbase.core.ads.AdProvider

object NoAds : AdProvider {
    override fun init(app: Application) = Unit

    @Composable
    override fun Banner(placement: String, modifier: Modifier) = Unit

    override fun showInterstitial(activity: Activity, placement: String, onDismiss: () -> Unit) = onDismiss()

    override fun showAppOpen(activity: Activity, placement: String, waitMs: Long, onDone: () -> Unit) = onDone()

    /** 没有广告要等，清理动画不为它多等 */
    override val interstitialReady = true
    override val fullscreenShowing = false

    override fun gatherConsent(activity: Activity, onDone: () -> Unit) = onDone()
    override val privacyOptionsRequired = false
    override fun showPrivacyOptions(activity: Activity) = Unit
    override fun openDebugger(activity: Activity) = Unit
}
