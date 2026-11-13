# com.orbitclean.app/
> L2 | 父级: /CLAUDE.md

Orbit Clean 品牌应用模块（:apps:orbitclean）的代码。品牌配置在模块根目录 brand.properties，公共部分在 core 下四个模块（见 /core/CLAUDE.md）。

成员清单
App.kt: Orbit Clean 的 Application：先注册品牌主题（installOrbitTheme）和 CoreConfig（来自 BuildConfig，即品牌配置），再创建 ScanRepository / SystemInfoRepository，选择广告 Provider（MAX → AdMob → 占位），启动归因、提醒；删除文件时记录清理时间
MainActivity.kt: 继承 :core:ui 的 StartupActivity，只提供闪屏、主题、主界面（FolderGate 包住 AppNav）；提醒通知点击直达扫描页
data/: Orbit Clean 的扫描层，ScanRepository（基于 :core:storage 的 FileStore 索引按 FileKind 筛选并缓存：冗余/大文件/重复/最近/按类型；跟随文件变化）、Models（FileKind）、SystemInfoRepository
reminder/: ReminderWorker（提醒内容：存储快满 > 垃圾较多 > 很久没清理）、CleanHistory（上次清理时间）；定时与通知框架在 :core:common 的 Reminders
ui/: Orbit Clean 的界面层，见 ui/CLAUDE.md

依赖方向: ui → data / reminder / core 模块；data、reminder → core 模块；core 模块由 Gradle 隔离，看不到这里的任何代码，需要的值经 CoreServices 的 CoreConfig 传入。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
