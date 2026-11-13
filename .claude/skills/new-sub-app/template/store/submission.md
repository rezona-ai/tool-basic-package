# {{APP_NAME}} 提审材料

> 由 new-sub-app skill 生成于 {{DATE}}。照着这份在 Play Console 里逐项填写。
> 带「核对」的项目要按包里实际用到的 SDK 和权限再确认一遍。和实际不符是被拒的最常见原因，Orbit Clean 1.0.2–1.0.4 就是因为权限说不清被拒了三次。

## 0. 基本信息

| 项 | 值 |
|---|---|
| 应用名 | {{APP_NAME}} |
| 包名 | {{APPLICATION_ID}} |
| 默认语言 | English (United States) – en-US |
| 应用或游戏 | 应用 |
| 免费或付费 | 免费 |
| 类别 | 工具（Tools） |
| 联系邮箱 | {{FEEDBACK_EMAIL}}（会公开在商店页，必须是真实、能收信的邮箱） |
| 网站 | {{WEBSITE_URL}}（`app-ads.txt` 要能在这个域名的根目录访问到） |
| 隐私政策 | {{PRIVACY_URL}}（提审时必须能打开，内容和下面的数据安全表单一致） |

## 1. 应用内容（Policy → App content）

### 隐私政策
填 {{PRIVACY_URL}}。应用内的入口在「设置 → Privacy policy」。

### 应用访问权限（App access）
选 **All functionality is available without any access restrictions**：没有登录，也没有付费墙。

### 广告（Ads）
选 **Yes, my app contains ads**。

### 内容分级（Content rating）
- **类别：** All Other App Types（实用工具、效率类）
- **问卷：**
  - 暴力、血腥、性、粗俗语言、受管制物品、赌博：全部选 **No**
  - 用户之间能否互动或交换内容：**No**
  - 是否共享用户的位置信息：**No**（应用本身不读取位置；广告 SDK 根据 IP 推断的大致位置在数据安全表单里申报）
  - 是否允许购买数字商品：**No**
  - 是否不受限制地访问互联网（比如内置浏览器）：**No**
- **预期结果：** 各地区都是最低年龄分级（IARC 3+ / Everyone）

### 目标受众和内容（Target audience and content）
- **目标年龄：** 只勾 **18 岁及以上**。勾了 13 岁以下就适用「家庭」政策，广告 SDK 和广告内容的要求都会大幅提高。
- **是否可能吸引儿童：** No。商店页、图标、截图都不要用卡通、儿童形象。

### 广告 ID（Advertising ID）
- **是否使用广告 ID：** Yes。Google Mobile Ads SDK 会合并 `com.google.android.gms.permission.AD_ID` 权限。
- **用途：**
  - Advertising or marketing（AdMob）
  - Analytics（用到 Adjust 时勾选，用于归因）
  - Fraud prevention, security, and compliance

### 数据安全（Data safety）
以 Google 和 Adjust 官方的申报说明为准，逐行核对：
- AdMob：https://developers.google.com/admob/android/privacy/play-data-disclosure
- Adjust：Adjust 帮助中心里的「Google Play data safety」说明（用到 Adjust 时才需要）

**总体问题：**

| 问题 | 回答 |
|---|---|
| 是否收集或分享所需类型的用户数据 | Yes（来自广告和归因 SDK） |
| 所有用户数据是否都在传输过程中加密 | Yes（SDK 都通过 HTTPS 通信） |
| 是否提供删除数据的方式 | 没有账号体系时选 No。用户可以在系统设置里重置或删除广告 ID |

**数据类型（核对）：**

| 数据类型 | 来源 | 收集 / 分享 | 用途 | 可选 |
|---|---|---|---|---|
| 位置 → 大致位置（由 IP 推断） | AdMob | 按 AdMob 官方说明 | 广告、分析、欺诈预防 | 否 |
| 应用活动 → 应用互动 | AdMob | 按 AdMob 官方说明 | 广告、分析 | 否 |
| 应用信息和性能 → 崩溃日志、诊断信息 | AdMob | 按 AdMob 官方说明 | 分析、欺诈预防 | 否 |
| 设备或其他 ID → 广告 ID、应用组 ID | AdMob、Adjust | 按官方说明 | 广告、分析、欺诈预防 | 否 |
| 应用活动 → 其他操作（安装来源、广告收入） | Adjust（填了 ADJUST_APP_TOKEN 才有） | 按 Adjust 官方说明 | 分析 | 否 |

**应用自身处理的数据：**

{{APP_DATA}}

### 其他声明
政府应用、金融功能、健康、新闻应用、COVID-19 相关：全部选 **No / 不适用**。

### 敏感权限和 API
{{PERMISSION_ANSWERS}}

## 2. 商店页（Grow → Store presence → Main store listing）

| 项 | 内容 / 文件 |
|---|---|
| 应用名（≤30 字符） | {{APP_NAME}} |
| 简短说明（≤80 字符） | {{TAGLINE}} |
| 完整说明 | 见同目录的 `listing.md` |
| 应用图标 | `store/icon-512.png` |
| 置顶大图 | `store/feature-graphic.png` |
| 手机截图 | `store/screenshots/`（真实页面，按文件名顺序上传） |

素材用 `python3 .claude/skills/new-sub-app/store_assets.py check <slug>` 检查过再上传。

## 3. 审核说明（App access 或提交时的备注，英文）

```
{{APP_NAME}} works without an account; every feature is available on first launch.

How to try the main feature:
{{CORE_FLOW}}

Ads: an app open ad may show on launch, a banner is shown on the home screen, and an interstitial may show only after a task is finished (never during it). The app uses the Google UMP consent form for users in the EEA/UK.

Permissions: see the privacy policy at {{PRIVACY_URL}}. The app does not request access to all files or to the photo library; files are chosen by the user through the system picker.
```

## 4. 发布流程

1. **上传密钥：** 运行 `keytool` 生成，在根目录建 `keystore-<slug>.properties`，在 `brand.properties` 的 `SIGNING_PROPERTIES` 里指向它。
2. **正式广告 ID：** 写进 `apps/<slug>/brand.local.properties`（不入库）。AdMob 后台要为本应用新建应用和广告位，发布 GDPR 消息，并开启展示级广告收入。
3. **网站：** 隐私政策和 `app-ads.txt` 部署好，并确认都能打开。
4. **打包：** `./gradlew :apps:<slug>:bundleRelease`，会经过 validateBrand 和 validateUpload 两道检查。
5. **封闭测试（视账号而定）：** 2023-11-13 之后注册的个人开发者账号，要先在封闭测试轨道连续 14 天保持至少 12 名测试者，才能申请正式版。组织账号不需要。
6. **提交前：** 看一眼 Play Console 的预发布报告（Pre-launch report），里面有崩溃或无障碍问题就先修。
7. **上架后：** 在 AdMob 里把应用关联到 Play 商店页，等 AdMob 审核应用。审核通过前广告基本没有填充。
