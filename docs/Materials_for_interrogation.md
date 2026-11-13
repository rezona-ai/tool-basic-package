# Google Play 重新提审：移除所有文件访问权限（1.0.5）

| 版本 | versionCode | 结果 |
|---|---|---|
| 1.0.2 | 10200 | 被拒：所有文件访问（MANAGE_EXTERNAL_STORAGE）不是核心功能 |
| 1.0.3 | 10300 | 被拒：同上。加了文件管理功能，但打开仍是清理首页 |
| 1.0.4 | 10400 | 被拒：同上。启动页改成文件管理器也没过，应用名、CLEAN 标签、扫描动画仍是清理应用 |
| 1.0.5 | 10500 | **已过审上架**：不再申请任何存储权限，只访问用户在系统文件夹选择器里选中的文件夹 |
| 1.0.6 | 10600 | 待提交：权限与 1.0.5 完全相同；Rate us 指定 Play 商店打开、清理完成页接入 Play 应用内评分（第 2 次起成功清理、30 天冷却、不先问问题、不和广告挂钩） |

结论：只要应用还有清理功能，Google 就不会认可"所有文件访问"是核心功能。1.0.5 直接删掉这个权限，按 Google 拒审邮件里的建议改用系统文件夹选择器（Storage Access Framework）。

## 1. 1.0.5 改了什么

| 项目 | 1.0.4 | 1.0.5 |
|---|---|---|
| 存储权限 | MANAGE_EXTERNAL_STORAGE、READ/WRITE_EXTERNAL_STORAGE | **一个都没有**（合并后的 manifest 已核对） |
| 首次启动 | 跳到系统设置页开"所有文件访问" | 文件夹授权页：选 DCIM / Pictures / Movies / Music / Documents 或任意文件夹，系统弹窗确认 |
| 文件管理首页 | 存储根目录的全部文件夹 | 用户授权的文件夹 + "Add a folder" |
| 设置页 | — | 新增 **Folder access**：随时增删授权文件夹 |
| 清理、分类、搜索 | 扫描整个存储 | 只扫描授权的文件夹 |
| Play 声明 | 需要所有文件访问声明表单 + 视频 | **不需要** |

**功能上的取舍（系统限制，Google 允许范围内无法绕过）：**

- Android 11 及以上，系统选择器不允许选**存储根目录**和 **Download 文件夹本身**（可以选 Download 里的子文件夹）。Download 根目录里的 APK、PDF 等文件扫不到。
- Android 10 及以下可以直接授权"Internal storage"（整个内部存储），功能与以前一致。
- 授权保存在系统里，重启后仍有效；卸载应用后失效。

## 2. Play Console 要做的事

1. 上传 `dist/PortVanguard-1.0.5.aab`（versionCode 10500）到正式版，替换被拒的 10400。
2. **检查所有测试轨道**（内部测试 / 封闭测试 / 开放测试）：如果还有包含 MANAGE_EXTERNAL_STORAGE 的旧版本处于活跃状态，也换成 10500 或停用该轨道。否则 Play Console 仍会要求填写所有文件访问声明。
3. 应用内容 → 敏感权限和 API：上传 10500 后，所有文件访问声明应该不再出现。如果还在，说明还有某个轨道的旧包带着这个权限（见第 2 步）。
4. 审核说明（如果有输入框）：

```
This version removes MANAGE_EXTERNAL_STORAGE and all other storage permissions. The app only accesses folders the user picks in the system folder picker (Storage Access Framework, ACTION_OPEN_DOCUMENT_TREE). Users can add or remove folders at any time in Settings > Folder access.
```

## 3. 商店页文案

不再受"核心功能必须是文件管理"的约束，但描述必须和实际行为一致：**不能再写"浏览所有文件夹 / every folder"**，也不要再提"All files access"。

**简短说明（≤80 字符）**

```
Manage and clean up the folders you choose: large, duplicate and leftover files.
```

**完整说明**（不要出现 booster、speed up、optimize、"手机变快"这类夸大说法）

```
Orbit Clean helps you manage files and free up space in the folders you choose.

FILE MANAGER
• Pick the folders you want to manage, like DCIM, Pictures, Movies, Music or Documents
• Open, copy, move, rename, share and delete files and folders
• Create new folders and search files by name
• Browse by type: images, videos, audio, documents, archives and recent files

STORAGE CLEANUP
• Find large files that take up the most space
• Find duplicate files (byte-for-byte identical copies)
• Find leftover APK installers, temp and log files, thumbnail caches and empty folders

PRIVACY
Orbit Clean only sees the folders you choose in the system folder picker. It doesn't ask for access to your whole storage. You can add or remove folders any time in Settings. All file operations happen on your device. Your files are never uploaded.
```

**截图**：截图里的文件夹列表要换成授权后的样子（例如 DCIM、Documents），可以加一张文件夹授权页。

## 4. 数据安全表单

不变：应用不收集也不上传文件内容。
