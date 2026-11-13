# 隐私政策与官网：生产和发布流程

本文记录 Orbit Clean 的隐私政策是怎么写、怎么托管、怎么接进 app 和 Play Console 的。其他 app 照着做即可。

## 结果一览（Orbit Clean）

| 项目 | 值 |
|---|---|
| 隐私政策网址 | https://orbitclean.github.io/privacy/ |
| 官网（开发者网站） | https://orbitclean.github.io/ |
| app-ads.txt | https://orbitclean.github.io/app-ads.txt |
| 托管方式 | GitHub Pages，免费 |
| 网站仓库 | `orbitclean/orbitclean.github.io`（公开） |
| 隐私政策源文件 | 本仓库 `apps/<品牌>/store/privacy-policy/index.html` |
| app-ads.txt 源文件 | 本仓库 `apps/<品牌>/store/app-ads.txt` |
| app 内入口 | `AppLinks.PRIVACY_URL`，设置页 → Privacy policy |
| 联系邮箱 | j0618313586@gmail.com |

## 为什么这样做

- **隐私政策是必填项。** 带广告、或申请了"所有文件访问"权限的 app，Play Console 要求填一个公开可访问的隐私政策网址，app 内也要有入口。
- **网址里不能出现公司名。** 公司名会暴露 app 之间的关联，所以每个 app 单独建一个 GitHub 组织，用 `<组织名>.github.io` 当域名。
- **app-ads.txt 必须放在开发者网站根目录。** AdMob 和其他广告平台会去这个位置核验广告位归属。不放也能出广告，但填充率和单价会打折。所以每个 app 的官网根目录同时放 app-ads.txt。

## 操作步骤

### 1. 新建 GitHub 组织（网页上手动操作，约 1 分钟）

1. 打开 https://github.com/organizations/plan ，选 **Free** 方案。
2. 组织名填 app 的英文名，全小写、不带公司名。例如 `orbitclean`。
   - 最终网址是 `https://<组织名>.github.io/`，所以起名要谨慎，以后改名网址也会变。
3. 联系邮箱填 app 的对外邮箱，不要填公司邮箱。归属选 "My personal account"。
4. 确认自己是这个组织的 Owner。

### 2. 写隐私政策页面

复制 `apps/<品牌>/store/privacy-policy/index.html` 当模板。这是一个单文件 HTML，样式内联，没有外部依赖，自带深色模式。必须改的地方：

| 位置 | 改成 |
|---|---|
| `<title>`、页头品牌名、版权行 | 新 app 名 |
| CSS 变量 `--brand`、`--brand-dark` | 新 app 主题色（可选） |
| 页头 logo 的 `<svg>` | 新 app 的图形（可选） |
| Effective date / Last updated | 发布当天的日期 |
| 第 1 节包名 `com.orbitclean.app` | 新 app 的 applicationId |
| 第 2 节"app 做了什么" | 按新 app 的真实功能重写 |
| 第 3 节权限表 | 和新 app 的 `AndroidManifest.xml` 里的权限逐条对齐 |
| 第 4 节广告平台 | 写实际接入的 SDK，附上各家隐私政策链接 |
| 第 5 节自己收集的数据 | 如果接了 Firebase、统计、崩溃上报、账号系统，要在这里写清楚 |
| 第 11 节联系邮箱 | 新 app 的对外邮箱 |

写作要点：

- **内容必须和 app 实际行为一致。** Play 审核会对照权限、SDK 和"数据安全"表单检查，写多了没关系，写少了会被拒。
- **网页内容全部用英文。** 面向海外用户和审核。
- **以下几节保留：** GDPR（EEA/UK/瑞士）、美国州法（CCPA）、儿童隐私、数据保留、政策变更、联系方式。这几节是通用合规内容。
- **全文搜一遍公司名，确保 0 处。** 包括 HTML 注释和 meta 标签。

### 3. 准备官网文件

在一个空目录里放：

```
.nojekyll                ← 空文件，关闭 Jekyll 处理
index.html               ← 简单落地页：app 名、一句话介绍、Privacy Policy 和 Contact 链接
privacy/index.html       ← 第 2 步写好的隐私政策
app-ads.txt              ← 有 AdMob 发布商 ID 后再加，见第 6 步
```

`index.html` 可以直接复制 `orbitclean.github.io` 仓库的首页改名字和颜色。

### 4. 建仓库并发布

仓库名必须是 `<组织名>.github.io`，公开。这样 GitHub Pages 会自动从 main 分支根目录发布，不用额外设置。

```bash
gh repo create <组织名>/<组织名>.github.io --public --description "<App 名> website and privacy policy"
```

```bash
cd <网站目录> && git init -b main && git add -A && git commit -m "feat: add landing page and privacy policy"
```

```bash
git remote add origin https://github.com/<组织名>/<组织名>.github.io.git && git push -u origin main
```

推送后 1～2 分钟上线。用下面的命令确认返回 200：

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://<组织名>.github.io/privacy/
```

注意：

- 提交作者会公开显示。如果 git 全局配置里的邮箱带公司名，先在这个仓库里单独设置：`git config user.email <个人或 noreply 邮箱>`。
- 网络不稳定、HTTPS 推送报 SSL 错误时，改用 SSH 地址 `git@github.com:<组织名>/<组织名>.github.io.git`。

### 5. 接入 app 和 Play Console

- **app 内：** 网址和邮箱写进 `AppLinks.kt` 的 `PRIVACY_URL`、`FEEDBACK_EMAIL`，在设置页做 "Privacy policy" 入口（用浏览器打开）。
- **源文件留档：** 把隐私政策 HTML 复制一份到 app 仓库的 `apps/<品牌>/store/privacy-policy/index.html`，方便以后修改和追溯。
- **Play Console：**
  - 应用内容 → 隐私权政策：填 `https://<组织名>.github.io/privacy/`。
  - 商店设置 → 联系方式：网站填 `https://<组织名>.github.io/`，邮箱填对外邮箱。
  - 数据安全表单的回答要和隐私政策第 3～5 节一致。

### 6. 发布 app-ads.txt（接了 AdMob 之后）

1. 在 AdMob 后台 → 应用 → app-ads.txt 页面，复制给出的那一行。格式如下：

   ```
   google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
   ```

2. 存成网站仓库根目录的 `app-ads.txt`，同时在 app 仓库存一份 `apps/<品牌>/store/app-ads.txt`。提交并推送。
3. 确认 `https://<组织名>.github.io/app-ads.txt` 能访问。
4. app 在 Play 上架、商店页的"开发者网站"填好之后，AdMob 会自动抓取核验，通常需要 24 小时以上。

## 以后修改隐私政策

1. 改 app 仓库里的 `apps/<品牌>/store/privacy-policy/index.html`，同时更新 "Last updated" 日期。
2. 把文件复制到网站仓库的 `privacy/index.html`，提交并推送。
3. 网址不变，所以 app 和 Play Console 都不用改。

需要更新隐私政策的情况：增减权限、换广告平台、接入 Firebase 或统计 SDK、新增账号或上传功能。
