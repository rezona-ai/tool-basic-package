# Adjust 接入指南

本文记录 Orbit Clean 接入 Adjust 的做法：归因统计、AdMob 广告收入上报、Google DMA 授权信号。其他 app 照着做即可。

## 一、整体结构

| 项目 | 做法 |
|---|---|
| SDK | `com.adjust.sdk:adjust-android:5.8.1`，`com.android.installreferrer:installreferrer:2.2`；广告 ID 库由 `play-services-ads` 间接引入 |
| App Token | 写在品牌配置 `apps/<品牌>/brand.properties` 的 `ADJUST_APP_TOKEN`，留空时不初始化 Adjust |
| 环境 | debug 包上报到 **Sandbox**，release 包上报到 **Production**，测试数据不会混进正式数据 |
| 会话统计 | SDK 自带的 `SystemLifecycleContentProvider` 自动统计，**不要**再手动调用 `Adjust.onResume()` / `onPause()`，否则会重复计数 |
| 广告收入 | AdMob 每次展示的收入通过 `OnPaidEventListener` 上报给 Adjust |
| 授权 | GDPR 授权结束后，把 UMP 的结果转成 Google DMA 信号发给 Adjust |
| 混淆 | Adjust 的 AAR 自带混淆规则，`proguard-rules.pro` 不用额外加 |

关键文件：

| 文件 | 作用 |
|---|---|
| `apps/<品牌>/brand.properties` | `ADJUST_APP_TOKEN` |
| `core/ads/build.gradle.kts` | Adjust 与 Install Referrer 依赖 |
| `build-logic/.../BrandAppPlugin.kt` | 把 App Token 注入各品牌应用的 BuildConfig |
| `attribution/Attribution.kt` | 初始化、DMA 授权信号、广告收入上报 |
| `App.kt` | `Attribution.init(this)`，放在广告初始化之前 |
| `ads/AdMobAdProvider.kt` | 开屏、插屏、横幅设置 `OnPaidEventListener`；授权结束和修改隐私设置后调用 `Attribution.syncConsent` |

## 二、Adjust 后台配置

1. Adjust 后台 → **Apps** → **New app**，平台选 Android，填包名，记下 **App Token**（12 位小写字母和数字）。
2. 应用设置 → **Platforms → Android**：填 Google Play 商店链接（上架后再填也可以）。
3. **广告收入**：应用设置里开启 **Ad revenue**，来源选 AdMob SDK。这个功能可能需要单独开通套餐，没开通时 SDK 照常上报，后台不展示。
4. **测试设备**：Testing console 里可以用广告 ID 查询、清除测试设备，方便重复测试"首次安装"。

## 三、AdMob 后台配置

AdMob 后台 → **设置** → 打开 **展示级广告收入（Impression-level ad revenue）**。不打开的话，AdMob 不会回调 `OnPaidEventListener`，Adjust 就收不到广告收入。

## 四、代码接入

### 1. 依赖和配置

`gradle/libs.versions.toml`：

```toml
[versions]
adjust = "5.8.1"
installReferrer = "2.2"

[libraries]
adjust-android = { group = "com.adjust.sdk", name = "adjust-android", version.ref = "adjust" }
android-installreferrer = { group = "com.android.installreferrer", name = "installreferrer", version.ref = "installReferrer" }
```

`core/ads/build.gradle.kts`：

```kotlin
dependencies {
    implementation(libs.adjust.android)
    implementation(libs.android.installreferrer)
}
```

`build-logic/convention/src/main/kotlin/BrandAppPlugin.kt` 读取各品牌 `brand.properties` 的 `ADJUST_APP_TOKEN` 写入 BuildConfig，品牌的 Application 再经 `CoreConfig.adjustToken` 交给 core。

`apps/orbitclean/brand.properties`：

```properties
ADJUST_APP_TOKEN=<12 位 App Token>   # 基座留空，不初始化 Adjust
```

权限不用手动加：Adjust 的清单自带 `INTERNET` 和 `AD_ID`。

### 2. 初始化

在 `Application.onCreate` 里尽早调用：

```kotlin
val environment = if (BuildConfig.DEBUG) AdjustConfig.ENVIRONMENT_SANDBOX else AdjustConfig.ENVIRONMENT_PRODUCTION
val config = AdjustConfig(app, BuildConfig.ADJUST_APP_TOKEN, environment).apply {
    setLogLevel(if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.WARN)
}
Adjust.initSdk(config)
```

### 3. 广告收入

每个 AdMob 广告在展示前设置回调，`placement` 用 `AdPlacement` 里的位置名：

```kotlin
ad.onPaidEventListener = OnPaidEventListener { value ->
    val revenue = AdjustAdRevenue("admob_sdk").apply {
        setRevenue(value.valueMicros / 1_000_000.0, value.currencyCode)
        setAdRevenueUnit(ad.adUnitId)
        setAdRevenuePlacement(placement)
        ad.responseInfo.loadedAdapterResponseInfo?.adSourceName?.let { setAdRevenueNetwork(it) }
    }
    Adjust.trackAdRevenue(revenue)
}
```

横幅用 `AdView.onPaidEventListener`，写法相同。

### 4. Google DMA 授权信号

在欧洲投放 Google Ads 并用 Adjust 归因时，Google 要求提供用户的授权信号。Adjust SDK 不会自己读取 UMP 写入的 TCF 授权串，所以 `Attribution.syncConsent` 会读出来再发给 Adjust：

| Adjust 参数 | 取值 |
|---|---|
| `google_dma` / `eea` | `IABTCF_gdprApplies` 为 1 时是 1 |
| `google_dma` / `ad_user_data` | TCF 目的 1 和 7 都同意时是 1；不在 EEA 时是 1 |
| `google_dma` / `ad_personalization` | TCF 目的 3 和 4 都同意时是 1；不在 EEA 时是 1 |

调用时机：授权流程结束时、用户在设置页修改隐私设置后。结果和上次相同时不重复发送。

## 五、测试

用 debug 包测试，数据进 Sandbox：

```bash
adb logcat -s Adjust Attribution
```

正常流程的日志：

```
Attribution: Adjust started, environment=sandbox
Adjust     : Response message: Install tracked
Adjust     : Response message: Attribution found
Attribution: attribution: network=Organic campaign=
Attribution: google_dma sent: eea=true,ad_user_data=true,ad_personalization=true
Adjust     : Response message: OK                      ← third_party_sharing
Attribution: ad revenue [app_open_cold] 0 micros USD
Adjust     : Response message: Ad revenue tracked
```

说明：

- **测试广告的收入都是 0**，正常；release 包的真实广告才有金额。
- **"Install tracked" 每台设备只出现一次。** 想重新测首次安装，要在 Adjust 后台 Testing console 里清除这台设备，再卸载重装。
- **不要用 release 包反复测试**，会往 Production 里写测试数据。

## 六、上架前检查

- [ ] `ADJUST_APP_TOKEN` 已填写，release 包日志显示 `environment=production`
- [ ] AdMob 已开启展示级广告收入
- [ ] 隐私政策写明了 Adjust 收集的数据（见第 4 节"Install attribution and measurement"）
- [ ] Play Console 数据安全表单补充：设备或其他 ID、应用互动（安装、会话），用途加上"分析"；声明与第三方共享
- [ ] 上架后在 Adjust 后台填 Google Play 商店链接
