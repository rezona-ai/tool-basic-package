package com.appbase.core.attribution

import android.app.Application
import android.content.Context
import android.util.Log
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.AdjustThirdPartySharing
import com.adjust.sdk.LogLevel
import com.appbase.core.CoreServices
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo

/**
 * Adjust 归因与广告收入上报。App Token 来自品牌配置的 ADJUST_APP_TOKEN（经 CoreServices.config.adjustToken），留空时不初始化。
 * debug 包上报到 Sandbox 环境，release 包上报到 Production 环境，测试数据不会混进正式数据。
 * 会话（前后台切换）由 SDK 自带的 SystemLifecycleContentProvider 自动统计，不需要手动调用 onResume / onPause。
 */
object Attribution {
    private const val TAG = "Attribution"
    private const val PREFS = "attribution"
    private const val KEY_DMA_SENT = "dma_sent"

    val isConfigured: Boolean get() = CoreServices.config.adjustToken.isNotBlank()

    private var started = false

    fun init(app: Application) {
        if (!isConfigured) {
            Log.i(TAG, "Adjust not configured")
            return
        }
        val environment = if (CoreServices.config.debug) AdjustConfig.ENVIRONMENT_SANDBOX else AdjustConfig.ENVIRONMENT_PRODUCTION
        val config = AdjustConfig(app, CoreServices.config.adjustToken, environment).apply {
            setLogLevel(if (CoreServices.config.debug) LogLevel.VERBOSE else LogLevel.WARN)
            setOnAttributionChangedListener { attribution ->
                Log.i(TAG, "attribution: network=${attribution.network} campaign=${attribution.campaign}")
            }
        }
        Adjust.initSdk(config)
        started = true
        Log.i(TAG, "Adjust started, environment=$environment")
    }

    /**
     * 把 Google UMP 的授权结果转成 Google DMA 信号发给 Adjust（Adjust SDK 不会自己读取 TCF 授权串）。
     * 在授权流程结束、用户修改隐私设置后调用；结果和上次一样时不重复发送。
     * 映射规则：ad_user_data = TCF 目的 1 和 7，ad_personalization = 目的 3 和 4。
     */
    fun syncConsent(context: Context) {
        if (!started) return
        val tcf = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        val gdprApplies = runCatching { tcf.getInt("IABTCF_gdprApplies", -1) }.getOrDefault(-1)
        if (gdprApplies == -1) return // 授权状态还不知道，等下次
        val purposes = tcf.getString("IABTCF_PurposeConsents", "").orEmpty()
        fun consented(vararg ids: Int) = ids.all { purposes.getOrNull(it - 1) == '1' }
        val eea = gdprApplies == 1
        // 不在 EEA 时 Google 不要求授权，按已授权处理
        val adUserData = !eea || consented(1, 7)
        val adPersonalization = !eea || consented(3, 4)
        val signal = "eea=$eea,ad_user_data=$adUserData,ad_personalization=$adPersonalization"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DMA_SENT, null) == signal) return
        val sharing = AdjustThirdPartySharing(null).apply {
            addGranularOption("google_dma", "eea", if (eea) "1" else "0")
            addGranularOption("google_dma", "ad_user_data", if (adUserData) "1" else "0")
            addGranularOption("google_dma", "ad_personalization", if (adPersonalization) "1" else "0")
        }
        Adjust.trackThirdPartySharing(sharing)
        prefs.edit().putString(KEY_DMA_SENT, signal).apply()
        Log.i(TAG, "google_dma sent: $signal")
    }

    /**
     * 上报一次 AdMob 广告展示的收入（来自 OnPaidEventListener）。
     * 需要在 AdMob 后台开启「展示级广告收入」，否则 AdMob 不会回调。
     */
    fun trackAdMobRevenue(value: AdValue, adUnitId: String, placement: String, responseInfo: ResponseInfo?) {
        if (!started) return
        val revenue = AdjustAdRevenue("admob_sdk").apply {
            setRevenue(value.valueMicros / 1_000_000.0, value.currencyCode)
            setAdRevenueUnit(adUnitId)
            setAdRevenuePlacement(placement)
            responseInfo?.loadedAdapterResponseInfo?.adSourceName?.let { setAdRevenueNetwork(it) }
        }
        Adjust.trackAdRevenue(revenue)
        if (CoreServices.config.debug) Log.d(TAG, "ad revenue [$placement] ${value.valueMicros} micros ${value.currencyCode}")
    }
}
