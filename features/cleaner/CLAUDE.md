# features/cleaner/
> L2 | 父级: /CLAUDE.md

清理 + 文件管理的共享功能层（:features:cleaner，包名 com.appbase.feature.cleaner）。从 Orbit Clean 的 data 层和页面状态复制而来，去掉了所有品牌值；复刻模式的品牌应用依赖它，Orbit Clean 自己不依赖（它的代码一行没动）。
和 core 一样不含任何品牌文案、颜色、链接：类别名、分组名、提示文字、通知文案都由品牌传入或由品牌页面按枚举自己写。

成员清单
Cleaner.kt: 入口：FileKind（CLEANABLE 三类 + BROWSABLE 六类）、GroupLabels（冗余分组名、重复组标题，品牌提供）、Cleaner.init（CoreServices.init 之后调用，创建 scans / system，删除时记清理时间）
data/ScanRepository.kt: 扫描层：各 FileKind 从 FileStore 同一份索引筛选并缓存；删除后剪枝、目录变化作废；重复文件三步确认（大小 → 采样哈希 → 全量哈希）
data/SystemInfoRepository.kt: 设备信息（CPU、电池、内存、系统版本），电池状态用枚举返回
data/CleanHistory.kt: 上次清理时间（与提醒开关共用 Reminders.prefs）
data/ReminderRules.kt: 提醒规则：存储快满 > 垃圾较多 > 很久没清理；decide 纯函数，check 给品牌 Worker 用，通知文案由品牌写
state/ScanState.kt: StorageViewModel（首页用量）、ScanUi / ScanRow（纯函数折叠，可指定类别）、ScanViewModel
state/CleanState.kt: CleanViewModel（一个或多个类别的勾选与删除，分步清理和一键清理共用）、defaultSelection、CleanResult
state/DoneState.kt: StorageDelta、Recommend、recommendsFor（按已清理类别排除）、DoneViewModel
state/BrowserState.kt: BrowserViewModel（目录、多选、搜索、复制 / 移动 / 删除 / 重命名 / 新建文件夹）、FileClipboard、BusyOp、BrowserMessage
src/test/CleanerStateTest.kt: 扫描折叠、默认勾选、用量对比、推荐、提醒优先级

依赖: :core:common、:core:storage（api），lifecycle-viewmodel、compose runtime（状态用 mutableStateOf）。不依赖 :core:ads / :core:ui：广告时机和界面都在品牌应用里。
规则: 只放“和品牌无关的功能逻辑”。改这里会影响所有复刻包，改完跑 :features:cleaner:testDebugUnitTest 和至少一个复刻包的编译。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
