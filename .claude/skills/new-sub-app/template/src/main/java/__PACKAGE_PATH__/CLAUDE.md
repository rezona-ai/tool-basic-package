# {{CODE_PACKAGE}}/
> L2 | 父级: /CLAUDE.md

{{APP_NAME}} 品牌应用模块（:apps:{{SLUG}}）的代码。方向：{{DIRECTION_NAME}}。品牌配置在模块根目录 brand.properties，公共部分在 core 下四个模块（见 /core/CLAUDE.md），登记在 apps/REGISTRY.md。

成员清单
App.kt: Application，注册主题（installAppTheme）和 CoreConfig，选择广告 Provider，启动归因、提醒
MainActivity.kt: 继承 :core:ui 的 StartupActivity，提供闪屏、主题、主界面
ui/AppNav.kt: 路由中枢，底部导航（首页 + 设置），功能子页面
ui/Placements.kt: 广告位名称
ui/theme/Theme.kt: 配色「{{PALETTE_NAME}}」与风格「{{STYLE_NAME}}」
ui/home/HomeScreen.kt: 首页，核心功能入口

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
