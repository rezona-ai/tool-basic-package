/**
 * [INPUT]: 依赖 BuildConfig（brand.properties 注入的值），依赖 core 的 CoreServices / 广告 / 归因 / 提醒，
 *          依赖 :features:cleaner 的 Cleaner，依赖 data/Copy 的分组文案、reminder/ReminderWorker、ui/theme 的 installAppTheme
 * [OUTPUT]: 对外提供 App（instance）
 * [POS]: Tidy Grove 的 Application：注册品牌主题和 CoreConfig，初始化清理功能层，选择广告 Provider（MAX → AdMob → 占位），启动归因、提醒
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.appbase.core.AdIds
import com.appbase.core.CoreConfig
import com.appbase.core.CoreServices
import com.appbase.core.ReminderSetup
import com.appbase.core.ads.AdMobAdProvider
import com.appbase.core.ads.Ads
import com.appbase.core.ads.MaxAdProvider
import com.appbase.core.ads.PlaceholderAdProvider
import com.appbase.core.attribution.Attribution
import com.appbase.core.common.Reminders
import com.appbase.core.storage.files
import com.appbase.feature.cleaner.Cleaner
import com.appbase.tidygrove.data.Copy
import com.appbase.tidygrove.reminder.ReminderWorker
import com.appbase.tidygrove.ui.theme.AppPalette
import com.appbase.tidygrove.ui.theme.installAppTheme

class App : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 最先注册品牌主题和配置：之后的颜色 token、字体样式、广告、提醒都依赖它们
        installAppTheme()
        CoreServices.init(this, coreConfig())
        Cleaner.init(this, CoreServices.files, Copy.groups)
        Attribution.init(this)
        Ads.provider = when {
            MaxAdProvider.isConfigured -> MaxAdProvider
            AdMobAdProvider.isConfigured -> AdMobAdProvider
            else -> PlaceholderAdProvider
        }
        Log.i("Ads", "ad provider: ${Ads.provider.javaClass.simpleName}")
        Ads.provider.init(this)
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
            channelName = Copy.reminderChannel,
            smallIcon = R.drawable.ic_notification,
            color = AppPalette.primary.toArgb(),
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
