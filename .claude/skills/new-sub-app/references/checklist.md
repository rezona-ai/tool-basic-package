# 检查清单

## A. 差异化（生成前、提交审核前各对照一次）

前六项任一项与 apps/REGISTRY.md 里已有的包相同，就回到第 1 步换方向：

- [ ] 核心用途：一句话说清楚它解决的问题，并且和其他包不同
- [ ] 主流程：从打开应用到拿到结果的步骤不同
- [ ] 数据来源和权限：只申请本包核心功能需要的权限
- [ ] 页面结构：首页的信息组织方式不同，不只是换颜色
- [ ] 视觉：浅色 / 深色、主色、图标图形、字体与组件风格都和已有包不同
- [ ] 商店页：名称、图标、截图、短描述、长描述全部重写
- [ ] 技术身份：包名、上传密钥、AdMob 应用、隐私政策网址每个包独立

## B. 生成后的自检（skill 自己跑完再交付）

- [ ] `./gradlew :apps:<slug>:testDebugUnitTest :apps:<slug>:assembleDebug` 通过
- [ ] 模拟器上：冷启动开屏（测试广告）→ 首页 → 走完一次核心流程 → 结果页插屏 → 返回 → 设置页各项可点
- [ ] 无崩溃（`adb logcat -b crash` 为空）
- [ ] 合并后的清单只有预期的权限：`aapt dump permissions <apk>`
- [ ] release 包（R8 混淆后）装到模拟器，核心流程走完无崩溃（命令见 SKILL.md 第 8 步）
- [ ] 广告规则：插屏只在结果出来之后、每次操作最多一次；没有假进度；广告不挡按钮；AdBackBlocker 保持关闭
- [ ] 商店素材：`store_assets.py check <slug>` 通过；每张截图都看过，没有闪屏、广告、空状态
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
- [ ] 放在已有包同一个开发者账号下；不为规避处罚开新账号
