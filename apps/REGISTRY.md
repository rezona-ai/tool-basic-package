# 品牌应用登记表

每个品牌应用（apps/<目录>）一行。出新马甲包前先读这张表：新包的**核心用途、主流程、数据来源与权限**任何一项和已有的包相同，就换方向（Play 的重复内容政策，见 docs/马甲包调研文档-第一版.md「差异化分级」）。
新包生成后、提交审核前、上架后都要回来更新这一行。

## 总览

| 目录 | 应用名 | 上架包名 | 状态 | 核心用途（一句话） | 主流程 | 数据来源与权限 | 首页结构 | 视觉 |
|---|---|---|---|---|---|---|---|---|
| orbitclean | Orbit Clean | com.kexing.portvanguard | 1.0.5 已上架（真实广告）；基座分支里是测试广告 | 管理用户授权的文件夹，清理冗余、大文件、重复文件 | 选文件夹 → 文件首页浏览 / 一键扫描 → 勾选删除 → 完成页 | SAF 文件夹授权（无存储权限）、通知 | 文件管理首页（用量卡 + 按类型浏览 + 授权文件夹列表）+ 土星清理页 | 深空深色、土星金 + 品牌青、3D 土星场景、Unbounded/Sora 字体 |

## 已占用的方向（新包不要再做）

- **文件管理**：浏览、复制、移动、重命名、搜索授权文件夹里的文件（orbitclean）
- **存储清理**：冗余文件（安装包、临时文件、缩略图缓存、空文件夹）、大文件、重复文件（orbitclean）
- **按类型浏览**：图片、视频、音频、文档、压缩包、最近文件（orbitclean）

和上面重叠度高、也不建议做的：相似照片清理、存储分析 / 大文件排行、"手机加速"类（后者还违反 Play 的误导性声明政策）。

## 每个包的详细记录

### orbitclean — Orbit Clean
- **Play 账号 / 商店页**：Kexing PortVanguard 覆盖上架；商店标题 "Orbit Clean: File Manager"
- **版本记录**：1.0.2–1.0.4 因所有文件访问权限被拒；1.0.5 改为 SAF 文件夹授权后过审上架；1.0.6 加应用内评分（未上传）
- **广告位**：home_banner、more_banner、file_list_banner、clean_done_banner、scan_done_interstitial、clean_done_interstitial、tool_result_interstitial、click_notice_interstitial、back_interstitial、app_open_cold / app_open_warm
- **权限**：INTERNET、POST_NOTIFICATIONS（及广告 SDK 自带的 AD_ID 等）；明确移除所有存储权限
- **隐私政策 / 网站**：https://orbitclean.github.io/privacy/ 、https://orbitclean.github.io/
