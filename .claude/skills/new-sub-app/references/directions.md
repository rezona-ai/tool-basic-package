# 马甲包功能方向库

第 1 步从这里挑 4 个给用户选。挑之前先读 `apps/REGISTRY.md`：和已有包的**核心用途、主流程、数据来源与权限**任一项相同的方向不能列出。可以提出库里没有的新方向，但同样要填完下面每一栏。

优先级：不需要敏感权限 > 只需要普通运行时权限（相机、通知）> 需要 Play 权限声明的（照片和视频、所有文件访问，原则上不选）。

| id | 方向 | 核心用途 | 主流程 | 数据来源与权限 | 和 Orbit Clean 的区别 | 实现要点 | 难度 |
|---|---|---|---|---|---|---|---|
| private-vault | 私密保险箱 | 把选中的文件加密存进应用，凭 PIN / 指纹打开 | 设 PIN → 选文件导入并加密 → 解锁后查看 / 导出还原 | 系统文件选择器 ACTION_OPEN_DOCUMENT（无权限）；USE_BIOMETRIC（普通权限） | 核心是隐藏和加密，不浏览、不清理 | AES-GCM + Android Keystore 密钥；文件存 app 私有目录；引导里说明卸载会丢数据；导入后由用户决定是否删除原文件（SAF） | 中 |
| image-compressor | 图片压缩与格式转换 | 批量缩小图片体积、改尺寸、转 JPEG / WebP / PNG | 照片选择器选图 → 选质量 / 尺寸 / 格式 → 预览前后大小 → 保存到用户选的文件夹 | Photo Picker（无权限）；保存用 SAF ACTION_OPEN_DOCUMENT_TREE / CREATE_DOCUMENT | 核心是转换生成新文件，不删除、不扫描 | BitmapFactory 采样解码 + Bitmap.compress；ExifInterface 保留方向；大图分批处理防 OOM | 低 |
| pdf-maker | 图片转 PDF | 把多张图片（拍的文档、截图）合成一个 PDF | 选图 → 拖动排序、选页面尺寸和边距 → 生成 PDF → 分享或保存 | Photo Picker（无权限）；SAF 保存 | 核心是生成文档 | android.graphics.pdf.PdfDocument；页面尺寸 A4 / Letter / 原图；生成后用 FileIntents 分享 | 低 |
| exif-cleaner | 照片隐私信息清除 | 查看并去掉照片里的位置、设备等元数据后再分享 | 选图 → 显示 GPS / 相机 / 时间信息 → 一键清除 → 保存副本或直接分享 | Photo Picker（无权限）；SAF 保存 | 核心是隐私处理，结果是新副本 | androidx.exifinterface；清除后重新编码或复制并删除标签；地图位置只显示文字坐标 | 低 |
| qr-toolkit | 二维码工具 | 生成文本 / 网址 / Wi-Fi / 名片二维码，并能识别图片里的二维码 | 选类型填内容 → 生成并保存 / 分享；或选图识别 → 打开链接 / 复制 | 生成无权限；识别用 Photo Picker；如加相机扫码需 CAMERA（普通运行时权限） | 完全不同的领域（L3） | 生成用 com.google.zxing:core（纯 Java）；识别图片同样用 ZXing；相机扫码可后加 CameraX | 低～中 |
| zip-tool | 压缩包工具 | 把选中的文件打成 zip，或把 zip 解压到指定文件夹 | 选文件 / 选 zip → 选目标文件夹 → 进度 → 完成页 | SAF（无权限） | 核心是打包 / 解包；注意别做成文件浏览器 | java.util.zip；大文件流式处理；带密码 zip 需额外依赖（zip4j），先不做 | 中 |
| batch-renamer | 批量重命名 | 按规则一次重命名一批文件（序号、日期、前后缀、替换） | 选文件夹 / 文件 → 配规则 → 预览新旧名 → 执行 → 可撤销 | SAF 文件夹授权（无权限，可复用 :core:storage） | 核心是规则批处理；与 Orbit 的单个重命名有重叠，首页和流程必须完全围绕"规则 + 预览" | DocumentsContract.renameDocument；冲突检测；保留一次撤销记录 | 中（重叠风险中） |
| video-compressor | 视频压缩 | 降低视频分辨率 / 码率以便发送 | 选视频 → 选目标大小或清晰度 → 转码进度 → 保存 / 分享 | Photo Picker（无权限）；SAF 保存 | 核心是转码 | androidx.media3:media3-transformer；前台转码，退出前提示；大视频耗时长 | 高 |

## 不要列出的方向

- 与 apps/REGISTRY.md「已占用的方向」重叠的：文件管理、存储清理、按类型浏览、相似照片清理、存储分析
- 需要所有文件访问或照片视频权限声明才能成立的（如全盘扫描、相册回收站）
- "手机加速 / 电池优化 / 清理内存"之类做不到也说不清的功能（误导性声明）
- 模仿某个知名应用名称或界面的

## 每个方向落地时都要有

- **独立的首页**：首页信息组织围绕自己的核心功能，不复用 Orbit Clean 的页面结构或土星场景
- **完成页 / 结果页**：插屏和应用内评分都放在"做完一件事"之后（ReviewPrompt.recordClean + maybeAsk 可直接复用，计数含义改成"完成一次核心操作"）
- **至少一个纯函数单测**：核心规则（命名规则、尺寸计算、加密格式等）
