# accounts/

每个 Play 开发者账号一个子目录：`accounts/<账号>/`。品牌应用在 brand.properties 里写 `ACCOUNT=<账号>` 就归到这个账号下（复刻模式的包必须填，其他包可以不填）。

除了本说明和 `example/`，这里的内容都不入库（见 .gitignore）：账号信息、网站文件都只留在本机。

```
accounts/
  README.md                          本说明（入库）
  example/account.properties.example 账号配置模板（入库）
  <账号>/account.properties          本账号的配置（不入库）
  <账号>/site/                       本账号开发者网站的文件，由 account.py site 生成（不入库）
```

## account.properties

| 字段 | 用途 |
|---|---|
| DEVELOPER_NAME | Play 管理中心里的开发者名称 |
| WEBSITE_ROOT | 本账号的开发者网站根地址（https://），`app-ads.txt` 放在它的根目录；本账号各包的隐私政策、网站都在这个域名下 |
| FEEDBACK_EMAIL | 本账号的联系邮箱；本账号的包没单独填时用它 |
| ADMOB_PUBLISHER_ID | 本账号对应的 AdMob 发布商 ID（`pub-` + 16 位数字）；账号里只有白包（`ADS=off`）时可以先空着 |
| SIGNING_PROPERTIES | 本账号的上传密钥配置文件（项目根目录，不入库）；本账号的包没单独填时用它 |

## 构建时的校验（build-logic 的 AccountGuard）

填了 `ACCOUNT` 的包，打上传用的 AAB（bundleRelease）前会检查：

- 账号配置存在，五个字段都填了；
- 四个 AdMob ID 都属于本账号的发布商，而且来自本应用自己的 brand.local.properties，不是所有应用共用的 Gradle 属性；
- 有上传密钥；
- 隐私政策、网站地址在本账号的网站域名下；`store/app-ads.txt` 只写本账号的发布商；
- 本应用的包名、链接、邮箱、广告 ID，以及 `src/main`、`store/` 里的文字，不出现别的账号或不属于本账号的应用（例如 Orbit Clean）的名称、包名、域名、邮箱、发布商 ID。

白包（`brand.properties` 的 `ADS=off`）不带广告：不要求 `ADMOB_PUBLISHER_ID`，不查广告 ID 和 `app-ads.txt`，其余照查；`account.py site` 在没有发布商 ID 时不生成 `app-ads.txt`。

没填 `ACCOUNT` 的包（Orbit Clean、QR Atelier、Namewise）不走这些校验，构建结果不变。

## 账号脚本

```bash
python3 .claude/skills/new-sub-app/account.py init <账号>     # 从模板建 accounts/<账号>/account.properties
python3 .claude/skills/new-sub-app/account.py list            # 列出账号和每个账号下的包
python3 .claude/skills/new-sub-app/account.py site <账号>     # 生成 accounts/<账号>/site/：根目录 app-ads.txt + 每个包的隐私政策
```

同一个账号里，同一个母包只能有一个复刻包（scaffold.py 生成时会拦）。
