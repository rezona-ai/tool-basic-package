/**
 * [INPUT]: 依赖 BuildConfig（本模块 brand.properties 注入的值），依赖 core 的 CoreServices / 广告 / 归因 / 提醒，
 *          依赖 data 的 ScanRepository / SystemInfoRepository，依赖 ui/theme 的 installOrbitTheme
 * [OUTPUT]: 对外提供 App（instance、scans、systemInfoRepository）
 * [POS]: Orbit Clean 的 Application：注册品牌主题和 CoreConfig，选择广告 Provider（MAX → AdMob → 占位），启动归因、提醒
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.appbase.core.AdIds
import com.appbase.core.CoreConfig
import com.appbase.core.CoreServices
import com.appbase.core.ReminderSetup
import com.appbase.core.ads.AdBackBlocker
import com.appbase.core.ads.AdMobAdProvider
import com.appbase.core.ads.Ads
import com.appbase.core.ads.MaxAdProvider
import com.appbase.core.ads.PlaceholderAdProvider
import com.appbase.core.attribution.Attribution
import com.appbase.core.common.Reminders
import com.appbase.core.storage.StoreChange
import com.appbase.core.storage.files
import com.orbitclean.app.data.ScanRepository
import com.orbitclean.app.data.SystemInfoRepository
import com.orbitclean.app.reminder.CleanHistory
import com.orbitclean.app.reminder.ReminderWorker
import com.orbitclean.app.ui.theme.installOrbitTheme

class App : Application(), SingletonImageLoader.Factory {

    lateinit var scans: ScanRepository
        private set
    lateinit var systemInfoRepository: SystemInfoRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 最先注册品牌主题和配置：之后的颜色 token、字体样式、广告、提醒都依赖它们
        installOrbitTheme()
        CoreServices.init(this, coreConfig())
        scans = ScanRepository(CoreServices.files)
        systemInfoRepository = SystemInfoRepository(this)
        // 删除文件即记一次清理，提醒据此判断“很久没清理”
        CoreServices.files.addListener { if (it is StoreChange.Deleted) CleanHistory.markCleaned(this) }
        Attribution.init(this)
        // 优先 MAX；未配置时直连 AdMob（debug 包为 Google 测试广告位）；都没有时不展示广告
        Ads.provider = when {
            MaxAdProvider.isConfigured -> MaxAdProvider
            AdMobAdProvider.isConfigured -> AdMobAdProvider
            else -> PlaceholderAdProvider
        }
        Log.i("Ads", "ad provider: ${Ads.provider.javaClass.simpleName}")
        Ads.provider.init(this)
        AdBackBlocker.install(this)
        Reminders.schedule(this)
    }

    private fun coreConfig() = CoreConfig(
        debug = BuildConfig.DEBUG,
        appName = BuildConfig.APP_NAME,
        versionName = BuildConfig.VERSION_NAME,
        privacyUrl = BuildConfig.PRIVACY_URL,
        feedbackEmail = BuildConfig.FEEDBACK_EMAIL,
        ads = AdIds(
            admobBanner = BuildConfig.ADMOB_BANNER_AD_UNIT_ID,
            admobInterstitial = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
            admobAppOpen = BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID,
            admobTestDevices = BuildConfig.ADMOB_TEST_DEVICE_IDS,
            applovinSdkKey = BuildConfig.APPLOVIN_SDK_KEY,
            maxBanner = BuildConfig.MAX_BANNER_AD_UNIT_ID,
            maxInterstitial = BuildConfig.MAX_INTERSTITIAL_AD_UNIT_ID,
            maxAppOpen = BuildConfig.MAX_APP_OPEN_AD_UNIT_ID,
        ),
        adjustToken = BuildConfig.ADJUST_APP_TOKEN,
        reminder = ReminderSetup(
            worker = ReminderWorker::class.java,
            activity = MainActivity::class.java,
            channelName = "Cleaning reminders",
            smallIcon = R.drawable.ic_notification,
            color = 0xFF3DBFA5.toInt(),
        ),
    )

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: App
            private set
    }
}
