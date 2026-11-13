package com.appbase.core.ads

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.CoreServices
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 广告接入点。App.onCreate 按品牌配置选择实现：优先 [MaxAdProvider]（AppLovin MAX），
 * 未配置 MAX 时用 [AdMobAdProvider]（AdMob 直连），都未配置时用 [PlaceholderAdProvider]。
 * 页面里只引用 [AdPlacement] 中的位置名，不直接依赖具体 SDK。
 */
interface AdProvider {
    fun init(app: Application)

    @Composable
    fun Banner(placement: String, modifier: Modifier)

    /** 展示插屏；无广告可展示或广告关闭后都必须回调 [onDismiss] */
    fun showInterstitial(activity: Activity, placement: String, onDismiss: () -> Unit)

    /**
     * 展示开屏广告。广告未就绪时最多等待 [waitMs] 毫秒（冷启动闪屏期间等待，热启动传 0 只展示已就绪的）。
     * 无论是否展示，结束后都必须回调 [onDone]。
     */
    fun showAppOpen(activity: Activity, placement: String, waitMs: Long, onDone: () -> Unit)

    /** 插屏已加载好、可立即展示（处理中动画据此决定是否继续等待） */
    val interstitialReady: Boolean

    /** 正有全屏广告在展示（含已发起、尚未回调展示的） */
    val fullscreenShowing: Boolean

    /**
     * 冷启动闪屏上调用：按需展示隐私授权弹窗（EEA、英国、瑞士等受 GDPR 约束的地区），完成后回调 [onDone]。
     * 授权完成前不请求广告；不需要授权的地区直接回调。
     */
    fun gatherConsent(activity: Activity, onDone: () -> Unit)

    /** 当前用户是否需要“隐私设置”入口（受 GDPR 约束的地区必须能随时修改授权） */
    val privacyOptionsRequired: Boolean

    /** 打开隐私设置，让用户修改广告授权 */
    fun showPrivacyOptions(activity: Activity)

    /** 打开广告平台的调试工具（debug 包设置页入口） */
    fun openDebugger(activity: Activity)
}

/** 公共广告位：返回插屏、开屏。各品牌自己的广告位名称定义在品牌代码里（Orbit Clean 见 ui/Placements.kt） */
object AdPlacement {
    const val BACK_INTERSTITIAL = "back_interstitial"
    const val APP_OPEN_PREFIX = "app_open"
    const val APP_OPEN_COLD = "app_open_cold"
    const val APP_OPEN_WARM = "app_open_warm"
}

object Ads {
    var provider: AdProvider = PlaceholderAdProvider
    /** 过渡遮罩是否显示（core/ui 的 StartupActivity 顶层渲染 AdMask） */
    var maskVisible by mutableStateOf(false)
}

@Composable
fun AdBanner(placement: String, modifier: Modifier = Modifier) {
    Ads.provider.Banner(placement, modifier)
}

/** 冷热启动共用入口 */
fun showAppOpen(activity: Activity, placement: String, waitMs: Long, onDone: () -> Unit) {
    Ads.provider.showAppOpen(activity, placement, waitMs, onDone)
}

/** 页面统一从这里弹插屏：先过 [AdPolicy] 的频率规则，不满足时直接回调 [onDismiss] */
fun showInterstitial(activity: Activity?, placement: String, onDismiss: () -> Unit = {}) {
    if (activity == null || !AdPolicy.canShowInterstitial()) {
        onDismiss()
    } else {
        Ads.provider.showInterstitial(activity, placement, onDismiss)
    }
}

/** 挂起直到插屏关闭（或无法展示） */
suspend fun awaitInterstitial(activity: Activity?, placement: String) =
    suspendCancellableCoroutine { cont ->
        showInterstitial(activity, placement) { if (cont.isActive) cont.resume(Unit) }
    }

/**
 * 等待插屏加载好，最多 [timeoutMs]。冷却期内（本来就不会弹）立即返回 false。
 * 用于“处理中”动画：广告没加载好时动画继续转，而不是直接放弃这次展示。
 */
suspend fun awaitInterstitialReady(timeoutMs: Long): Boolean {
    if (!AdPolicy.canShowInterstitial()) return false
    if (timeoutMs <= 0) return Ads.provider.interstitialReady
    return withTimeoutOrNull(timeoutMs) {
        while (!Ads.provider.interstitialReady) delay(100)
        true
    } ?: false
}

/**
 * 先盖一层过渡遮罩等插屏加载（[AdPolicy.MASK_MIN_MS]～[AdPolicy.MASK_MAX_MS]），再弹插屏，挂起到广告关闭。
 * 冷却期内不显示遮罩，立即返回。
 */
suspend fun showInterstitialWithMask(activity: Activity?, placement: String) {
    if (activity == null || !AdPolicy.canShowInterstitial()) return
    Ads.maskVisible = true
    try {
        val start = SystemClock.elapsedRealtime()
        awaitInterstitialReady(AdPolicy.MASK_MAX_MS)
        delay(AdPolicy.MASK_MIN_MS - (SystemClock.elapsedRealtime() - start))
        awaitInterstitial(activity, placement)
    } finally {
        Ads.maskVisible = false
    }
}

/**
 * 功能页的返回：先盖遮罩尝试弹返回插屏，关闭后再执行 [onBack]。同时接管系统返回键。
 * [enabled] 为 false 时不拦截系统返回键。
 */
@Composable
fun rememberAdBack(enabled: Boolean = true, onBack: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)
    var leaving by remember { mutableStateOf(false) }
    val back: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                showInterstitialWithMask(activity, AdPlacement.BACK_INTERSTITIAL)
                currentOnBack()
            }
        }
    }
    BackHandler(enabled = enabled, onBack = back)
    return back
}

/** 未接入广告时的占位：debug 包显示广告位虚线框，release 包不显示 */
object PlaceholderAdProvider : AdProvider {
    override fun init(app: Application) = Unit

    @Composable
    override fun Banner(placement: String, modifier: Modifier) {
        if (!CoreServices.config.debug) return
        Box(
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Ad · $placement", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }

    override fun showInterstitial(activity: Activity, placement: String, onDismiss: () -> Unit) {
        fakeShow(activity, placement)
        onDismiss()
    }

    override fun showAppOpen(activity: Activity, placement: String, waitMs: Long, onDone: () -> Unit) {
        fakeShow(activity, placement)
        onDone()
    }

    /** 占位“广告”总是就绪，避免处理中动画白等 */
    override val interstitialReady = true
    override val fullscreenShowing = false

    /** 不展示真实广告，无需授权 */
    override fun gatherConsent(activity: Activity, onDone: () -> Unit) = onDone()
    override val privacyOptionsRequired = false
    override fun showPrivacyOptions(activity: Activity) = Unit

    /** 视为已展示（计入频率规则）；debug 包里提示“这里会弹全屏广告”，方便没配 ID 时核对弹出时机 */
    private fun fakeShow(activity: Activity, placement: String) {
        AdPolicy.onFullscreenShown(placement)
        if (CoreServices.config.debug) Toast.makeText(activity, "Ad · $placement", Toast.LENGTH_SHORT).show()
    }

    override fun openDebugger(activity: Activity) {
        Toast.makeText(activity, "Ads not configured: fill in AdMob or MAX IDs in brand.properties", Toast.LENGTH_LONG).show()
    }
}
