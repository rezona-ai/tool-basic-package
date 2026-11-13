# ui/
> L2 | 父级: ../CLAUDE.md

成员清单
AppNav.kt: 路由中枢，底层常驻土星舞台 + NavHost；启动页是文件管理首页；底部导航 FILES/CLEAN/MORE/SETTINGS，CLEAN（土星页）的导航放在玻璃面板内，其余页单独显示；处理返回插屏、通知直达扫描
Placements.kt: Orbit Clean 各页面的广告位名称（返回插屏、开屏在 core/ads 的 AdPlacement）
theme/Theme.kt: Orbit Clean 配色真相源，OrbitPalette（深空底 + 冷灰三档文字 + 品牌青 Primary / 土星金 Secondary）经 installOrbitTheme 注册给 core；原型独有的玻璃面板、微光渐变 token；AppTheme
theme/Type.kt: 字体真相源，Unbounded（大数字/品牌名）+ Sora（其余，字体文件在 src/main/res/font），OrbitText 文字样式
saturn/: 土星场景引擎（着色器 / Canvas 降级 / 粒子 / 镜头），见 saturn/CLAUDE.md
components/Design.kt: 原型画面缩放层，DesignFrame（1dp = 屏宽/390）、DesignDensity、Modifier.reportTop、PlainScreen（常规页底色与系统栏留白）
components/Orbit.kt: 原型组件，每个对应一条 CSS 规则：OrbitTopBar、GlassSheet（背景模糊）、PrimaryCta、ToolTile、OrbitButton、OrbitRow、ValueText、ProgressLine、OrbitNavBar
components/Processing.kt: SaturnProgressView（扫描/清理两种气氛的土星进度页）、ProcessingGate（动画 → 插屏 → 内容）
components/KindStyle.kt: FileKind 的图标与颜色（通用列表组件在 core/ui/Common.kt）
home/HomeViewModel.kt: 首页存储用量，回到前台时刷新
home/HomeScreen.kt: 清理页（CLEAN 标签，原首页），土星 + 用量轨道 + 大号百分比 + 主按钮 + 四个快捷入口（文件管理 + 三个清理工具）
scan/ScanViewModel.kt: 一键扫描状态，ScanUi.from 把仓库结果折叠为每类一行 + 可清理空间
scan/JunkScanScreen.kt: 扫描页，雷达光束 + 吸入粒子，完成后弹插屏，点类别进文件列表
filelist/FileListScreen.kt: 文件列表与删除，FileListViewModel 管勾选与清理，清理时显示土星清理动画
done/CleanDoneViewModel.kt: 完成页状态，StorageDelta 清理前后用量、recommendsFor 继续清理推荐
done/CleanDoneScreen.kt: 完成页，冲击波 + 迸发粒子、用量对比、推荐、Banner、回首页；进入后按 core/common 的 ReviewPrompt 请求应用内评分
more/MoreScreen.kt: 更多页，文件管理器入口、系统信息、分类入口（含最近文件）
browser/BrowserScreen.kt: 文件管理器（启动页 = 底部导航 FILES 页 + 子目录页）：首页列出授权文件夹 + Add a folder；浏览/打开、多选复制/移动/重命名/分享/删除、新建文件夹、按名搜索
browser/FilesOverview.kt: 文件管理首页头部（仅根目录）：存储用量卡、按类型浏览、Storage cleanup 入口
info/DeviceInfoScreen.kt: 设备信息分组列表
splash/SplashScreen.kt: 冷启动闪屏

还原原则: 土星场景页（首页/扫描/清理/完成/处理中）包在 DesignFrame 里，尺寸、字号、坐标直接照抄 HTML 原型 CSS；常规页走 PlainScreen + core/ui/Common.kt。
公共页面: 设置页、文件夹授权页、通用组件、启动流程都在 core/ui，这里的 AppNav 只负责编排。
分层: Screen 只渲染与转发事件；业务折叠放 ViewModel（纯函数可单测）；广告节奏（插屏时机）作为 UI 副作用留在 Screen 的 LaunchedEffect。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
