---
name: new-sub-app
description: 基于当前基座代码生成一个新的马甲包（品牌应用模块 apps/<slug>）。用户说"基于当前基座代码输出一个马甲包应用"、"出一个新马甲包"、"新建 sub-app / 新品牌应用"时使用。依次让用户选功能方向、主题色、UI 风格，填品牌信息和 AdMob ID，确认后生成模块代码、编译并在模拟器上验证。
---

# 生成马甲包（品牌应用模块）

基座结构：`core/` 下四个公共模块（common、ads、storage、ui），`apps/<slug>/` 每个目录一个品牌应用，构建约定在 `build-logic/`。新包 = 在 `apps/` 下新建一个模块，**core 不改**。用户说的"sub-app 目录"就是 `apps/<slug>/`。

开始前先读：`CLAUDE.md`、`core/CLAUDE.md`、`apps/REGISTRY.md`、本目录的 `references/directions.md` 与 `references/checklist.md`、`presets.json`。全程用中文和用户交流。

## 硬性规则

- **只做 L2 及以上**：新包的核心用途、主流程、数据来源与权限必须和 `apps/REGISTRY.md` 里所有已有包不同。主题色和 UI 风格只是让它看起来不同，不能替代功能差异。用户坚持要做重叠方向时，说明会被判重复内容，并请用户换方向。
- **不碰规避类做法**：不做代码混淆或资源打乱来规避相似度检测，不做审核期和上线后不同内容，不提供多账号隔离、身份或设备伪装方面的建议。
- **不复制 Orbit Clean 的视觉标识**：土星场景（ui/saturn）、原型组件（Orbit.kt、Design.kt）、Unbounded / Sora 字体组合不进新包。
- **权限最小化**：优先不需要权限的系统选择器（Photo Picker、SAF）。不申请所有文件访问、照片视频权限。
- **不经手密钥和密码**：上传密钥让用户自己用 keytool 生成。
- 用户可能随时改主意：任一步选了"其他"，按用户输入处理；信息不够时只追问那一项。

## 第 1 步：功能方向

1. 从 `references/directions.md` 里排除与 REGISTRY 重叠的，挑 4 个最合适的（可以提出库外的新方向，同样要能填满表里的每一栏）。
2. 用 AskUserQuestion 单选，header「功能方向」。每个选项：label 是方向名（≤5 个词），description 写清核心用途、主流程、所需权限、难度，以及和已有包的区别。

## 第 2 步：主题色

从 `presets.json` 的 palettes 里挑 4 个适合该方向、且和 REGISTRY 视觉列明显不同的（例如已有包是深色，就多给浅色）。AskUserQuestion 单选，header「主题色」，label 用预设 name，description 写 summary 和主色 / 辅色色值，preview 里放主要色值列表。

## 第 3 步：UI 风格

从 presets.json 的 styles 里挑 4 个。AskUserQuestion 单选，header「UI 风格」，description 写 summary（圆角、卡片、按钮、字体）。

## 第 4 步：品牌信息表单

1. 先根据方向起 2～3 个应用名候选，并定 slug（小写字母开头，3–30 位字母数字，不能和 apps/ 下已有目录重名）。
2. 运行 `python3 .claude/skills/new-sub-app/scaffold.py suggest <slug> "<应用名>"` 拿随机占位值。
3. AskUserQuestion 一次问 4 题：
   - 包名：第一个选项"随机占位（com.example.<slug>）"，第二个给一个像样的建议值。
   - 应用名：给 2～3 个候选。
   - 隐私政策地址：选项"随机占位（https://example.com/<slug>/privacy/）"。
   - 网站地址：选项"随机占位（https://example.com/<slug>/）"。
4. 再问一次反馈邮箱（选项"随机占位（<slug>@example.com）"），同时让用户确认一句话介绍（tagline，≤80 字符，给 2 个候选）。

用户在"其他"里填的值要校验格式：包名是合法包名且不以 com.example 开头，网址以 https:// 开头，邮箱合法。不合格就说明原因并重问那一项。占位值能打 debug 包，release 会被 validateBrand 拦下，这是故意的，要告诉用户。

## 第 5 步：AdMob 表单

AskUserQuestion 一次问 4 题：AdMob 应用 ID、横幅、插屏、开屏广告位。每题第一个选项"Google 测试 ID（占位）"。用户自己填的值要校验格式（应用 ID `ca-app-pub-<16位数字>~<数字>`，广告位 `ca-app-pub-<16位数字>/<数字>`）。说明：测试 ID 打不出可上传的 AAB（validateUpload 会拦），上架前要在 AdMob 后台为本包新建应用和广告位。真实 ID 不入库：scaffold.py 把它们写进 `apps/<slug>/brand.local.properties`（已被 .gitignore 忽略，构建时优先读它），brand.properties 始终保持测试 ID。不要把真实 ID 写进 brand.properties 或 ~/.gradle/gradle.properties（后者对所有包生效，会串号）。

## 第 6 步：确认

用一段简短的中文汇总：方向（核心用途、主流程、权限）、主题色、UI 风格、slug、包名、应用名、介绍、链接、邮箱、AdMob（是否占位）。再用 AskUserQuestion 问「按这个生成吗？」，选项"生成"和"修改某一项"。

## 第 7 步：生成

1. 在 scratchpad 写 config.json（字段见 scaffold.py 文件头说明），并为方向设计启动图标图形 `iconGlyph`：108 画布、只画在 21..87 安全区、颜色用 `@color/icon_fg`、简单几何形（2～4 个 path），和方向含义对应，不要用文字。方向需要的权限写进 `permissions`。
2. 运行 `python3 .claude/skills/new-sub-app/scaffold.py create <config.json>`。它会生成 `apps/<slug>/`，并列出要手写的占位。
3. 实现核心功能（这一步是重点，按 directions.md 的"实现要点"）：
   - 页面放在 `ui/<功能>/`，业务规则放 ViewModel 或纯 Kotlin 类，规则要有单测（`src/test`）。
   - 首页（ui/home/HomeScreen.kt）重写成围绕核心功能的入口，不是占位卡片。
   - 结果页：完成一次核心操作后，先 `ReviewPrompt.recordClean` 计数，再 `maybeAsk`；插屏 `showInterstitial(activity, Placements.RESULT_INTERSTITIAL)` 只放在结果出来之后。广告位名称都加到 `ui/Placements.kt`。
   - 用 `:core:ui` 的组件（AppCard、GradientButton、TopBar、IconBadge、FileRow、EmptyState…）和颜色 token，不写字面量颜色。
   - 跳出 App 前（系统选择器、分享、打开文件）调用 `ExternalLaunch.mark()`；`:core:storage` 的 `openFile`、`shareFiles` 已经调用过。
   - 需要文件夹授权的方向：MainActivity 用 `FolderGate { AppNav(...) }`，SettingsScreen 传 `onFolders`，并加一个文件夹授权路由（`FolderAccessScreen(onBack = ...)`）。
   - 有真实用途的提醒才配 `ReminderSetup`，同时在清单加 POST_NOTIFICATIONS，并给 SettingsScreen 传 `ReminderLabels`。
   - 新依赖先加进 `gradle/libs.versions.toml`，版本要至少发布两周。新依赖如果用到反射或序列化，在模块的 `proguard-rules.pro` 里补 keep 规则（第 8 步的 release 装机会暴露缺的规则）。
   - **广告规则（审核和 AdMob 政策都会查）：**
     - 插屏只在一次核心操作的结果出来之后弹，每次操作最多一次；不在操作进行中、不在首页刚打开、不在用户点通知进来时弹。
     - 不做假进度：本身很快的操作（生成二维码、读取 EXIF）直接出结果，不为等插屏加载加「处理中」动画或最短时长。确实耗时的操作（压缩、转码、打包）进度要反映真实进度。
     - 广告不能挡住功能按钮，也不能放在误点率高的位置（紧贴主按钮、列表行之间）。横幅只放首页底部这类固定位置。
     - 不打开 `AdBackBlocker`（全屏广告期间屏蔽返回键，有政策风险）。
4. 填写占位正文，内容必须和实际功能、权限一致：
   - `store/privacy-policy/index.html`：{{SUMMARY}}、{{WHAT_IT_DOES_HTML}}、{{PERMISSION_ROWS}}、{{LOCAL_DATA}}，用英文写。没填 ADJUST_APP_TOKEN 时删掉 Adjust 小节。
   - `store/listing.md`：{{FULL_DESCRIPTION}}，用英文写，列出的每个功能在包里都要真实存在。
   - `store/submission.md`（提审材料，中文）：
     - {{APP_DATA}}：应用自己处理哪些用户数据。一般是「文件只在本机处理，不上传」，按实际写。
     - {{PERMISSION_ANSWERS}}：每个敏感权限的 Play 声明答案。没有敏感权限就写「无需声明」。
     - {{CORE_FLOW}}：审核员体验核心功能的步骤，用英文写，编号 1–4 步。
     - 没用 Adjust 时删掉文中 Adjust 相关的行。
5. 更新 `apps/REGISTRY.md`（总览一行 + 详细记录）和模块里的 CLAUDE.md 成员清单。

## 第 8 步：验证

按 `references/checklist.md` 的 B 节逐项跑，发现问题就修，修完再跑。打开 Browser 前先看 `adb devices`；在模拟器上测试广告可以关，但不要点广告内容。

1. **debug 包：** 单测、debug 包，在模拟器上走一遍核心流程，确认无崩溃，截图看首页和设置页的配色与风格是否生效。
2. **release 包装机（审核员装的就是 release 包，R8 混淆后才会暴露的崩溃只能这样发现）：**
   - 占位值会被 validateBrand 拦下，验证时用 `-P` 临时换成可以通过检查的值（不改任何文件，包名加 `.verify` 前缀，和 debug 包并存）：

     ```bash
     ./gradlew :apps:<slug>:assembleRelease -PAPPLICATION_ID=com.verify.<slug> -PPRIVACY_URL=https://verify.invalid/privacy/ -PWEBSITE_URL=https://verify.invalid/ -PFEEDBACK_EMAIL=verify@verify.invalid
     ```

     brand.properties 已经是真实值时，直接 `./gradlew :apps:<slug>:assembleRelease`。
   - `adb install -r apps/<slug>/build/outputs/apk/release/<slug>-release.apk`，冷启动后把核心流程完整走一遍：开屏 → 首页 → 核心操作 → 结果页插屏 → 返回 → 设置页。
   - `adb logcat -b crash -d` 为空；有崩溃就补 keep 规则或修代码，重新打包再走。
   - 合并后的权限只有预期的：`aapt dump permissions <release apk>`。
   - 走完卸载：`adb uninstall com.verify.<slug>`。
3. **商店素材**（用 `store_assets.py`，在仓库根目录运行）：
   - `python3 .claude/skills/new-sub-app/store_assets.py graphics <slug>`：生成 `store/icon-512.png` 和 `store/feature-graphic.png`。打开两张图看一眼：图标图形是否居中、清楚，大图上的应用名和介绍有没有被截断。
   - `python3 .claude/skills/new-sub-app/store_assets.py demo on`：打开截图模式（飞行模式不出广告，状态栏固定 12:00）。然后 `am force-stop` 并冷启动应用，等闪屏结束（最多 8 秒）。
   - 依次走到 4 个页面，每个页面跑一次 `store_assets.py screenshot <slug> <name>`：首页（`home`）、核心操作中或参数页、结果页（`result`）、再选一个有代表性的页面。截图前页面要有真实内容（先用核心功能处理一两个示例文件），不要截空状态。
   - **每张截图都要打开看：** 闪屏、广告、加载动画、空白页、系统弹窗都不算，删掉重截。脚本会拒绝广告页，但拦不住闪屏这类静止画面。
   - `store_assets.py demo off` 恢复网络和状态栏。
   - `store_assets.py check <slug>` 通过（尺寸、格式、张数都符合 Play 要求）。

## 第 9 步：交付

用中文汇报：

- 生成了什么：模块路径、方向、配色 / 风格、核心页面、广告位、权限，并贴一两张模拟器截图。
- 验证结果：debug 包和 release 包分别在模拟器上验证了什么，哪些没有验证。
- 商店素材和提审材料：`store/` 下的图标、置顶大图、截图（`store_assets.py check` 的结果），以及 `store/submission.md`。
- 差异化清单 A 节逐项结论。
- 上传前用户要做的事：checklist C 节，以及生成上传密钥的命令（只给命令，密码由用户自己设）：

```bash
keytool -genkeypair -v -keystore <slug>-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

不提交 git，除非用户要求。
