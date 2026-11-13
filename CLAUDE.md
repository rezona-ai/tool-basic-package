# AppBase - 安卓工具类 IAA 马甲基座（首个品牌：Orbit Clean，深空土星主题）
Kotlin 2.2.21 + Jetpack Compose (BOM 2025.10.01) + Material3 + AGSL RuntimeShader · AGP 8.13.1 · minSdk 26 / targetSdk 36 · AdMob / AppLovin MAX + Adjust

<directory>
core/ - 马甲基座的公共模块（包名 com.appbase.core），不含任何品牌值，见 core/CLAUDE.md：common（CoreServices/CoreConfig、格式化、评分、提醒框架）← ads（广告、频控、Adjust）/ storage（SAF 文件读写）← ui（主题骨架、通用组件、设置页、启动流程）
apps/ - 品牌应用模块，每个子目录一个应用（:apps:<目录名>，settings.gradle.kts 自动发现）：brand.properties（包名、版本、签名、名称、链接、广告 ID）+ src/（品牌代码与资源）+ store/（商店图标、隐私政策、app-ads.txt 模板）
apps/orbitclean/ - Orbit Clean（土星主题文件管理 + 清理），代码在 com.orbitclean.app，地图见其 CLAUDE.md
apps/REGISTRY.md - 已出包登记表：每个包的核心用途、主流程、权限、视觉；出新包前必读，避免重复内容
.claude/skills/new-sub-app/ - 出马甲包的 skill："基于当前基座代码输出一个马甲包应用" → 选方向 / 主题色 / UI 风格 → 填品牌信息与 AdMob → 生成 apps/<slug> 并验证（模板、预设、scaffold.py 生成模块、store_assets.py 生成并检查商店素材，都在这里；每个包的提审材料生成在 apps/<slug>/store/submission.md）
build-logic/ - 构建约定插件：appbase.core-library（core 模块）、appbase.brand-app（读取 brand.properties、签名；validateBrand 拦 release 的示例包名/网址/邮箱，validateUpload 拦 AAB 里的 Google 测试广告 ID）
docs/ - 接入说明：Adjust、隐私政策；Play 提审：1.0.5 移除所有文件访问权限后的提交步骤与商店页（Materials_for_interrogation.md）；马甲包调研（第一版、第二版）
</directory>

<config>
settings.gradle.kts - 模块清单：core 四个模块 + apps 下自动发现的品牌应用
apps/<品牌>/build.gradle.kts - 品牌应用：namespace + 依赖（其余由 appbase.brand-app 注入）
gradle/libs.versions.toml - 依赖版本真相源
gradle.properties - Gradle 设置
local.properties - Android SDK 路径（不入库）；签名配置 keystore-*.properties 与 *.jks 在根目录（不入库），由 brand.properties 的 SIGNING_PROPERTIES 指向
</config>

构建：./gradlew testDebugUnitTest :apps:orbitclean:assembleDebug :apps:orbitclean:bundleRelease（产物在 apps/orbitclean/build/outputs/，文件名以 orbitclean- 开头）
界面约定（Orbit Clean）：深空单主题，只有土星金 + 品牌青两个强调色；启动页是文件管理首页（常规页）；不申请任何存储权限，只读写用户用系统文件夹选择器（SAF）授权的文件夹；大场景页面（CLEAN 清理页/扫描/清理中/完成）用 ui/saturn 的 SaturnScene，常规列表页用 :core:ui 的 Common.kt。
基座约定：core 不含任何品牌值、不依赖品牌代码（Gradle 模块隔离）；品牌差异只放在 apps/<品牌>/ 下。新增品牌 = apps 下新建目录（brand.properties + build.gradle.kts + 自己的代码），core 不用改。
广告约定：基座只接 Google 官方测试广告（brand.properties 里的 ADMOB_* 是测试 ID），没有远程开关，四种广告位按 AdPolicy 频控固定开启；出正式包时把该包自己在 AdMob 新建的 ID 写进 apps/<品牌>/brand.local.properties（不入库，只对本包生效，优先于 brand.properties 和同名 Gradle 属性），brand.properties 保持测试 ID。
设计稿：HTML 原型 https://claude.ai/artifact/D4D7LaCqhaayYoNw8C9v9q

法则: 极简·稳定·导航·版本精确
