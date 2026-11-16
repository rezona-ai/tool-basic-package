# com.appbase.renamer/
> L2 | 父级: /CLAUDE.md

Namewise 品牌应用模块（:apps:renamer）的代码。方向：批量重命名（同领域：复用 :core:storage 的文件夹授权和 FileStore.rename）。品牌配置在模块根目录 brand.properties，公共部分在 core 下四个模块（见 /core/CLAUDE.md），登记在 apps/REGISTRY.md。
不申请任何存储权限；首屏不用 FolderGate（和 Orbit Clean 的首屏一样），在规则页用 rememberFolderPicker 现场选文件夹。

成员清单
App.kt: Application，注册主题（installAppTheme）和 CoreConfig，初始化 HistoryStore，选择广告 Provider，启动归因
MainActivity.kt: 继承 :core:ui 的 StartupActivity，提供闪屏、主题、主界面
data/RenameRules.kt: 改名规则真相源（纯 Kotlin，有单测）：五种规则、排序、新名计算、问题检查（空名、非法字符、过长、重名，按不区分大小写比较）
data/Renamer.kt: 执行器：两段式（先改临时名再改目标名），支持互换和只改大小写；第二段失败时改回原名；撤销也走它
data/HistoryStore.kt: 最近 20 批改名记录（SharedPreferences + JSON，StateFlow），只存本机
data/ExternalApps.kt: 交给其他应用：跳出前标记 ExternalLaunch，分享应用自己生成的文件、分享 / 复制文本（本应用暂未用到）
ui/AppNav.kt: 路由中枢：底部导航（首页 + 设置）、设置里的 Folder access、规则页、结果页（fresh=true 时弹一次插屏）；返回不加插屏
ui/Placements.kt: 广告位名称（home_banner、result_interstitial）
ui/theme/Theme.kt: 配色「海盐蓝 · 浅色」与风格「锐利科技」
ui/components/Forms.kt: 表单组件：输入框、次要按钮、单选标签
ui/components/RenameUi.kt: 规则图标、分步标题、旧名 → 新名对照行（等宽字体）
ui/folders/FolderPicker.kt: 现场选文件夹（系统文件夹选择器，选中即长期授权）；已授权文件夹在设置页 Folder access 管理
ui/home/HomeScreen.kt: 首页：五种规则卡片（带示例）+ 最近改名批次 + 底部横幅
ui/rule/RuleViewModel.kt: 规则页状态：文件夹、文件、勾选、规则参数、排序、预览、执行进度（viewModelScope，离开页面不中断）
ui/rule/RuleScreen.kt: 规则页：1 选文件夹 → 2 调规则 → 3 勾选文件看对照 → 执行（真实进度）
ui/result/ResultScreen.kt: 结果页：改了多少、失败多少、每个文件旧名 → 新名，整批撤销；刚完成时插屏 + 应用内评分

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
