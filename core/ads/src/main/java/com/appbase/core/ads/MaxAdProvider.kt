package com.appbase.core.ads

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.appbase.core.CoreServices
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdRevenueListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.applovin.sdk.AppLovinSdkUtils
import kotlin.math.pow

/**
 * AppLovin MAX 实现。SDK Key 与 Ad Unit ID 来自品牌配置 brand.properties（经 CoreServices.config.ads）。
 * MAX 每种广告格式只需一个 Ad Unit，[AdPlacement] 作为 placement 上报，用于在报表中区分广告位。
 */
object MaxAdProvider : AdProvider {

    private const val TAG = "MaxAds"

    /** SDK Key、Banner、插屏三个配置都已替换为真实值时才启用 MAX；开屏广告位可选 */
    val isConfigured: Boolean
        get() = listOf(
            CoreServices.config.ads.applovinSdkKey,
            CoreServices.config.ads.maxBanner,
            CoreServices.config.ads.maxInterstitial,
        ).all(::isRealId)

    private fun isRealId(id: String) = id.isNotBlank() && !id.startsWith("YOUR_")

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sdk: AppLovinSdk
    private var interstitial: FullscreenAd? = null
    private var appOpen: FullscreenAd? = null

    /** 冷启动闪屏期间等待开屏广告加载的请求 */
    private class AppOpenRequest(val activity: Activity, val placement: String, val onDone: () -> Unit)
    private var appOpenRequest: AppOpenRequest? = null

    /** 单次展示收益（USD）。后续接入统计平台时在这里上报 */
    private val revenueListener = MaxAdRevenueListener { ad ->
        Log.i(
            TAG,
            "revenue ${ad.revenue} USD (${ad.revenuePrecision}) format=${ad.format.label} " +
                "network=${ad.networkName} placement=${ad.placement}",
        )
    }

    override fun init(app: Application) {
        sdk = AppLovinSdk.getInstance(app)
        sdk.settings.setVerboseLogging(CoreServices.config.debug)
        // GDPR 授权：开启 MAX 内置的隐私授权流程（基于 Google UMP），在 EEA / 英国等地区初始化时自动弹出，
        // 用户选择完成后 SDK 才开始请求广告
        sdk.settings.termsAndPrivacyPolicyFlowSettings.apply {
            setShowTermsAndPrivacyPolicyAlertInGdpr(true)
            privacyPolicyUri = CoreServices.config.privacyUrl.toUri()
        }
        val config = AppLovinSdkInitializationConfiguration.builder(CoreServices.config.ads.applovinSdkKey)
            .setMediationProvider(AppLovinMediationProvider.MAX)
            .build()
        sdk.initialize(config) {
            Log.i(TAG, "SDK initialized")
            interstitial = MaxInterstitialAd(CoreServices.config.ads.maxInterstitial).let { ad ->
                FullscreenAd("interstitial", ad::isReady, ad::loadAd) { placement, activity -> ad.showAd(placement, activity) }
                    .also { ad.setListener(it); ad.setRevenueListener(revenueListener) }
            }
            if (isRealId(CoreServices.config.ads.maxAppOpen)) {
                appOpen = MaxAppOpenAd(CoreServices.config.ads.maxAppOpen).let { ad ->
                    FullscreenAd("app_open", ad::isReady, ad::loadAd) { placement, _ -> ad.showAd(placement) }
                        .also { ad.setListener(it); ad.setRevenueListener(revenueListener) }
                }
            }
            interstitial?.load()
            appOpen?.load()
        }
    }

    // region Fullscreen

    /**
     * 插屏与开屏共用的加载 / 重试 / 展示逻辑。
     * MaxInterstitialAd 和 MaxAppOpenAd 没有公共父类，用函数引用适配。
     */
    private class FullscreenAd(
        private val label: String,
        private val isReady: () -> Boolean,
        val load: () -> Unit,
        private val showAd: (placement: String, activity: Activity) -> Unit,
    ) : MaxAdListener {
        private var retryAttempt = 0
        private var placement = ""
        private var onDismiss: (() -> Unit)? = null
        val showing get() = onDismiss != null
        val ready get() = isReady()

        fun show(activity: Activity, placement: String, onDismiss: () -> Unit) {
            this.placement = placement
            this.onDismiss = onDismiss
            showAd(placement, activity)
        }

        override fun onAdLoaded(ad: MaxAd) {
            retryAttempt = 0
            onFullscreenLoaded()
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            // 指数退避重试，最长 64 秒
            retryAttempt++
            val delayMs = 1000L * 2.0.pow(minOf(6, retryAttempt)).toLong()
            Log.w(TAG, "$label load failed: ${error.code} ${error.message}, retry in ${delayMs}ms")
            handler.postDelayed({ load() }, delayMs)
        }

        override fun onAdDisplayed(ad: MaxAd) = AdPolicy.onFullscreenShown(placement)

        override fun onAdHidden(ad: MaxAd) = finish()

        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
            Log.w(TAG, "$label display failed: ${error.code} ${error.message}")
            finish()
        }

        // 点击广告会跳到浏览器或商店，回来时不算热启动
        override fun onAdClicked(ad: MaxAd) = AdPolicy.markExternalLaunch()

        private fun finish() {
            onDismiss?.invoke()
            onDismiss = null
            load()
        }
    }

    override val fullscreenShowing get() = interstitial?.showing == true || appOpen?.showing == true

    override val interstitialReady get() = interstitial?.ready == true

    /** 频率规则已由 [AdPolicy] 在调用前检查，这里只看广告是否可展示 */
    override fun showInterstitial(activity: Activity, placement: String, onDismiss: () -> Unit) {
        val ad = interstitial
        if (ad == null || !ad.ready || fullscreenShowing) {
            onDismiss()
            return
        }
        ad.show(activity, placement, onDismiss)
    }

    /** 未配置开屏广告位时用插屏广告位顶上 */
    private val appOpenSlot get() = appOpen ?: interstitial

    private fun tryShowAppOpen(activity: Activity, placement: String, onDone: () -> Unit): Boolean {
        val ad = appOpenSlot
        if (ad == null || !ad.ready || fullscreenShowing) return false
        ad.show(activity, placement, onDone)
        return true
    }

    override fun showAppOpen(activity: Activity, placement: String, waitMs: Long, onDone: () -> Unit) {
        if (tryShowAppOpen(activity, placement, onDone)) return
        if (waitMs <= 0) {
            onDone()
            return
        }
        // 未就绪：等加载完成（SDK 初始化完成后也会触发加载）或超时放行
        val request = AppOpenRequest(activity, placement, onDone)
        appOpenRequest = request
        handler.postDelayed({
            if (appOpenRequest === request) {
                appOpenRequest = null
                onDone()
            }
        }, waitMs)
    }

    private fun onFullscreenLoaded() {
        val request = appOpenRequest ?: return
        // 闪屏已经关掉就交给超时回调收尾
        if (request.activity.isFinishing || request.activity.isDestroyed) return
        if (tryShowAppOpen(request.activity, request.placement, request.onDone)) appOpenRequest = null
    }

    // endregion

    @Composable
    override fun Banner(placement: String, modifier: Modifier) {
        val context = LocalContext.current
        var loaded by remember(placement) { mutableStateOf(false) }
        val adView = remember(placement) {
            MaxAdView(CoreServices.config.ads.maxBanner, context).apply {
                setPlacement(placement)
                setListener(BannerLogger(placement) { loaded = true })
                setRevenueListener(revenueListener)
                loadAd()
            }
        }
        DisposableEffect(adView) { onDispose { adView.destroy() } }
        // MAX 标准 Banner：手机 50dp，平板 90dp；加载成功之前高度为 0，不在页面上留空位
        val height = if (!loaded) 0.dp else if (AppLovinSdkUtils.isTablet(context)) 90.dp else 50.dp
        AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth().height(height))
    }

    private class BannerLogger(private val placement: String, private val onLoaded: () -> Unit) : MaxAdViewAdListener {
        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            Log.w(TAG, "banner [$placement] load failed: ${error.code} ${error.message}")
        }

        override fun onAdLoaded(ad: MaxAd) = onLoaded()
        override fun onAdDisplayed(ad: MaxAd) = Unit
        override fun onAdHidden(ad: MaxAd) = Unit
        override fun onAdClicked(ad: MaxAd) = AdPolicy.markExternalLaunch()
        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) = Unit
        override fun onAdExpanded(ad: MaxAd) = Unit
        override fun onAdCollapsed(ad: MaxAd) = Unit
    }

    /** MAX 在 SDK 初始化时自行展示授权弹窗，这里无需额外等待 */
    override fun gatherConsent(activity: Activity, onDone: () -> Unit) = onDone()

    override val privacyOptionsRequired: Boolean
        get() = ::sdk.isInitialized && sdk.isInitialized &&
            sdk.configuration.consentFlowUserGeography == AppLovinSdkConfiguration.ConsentFlowUserGeography.GDPR

    override fun showPrivacyOptions(activity: Activity) {
        sdk.cmpService.showCmpForExistingUser(activity) { error ->
            if (error != null) Log.w(TAG, "privacy options failed: ${error.message}")
        }
    }

    override fun openDebugger(activity: Activity) {
        sdk.showMediationDebugger()
    }
}
