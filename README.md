# AppBase：安卓工具类 IAA 马甲基座

一套用来批量出「安卓工具类 + 广告变现（IAA）」应用的基座。公共能力（广告、频控、归因、评分、提醒、文件夹授权、主题骨架、通用页面）放在 `core/`，每个品牌应用（马甲包）是 `apps/` 下的一个独立模块，只放自己的品牌值和业务代码。

- 首个品牌：**Orbit Clean**（深空土星主题的文件管理 + 清理），已在 Google Play 上架。
- 已生成、未上架：**QR Atelier**（`apps/qrkit`，二维码工具）、**Namewise**（`apps/renamer`，批量重命名）。
- 出新包：在 Claude Code 里说「基于当前基座代码输出一个马甲包应用」，由 `new-sub-app` skill 引导完成，完整流程见[第 7 节](#7-用-skill-出马甲包)。

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
  qrkit/                  QR Atelier：二维码工具（skill 生成，未上架）
  renamer/                Namewise：批量重命名（skill 生成，同领域，未上架）
  REGISTRY.md             已出包登记表：出新包前必读
features/
  cleaner/                共享功能层：清理 + 文件管理（从 Orbit Clean 复制、去掉品牌值），复刻包依赖它
accounts/                 Play 账号配置（不入库，只有 README 和模板入库），复刻包用
build-logic/              构建约定插件
  convention/             appbase.core-library、appbase.brand-app（BrandAppPlugin.kt）、账号串号校验（AccountGuard.kt）
.claude/skills/new-sub-app/  出马甲包的 skill（template/ 模板、clone/ 复刻模板与变体、预设、scaffold.py 生成模块、
                             account.py 账号与账号网站、emu.py 操作模拟器、verify_release.py 验证 release 包、
                             store_assets.py 商店素材）
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
| 4（最低） | `accounts/<ACCOUNT>/account.properties` | 否 | 同一账号的包 | 只有 `SIGNING_PROPERTIES`、`FEEDBACK_EMAIL` 两项，在包自己没填时用账号的；只有填了 `ACCOUNT` 的包会读 |

- `brand.local.properties` 不存在或某一项留空时，就往下一级取。
- 没填 `ACCOUNT` 的包（Orbit Clean、QR Atelier、Namewise）只用前三级，构建结果和加账号功能之前完全一样。
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
| `ACCOUNT` | 复刻包必填 | 所属 Play 账号（`accounts/<ACCOUNT>/`）；填了才读账号默认值、做账号串号校验 |
| `CLONE_OF` / `CLONE_VARIANTS` | 复刻包自动写 | 母包和变体组合；构建不读，scaffold.py 用来拦重复 |

### 6.3 构建校验

| 任务 | 何时运行 | 拦截什么 |
|---|---|---|
| `validateBrand` | 所有 release 构建前 | 包名是示例或不合法；隐私地址、网站地址不是真实的 `https://`；反馈邮箱无效或是 example.com |
| `validateUpload` | `bundleRelease` 前 | AdMob 应用 ID 或任一广告位为空、或仍是 Google 测试 ID |
| 账号串号校验（AccountGuard） | `bundleRelease` 前，只对填了 `ACCOUNT` 的包 | 账号配置缺失；广告 ID 不属于本账号的发布商，或来自所有包共用的 Gradle 属性；没有上传密钥；隐私政策、网站不在本账号域名下；`store/app-ads.txt` 不是本账号的发布商；取值或 `src/main`、`store/` 的文字里出现别的账号或不属于本账号的包的名称、包名、域名、邮箱、发布商 ID。详见 [accounts/README.md](accounts/README.md) |

### 6.4 品牌目录里还有什么

| 路径 | 内容 |
|---|---|
| `src/main/java/...` | 品牌代码：Application、MainActivity、导航、页面、主题、广告位名称（`ui/Placements.kt`） |
| `src/main/res/` | 启动图标、通知图标、字体、窗口底色、`app_name` 以外的字符串 |
| `src/main/assets/` | 字体许可证等 |
| `src/test/` | 品牌业务规则的单测 |
| `store/` | 商店素材：`icon-512.png`、`feature-graphic.png`、`screenshots/`；隐私政策页面 `privacy-policy/index.html`；`app-ads.txt`；商店描述 `listing.md`；提审材料 `submission.md`（Play Console 每张表的逐项答案） |

## 7. 用 skill 出马甲包

skill 位置：[.claude/skills/new-sub-app/](.claude/skills/new-sub-app/SKILL.md)。在本仓库打开 Claude Code 就能用。已经用它出过两个包：[QR Atelier](apps/qrkit)（换领域，二维码工具）和 [Namewise](apps/renamer)（同领域，批量重命名）。

### 7.1 整体流程

```
准备（你）              对话里回答问题（你）           skill 自动完成                       交付后（你）
─────────────          ──────────────────          ──────────────────────────         ──────────────────
启动模拟器              0 出包方式                   7 生成模块、实现核心功能              试用 debug 包
新建 git 分支    →      1 功能方向           →      8 单测、debug / release 装机验证  →   看代码、提交
（可选）想好账号、网站    2 主题色  3 UI 风格          商店素材、截图                       换掉占位值
                       4 品牌信息 5 AdMob           填隐私政策、商店页、提审材料          上架前清单（第 9 节）
                       6 确认                       9 汇报
```

- 你只需要在第 0–6 步回答选择题，每一步都可以选「其他」自己填。
- 第 7–9 步由 skill 自己完成，中间不需要你操作。整个过程比较长，大部分时间花在实现核心功能和模拟器验证上。
- skill **不会提交 git**。

### 7.2 开始前准备

| 项 | 要求 | 为什么 |
|---|---|---|
| 模拟器 | 启动一个模拟器，`adb devices` 能看到它 | 第 8 步要在模拟器上走核心流程、截商店截图 |
| Google Chrome、Pillow | 本机装了 Chrome；Python 能 `import PIL` | 渲染商店图标和置顶大图、处理截图 |
| git 分支 | 从 `main` 新建一个分支，工作区干净 | 新包会生成几十个文件，方便单独审查和提交 |
| 账号、网站（可选） | 想好新包放在哪个 Play 开发者账号、隐私政策放在哪个网站 | 不影响生成，但上架前必须定；没定就先用占位值 |

```bash
adb devices
```

### 7.3 触发

对 Claude Code 说下面任意一句：
- 「基于当前基座代码输出一个马甲包应用」
- 「出一个新马甲包」
- 「新建一个品牌应用」

### 7.4 你要回答的问题（第 0–6 步）

| 步骤 | 你要选什么 | 建议 |
|---|---|---|
| 0. 出包方式 | **换领域**：功能和已有包完全不同，文件只通过照片选择器、系统保存对话框进出。**同领域**：和 Orbit Clean 同属文件工具，复用它的文件夹授权能力，出包更快，但核心功能必须不同。**复刻（L0/L1）**：功能和 Orbit Clean 相同，换皮肤、交互、文案，放到另一个 Play 账号，流程见 [7.11](#711-复刻模式l0--l1) | 同领域的包在同一个账号里一次只上一个；复刻包每个账号同一母包只放一个 |
| 1. 功能方向 | skill 读 [apps/REGISTRY.md](apps/REGISTRY.md) 后，从方向库对应的表里挑出 4 个没被占用的方向，每个写清用途、流程、权限、难度 | 第一次出某类包，优先选难度低、无新依赖的 |
| 2. 主题色 | 4 套预设配色，已避开已有包的视觉 | 已有包多是深色时选浅色，拉开区别 |
| 3. UI 风格 | 4 种预设风格（圆角、卡片、按钮、字体） | 避开已有包用过的风格 |
| 4. 品牌信息 | 包名、应用名、隐私政策地址、网站地址；再问反馈邮箱和一句话介绍 | 包名上架后不能改，最好这一步就定下来；网址、邮箱没定就选「随机占位」 |
| 5. AdMob | 应用 ID 和横幅、插屏、开屏三个广告位 | 还没在 AdMob 后台建应用就选「Google 测试 ID」，以后把正式 ID 写进 `brand.local.properties` |
| 6. 确认 | 看一遍汇总，选「生成」或「修改某一项」 | 同领域包会多一句「和 Orbit Clean 的区别」，确认它说得通 |

**关于占位值：** `com.example.*`、`example.com` 和测试广告 ID 都能打 debug 包、跑完所有验证。release 构建会被 `validateBrand` 拦下，AAB 会被 `validateUpload` 拦下，这是故意的，防止带着占位值上架。

### 7.5 skill 自动完成的事（第 7–9 步）

**第 7 步：生成和实现**
1. 用 `scaffold.py` 按你的选择生成 `apps/<slug>/` 骨架：品牌配置、主题、导航、启动图标、隐私政策和提审材料模板。同领域包会多出现场选文件夹的组件，设置页也接好了 Folder access。
2. 实现核心功能：首页、功能页、结果页，核心规则写成纯函数并配单测。广告位按广告规则放（见 [7.8](#78-skill-的硬性规则)）。
3. 按实际功能填写隐私政策、商店长描述、提审材料（`store/submission.md`）。
4. 在 [apps/REGISTRY.md](apps/REGISTRY.md) 登记这个包，更新模块里的 `CLAUDE.md`。

**第 8 步：验证**
1. **debug 包：** 跑单测、打 debug 包，装到模拟器上用 `emu.py` 把核心流程走一遍，确认没有崩溃。
2. **release 包：** 用 `verify_release.py` 打 R8 混淆后的 release 包，装机后再走一遍，同时检查合并后的权限里有没有需要声明的。审核员装的就是这种包。
3. **商店素材：** 渲染 512 图标和 1024×500 置顶大图；打开截图模式（飞行模式不出广告、状态栏固定 12:00），准备好真实内容后截 4 张截图，最后用 `check` 检查是否符合 Play 要求。
4. 清理模拟器上为测试建的文件。

**第 9 步：汇报**
- 生成了什么、两种包分别验证了什么、哪些没验证（例如应用内评分只在从 Play 安装时才会弹）。
- 商店素材检查结果、差异化清单逐项结论。
- 上架前你要做的事，以及生成上传密钥的命令。

**生成出来的东西：**

| 路径 | 内容 |
|---|---|
| `apps/<slug>/brand.properties` | 包名、版本、名称、链接、测试广告 ID |
| `apps/<slug>/brand.local.properties` | 只有你填了正式广告 ID 或 Adjust Token 才会生成，不入库 |
| `apps/<slug>/src/main/java/...` | 品牌代码，结构见同目录的 `CLAUDE.md` |
| `apps/<slug>/src/test/` | 核心规则的单测 |
| `apps/<slug>/store/icon-512.png`、`feature-graphic.png`、`screenshots/` | 商店素材 |
| `apps/<slug>/store/listing.md` | 商店页文案（名称、简短说明、长描述） |
| `apps/<slug>/store/submission.md` | Play Console 每张表的逐项答案（数据安全、广告 ID、内容分级、目标受众、审核说明） |
| `apps/<slug>/store/privacy-policy/index.html` | 隐私政策页面，部署到网站上用 |
| `apps/<slug>/store/app-ads.txt` | AdMob 的 app-ads.txt，放在网站根目录 |

### 7.6 交付后你要做的事

1. **试用：** 让 Claude 打 debug 包并装到模拟器，或者自己装：

   ```bash
   ./gradlew :apps:<slug>:assembleDebug
   ```

   ```bash
   adb install -r apps/<slug>/build/outputs/apk/debug/<slug>-debug.apk
   ```

2. **审查和提交：** 看一遍 `apps/<slug>/` 的代码和 `store/` 下的文案，有要改的直接告诉 Claude。确认后自己提交，或者让 Claude 提交。
3. **上架前：** 按[第 9 节](#9-上架前清单)的 C 部分做：定包名、换掉占位值、在 AdMob 建应用、部署隐私政策、生成上传密钥、打 AAB、按 `submission.md` 填 Play Console。

### 7.7 方向库与预设

**功能方向**（[references/directions.md](.claude/skills/new-sub-app/references/directions.md)）都尽量不需要敏感权限，分两张表：

| 出包方式 | id | 方向 |
|---|---|---|
| 换领域 | `image-compressor` | 图片压缩与格式转换 |
| 换领域 | `pdf-maker` | 图片转 PDF |
| 换领域 | `exif-cleaner` | 照片隐私信息清除 |
| 换领域 | `video-compressor` | 视频压缩 |
| 换领域 | `qr-toolkit` | 二维码工具（已占用：qrkit） |
| 同领域 | `batch-renamer` | 批量重命名（已占用：renamer） |
| 同领域 | `zip-tool` | 压缩包工具 |
| 同领域 | `private-vault` | 私密保险箱 |
| 同领域 | `folder-backup` | 文件夹备份 |
| 同领域 | `photo-sorter` | 照片按日期归档 |

**同领域的红线**（详见方向库）：
- 首页不能是文件浏览器或存储仪表盘。
- 不做清理，不做通用文件管理。
- 首屏不用 `FolderGate`（它和 Orbit Clean 的首屏一模一样），在需要文件夹的那一步现场选。
- 视觉、商店页不能和 Orbit Clean 相似。
- 同一个账号里同领域的包一次只上一个。

**主题色**（[presets.json](.claude/skills/new-sub-app/presets.json) 的 `palettes`）：

| 浅色 | 深色 |
|---|---|
| `ocean-light` 海盐蓝（renamer 在用） | `midnight-dark` 午夜蓝 |
| `forest-light` 森绿 | `graphite-dark` 石墨 |
| `sunset-light` 暖橙 | `emerald-dark` 翡翠 |
| `lavender-light` 薰衣草（qrkit 在用） | `rose-dark` 玫瑰 |

**UI 风格**（`styles`）：

| id | 名称 | 特点 |
|---|---|---|
| `soft-cards` | 柔和卡片 | 大圆角、浮起卡片、纯色胶囊按钮、圆形图标底座 |
| `outline-minimal` | 线框极简 | 小圆角、只有描边、描边按钮、方形图标底座，信息密度高 |
| `flat-material` | 扁平 Material（qrkit 在用） | 中等圆角、纯色平铺卡片、纯色方角按钮 |
| `sharp-tech` | 锐利科技（renamer 在用） | 近直角、描边面板、纯色大写按钮、等宽字体 |
| `classic-serif` | 经典衬线 | 中小圆角、平铺卡片、描边胶囊按钮、衬线标题 |
| `rounded-playful` | 圆润活泼 | 超大圆角、浅色填充卡片、渐变胶囊按钮 |

### 7.8 skill 的硬性规则

- **换领域、同领域只做核心功能不同的包。** 核心用途、主流程、数据来源与权限必须和登记表里的所有包都不同。换颜色和风格不能替代功能差异，否则会被 Play 判为重复内容。
- **同功能复刻只走复刻模式。** 前提是各 Play 账号互相隔离、AdMob 不共用：复刻包必须填 `ACCOUNT`，不能和母包同账号，同一账号同一母包只一个；账号之间的发布商、签名、网站、邮箱不串（打 AAB 时 AccountGuard 校验）。
- **不做规避类的事。** 不为规避相似度检测做代码混淆或资源打乱，不做审核期和上线后内容不同，不提供冒用身份、买卖账号、设备或网络伪装、躲避账号关联检测方面的做法。
- **不复制 Orbit Clean 的视觉标识。** 土星场景、`Orbit.kt` / `Design.kt` 原型组件、Unbounded / Sora 字体组合都不进新包（复刻包也一样）。
- **权限最小化。** 不申请所有文件访问、照片视频权限。
- **广告规则。**
  - 插屏只在一次核心操作的结果出来之后弹，每次最多一次；从历史记录打开结果不弹，子页面返回不加返回插屏。
  - 不为等广告加载做假进度：本身很快的操作直接出结果。
  - 广告不挡功能按钮。
  - `AdBackBlocker` 保持关闭。
- **不经手密钥和密码。** 上传密钥只给你生成命令，密码由你自己设。

### 7.9 工具脚本

skill 用到的脚本都在 `.claude/skills/new-sub-app/` 下，也可以单独运行（在仓库根目录）。

#### scaffold.py：生成模块骨架

```bash
python3 .claude/skills/new-sub-app/scaffold.py suggest <slug> "<应用名>"
```

```bash
python3 .claude/skills/new-sub-app/scaffold.py create <config.json>
```

- `suggest` 打印一份随机占位配置（JSON）。
- `create` 按配置生成 `apps/<slug>/`，并列出还要手写的占位（隐私政策正文、商店长描述、提审材料）。
- `config.json` 的字段说明见[脚本](.claude/skills/new-sub-app/scaffold.py)文件头。两个开关：
  - `"folderAccess": true`：同领域包。生成 `ui/folders/FolderPicker.kt`（现场选文件夹），并在设置页接好 Folder access 管理页。
  - `adjustToken`：填了才接 Adjust（Token 写进 `brand.local.properties`）；不填时，隐私政策和提审材料里的 Adjust 内容自动去掉。
  - 复刻模式另加 `"mode": "clone"`、`parent`、`account`、`variants`：先铺 `template/`，再用 `clone/` 和选中的 `clone/variants/<轴>/<取值>/` 覆盖；会拦同账号同母包、变体差异不足、和已有复刻同组合（见 7.11）。
- 生成的模块除了首页、导航、主题，还带：
  - `data/ExternalApps.kt`：跳出应用（打开链接、拨号、分享…）前自动标记，回来不弹开屏；分享应用自己生成的文件走 `cacheDir/shared` + FileProvider，不需要存储权限。
  - `ui/components/Forms.kt`：输入框、描边次要按钮、单选标签。
- 生成后只是骨架，首页是占位卡片。

#### emu.py：按文字操作模拟器

| 命令 | 作用 |
|---|---|
| `list` | 列出屏幕上带文字的控件和坐标 |
| `tap "<文字>" [序号]` | 按文字点击，先精确匹配，没有再按包含匹配 |
| `type "<输入框标签>" "<内容>"` | 点输入框、分段输入、核对内容，不对就清空重输；只能输入 ASCII |
| `hide-keyboard` | 只在键盘弹出时收起（键盘没弹出时按返回会退出页面） |
| `wait "<文字>" [秒]` | 等页面上出现这段文字 |
| `scroll up\|down` | 滑动半屏并等惯性停下；列表项被底部按钮挡住时先滚出来再点 |
| `top` / `close-ad` | 当前前台 Activity；前台是广告时关掉它 |
| `consent [yes\|no]` | 回答 GDPR 授权表单（debug 包模拟欧洲地区，全新安装首次启动必弹） |
| `shot <png>` | 截屏缩小，便于查看 |

#### verify_release.py：验证 release 包

```bash
python3 .claude/skills/new-sub-app/verify_release.py start <slug>
```

```bash
python3 .claude/skills/new-sub-app/verify_release.py finish <slug>
```

- `start`：打 release APK，列出合并后的权限并标出需要声明的敏感权限，安装并冷启动。品牌信息还是占位值时，自动用 `-P` 临时把包名换成 `com.verify.<slug>`，不改文件，可以和 debug 包同时装着。
- 两步之间用 `emu.py` 把核心流程走一遍。
- `finish`：检查崩溃日志，卸载验证包。

#### store_assets.py：商店素材

需要 Pillow 和 Google Chrome（用无头模式渲染），输出到 `apps/<slug>/store/`。

```bash
python3 .claude/skills/new-sub-app/store_assets.py graphics <slug>
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py demo on
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py screenshot <slug> <name> <序号>
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py demo off
```

```bash
python3 .claude/skills/new-sub-app/store_assets.py check <slug>
```

| 命令 | 作用 |
|---|---|
| `graphics` | 把自适应图标渲染成 `icon-512.png`（512×512，满版正方形，Play 会自己加圆角）；用图标渐变、应用名和一句话介绍生成 `feature-graphic.png`（1024×500，无透明通道） |
| `demo on` / `demo off` | 截图模式：打开飞行模式让广告加载不出来，状态栏固定 12:00、满电、不显示通知。打开后要冷启动应用再截；`off` 恢复网络和状态栏 |
| `screenshot` | 等画面稳定后截当前屏幕，放进 1080×1920（9:16）画布，两侧用边缘像素补齐。序号决定上传顺序，同序号会替换旧图。前台是广告、键盘没收起时拒绝截图 |
| `check` | 按 Play 要求检查图标、置顶大图和截图（尺寸、格式、透明通道、2–8 张、长宽比），并提示是否满足推荐位要求（至少 4 张 9:16） |

### 7.10 常见情况

| 现象 | 原因和处理 |
|---|---|
| 全新安装后第一次打开，弹出「Welcome to Publisher Test Ads」 | GDPR 授权表单，debug 包会模拟欧洲地区。用 `emu.py consent` 回答 |
| 生成或识别完，插屏没弹 | 距离上一次全屏广告（包括开屏）不到 30 秒，是频控，不是问题 |
| 截图里有广告、键盘或闪屏 | 先 `demo on` 再冷启动；`emu.py hide-keyboard` 收键盘；闪屏最多停 8 秒，等首页出现再截。脚本拦得住广告和键盘，拦不住闪屏，每张截图都要打开看 |
| 用 `emu.py type` 输入，一个字都没进去 | 输入法弹出了引导层（例如 Gboard 的「Try out your stylus」）。`type` 会自动收起它再重试 |
| 系统文件夹选择器里某个文件夹点不到 | 被底部的「Use this folder」按钮挡住了。先 `emu.py scroll down`（或 `scroll up`）把它滚出来，不要按坐标点 |
| 打 release 包时报 `validateBrand` 失败 | 品牌信息还是占位值。验证时用 `verify_release.py start`，它会临时换掉；上架前把 `brand.properties` 换成真实值 |
| 打 AAB 时报 `validateUpload` 失败 | 广告 ID 还是 Google 测试 ID。把正式 ID 写进 `apps/<slug>/brand.local.properties` |
| 想要「和 Orbit Clean 一样，只换界面」的包 | 第 0 步选「复刻」，放到另一个 Play 账号，见 7.11 |
| 复刻时 scaffold.py 报 `already has a clone of orbitclean` | 这个账号里已经有一个 Orbit Clean 的复刻包，换一个账号 |
| 复刻时 scaffold.py 报 `only N axes differ` 或 `same variants` | 变体和母包太像，或和已有复刻包同一组合，换几个轴 |
| 复刻包打 AAB 时报 `(ACCOUNT=...)` 开头的错误 | 账号串号校验没过，按提示改 `brand.local.properties`、`store/app-ads.txt` 或去掉源码里别的包的名称 |
| 电脑上同时连着真机和模拟器 | 先 `export ANDROID_SERIAL=emulator-5554`，否则 adb 会报 more than one device，脚本也可能操作到真机 |

### 7.11 复刻模式（L0 / L1）

**前提：** 各 Play 账号互相隔离、AdMob 不共用。Play 会把同一开发者（及关联账号）的重复应用判成垃圾应用，所以复刻包只能放到别的账号，并且账号之间的配置一个都不能串。

**结构：**

```
features/cleaner/                 功能层：从 Orbit Clean 复制的扫描、删除、文件浏览、提醒规则、页面状态（不含品牌值）
.claude/skills/new-sub-app/clone/ 复刻模板：公共页面（类别列表、清理中、文件页、设备信息、路由、文案 Copy.kt）
  variants/<轴>/<取值>/            各轴的页面变体，scaffold.py 按选择覆盖进去
accounts/<账号>/                  账号配置和账号网站（不入库）
```

**六个变体轴**（[presets.json](.claude/skills/new-sub-app/presets.json) 的 `cloneVariants`；母包 Orbit Clean 是 saturn / tabs / dashboard / stepwise / list / summary）：

| 轴 | 取值 |
|---|---|
| 场景 scene | `ring` 环形进度、`particles` 粒子云、`stack` 分层柱（不能用母包的 saturn） |
| 导航 nav | `tabs` 底部三标签、`hub` 单页入口 |
| 首页 home | `dashboard` 仪表盘、`grid` 宫格 |
| 清理流程 clean | `stepwise` 逐类勾选删除、`onetap` 一键清理全部勾选 |
| 文件页 browser | `list` 列表、`grid` 网格 |
| 完成页 done | `summary` 汇总、`checklist` 清单 |

至少 3 个轴要和母包不同；同一母包的复刻包之间组合不能完全相同。配色、风格、图标、全部文案（`data/Copy.kt`、页面文字、商店页、隐私政策）每个包重做。

**流程：** 第 0 步选「复刻」后，skill 依次问：所属账号（没有就新建）→ 变体组合 → 主题色 → UI 风格 → 品牌信息（默认用账号的网站和邮箱）→ AdMob（发布商必须是本账号的）→ 确认。生成后重写文案、跑验证、用 `account.py site <账号>` 生成本账号的网站文件。

**账号脚本：**

```bash
python3 .claude/skills/new-sub-app/account.py init <账号>
```

```bash
python3 .claude/skills/new-sub-app/account.py list
```

```bash
python3 .claude/skills/new-sub-app/account.py site <账号>
```

`init` 从模板建账号配置，`list` 列出账号、配置缺什么、每个账号下的包，`site` 生成 `accounts/<账号>/site/`（根目录 app-ads.txt + 每个包的隐私政策），整个目录上传到账号的 `WEBSITE_ROOT`。字段说明见 [accounts/README.md](accounts/README.md)。

## 8. 手动新增品牌应用

不用 skill 时，最简单的做法是先用 `scaffold.py create` 生成骨架，再手动补完。要点：

1. 在 `apps/` 下新建目录，放 `brand.properties`、`build.gradle.kts`（只写 `namespace` 和依赖）和 `src/`。core 不用改，`settings.gradle.kts` 会自动发现。
2. Application 里按[第 4 节](#品牌如何接入-core)接入 core。
3. MainActivity 继承 `StartupActivity`。需要文件夹授权时，用 `scaffold.py` 的 `"folderAccess": true` 生成现场选文件夹的组件；不要用 `FolderGate` 包住导航，它的首屏和 Orbit Clean 完全一样。
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
- **core、features 不含品牌值。** 不放品牌名称、颜色、文案、链接、广告 ID，也不依赖品牌代码。品牌差异只放在 `apps/<品牌>/` 下。改 `features/cleaner` 会影响所有复刻包，改完跑它的单测和至少一个复刻包的编译。
- **账号之间不串。** 复刻包的发布商、签名、网站、邮箱都用自己账号的；`accounts/` 下除了说明和模板都不入库。
- **core 模块依赖只能单向**：`common ← ads、storage ← ui`。新增公共能力时先判断它属于哪个模块。
- **不申请存储权限。** 只读写用户用系统文件夹选择器（SAF）授权的文件夹。
- **密钥不入库。** `*.jks`、`keystore*.properties`、`brand.local.properties`、`local.properties` 都在 `.gitignore` 里。
- **正式广告 ID 不入库。** 只放在各包的 `brand.local.properties`。
- **每个子目录有自己的 `CLAUDE.md`。** 改了结构要同步更新它们，Claude Code 靠这些地图理解代码。

## 11. 已知缺口

| 方面 | 缺口 |
|---|---|
| 基座工程 | 没有远程开关，广告策略只能发版修改；没有崩溃和数据统计，只能看 Play Console 的 Android Vitals；单测较少，没有 CI；公共页面文字是写在 Kotlin 里的英文，没做多语言；`ReviewPrompt.recordClean`、提醒默认时刻等命名还带着清理场景 |
| 复刻模式 | 只支持 Orbit Clean 一个母包；每轴 2～3 种变体，组合有限；还没出过真实的复刻包，三个验证用的临时包只在模拟器上跑过 debug，release 只验证了能打出来；复刻包和母包是同一套功能代码，差异只在界面、文案和账号上 |
| skill | 已完整出过两个包（qrkit、renamer），但都只在模拟器上验证过，没在真机上跑；启动图标和置顶大图是简单几何图形加文字，想更有辨识度需要设计；只用系统字体；应用内评分只能在从 Play 安装后验证 |
| 广告合规 | GDPR 授权查询超时 5 秒、状态仍未知时会照常请求广告，对欧洲用户有合规风险；这是 core 的行为，改它会影响 Orbit Clean，需要加一个默认关闭、新包单独打开的开关 |
| 上架运营 | 新包的隐私政策和 app-ads.txt 部署在哪个域名还没定；版本号升级、登记表更新、隐私政策部署都靠手动 |
| Orbit Clean | 仓库和线上的隐私政策仍写着「All files access」，与 1.0.5 起的实际权限不符（按约定不在本仓库修改，需单独决定） |

## 12. 文档索引

| 文档 | 内容 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 仓库总地图（Claude Code 读取） |
| [core/CLAUDE.md](core/CLAUDE.md) | core 四个模块的地图与依赖规则 |
| [apps/REGISTRY.md](apps/REGISTRY.md) | 已出包登记表：用途、主流程、权限、视觉、已占用方向、复刻包的账号与变体 |
| [features/cleaner/CLAUDE.md](features/cleaner/CLAUDE.md) | 共享功能层（清理 + 文件管理）的地图 |
| [accounts/README.md](accounts/README.md) | Play 账号配置字段、账号串号校验、账号脚本 |
| [docs/ADJUST_INTEGRATION.md](docs/ADJUST_INTEGRATION.md) | Adjust 归因、广告收入上报、DMA 授权信号的接入 |
| [docs/PRIVACY_POLICY_SETUP.md](docs/PRIVACY_POLICY_SETUP.md) | 隐私政策页面的部署 |
| [docs/Materials_for_interrogation.md](docs/Materials_for_interrogation.md) | Play 提审：移除所有文件访问权限后的提交步骤与商店页 |
| [docs/马甲包调研文档-第一版.md](docs/马甲包调研文档-第一版.md) | 马甲包调研（含差异化分级） |
| [docs/马甲包调研文档-第二版.md](docs/马甲包调研文档-第二版.md) | 马甲包调研（第二版结论，文末附 v3 决策：复刻模式） |
| [.claude/skills/new-sub-app/SKILL.md](.claude/skills/new-sub-app/SKILL.md) | 出马甲包 skill 的完整步骤 |
