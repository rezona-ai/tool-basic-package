# 检查清单

## A. 差异化（生成前、提交审核前各对照一次）

复刻模式（L0 / L1）不对 A 节，改对 A2 节。

前六项任一项与 apps/REGISTRY.md 里已有的包相同，就回到第 1 步换方向：

- [ ] 核心用途：一句话说清楚它解决的问题，并且和其他包不同
- [ ] 主流程：从打开应用到拿到结果的步骤不同
- [ ] 数据来源和权限：只申请本包核心功能需要的权限
- [ ] 页面结构：首页的信息组织方式不同，不只是换颜色
- [ ] 视觉：浅色 / 深色、主色、图标图形、字体与组件风格都和已有包不同
- [ ] 商店页：名称、图标、截图、短描述、长描述全部重写
- [ ] 技术身份：包名、上传密钥、AdMob 应用、隐私政策网址每个包独立
- [ ] 不是同功能复刻：功能和任何已有包都不同，不只是换界面、图标、文案
- [ ] 同领域包（folderAccess）：逐条对过 directions.md 的「同领域的红线」——首页不是文件浏览器或存储仪表盘、没有清理功能、没有通用文件管理、首屏没用 FolderGate、视觉和商店页不像 Orbit Clean；同账号里没有另一个未稳定的同领域包

## A2. 复刻模式（L0 / L1）

功能和母包相同是这个模式的前提，下面这些是它必须守住的：

- [ ] 账号：`ACCOUNT` 指向的账号不是母包所在的账号；这个账号里没有同一母包的另一个复刻包（scaffold.py 会拦）
- [ ] 账号配置：`account.py list` 里本账号没有 `!` 提示；AdMob 发布商属于本账号，不和母包或别的账号共用
- [ ] 变体：至少 3 个轴和母包不同，场景不用母包的；不和同一母包的其他复刻包完全相同（scaffold.py 会拦）
- [ ] 视觉：配色、风格和母包、其他复刻包明显不同；启动图标按本包重画；没有母包的视觉标识（土星场景、Orbit 组件、Unbounded / Sora 字体）
- [ ] 文案：data/Copy.kt、各页面上的英文、通知、商店页、隐私政策都按本包重写，不照抄母包或其他复刻包
- [ ] 不串号：`src/main`、`store/` 里没有母包或别的账号的名称、包名、域名、邮箱、发布商 ID（打 AAB 时 AccountGuard 会查）
- [ ] 隐私政策和网站在本账号的网站域名下（`account.py site <账号>` 生成）

## B. 生成后的自检（skill 自己跑完再交付）

- [ ] `./gradlew :apps:<slug>:testDebugUnitTest :apps:<slug>:assembleDebug` 通过
- [ ] 模拟器上：冷启动开屏（测试广告）→ 首页 → 走完一次核心流程 → 结果页插屏 → 返回 → 设置页各项可点
- [ ] 复刻包：文件夹授权页 → 首页 → 扫描 → 清理 → 清理完成插屏 → 完成页 → 文件页（浏览、多选、新建文件夹）→ 设备信息 → 设置页（Folder access、提醒开关）
- [ ] 复刻包动效：`emu.py record` 录一遍首页 → 扫描 → 清理 → 完成页，帧图里每个阶段都在动（数字滚动、卡片错开浮现、扫描光效和逐行对勾、清理粒子、完成迸发 / 冲击波、页面转场）；新写的页面也用了 `ui/motion` 的工具
- [ ] 无崩溃（`adb logcat -b crash` 为空）
- [ ] 合并后的清单只有预期的权限：`aapt dump permissions <apk>`
- [ ] release 包（R8 混淆后）：`verify_release.py start <slug>` → 用 emu.py 走完核心流程 → `verify_release.py finish <slug>` 无崩溃
- [ ] 广告规则：插屏只在结果出来之后、每次操作最多一次；从历史打开结果不弹；子页面返回不加返回插屏；没有假进度；广告不挡按钮；AdBackBlocker 保持关闭
- [ ] 商店素材：`store_assets.py check <slug>` 通过；每张截图都看过，没有闪屏、广告、键盘、按下高亮、空状态；模拟器上的测试图片已删除
- [ ] 隐私政策 store/privacy-policy/index.html、listing.md、submission.md 的 {{...}} 都已按实际功能填写；app-ads.txt 已生成
- [ ] apps/REGISTRY.md 已加一行，并在「每个包的详细记录」写明广告位和权限
- [ ] apps/<slug>/src/main/java/.../CLAUDE.md 成员清单与实际文件一致

## C. 上传前（交给用户做，skill 只列出来）

- [ ] 上传密钥：用户自己运行 keytool 生成，并在根目录建 keystore-<slug>.properties（不入库），brand.properties 的 SIGNING_PROPERTIES 指向它。不要替用户生成或保存密码
- [ ] brand.properties 的占位值全部换成真实值：APPLICATION_ID、PRIVACY_URL、WEBSITE_URL、FEEDBACK_EMAIL
- [ ] 正式广告 ID（ADMOB_*，以及用到的 APPLOVIN_* / MAX_* / ADJUST_APP_TOKEN）写进 apps/<slug>/brand.local.properties（不入库）；AdMob 后台为本包新建应用和广告位
- [ ] 隐私政策页面和 app-ads.txt 部署到网站并能访问
- [ ] `./gradlew :apps:<slug>:bundleRelease` 通过（validateBrand + validateUpload）
- [ ] AdMob 后台为本应用发布 GDPR 消息
- [ ] Play Console：按 store/submission.md 逐项填写（数据安全、广告 ID 声明、内容分级、目标受众、商店页、审核说明），素材用 store/ 下生成的文件
- [ ] 新个人账号：封闭测试 12 人 × 14 天
- [ ] 换领域、同领域的包：放在已有包同一个开发者账号下；不为规避处罚开新账号
- [ ] 复刻包：按 brand.properties 的 ACCOUNT 放进对应账号；`account.py site <账号>` 生成的网站文件（app-ads.txt、各包隐私政策）已部署；`bundleRelease` 的账号串号校验通过
