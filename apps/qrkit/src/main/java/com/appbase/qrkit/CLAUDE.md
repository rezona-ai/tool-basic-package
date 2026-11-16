# com.appbase.qrkit/
> L2 | 父级: /CLAUDE.md

QR Atelier 品牌应用模块（:apps:qrkit）的代码。方向：二维码工具（生成 + 从照片识别）。品牌配置在模块根目录 brand.properties，公共部分在 core 下四个模块（见 /core/CLAUDE.md），登记在 apps/REGISTRY.md。
不申请任何敏感权限：选图用 Photo Picker，保存用系统保存对话框，分享走 cacheDir/shared + FileProvider（res/xml/file_paths.xml）。

成员清单
App.kt: Application，注册主题（installAppTheme）和 CoreConfig，初始化 HistoryStore，选择广告 Provider，启动归因
MainActivity.kt: 继承 :core:ui 的 StartupActivity，提供闪屏、主题、主界面
data/QrContent.kt: 二维码内容规则（纯 Kotlin，有单测）：各类型（文本、链接、Wi-Fi、联系人、邮箱、电话）的拼接、校验、识别结果解析
data/QrCodec.kt: ZXing 编解码（纯 Java 依赖，有单测）：文本 → 矩阵，像素 → 文本（两种二值化 + 反色）
data/QrImages.kt: Android 图片读写：渲染 Bitmap、从 Uri 识别、保存 PNG、FileProvider 分享、跳出应用前标记 ExternalLaunch
data/HistoryStore.kt: 最近 50 条记录（SharedPreferences + JSON，StateFlow 推给页面），只存本机
ui/AppNav.kt: 路由中枢：底部导航（首页 + 设置）、生成页、结果页（fresh=true 时由结果页弹一次插屏）；不使用返回插屏
ui/Placements.kt: 广告位名称（home_banner、result_interstitial）
ui/theme/Theme.kt: 配色「薰衣草 · 浅色」与风格「扁平 Material」
ui/components/QrUi.kt: 类型图标与颜色、二维码图片、输入框、次要按钮、类型标签
ui/home/HomeScreen.kt: 首页：生成 / 从照片识别两个入口、快捷类型、最近记录、底部横幅
ui/create/CreateScreen.kt: 生成页：选类型、填表单、校验
ui/result/ResultScreen.kt: 结果页：生成的显示二维码 + 保存 / 分享，识别的按类型给出操作；完成后插屏 + 应用内评分

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
