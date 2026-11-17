# Tidy Grove 提审材料

> 由 new-sub-app skill 生成于 2026-11-16。照着这份在 Play Console 里逐项填写。
> 带「核对」的项目要按包里实际用到的 SDK 和权限再确认一遍。和实际不符是被拒的最常见原因，基座的第一个包就因为权限说明和实际不符被拒过三次。
>
> **当前是无广告首版（白包，`brand.properties` 的 `ADS=off`）**：包里没有任何广告、授权弹窗、归因 SDK，没有广告 ID 权限，下面的声明都按「无广告」填。
> 上架稳定后发带广告的版本，按第 5 节改，不要沿用这份无广告的答案。

## 0. 基本信息

| 项 | 值 |
|---|---|
| 应用名 | Tidy Grove |
| 包名 | com.example.tidygrove |
| 默认语言 | English (United States) – en-US |
| 应用或游戏 | 应用 |
| 免费或付费 | 免费 |
| 类别 | 工具（Tools） |
| 联系邮箱 | tidygrove@example.com（会公开在商店页，必须是真实、能收信的邮箱） |
| 网站 | https://example.com/tidygrove/（无广告版不需要 `app-ads.txt`，发带广告版本前再部署到域名根目录） |
| 隐私政策 | https://example.com/tidygrove/privacy/（提审时必须能打开，内容和下面的数据安全表单一致） |

## 1. 应用内容（Policy → App content）

### 隐私政策
填 https://example.com/tidygrove/privacy/。应用内的入口在「设置 → Privacy policy」。

### 应用访问权限（App access）
选 **All functionality is available without any access restrictions**：没有登录，也没有付费墙。

### 广告（Ads）
选 **No, my app does not contain ads**。包里没有广告 SDK（`verify_release.py start tidygrove` 后可用 `aapt dump permissions` 和 dex 检查核对）。

### 内容分级（Content rating）
- **类别：** All Other App Types（实用工具、效率类）
- **问卷：**
  - 暴力、血腥、性、粗俗语言、受管制物品、赌博：全部选 **No**
  - 用户之间能否互动或交换内容：**No**
  - 是否共享用户的位置信息：**No**（应用不读取位置，也没有会收集位置的 SDK）
  - 是否允许购买数字商品：**No**
  - 是否不受限制地访问互联网（比如内置浏览器）：**No**
- **预期结果：** 各地区都是最低年龄分级（IARC 3+ / Everyone）

### 目标受众和内容（Target audience and content）
- **目标年龄：** 只勾 **18 岁及以上**。和以后带广告的版本保持一致：中途改目标年龄会重新审核，勾了 13 岁以下就适用「家庭」政策，广告 SDK 和广告内容的要求都会大幅提高。
- **是否可能吸引儿童：** No。商店页、图标、截图都不要用卡通、儿童形象。

### 广告 ID（Advertising ID）
- **是否使用广告 ID：** **No**。合并后的清单里没有 `com.google.android.gms.permission.AD_ID`（build-logic 在 `ADS=off` 时排除广告 SDK，并在清单里兜底去掉）。

### 数据安全（Data safety）

**总体问题：**

| 问题 | 回答 |
|---|---|
| 是否收集或分享所需类型的用户数据 | **No**。包里没有广告、分析、归因或崩溃上报 SDK，应用自己也不上传任何数据 |

选 No 之后不用再填数据类型、加密、删除方式。

**应用自身处理的数据：**

不需要单独申报。数据安全表单只统计离开设备的数据，这些都留在本机：
- **文件名、大小、修改时间**：只在本机读取，用来扫描冗余文件、重复文件、大文件和文件管理，不上传。查找重复文件时会在本机读取同样大小的文件内容做比对，也不上传。
- **设置**（提醒开关、上次清理时间）：存在应用私有存储，不上传；应用备份已关闭（`allowBackup=false`）。
- **已授权的文件夹**：由系统保存的文件夹授权（SAF 持久授权），用户可以在设置页 Folder access 里移除。

### 其他声明
政府应用、金融功能、健康、新闻应用、COVID-19 相关：全部选 **No / 不适用**。

### 敏感权限和 API
无需声明。合并后的清单里没有存储（`MANAGE_EXTERNAL_STORAGE`、`READ/WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_*`）、相机、通讯录、位置等权限：
- 访问文件用系统文件夹选择器（`ACTION_OPEN_DOCUMENT_TREE`），用户选中并点「允许」后，只能读写那个文件夹。
- `POST_NOTIFICATIONS`：Android 13+ 的运行时权限，用于可选的清理提醒，不需要 Play 声明。
- 合并清单里的其他权限都是普通权限：`INTERNET`、`ACCESS_NETWORK_STATE`、`WAKE_LOCK`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`（后四个来自 WorkManager，用于定时提醒），没有 `AD_ID`（以 `verify_release.py start tidygrove` 的输出为准）。

## 2. 商店页（Grow → Store presence → Main store listing）

| 项 | 内容 / 文件 |
|---|---|
| 应用名（≤30 字符） | Tidy Grove |
| 简短说明（≤80 字符） | Clear clutter, keep what matters |
| 完整说明 | 见同目录的 `listing.md` |
| 应用图标 | `store/icon-512.png` |
| 置顶大图 | `store/feature-graphic.png` |
| 手机截图 | `store/screenshots/`（真实页面，按文件名顺序上传） |

素材用 `python3 .claude/skills/new-sub-app/store_assets.py check <slug>` 检查过再上传。

## 3. 审核说明（App access 或提交时的备注，英文）

```
Tidy Grove works without an account; every feature is available on first launch.

How to try the main feature:
1. On first launch, tap "Add" next to a folder such as Download or Documents (or "Choose another folder"), tap "Use this folder" and "Allow", then tap "Continue".
2. On the home screen, tap "Start a sweep". The app looks for leftover files, duplicates and large files in the allowed folders.
3. Tick the groups to clear (or tap "Look" to review individual files), tap "Clear", and confirm. A summary of what was removed is shown.
4. Open the "Folders" tab to browse the allowed folders; press and hold a file to copy, move, rename, share or delete it.

Ads: this version contains no ads and no advertising, analytics or attribution SDKs.

Permissions: see the privacy policy at https://example.com/tidygrove/privacy/. The app does not request access to all files or to the photo library; files are chosen by the user through the system picker.
```

## 4. 发布流程

1. **上传密钥：** 运行 `keytool` 生成，在根目录建 `keystore-<slug>.properties`，在 `brand.properties` 的 `SIGNING_PROPERTIES` 里指向它。
2. **广告 ID：** 无广告版不需要，`ADS=off` 时打 AAB 不要求正式 AdMob ID，账号也可以先不填 `ADMOB_PUBLISHER_ID`。
3. **网站：** `account.py site studio-b` 生成后上传，确认隐私政策能打开（发带广告版本前再补 `app-ads.txt`）。
4. **打包：** `./gradlew :apps:tidygrove:bundleRelease`，会经过 validateBrand、validateUpload 和账号串号校验。
5. **封闭测试（视账号而定）：** 2023-11-13 之后注册的个人开发者账号，要先在封闭测试轨道连续 14 天保持至少 12 名测试者，才能申请正式版。组织账号不需要。
6. **提交前：** 看一眼 Play Console 的预发布报告（Pre-launch report），里面有崩溃或无障碍问题就先修。
7. **上架后：** 观察审核结果、崩溃和评分。稳定后再按第 5 节发带广告的版本。

## 5. 发带广告的版本（ADS=on）

同一个包名升级，不是新应用。Play 会审核这次更新，商店声明必须和新版本一致，**和更新同时提交**：

1. **AdMob：** 在 studio-b 对应的 AdMob 账号里新建应用并关联已上架的 Play 商店页，建横幅、插屏、开屏三个广告位，发布 GDPR 消息。把发布商 ID 填进 `accounts/studio-b/account.properties` 的 `ADMOB_PUBLISHER_ID`，正式 ID 写进 `apps/tidygrove/brand.local.properties`。
2. **代码配置：** `brand.properties` 改 `ADS=on`，`VERSION_CODE` / `VERSION_NAME` 递增（例如 10100 / 1.1.0）。
3. **网站：** `store/app-ads.txt` 改成本账号发布商，重新运行 `account.py site studio-b`，把 `app-ads.txt` 部署到域名根目录。
4. **隐私政策：** 用 `store/ads-version/privacy-policy.html` 替换 `store/privacy-policy/index.html`（日期改成更新当天，核对包名、邮箱），先部署到网站再提交更新。
5. **Play Console 声明：** 广告改 **Yes**；广告 ID 改 **Yes**（用途：Advertising or marketing、Fraud prevention, security, and compliance）；数据安全按 AdMob 官方说明（https://developers.google.com/admob/android/privacy/play-data-disclosure）申报：大致位置、应用互动、崩溃日志和诊断信息、设备或其他 ID，传输中加密 Yes。商店页会显示「含广告」。
6. **审核说明：** 把 Ads 一行改回：an app open ad may show on launch, a banner is shown on the home screen, and an interstitial may show only after a task is finished (never during it); the app uses the Google UMP consent form for users in the EEA/UK.
7. **验证：** `verify_release.py start tidygrove` 确认权限里出现 `AD_ID`、开屏 / 插屏 / 横幅按规则出现，再 `bundleRelease`（这时会要求正式 AdMob ID 和 app-ads.txt）。
