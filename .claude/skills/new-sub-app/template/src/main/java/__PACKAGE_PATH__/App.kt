/**
 * [INPUT]: 依赖 BuildConfig（brand.properties 注入的值），依赖 core 的 CoreServices / 广告 / 归因 / 提醒，依赖 ui/theme 的 installAppTheme
 * [OUTPUT]: 对外提供 App（instance）
 * [POS]: {{APP_NAME}} 的 Application：注册品牌主题和 CoreConfig，选择广告 Provider（MAX → AdMob → 占位），启动归因、提醒
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}

import android.app.Application
import android.util.Log
import com.appbase.core.AdIds
import com.appbase.core.CoreConfig
import com.appbase.core.CoreServices
import com.appbase.core.ads.AdBackBlocker
import com.appbase.core.ads.AdMobAdProvider
import com.appbase.core.ads.Ads
import com.appbase.core.ads.MaxAdProvider
import com.appbase.core.ads.PlaceholderAdProvider
import com.appbase.core.attribution.Attribution
import com.appbase.core.common.Reminders
import {{CODE_PACKAGE}}.ui.theme.installAppTheme

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 最先注册品牌主题和配置：之后的颜色 token、字体样式、广告、提醒都依赖它们
        installAppTheme()
        CoreServices.init(this, coreConfig())
        Attribution.init(this)
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
        // 有真正有用的提醒（例如到期、定期任务）时再配 ReminderSetup（Worker、通知图标 R.drawable.ic_notification、品牌色），否则保持 null
        reminder = null,
    )

    companion object {
        lateinit var instance: App
            private set
    }
}
