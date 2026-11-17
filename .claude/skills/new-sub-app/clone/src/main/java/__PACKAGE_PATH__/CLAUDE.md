# {{CODE_PACKAGE}}/
> L2 | 父级: /CLAUDE.md

{{APP_NAME}} 品牌应用模块（:apps:{{SLUG}}）的代码。复刻模式：功能来自 :features:cleaner（清理 + 文件管理），本模块只放界面、文案、配色和广告位。
所属账号 {{ACCOUNT}}（brand.properties 的 ACCOUNT）；母包和变体组合登记在 apps/REGISTRY.md。
变体：{{VARIANTS}}（场景 {{SCENE}}、导航 {{NAV}}、首页 {{HOME}}、清理流程 {{CLEAN}}、文件页 {{BROWSER}}、完成页 {{DONE}}）。
不申请任何存储权限，只读写用户用系统文件夹选择器（SAF）授权的文件夹；首次打开先过 FolderGate 授权。

成员清单
App.kt: Application，注册主题和 CoreConfig，Cleaner.init（分组文案来自 data/Copy），选择广告 Provider，启动归因、提醒，图片 / 视频缩略图加载
MainActivity.kt: 继承 :core:ui 的 StartupActivity：闪屏、主题、FolderGate 包住 AppNav；点提醒通知直达扫描
data/Copy.kt: 功能文案真相源：类别名与简介、冗余分组名、重复组标题、提醒文案（每个复刻包都要重写）
data/ExternalApps.kt: 交给其他应用（模板自带，本应用暂未用到）
reminder/ReminderWorker.kt: 定时提醒：规则在 :features:cleaner 的 ReminderRules，这里只写通知文案
ui/AppNav.kt: 路由中枢（变体 {{NAV}}）
ui/Routes.kt: 两种导航共用的路由：扫描、类别、完成、子目录、设备信息、文件夹授权、设置
ui/Placements.kt: 广告位：home_banner、done_banner、scan_done_interstitial、clean_done_interstitial
ui/scene/CleanScene.kt: 大场景插槽（变体 {{SCENE}}）：IDLE / SCANNING / CLEANING / DONE 四个阶段各有动画，内置粒子层；卡片里用 compact
ui/motion/Fx.kt: 帧驱动的粒子层 FxLayer：空闲环绕 / 扫描汇聚 / 清理螺旋吸入 / 完成迸发 + 冲击波
ui/motion/Motion.kt: 动效工具箱：页面转场、数字滚动 animateCountUp、错开入场 enterUp、按压缩放 pressScale、对勾描线 AnimatedCheck、场景时钟、系统关动画判断
ui/scan/ScanReveal.kt: 扫描结果逐行揭晓的节奏、结果行的转圈 → 对勾
ui/home/HomeScreen.kt: 首页（变体 {{HOME}}）
ui/scan/ScanScreen.kt: 扫描页（变体 {{CLEAN}}）
ui/clean/CategoryScreen.kt: 单个类别的文件列表、勾选、删除
ui/clean/Cleaning.kt: 清理中整页 + 清理完成插屏
ui/done/DoneScreen.kt: 完成页（变体 {{DONE}}），进入时记清理次数并按规则请求评分
ui/browser/BrowserScreen.kt: 文件管理（布局 {{BROWSER}}）：授权文件夹、浏览、多选复制 / 移动 / 重命名 / 分享 / 删除、新建文件夹、搜索
ui/info/DeviceInfoScreen.kt: 设备信息（只读）
ui/components/CleanUi.kt: 类别图标与颜色、用量条、确认弹窗、完成页评分、通知权限
ui/components/Forms.kt: 表单组件（模板自带）
ui/theme/Theme.kt: 配色「{{PALETTE_NAME}}」与风格「{{STYLE_NAME}}」

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
