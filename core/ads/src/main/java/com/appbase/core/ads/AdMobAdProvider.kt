package com.appbase.core.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.appbase.core.CoreServices
import com.appbase.core.attribution.Attribution
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.security.MessageDigest

/**
 * Google AdMob 直连实现。App ID 与广告位 ID 来自品牌配置 brand.properties（App ID 经 manifestPlaceholders，广告位经 CoreServices.config.ads）。
 * debug 包的广告位在 build.gradle.kts 中固定为 Google 官方测试广告位，不会产生无效流量；release 包使用真实广告位。
 * 授权（GDPR）走 Google UMP，需要在 AdMob 后台「隐私和消息」发布欧洲法规消息。
 */
object AdMobAdProvider : AdProvider {
    private const val TAG = "AdMobAds"
    /** 加载失败后的重试间隔：从 10 秒起翻倍，最长 5 分钟，加载成功后重置 */
    private const val RETRY_MIN_MS = 10_000L
    private const val RETRY_MAX_MS = 300_000L
    private const val BANNER_RETRY_MIN_MS = 30_000L

    /** 授权信息查询的等待上限：网络到 Google 很慢时不让闪屏一直卡着 */
    private const val CONSENT_TIMEOUT_MS = 5_000L
    private val CONSENT_TIMEOUT_TOKEN = Any()

    /** 三个广告位都已填写时才启用 */
    val isConfigured: Boolean
        get() = listOf(
            CoreServices.config.ads.admobBanner,
            CoreServices.config.ads.admobInterstitial,
            CoreServices.config.ads.admobAppOpen,
        ).all { it.startsWith("ca-app-pub-") }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var app: Application
    private var interstitial: InterstitialAd? = null
    private var appOpen: AppOpenAd? = null
    private var interstitialRetryMs = RETRY_MIN_MS
    private var appOpenRetryMs = RETRY_MIN_MS
    private var showing = false

    /** 已允许请求广告（Banner 据此决定是否加载，所以用 Compose 状态） */
    private var adsStarted by mutableStateOf(false)

    override val interstitialReady get() = interstitial != null
    override val fullscreenShowing get() = showing

    private class AppOpenRequest(val activity: Activity, val placement: String, val onDone: () -> Unit)
    private var appOpenRequest: AppOpenRequest? = null

    override fun init(app: Application) {
        this.app = app
        // 之前已完成授权：不必等闪屏上的授权流程，直接开始初始化与预加载
        if (UserMessagingPlatform.getConsentInformation(app).canRequestAds()) startAds()
    }

    /** 授权完成后才初始化广告 SDK 并请求广告 */
    private fun startAds() {
        if (adsStarted) return
        adsStarted = true
        Log.i(TAG, "start ads, consentUnknown=$consentUnknown")
        // 测试设备上的真实广告位只返回测试广告，避免自己点击产生无效流量
        val testDevices = CoreServices.config.ads.admobTestDevices.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (testDevices.isNotEmpty()) {
            MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setTestDeviceIds(testDevices).build())
        }
        Thread {
            MobileAds.initialize(app) { Log.i(TAG, "Mobile Ads initialized") }
        }.start()
        // 不等初始化回调就开始加载：未使用聚合时 SDK 会在首次请求时完成初始化。
        // 网络到 Google 较慢时初始化回调可能几分钟都不返回，等它会导致开屏、插屏一直没有加载
        loadInterstitial()
        loadAppOpen()
    }

    // region Consent

    /** 授权状态还不知道（授权查询失败或超时，且之前从未查到过） */
    private val consentUnknown: Boolean
        get() = UserMessagingPlatform.getConsentInformation(app).consentStatus ==
            ConsentInformation.ConsentStatus.UNKNOWN

    /**
     * 授权状态未知时也发普通请求，不加非个性化标记：实测非个性化请求在新广告位上几乎全部 No fill。
     * 授权状态未知时请求里没有 TCF 授权信号，Google 会按自己的欧盟用户授权政策处理这类流量
     */
    private fun adRequest(): AdRequest = AdRequest.Builder().build()

    /**
     * Google UMP 授权流程：受 GDPR 约束的地区弹出授权表单，其他地区直接放行。
     * debug 包始终模拟 EEA 地区，任何地区的测试机都能看到授权弹窗。已授权过的用户立即放行，授权信息在后台刷新。
     * 授权查询最多等 [CONSENT_TIMEOUT_MS]：查询失败或超时、状态仍未知时照常请求广告，避免网络差时一条广告都不出；
     * 明确需要授权但用户未同意时（canRequestAds 为 false 且状态已知）不请求广告。
     */
    override fun gatherConsent(activity: Activity, onDone: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().apply {
            if (CoreServices.config.debug) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .addTestDeviceHashedId(testDeviceHashedId(activity))
                        .build(),
                )
            }
        }.build()
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(CONSENT_TIMEOUT_TOKEN)
            Attribution.syncConsent(activity)
            if (info.canRequestAds() || consentUnknown) startAds()
            onDone()
        }
        // 超时只针对授权信息查询：查询返回后（可能要弹表单）取消超时，等用户在表单里做完选择，避免授权前就请求广告
        handler.postAtTime({
            Log.w(TAG, "consent info update timed out after ${CONSENT_TIMEOUT_MS}ms")
            finish()
        }, CONSENT_TIMEOUT_TOKEN, SystemClock.uptimeMillis() + CONSENT_TIMEOUT_MS)
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                handler.removeCallbacksAndMessages(CONSENT_TIMEOUT_TOKEN)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w(TAG, "consent form: ${it.message}") }
                    finish()
                }
            },
            { error ->
                Log.w(TAG, "consent info update failed: ${error.message}")
                finish()
            },
        )
        if (info.canRequestAds()) finish()
    }

    override val privacyOptionsRequired: Boolean
        get() = UserMessagingPlatform.getConsentInformation(app).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(TAG, "privacy options: ${it.message}") }
            Attribution.syncConsent(activity)
        }
    }

    /** UMP 的测试设备 ID：ANDROID_ID 的 MD5（大写），让调试地区设置在真机上也生效 */
    @SuppressLint("HardwareIds")
    private fun testDeviceHashedId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return MessageDigest.getInstance("MD5").digest(androidId.toByteArray())
            .joinToString("") { "%02X".format(it) }
    }

    // endregion

    // region Fullscreen

    private fun loadInterstitial() {
        InterstitialAd.load(
            app,
            CoreServices.config.ads.admobInterstitial,
            adRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.i(TAG, "interstitial loaded")
                    interstitial = ad
                    interstitialRetryMs = RETRY_MIN_MS
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "interstitial load failed: ${error.code} ${error.message}, retry in ${interstitialRetryMs}ms")
                    handler.postDelayed(::loadInterstitial, interstitialRetryMs)
                    interstitialRetryMs = (interstitialRetryMs * 2).coerceAtMost(RETRY_MAX_MS)
                }
            },
        )
    }

    private fun loadAppOpen() {
        AppOpenAd.load(
            app,
            CoreServices.config.ads.admobAppOpen,
            adRequest(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.i(TAG, "app open loaded")
                    appOpen = ad
                    appOpenRetryMs = RETRY_MIN_MS
                    val request = appOpenRequest ?: return
                    // 闪屏已经关掉就交给超时回调收尾
                    if (request.activity.isFinishing || request.activity.isDestroyed) return
                    if (tryShowAppOpen(request.activity, request.placement, request.onDone)) appOpenRequest = null
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "app open load failed: ${error.code} ${error.message}, retry in ${appOpenRetryMs}ms")
                    handler.postDelayed(::loadAppOpen, appOpenRetryMs)
                    appOpenRetryMs = (appOpenRetryMs * 2).coerceAtMost(RETRY_MAX_MS)
                }
            },
        )
    }

    /** 全屏广告的展示回调：展示时计入频率规则，关闭或展示失败时回调并预加载下一条 */
    private fun callback(placement: String, onDone: () -> Unit, reload: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = AdPolicy.onFullscreenShown(placement)

        // 点击广告会跳到浏览器或商店，回来时不算热启动
        override fun onAdClicked() = AdPolicy.markExternalLaunch()

        override fun onAdDismissedFullScreenContent() = finish()

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.w(TAG, "$placement show failed: ${error.message}")
            finish()
        }

        private fun finish() {
            showing = false
            onDone()
            reload()
        }
    }

    /** 频率规则已由 [AdPolicy] 在调用前检查，这里只看广告是否可展示 */
    override fun showInterstitial(activity: Activity, placement: String, onDismiss: () -> Unit) {
        val ad = interstitial
        if (ad == null || showing) {
            onDismiss()
            return
        }
        interstitial = null
        showing = true
        ad.fullScreenContentCallback = callback(placement, onDismiss, ::loadInterstitial)
        ad.onPaidEventListener = OnPaidEventListener {
            Attribution.trackAdMobRevenue(it, ad.adUnitId, placement, ad.responseInfo)
        }
        ad.show(activity)
    }

    private fun tryShowAppOpen(activity: Activity, placement: String, onDone: () -> Unit): Boolean {
        val ad = appOpen ?: return false
        if (showing) return false
        appOpen = null
        showing = true
        ad.fullScreenContentCallback = callback(placement, onDone, ::loadAppOpen)
        ad.onPaidEventListener = OnPaidEventListener {
            Attribution.trackAdMobRevenue(it, ad.adUnitId, placement, ad.responseInfo)
        }
        ad.show(activity)
        return true
    }

    override fun showAppOpen(activity: Activity, placement: String, waitMs: Long, onDone: () -> Unit) {
        if (tryShowAppOpen(activity, placement, onDone)) return
        if (waitMs <= 0) {
            onDone()
            return
        }
        // 未就绪：等加载完成或超时放行
        val request = AppOpenRequest(activity, placement, onDone)
        appOpenRequest = request
        handler.postDelayed({
            if (appOpenRequest === request) {
                appOpenRequest = null
                onDone()
            }
        }, waitMs)
    }

    // endregion

    /**
     * 自适应横幅出现 No fill 后，本次运行改用标准 320×50（新广告位上自适应尺寸曾几乎全部 No fill，标准尺寸能出广告）。
     * 用 Compose 状态，切换后页面上的横幅会立即换成标准尺寸重新请求
     */
    private var adaptiveBannerNoFill by mutableStateOf(false)

    /**
     * 横幅宽度铺满所在容器（与页面卡片同宽），高度由 AdMob 按宽度计算（锚定自适应横幅，手机上约 50～60dp）。
     * 自适应尺寸无填充时回退到居中的标准 320×50，见 [adaptiveBannerNoFill]。
     * 广告加载成功之前高度为 0（AdView 仍挂在窗口上照常请求），页面不会留出一块空位
     */
    @Composable
    override fun Banner(placement: String, modifier: Modifier) {
        // 授权流程结束、允许请求广告之前不加载
        if (!adsStarted) return
        val context = LocalContext.current
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val widthDp = maxWidth.value.toInt()
            val adaptive = !adaptiveBannerNoFill && widthDp > AdSize.BANNER.width
            val size = remember(widthDp, adaptive) {
                if (adaptive) AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) else AdSize.BANNER
            }
            val banner = remember(placement, size) { BannerSlot(context, placement, size, adaptive) }
            DisposableEffect(banner) { onDispose { banner.destroy() } }
            AndroidView(
                factory = { banner.view },
                modifier = Modifier.width(size.width.dp).height(if (banner.loaded) size.height.dp else 0.dp),
            )
        }
    }

    /**
     * 一个页面上的横幅。加载失败（多为新广告位 No fill）时，在页面停留期间按 30 秒起翻倍、最长 5 分钟的间隔重新请求；
     * 加载成功后由 AdMob 后台的自动刷新接管。页面离开时停止重试并销毁。
     * 自适应尺寸 No fill 时不在原地重试，而是让页面换成标准尺寸的新横幅
     */
    private class BannerSlot(context: Context, private val placement: String, size: AdSize, private val adaptive: Boolean) {
        private var retryMs = BANNER_RETRY_MIN_MS

        /** 是否已有广告可展示；页面据此决定是否给横幅留出高度 */
        var loaded by mutableStateOf(false)
            private set
        private val retry = Runnable { if (view.isAttachedToWindow) view.loadAd(adRequest()) }

        val view: AdView = AdView(context).apply {
            adUnitId = CoreServices.config.ads.admobBanner
            setAdSize(size)
            onPaidEventListener = OnPaidEventListener {
                Attribution.trackAdMobRevenue(it, adUnitId, placement, responseInfo)
            }
            adListener = object : AdListener() {
                override fun onAdClicked() = AdPolicy.markExternalLaunch()

                override fun onAdLoaded() {
                    Log.i(TAG, "banner [$placement] loaded, size=${adSize}")
                    loaded = true
                    retryMs = BANNER_RETRY_MIN_MS
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (adaptive && error.code == AdRequest.ERROR_CODE_NO_FILL) {
                        Log.w(TAG, "banner [$placement] adaptive no fill, falling back to 320x50")
                        adaptiveBannerNoFill = true
                        return
                    }
                    Log.w(TAG, "banner [$placement] load failed: ${error.code} ${error.message}, retry in ${retryMs}ms")
                    handler.removeCallbacks(retry)
                    handler.postDelayed(retry, retryMs)
                    retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
                }
            }
            loadAd(adRequest())
        }

        fun destroy() {
            handler.removeCallbacks(retry)
            view.destroy()
        }
    }

    /** debug 包设置页入口：打开 Google 的广告检查器（Ad Inspector） */
    override fun openDebugger(activity: Activity) {
        MobileAds.openAdInspector(activity) { error ->
            error?.let { Log.w(TAG, "ad inspector: ${it.message}") }
        }
    }
}
