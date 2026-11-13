# core/
> L2 | 父级: /CLAUDE.md

马甲基座的公共模块：所有品牌共用，不含任何品牌名称、颜色、文案、链接或广告 ID，不申请危险权限。代码包名 com.appbase.core。
品牌在 Application.onCreate 里依次调用 BrandTheme.install（颜色 + 字体）和 CoreServices.init（CoreConfig），之后这里的一切都可用。
构建由 build-logic 的 appbase.core-library 统一（SDK、Java 17、consumer-rules.pro）；有界面的模块另加 Compose 编译插件。

模块与依赖（只能单向）: common ← ads、storage ← ui；品牌应用依赖四个模块
common/ (:core:common): CoreServices.kt（CoreConfig：debug、名称、版本、隐私/反馈链接、AdIds、Adjust Token、ReminderSetup；CoreServices：config + context）、Format（formatSize/plural/formatDate）、ReviewPrompt（Play 应用内评分时机）、Reminders（本地提醒框架：开关、权限、定时运行品牌的 Worker、发通知）、ExternalLaunch（"刚刚是我们自己跳出 App"标记，热启动开屏据此跳过）；consumer-rules.pro 带 review-ktx 的 R8 规则
ads/ (:core:ads): AdProvider 接口 + AdMob/MAX/占位实现、AdPolicy 频控（没有远程开关，广告固定开启）、返回插屏、开屏入口；AdPlacement 只含返回插屏和开屏，其余广告位由品牌定义；attribution/Attribution.kt（Adjust 归因与广告收入上报）
storage/ (:core:storage): 文件夹授权（SAF）的文件层，FolderAccess（授权、路径 ↔ 文档 Uri）、FileStore（浏览/删除/重命名/新建/复制移动/搜索/整体索引/存储用量，变化通知 StoreChange）、CoreFiles（CoreServices.files 扩展）、StorageModels（FileItem/FileGroup/FileType/StorageInfo）、FileIntents（openFile/shareFiles）
ui/ (:core:ui): theme/Palette.kt（Palette 配色 + BrandStyle 风格参数：圆角倍率、卡片画法、按钮画法、图标底座形状 + BrandTheme 注册处 + CoreTheme + 公共颜色 token；BrandStyle 默认值即 Orbit Clean 的样子）、Common.kt（GradientButton、TopBar、AppCard、SectionTitle、IconBadge、FileThumb、FileRow、LoadingState、EmptyState、FileType 图标与颜色）、FolderAccessScreen.kt（文件夹授权页）、SettingsScreen.kt（通用设置页：文件夹授权/提醒开关可选，分享、评分、隐私、隐私设置、反馈、版本）、StartupActivity.kt（品牌 Activity 基类：闪屏、隐私授权、冷/热启动开屏、系统栏、提醒通知点击；FolderGate 首次授权门槛）、AdMask.kt（返回插屏的过渡遮罩）

规则: 模块之间只能按上面的方向依赖；任何模块都不能引用品牌代码（Gradle 已隔离，写了也编译不过）。新增公共能力时先判断它属于哪个模块，不要为方便把 ui 依赖下沉到 common。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
