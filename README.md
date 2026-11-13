# AppBase：安卓工具类 IAA 马甲基座

一套用来批量出「安卓工具类 + 广告变现（IAA）」应用的基座。公共能力（广告、频控、归因、评分、提醒、文件夹授权、主题骨架、通用页面）放在 `core/`，每个品牌应用（马甲包）是 `apps/` 下的一个独立模块，只放自己的品牌值和业务代码。

- 首个品牌：**Orbit Clean**（深空土星主题的文件管理 + 清理），已在 Google Play 上架。
- 出新包：在 Claude Code 里说「基于当前基座代码输出一个马甲包应用」，由 `new-sub-app` skill 引导完成（见[第 7 节](#7-用-skill-出马甲包)）。

> ⚠️ **Orbit Clean 已上架，`apps/orbitclean/` 下的任何文件都不要改**（代码、资源、`brand.properties`、`store/`）。对 `core/`、`build-logic/` 的改动只能做加法：不加新配置时，Orbit Clean 的构建产物必须和原来一致。

---

## 目录

1. [技术栈与环境](#1-技术栈与环境)
2. [快速开始](#2-快速开始)
3. [目录结构](#3-目录结构)
4. [core 公共模块](#4-core-公共模块)
5. [广告、频控与归因](#5-广告频控与归因)
6. [品牌应用的配置](#6-品牌应用的配置)
7. [用 skill 出马甲包](#7-用-skill-出马甲包)
8. [手动新增品牌应用](#8-手动新增品牌应用)
9. [上架前清单](#9-上架前清单)
10. [约定与禁忌](#10-约定与禁忌)
11. [已知缺口](#11-已知缺口)
12. [文档索引](#12-文档索引)

---

## 1. 技术栈与环境

| 项 | 版本 / 说明 |
|---|---|
| Kotlin | 2.2.21 |
| Jetpack Compose | BOM 2025.10.01 + Material3，AGSL RuntimeShader（Orbit Clean 的土星场景） |
| AGP | 8.13.1 |
| SDK | minSdk 26 / targetSdk 36，Java 17 |
| 广告 | Google AdMob（play-services-ads 24.5.0）、AppLovin MAX 13.7.0（可选）、Google UMP（GDPR 授权） |
| 归因 | Adjust 5.8.1 + Install Referrer |
| 其他 | WorkManager（提醒）、Play In-App Review（评分）、Coil（缩略图） |

依赖版本的唯一来源是 [gradle/libs.versions.toml](gradle/libs.versions.toml)。新加的依赖版本要至少发布两周。

本机需要：
- Android SDK，路径写在 `local.properties`（不入库）。
- 打能上传的 release 包时：上传密钥（`*.jks`）和对应的 `keystore-*.properties`，都放在仓库根目录，不入库。

## 2. 快速开始

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew :apps:orbitclean:assembleDebug
```

```bash
./gradlew :apps:orbitclean:assembleRelease
```

- 产物在 `apps/<品牌>/build/outputs/`，文件名以品牌目录名开头，例如 `orbitclean-debug.apk`。
- **debug 包**：广告位固定用 Google 测试 ID，不读 `brand.properties` 里的广告位。
- **release APK**：会先跑 `validateBrand`（检查包名、网址、邮箱是不是示例值），用于本机测试。
- **上传用的 AAB**（`bundleRelease`）：会再跑 `validateUpload`。AdMob 还是 Google 测试 ID 时构建直接失败，所以基座默认配置打不出可上传的 AAB，这是有意的。

## 3. 目录结构

```
core/                     公共模块（包名 com.appbase.core），不含任何品牌值
  common/                 CoreServices / CoreConfig、格式化、评分时机、提醒框架
  ads/                    广告 Provider、频控 AdPolicy、Adjust 归因
  storage/                SAF 文件夹授权与文件读写
  ui/                     主题骨架、通用组件、设置页、启动流程
apps/                     品牌应用，每个子目录一个模块（:apps:<目录名>），自动发现
  orbitclean/             Orbit Clean（已上架，只读）
  REGISTRY.md             已出包登记表：出新包前必读
build-logic/              构建约定插件
  convention/             appbase.core-library、appbase.brand-app（BrandAppPlugin.kt）
.claude/skills/new-sub-app/  出马甲包的 skill（模板、预设、scaffold.py 生成模块、store_assets.py 商店素材）
docs/                     接入说明、提审材料、马甲包调研
gradle/libs.versions.toml 依赖版本
```

[settings.gradle.kts](settings.gradle.kts) 会把 `apps/` 下每个含 `build.gradle.kts` 的目录自动注册成模块，新增品牌不用改它。

## 4. core 公共模块

依赖方向只能是 `common ← ads、storage ← ui`，品牌应用依赖全部四个模块。core 不能引用任何品牌代码，Gradle 模块隔离保证写了也编译不过。详细地图见 [core/CLAUDE.md](core/CLAUDE.md)。

| 模块 | 提供什么 |
|---|---|
| `:core:common` | **CoreServices / CoreConfig**：品牌在启动时注入的配置（debug、名称、版本、隐私和反馈链接、广告 ID、Adjust Token、提醒设置）。**Format**：文件大小、复数、日期格式化。**ReviewPrompt**：Play 应用内评分的时机。**Reminders**：本地提醒框架（开关、通知权限、定时运行品牌的 Worker、发通知）。**ExternalLaunch**：标记「是我们自己跳出了 App」，回来时不弹热启动开屏。 |
| `:core:ads` | **AdProvider** 接口及 AdMob、MAX、占位三种实现；**AdPolicy** 频控；返回插屏、开屏入口；**Attribution**（Adjust 归因和广告收入上报）。`AdPlacement` 里只定义返回插屏和开屏，其余广告位由各品牌自己定义。 |
| `:core:storage` | 基于 SAF 的文件层：**FolderAccess**（文件夹授权、路径和文档 Uri 互转）、**FileStore**（浏览、删除、重命名、新建、复制移动、搜索、整体索引、存储用量，变化时发 `StoreChange`）、**FileIntents**（打开、分享文件）。不申请任何存储权限。 |
| `:core:ui` | **Palette + BrandStyle**：配色和风格参数（圆角倍率、卡片画法、按钮画法、图标底座形状），默认值就是 Orbit Clean 的样子。**Common.kt**：GradientButton、TopBar、AppCard、SectionTitle、IconBadge、FileThumb、FileRow、LoadingState、EmptyState 等通用组件。**SettingsScreen**：通用设置页（文件夹授权和提醒开关可选，分享、评分、隐私政策、隐私设置、反馈、版本）。**StartupActivity**：品牌 Activity 基类（闪屏、隐私授权、冷 / 热启动开屏、系统栏、提醒通知点击），`FolderGate` 是首次文件夹授权的门槛。**FolderAccessScreen**：文件夹授权页。**AdMask**：返回插屏前的过渡遮罩。 |

### 品牌如何接入 core

品牌的 `Application.onCreate` 里按顺序做：

1. 注册品牌主题（配色、字体、风格）：`BrandTheme.install(...)`
2. `CoreServices.init(this, CoreConfig(...))`，值来自 `BuildConfig`（由 `brand.properties` 注入）
3. `Attribution.init(this)`
4. 选择广告 Provider：配置了 MAX 用 MAX，否则配置了 AdMob 用 AdMob，都没有用占位实现；然后 `Ads.provider.init(this)`
5. `AdBackBlocker.install(this)`、`Reminders.schedule(this)`

完整写法可参考 skill 模板里的 [App.kt](.claude/skills/new-sub-app/template/src/main/java/__PACKAGE_PATH__/App.kt)。

## 5. 广告、频控与归因

- **没有远程开关。** 四种广告位（横幅、插屏、开屏、返回插屏）按 `AdPolicy` 固定开启。如果某个广告位出了政策问题，只能重新发版来改。
- **频控参数**（[AdPolicy.kt](core/ads/src/main/java/com/appbase/core/ads/AdPolicy.kt)，debug 测试广告也走同一套规则）：

| 参数 | 值 | 含义 |
|---|---|---|
| `FULLSCREEN_INTERVAL_MS` | 30 秒 | 两次全屏广告（插屏、开屏）的最短间隔 |
| `MAX_INTERSTITIALS_PER_SESSION` | 8 | 每次会话的插屏上限（开屏不计入） |
| `PROCESSING_MIN_MS` | 2.5 秒 | 功能页「处理中」动画最短时长 |
| `AD_WAIT_MAX_MS` | 8 秒 | 处理中动画等待插屏加载的上限 |
| `MASK_MIN_MS` / `MASK_MAX_MS` | 0.8 / 1.5 秒 | 返回、点通知进入时过渡遮罩的停留 / 最长等待 |
| `COLD_START_WAIT_MS` | 8 秒 | 冷启动闪屏最多等开屏广告多久 |
| `WARM_START_MIN_BACKGROUND_MS` | 3 秒 | 离开超过这个时长再回来才弹热启动开屏 |

- **测试 ID：** 基座只接 Google 官方测试广告。`brand.properties` 里的 `ADMOB_*` 都是测试 ID。
- **正式 ID：** 写进各包自己的 `brand.local.properties`（见下一节）。
- **GDPR：** 通过 Google UMP 弹授权，需要在 AdMob 后台为每个应用发布 GDPR 消息。
- **Adjust：** `ADJUST_APP_TOKEN` 留空时不初始化。debug 包走 Sandbox 环境，release 包走 Production。接入细节见 [docs/ADJUST_INTEGRATION.md](docs/ADJUST_INTEGRATION.md)。

## 6. 品牌应用的配置

每个品牌应用由 `build-logic` 的 `appbase.brand-app` 插件（[BrandAppPlugin.kt](build-logic/convention/src/main/kotlin/BrandAppPlugin.kt)）统一构建。品牌目录里的 `build.gradle.kts` 只需写 `namespace`（代码包名）和依赖，其余都从配置文件注入。

### 6.1 取值优先级

| 优先级 | 来源 | 是否入库 | 作用范围 | 用来放 |
|---|---|---|---|---|
| 1（最高） | `apps/<品牌>/brand.local.properties` | 否 | 只对这一个包 | 正式的 AdMob / AppLovin / Adjust ID |
| 2 | 同名 Gradle 属性（`-P` 或 `~/.gradle/gradle.properties`） | 否 | **所有包共用** | 本机通用设置，例如 `ADMOB_TEST_DEVICE_IDS` |
| 3 | `apps/<品牌>/brand.properties` | 是 | 只对这一个包 | 包名、版本、名称、链接、测试广告 ID |

- `brand.local.properties` 不存在或某一项留空时，就往下一级取。
- **不要把正式广告 ID 放进 Gradle 属性**：所有包都会读到同一套，会串号。打 AAB 时如果发现广告 ID 来自 Gradle 属性，构建会打出警告。

### 6.2 brand.properties 字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `APPLICATION_ID` | 是 | 上架包名，上架后不能再改；release 时不能是 `com.example.*` |
| `VERSION_CODE` / `VERSION_NAME` | 是 | 版本号 |
| `APP_NAME` | 是 | 应用名，同时写进 `app_name` 资源 |
| `APP_TAGLINE` | 否 | 品牌名下方的一句话介绍 |
| `SIGNING_PROPERTIES` | 否 | 根目录的密钥配置文件名（如 `keystore-xxx.properties`）。未填或文件不存在时，release 用 debug 签名，只能自测 |
| `PRIVACY_URL` | release 必填 | 隐私政策地址，必须是真实的 `https://` 地址 |
| `WEBSITE_URL` | release 必填 | 开发者网站，app-ads.txt 部署在它的根目录 |
| `FEEDBACK_EMAIL` | release 必填 | 反馈邮箱 |
| `ADMOB_APP_ID` | AAB 必填真实值 | AdMob 应用 ID，写进清单 |
| `ADMOB_BANNER_AD_UNIT_ID` / `ADMOB_INTERSTITIAL_AD_UNIT_ID` / `ADMOB_APP_OPEN_AD_UNIT_ID` | AAB 必填真实值 | 三个 AdMob 广告位；debug 包不读，固定用测试 ID |
| `ADMOB_TEST_DEVICE_IDS` | 否 | 只收测试广告的设备 ID，逗号分隔，建议放 `~/.gradle/gradle.properties` |
| `APPLOVIN_SDK_KEY`、`MAX_*_AD_UNIT_ID` | 否 | AppLovin MAX；保持 `YOUR_` 前缀时不初始化，直接用 AdMob |
| `ADJUST_APP_TOKEN` | 否 | Adjust App Token，留空不初始化 |

### 6.3 构建校验

| 任务 | 何时运行 | 拦截什么 |
|---|---|---|
| `validateBrand` | 所有 release 构建前 | 包名是示例或不合法；隐私地址、网站地址不是真实的 `https://`；反馈邮箱无效或是 example.com |
| `validateUpload` | `bundleRelease` 前 | AdMob 应用 ID 或任一广告位为空、或仍是 Google 测试 ID |

### 6.4 品牌目录里还有什么

| 路径 | 内容 |
|---|---|
| `src/main/java/...` | 品牌代码：Application、MainActivity、导航、页面、主题、广告位名称（`ui/Placements.kt`） |
| `src/main/res/` | 启动图标、通知图标、字体、窗口底色、`app_name` 以外的字符串 |
| `src/main/assets/` | 字体许可证等 |
| `src/test/` | 品牌业务规则的单测 |
| `store/` | 商店素材：`icon-512.png`、`feature-graphic.png`、`screenshots/`；隐私政策页面 `privacy-policy/index.html`；`app-ads.txt`；商店描述 `listing.md`；提审材料 `submission.md`（Play Console 每张表的逐项答案） |

## 7. 用 skill 出马甲包

skill 位置：[.claude/skills/new-sub-app/](.claude/skills/new-sub-app/SKILL.md)。在本仓库打开 Claude Code 就能用。

### 7.1 怎么触发

对 Claude Code 说下面任意一句：
- 「基于当前基座代码输出一个马甲包应用」
- 「出一个新马甲包」
- 「新建一个品牌应用」

### 7.2 流程

| 步骤 | 做什么 |
|---|---|
| 1. 功能方向 | 先读 [apps/REGISTRY.md](apps/REGISTRY.md)，排除和已有包重叠的方向，从方向库里挑 4 个让你选（也可以填自己的方向） |
| 2. 主题色 | 从 8 套预设配色里挑 4 套，避开已有包的视觉 |
| 3. UI 风格 | 从 6 种预设风格里挑 4 种 |
| 4. 品牌信息 | 包名、应用名、隐私政策地址、网站地址，再问反馈邮箱和一句话介绍。每项都可以选「随机占位」 |
| 5. AdMob | 应用 ID 和横幅、插屏、开屏广告位，默认 Google 测试 ID。填了真实 ID 会写进 `brand.local.properties`，不入库 |
| 6. 确认 | 汇总全部选择，确认后才生成 |
| 7. 生成 | 用模板搭出 `apps/<slug>/`，然后实现核心功能（首页、功能页、结果页、广告位、评分），按广告规则放广告，填写隐私政策、商店描述和提审材料，更新登记表 |
| 8. 验证 | 单测和 debug 包；release 包（R8 混淆后）装到模拟器上把核心流程走一遍；检查崩溃日志和合并后的权限；生成商店素材并检查 |
| 9. 交付 | 汇报生成内容、两种包的验证结果、商店素材和提审材料、差异化清单结论、上传前你要做的事，以及生成上传密钥的命令 |

占位值（`com.example.*`、`example.com`、测试广告 ID）能打 debug 包；release 会被 `validateBrand` 拦下，AAB 会被 `validateUpload` 拦下，这是故意的。skill 不会自动提交 git。

### 7.3 方向库与预设

**功能方向**（[references/directions.md](.claude/skills/new-sub-app/references/directions.md)）都尽量不需要敏感权限，优先用系统选择器（Photo Picker、SAF）：

| id | 方向 |
|---|---|
| `private-vault` | 私密保险箱 |
| `image-compressor` | 图片压缩与格式转换 |
| `pdf-maker` | 图片转 PDF |
| `exif-cleaner` | 照片隐私信息清除 |
| `qr-toolkit` | 二维码工具 |
| `zip-tool` | 压缩包工具 |
| `batch-renamer` | 批量重命名 |
| `video-compressor` | 视频压缩 |

**主题色**（[presets.json](.claude/skills/new-sub-app/presets.json) 的 `palettes`）：

| 浅色 | 深色 |
|---|---|
| `ocean-light` 海盐蓝 | `midnight-dark` 午夜蓝 |
| `forest-light` 森绿 | `graphite-dark` 石墨 |
| `sunset-light` 暖橙 | `emerald-dark` 翡翠 |
| `lavender-light` 薰衣草 | `rose-dark` 玫瑰 |

**UI 风格**（`styles`）：

| id | 名称 | 特点 |
|---|---|---|
| `soft-cards` | 柔和卡片 | 大圆角、浮起卡片、纯色胶囊按钮、圆形图标底座 |
| `outline-minimal` | 线框极简 | 小圆角、只有描边、描边按钮、方形图标底座，信息密度高 |
| `flat-material` | 扁平 Material | 中等圆角、纯色平铺卡片、纯色方角按钮 |
| `sharp-tech` | 锐利科技 | 近直角、描边面板、纯色大写按钮、等宽字体 |
| `classic-serif` | 经典衬线 | 中小圆角、平铺卡片、描边胶囊按钮、衬线标题 |
| `rounded-playful` | 圆润活泼 | 超大圆角、浅色填充卡片、渐变胶囊按钮 |

### 7.4 skill 的硬性规则

- **只做核心功能不同的包。** 核心用途、主流程、数据来源与权限必须和登记表里的所有包都不同。换颜色和风格不能替代功能差异，否则会被 Play 判为重复内容。
- **不做规避类的事。** 不为规避相似度检测做代码混淆或资源打乱，不做审核期和上线后内容不同，不提供多账号、身份或设备伪装方面的建议。
- **不复制 Orbit Clean 的视觉标识。** 土星场景、`Orbit.kt` / `Design.kt` 原型组件、Unbounded / Sora 字体组合都不进新包。
- **权限最小化。** 不申请所有文件访问、照片视频权限。
- **广告规则。**
  - 插屏只在一次核心操作的结果出来之后弹，每次最多一次。
  - 不为等广告加载做假进度：本身很快的操作直接出结果。
  - 广告不挡功能按钮。
  - `AdBackBlocker` 保持关闭。
- **不经手密钥和密码。** 上传密钥只给你生成命令，密码由你自己设。

### 7.5 直接用生成脚本

skill 内部用 [scaffold.py](.claude/skills/new-sub-app/scaffold.py)，也可以手动运行（在仓库根目录）：

```bash
python3 .claude/skills/new-sub-app/scaffold.py suggest <slug> "<应用名>"
```

```bash
python3 .claude/skills/new-sub-app/scaffold.py create <config.json>
```

- `suggest` 打印一份随机占位配置（JSON）。
- `create` 按配置生成 `apps/<slug>/`，并列出还要手写的占位（隐私政策正文、商店长描述）。
- `config.json` 的字段说明见脚本文件头。

生成后只是骨架，首页是占位卡片；核心功能、隐私政策、商店描述、提审材料还要按方向补完。

### 7.6 商店素材脚本

[store_assets.py](.claude/skills/new-sub-app/store_assets.py) 生成并检查 Play 要求的商店素材，输出到 `apps/<slug>/store/`。需要 Pillow 和 Google Chrome（用无头模式渲染）。

```bash
python3 .claude/skills/new-sub-app/store_assets.py graphics <slug>
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py demo on
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py screenshot <slug> <name>
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py demo off
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py check <slug>
```

| 命令 | 作用 |
|---|---|
| `graphics` | 把自适应图标（矢量背景 + 前景）渲染成 `icon-512.png`（512×512，满版正方形，Play 会自己加圆角）；用图标渐变、应用名和一句话介绍生成 `feature-graphic.png`（1024×500，无透明通道） |
| `demo on` / `demo off` | 截图模式：打开飞行模式让广告加载不出来，状态栏固定 12:00、满电、不显示通知。打开后要冷启动应用再截；`off` 恢复网络和状态栏 |
| `screenshot` | 等画面稳定后截模拟器当前屏幕，按比例放进 1080×1920（9:16）画布，两侧用边缘像素补齐。当前前台是广告页时拒绝截图。输出到 `screenshots/<序号>-<name>.png` |
| `check` | 按 Play 要求检查图标、置顶大图和截图（尺寸、格式、透明通道、2–8 张、长宽比），并提示是否满足推荐位要求（至少 4 张 9:16） |

脚本拦不住闪屏这类静止画面，每张截图都要打开看一眼。

## 8. 手动新增品牌应用

不用 skill 时，最简单的做法是先用 `scaffold.py create` 生成骨架，再手动补完。要点：

1. 在 `apps/` 下新建目录，放 `brand.properties`、`build.gradle.kts`（只写 `namespace` 和依赖）和 `src/`。core 不用改，`settings.gradle.kts` 会自动发现。
2. Application 里按[第 4 节](#品牌如何接入-core)接入 core。
3. MainActivity 继承 `StartupActivity`。需要文件夹授权时，用 `FolderGate { ... }` 包住导航，设置页传 `onFolders`。
4. 页面优先用 `:core:ui` 的组件和颜色 token，不写字面量颜色。
5. 跳出 App 前（系统选择器、分享、打开文件）调用 `ExternalLaunch.mark()`，否则回来会弹热启动开屏。`:core:storage` 的 `openFile`、`shareFiles` 已经调用过。
6. 完成一次核心操作后：先 `ReviewPrompt.recordClean` 计数，再 `maybeAsk` 请求评分；结果出来之后再展示插屏。广告位名称加到品牌的 `ui/Placements.kt`。
7. 只有真正有用的提醒才配 `ReminderSetup`，同时在清单里加 `POST_NOTIFICATIONS`。
8. 在 [apps/REGISTRY.md](apps/REGISTRY.md) 加一行登记。

## 9. 上架前清单

完整清单见 [references/checklist.md](.claude/skills/new-sub-app/references/checklist.md)，分三部分：

- **A. 差异化**（生成前、提审前各对照一次）：核心用途、主流程、数据来源和权限、页面结构、视觉、商店页、技术身份（包名、上传密钥、AdMob 应用、隐私网址）每个包都要独立。
- **B. 生成后自检**：
  - 单测和 debug 包通过；模拟器上走完冷启动开屏 → 首页 → 核心流程 → 结果页插屏 → 返回 → 设置页。
  - release 包（R8 混淆后）装机，核心流程走完无崩溃（`adb logcat -b crash` 为空）。
  - 合并后的清单只有预期权限；广告位置符合广告规则。
  - `store_assets.py check` 通过；隐私政策、商店描述、提审材料、登记表都已填写。
- **C. 上传前（你来做）：**
  - 用 `keytool` 生成上传密钥，在根目录建 `keystore-<slug>.properties`，在 `brand.properties` 的 `SIGNING_PROPERTIES` 里指向它。
  - `brand.properties` 的占位值换成真实值：包名、隐私地址、网站地址、反馈邮箱。
  - 正式广告 ID 写进 `brand.local.properties`。
  - 隐私政策和 app-ads.txt 部署到网站，并能访问。
  - `./gradlew :apps:<slug>:bundleRelease` 通过。
  - AdMob 后台为这个应用发布 GDPR 消息，并开启展示级广告收入（Adjust 需要）。
  - Play Console：按 `store/submission.md` 逐项填写（数据安全、广告 ID 声明、内容分级、目标受众、商店页、审核说明），素材用 `store/` 下生成的文件。
  - 新的个人开发者账号需要封闭测试：12 人 × 14 天。

生成上传密钥的命令（密码自己设，不要交给别人）：

```bash
keytool -genkeypair -v -keystore <slug>-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

## 10. 约定与禁忌

- **Orbit Clean 只读。** 已上架，`apps/orbitclean/` 一律不改。基座改动后要确认 Orbit Clean 的构建产物没有变化。
- **core 不含品牌值。** 不放品牌名称、颜色、文案、链接、广告 ID，也不依赖品牌代码。品牌差异只放在 `apps/<品牌>/` 下。
- **core 模块依赖只能单向**：`common ← ads、storage ← ui`。新增公共能力时先判断它属于哪个模块。
- **不申请存储权限。** 只读写用户用系统文件夹选择器（SAF）授权的文件夹。
- **密钥不入库。** `*.jks`、`keystore*.properties`、`brand.local.properties`、`local.properties` 都在 `.gitignore` 里。
- **正式广告 ID 不入库。** 只放在各包的 `brand.local.properties`。
- **每个子目录有自己的 `CLAUDE.md`。** 改了结构要同步更新它们，Claude Code 靠这些地图理解代码。

## 11. 已知缺口

| 方面 | 缺口 |
|---|---|
| 基座工程 | 没有远程开关，广告策略只能发版修改；没有崩溃和数据统计，只能看 Play Console 的 Android Vitals；单测较少，没有 CI；公共页面文字是写在 Kotlin 里的英文，没做多语言；`ReviewPrompt.recordClean`、提醒默认时刻等命名还带着清理场景 |
| skill | 还没完整跑过一次真实出包（release 装机、商店素材、提审材料这几步已用临时包单独验证过）；启动图标和置顶大图是简单几何图形加文字，想更有辨识度需要设计；只用系统字体；方向库的可行性还没在真机上验证 |
| 广告合规 | GDPR 授权查询超时 5 秒、状态仍未知时会照常请求广告，对欧洲用户有合规风险；这是 core 的行为，改它会影响 Orbit Clean，需要加一个默认关闭、新包单独打开的开关 |
| 上架运营 | 新包的隐私政策和 app-ads.txt 部署在哪个域名还没定；版本号升级、登记表更新、隐私政策部署都靠手动 |
| Orbit Clean | 仓库和线上的隐私政策仍写着「All files access」，与 1.0.5 起的实际权限不符（按约定不在本仓库修改，需单独决定） |

## 12. 文档索引

| 文档 | 内容 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 仓库总地图（Claude Code 读取） |
| [core/CLAUDE.md](core/CLAUDE.md) | core 四个模块的地图与依赖规则 |
| [apps/REGISTRY.md](apps/REGISTRY.md) | 已出包登记表：用途、主流程、权限、视觉、已占用方向 |
| [docs/ADJUST_INTEGRATION.md](docs/ADJUST_INTEGRATION.md) | Adjust 归因、广告收入上报、DMA 授权信号的接入 |
| [docs/PRIVACY_POLICY_SETUP.md](docs/PRIVACY_POLICY_SETUP.md) | 隐私政策页面的部署 |
| [docs/Materials_for_interrogation.md](docs/Materials_for_interrogation.md) | Play 提审：移除所有文件访问权限后的提交步骤与商店页 |
| [docs/马甲包调研文档-第一版.md](docs/马甲包调研文档-第一版.md) | 马甲包调研（含差异化分级） |
| [docs/马甲包调研文档-第二版.md](docs/马甲包调研文档-第二版.md) | 马甲包调研（第二版结论） |
| [.claude/skills/new-sub-app/SKILL.md](.claude/skills/new-sub-app/SKILL.md) | 出马甲包 skill 的完整步骤 |
