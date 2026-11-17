# 品牌应用登记表

每个品牌应用（apps/<目录>）一行。出新马甲包前先读这张表：换领域、同领域的新包，**核心用途、主流程、数据来源与权限**任何一项和已有的包相同，就换方向（Play 的重复内容政策，见 docs/马甲包调研文档-第一版.md「差异化分级」）。
复刻包（L0 / L1，skill 第 0 步方式 3）功能和母包相同，登记在下面的「复刻包」表，不占用方向；同一账号里同一母包只能有一个，变体组合不能重复。
新包生成后、提交审核前、上架后都要回来更新这一行。

## 总览

| 目录 | 应用名 | 上架包名 | 状态 | 核心用途（一句话） | 主流程 | 数据来源与权限 | 首页结构 | 视觉 |
|---|---|---|---|---|---|---|---|---|
| orbitclean | Orbit Clean | com.kexing.portvanguard | 1.0.5 已上架（真实广告）；基座分支里是测试广告 | 管理用户授权的文件夹，清理冗余、大文件、重复文件 | 选文件夹 → 文件首页浏览 / 一键扫描 → 勾选删除 → 完成页 | SAF 文件夹授权（无存储权限）、通知 | 文件管理首页（用量卡 + 按类型浏览 + 授权文件夹列表）+ 土星清理页 | 深空深色、土星金 + 品牌青、3D 土星场景、Unbounded/Sora 字体 |
| qrkit | QR Atelier | com.example.qrkit（占位，未定） | 已生成，未上架；品牌信息和 AdMob 都是占位 | 生成二维码（链接、文本、Wi-Fi、联系人），并识别照片里的二维码 | 首页选「生成」→ 选类型填内容 → 结果页保存 / 分享；或「从照片识别」→ 照片选择器选图 → 结果页打开链接 / 复制 / 加联系人 | 无权限：Photo Picker 选图、系统保存对话框写文件、FileProvider 分享；最近记录存本机 | 两个入口卡片（生成、识别）+ 快捷类型 + 最近记录列表，底部横幅 | 薰衣草浅色（靛紫 #6C5CE7 + 粉 #FF7EB6）、扁平 Material、系统字体、二维码定位块图标 |
| renamer | Namewise | com.example.renamer（占位，未定） | 已生成，未上架；品牌信息和 AdMob 都是占位 | 按规则批量重命名用户授权文件夹里的文件（编号、日期、前后缀、查找替换、大小写），预览后执行、可撤销 | 首页选规则 → 现场选文件夹（或已授权的）→ 调规则、勾选文件、看新旧名对照 → 执行 → 结果页 → 可撤销 | SAF 文件夹授权（无存储权限，同领域复用 :core:storage）；改名记录存本机 | 五种规则卡片（带示例）+ 最近改名批次，底部横幅；不是文件浏览器 | 海盐蓝浅色（#1E6FFF + 青绿 #00B3A4）、锐利科技（近直角、描边、大写按钮、等宽字体）、文件名 + 光标图标 |

## 复刻包

| 目录 | 应用名 | 账号（ACCOUNT） | 母包 | 变体组合（scene / nav / home / clean / browser / done） | 配色 / 风格 | 状态 |
|---|---|---|---|---|---|---|
| tidygrove | Tidy Grove | studio-b（账号配置未填） | orbitclean | ring / tabs / grid / onetap / grid / checklist（和母包不同 5 个轴） | 森绿浅色（#2E9D5B + 琥珀 #E0A526）/ 柔和卡片，叶子 + 星光图标 | 已生成，未上架；首版为无广告白包（ADS=off），包名、链接、邮箱都是占位 |

母包自己的组合：orbitclean = saturn / tabs / dashboard / stepwise / list / summary。复刻包至少 3 个轴不同，场景不用 saturn。

## 已占用的方向（换领域、同领域的新包不要再做）

- **文件管理**：浏览、复制、移动、重命名、搜索授权文件夹里的文件（orbitclean）
- **存储清理**：冗余文件（安装包、临时文件、缩略图缓存、空文件夹）、大文件、重复文件（orbitclean）
- **按类型浏览**：图片、视频、音频、文档、压缩包、最近文件（orbitclean）
- **二维码**：生成（链接、文本、Wi-Fi、联系人）和从照片识别二维码（qrkit）；条形码、相机扫码以后也归 qrkit，不另出包
- **批量重命名**：按规则批量改名、预览、撤销（renamer）；按规则移动 / 归档文件不算重命名，可以另做（例如 photo-sorter）

和上面重叠度高、也不建议做的：相似照片清理、存储分析 / 大文件排行、"手机加速"类（后者还违反 Play 的误导性声明政策）。

## 每个包的详细记录

### orbitclean — Orbit Clean
- **Play 账号 / 商店页**：Kexing PortVanguard 覆盖上架；商店标题 "Orbit Clean: File Manager"
- **版本记录**：1.0.2–1.0.4 因所有文件访问权限被拒；1.0.5 改为 SAF 文件夹授权后过审上架；1.0.6 加应用内评分（未上传）
- **广告位**：home_banner、more_banner、file_list_banner、clean_done_banner、scan_done_interstitial、clean_done_interstitial、tool_result_interstitial、click_notice_interstitial、back_interstitial、app_open_cold / app_open_warm
- **权限**：INTERNET、POST_NOTIFICATIONS（及广告 SDK 自带的 AD_ID 等）；明确移除所有存储权限
- **隐私政策 / 网站**：https://orbitclean.github.io/privacy/ 、https://orbitclean.github.io/

### qrkit — QR Atelier
- **Play 账号 / 商店页**：未定；商店文案见 apps/qrkit/store/listing.md，提审材料见 apps/qrkit/store/submission.md
- **版本记录**：1.0.0（versionCode 10000）由 new-sub-app skill 生成，未上传
- **广告位**：home_banner（首页底部）、result_interstitial（生成或识别结果出来后，每次操作最多一次）、app_open_cold / app_open_warm；不使用返回插屏
- **权限**：INTERNET（及广告 SDK 自带的 ACCESS_NETWORK_STATE、AD_ID 等）；没有存储、照片、相机、通讯录权限
- **新依赖**：com.google.zxing:core 3.5.3（纯 Java，无需 R8 规则）
- **隐私政策 / 网站**：占位 https://example.com/qrkit/privacy/ 、https://example.com/qrkit/（上架前要换）

### renamer — Namewise
- **出包方式**：同领域（folderAccess）：复用 Orbit Clean 的文件夹授权和 FileStore 改名能力；首屏不用 FolderGate，在规则页现场选文件夹
- **和 Orbit Clean 的区别**：Orbit 只能单个文件改名，核心是文件浏览和清理；Namewise 的核心是规则批处理 + 预览 + 撤销，首页是规则模板，没有清理、没有通用文件管理
- **Play 账号 / 商店页**：未定；商店文案见 apps/renamer/store/listing.md，提审材料见 apps/renamer/store/submission.md
- **版本记录**：1.0.0（versionCode 10000）由 new-sub-app skill 生成，未上传
- **广告位**：home_banner（首页底部）、result_interstitial（一批改名完成、结果页出来后，每批最多一次；撤销和从记录打开不弹）、app_open_cold / app_open_warm；不使用返回插屏
- **权限**：INTERNET（及广告 SDK 自带的 ACCESS_NETWORK_STATE、AD_ID 等）；没有任何存储权限
- **新依赖**：无
- **隐私政策 / 网站**：占位 https://example.com/renamer/privacy/ 、https://example.com/renamer/（上架前要换）

### tidygrove — Tidy Grove（复刻包）
- **出包方式**：复刻（L1）：功能和 Orbit Clean 相同（清理 + 文件管理），来自 :features:cleaner；Orbit Clean 本身没改
- **账号**：studio-b（accounts/studio-b/account.properties 已建，五项都还没填）；不能和 Orbit Clean 放同一个 Play 账号
- **和母包的区别**：场景环形进度（母包土星）、首页两列宫格（母包仪表盘）、一键清理（母包分步）、文件页网格（母包列表）、完成页清单（母包汇总）；导航同为底部标签。配色森绿浅色 + 柔和卡片，系统字体；全部文案按“打理树林”语气重写（Weeds / Doubles / Big branches…）
- **Play 账号 / 商店页**：未定；商店文案见 apps/tidygrove/store/listing.md，提审材料见 apps/tidygrove/store/submission.md
- **版本记录**：1.0.0（versionCode 10000）由 new-sub-app skill 复刻模式生成，未上传；2026-11-17 定为无广告首版（白包，ADS=off），上架稳定后同包名改 ADS=on 发带广告的版本（步骤见 store/submission.md 第 5 节）
- **广告位**：首版无广告，广告位调用都落到 NoAds。改 ADS=on 后为 home_banner、done_banner、clean_done_interstitial（一次清理完成后最多一次）、app_open_cold / app_open_warm；一键清理流程不弹 scan_done_interstitial，不使用返回插屏
- **权限**：首版 INTERNET、POST_NOTIFICATIONS，及 WorkManager 的 ACCESS_NETWORK_STATE、WAKE_LOCK、RECEIVE_BOOT_COMPLETED、FOREGROUND_SERVICE；没有 AD_ID，没有任何存储权限，SAF 文件夹授权。带广告版本会多出广告 SDK 的 AD_ID、ADSERVICES 等
- **新依赖**：无（用 :features:cleaner、work-runtime、coil）
- **隐私政策 / 网站**：占位 https://example.com/tidygrove/privacy/ 、https://example.com/tidygrove/（账号网站定了以后换成 <WEBSITE_ROOT>tidygrove/privacy/ 和 <WEBSITE_ROOT>tidygrove/）
