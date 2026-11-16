#!/usr/bin/env python3
"""
Play 账号配置（accounts/<账号>/，不入库，见 accounts/README.md）。

用法（在仓库根目录运行）：
  python3 .claude/skills/new-sub-app/account.py init <账号>    # 从模板建 accounts/<账号>/account.properties（已存在则不动）
  python3 .claude/skills/new-sub-app/account.py list           # 列出账号、配置是否填全，以及每个账号下的包（含复刻自哪个母包）
  python3 .claude/skills/new-sub-app/account.py site <账号>    # 生成 accounts/<账号>/site/：根目录 app-ads.txt + index.html +
                                                               # 本账号每个包的隐私政策（<slug>/privacy/index.html），整个目录上传到 WEBSITE_ROOT

账号 id：小写字母开头，只含小写字母、数字和连字符。
"""
import html
import os
import re
import shutil
import sys

ROOT = os.getcwd()
ACCOUNTS = os.path.join(ROOT, "accounts")
EXAMPLE = os.path.join(ACCOUNTS, "example", "account.properties.example")
KEYS = ["DEVELOPER_NAME", "WEBSITE_ROOT", "FEEDBACK_EMAIL", "ADMOB_PUBLISHER_ID", "SIGNING_PROPERTIES"]
ID = re.compile(r"^[a-z][a-z0-9-]{1,30}$")
PUBLISHER = re.compile(r"^pub-\d{16}$")


def read_props(path):
    out = {}
    if os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def account_file(account):
    return os.path.join(ACCOUNTS, account, "account.properties")


def apps_of(account):
    """本账号的包：[(目录, 应用名, 母包)]"""
    out = []
    apps = os.path.join(ROOT, "apps")
    for name in sorted(os.listdir(apps)):
        props = read_props(os.path.join(apps, name, "brand.properties"))
        if props.get("ACCOUNT") == account:
            out.append((name, props.get("APP_NAME", name), props.get("CLONE_OF", "")))
    return out


def problems(props):
    out = [f"{k} is empty" for k in KEYS if not props.get(k)]
    root = props.get("WEBSITE_ROOT", "")
    if root and not (root.startswith("https://") and root.endswith("/")):
        out.append("WEBSITE_ROOT must start with https:// and end with /")
    pub = props.get("ADMOB_PUBLISHER_ID", "")
    if pub and not PUBLISHER.match(pub):
        out.append("ADMOB_PUBLISHER_ID must look like pub-0000000000000000")
    signing = props.get("SIGNING_PROPERTIES", "")
    if signing and not os.path.isfile(os.path.join(ROOT, signing)):
        out.append(f"SIGNING_PROPERTIES file {signing} not found in the project root")
    return out


def init(account):
    if not ID.match(account):
        sys.exit(f"account id must match {ID.pattern}")
    path = account_file(account)
    if os.path.exists(path):
        print(f"{os.path.relpath(path, ROOT)} already exists")
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    shutil.copyfile(EXAMPLE, path)
    print(f"created {os.path.relpath(path, ROOT)} (not committed); fill in: {', '.join(KEYS)}")


def list_accounts():
    names = sorted(d for d in os.listdir(ACCOUNTS) if d != "example" and os.path.isdir(os.path.join(ACCOUNTS, d)))
    if not names:
        print("no accounts yet (account.py init <id>)")
    for name in names:
        props = read_props(account_file(name))
        issues = problems(props) if props else ["account.properties missing"]
        print(f"{name}: {props.get('DEVELOPER_NAME') or '-'} · {props.get('WEBSITE_ROOT') or '-'} · {props.get('ADMOB_PUBLISHER_ID') or '-'}")
        for issue in issues:
            print(f"  ! {issue}")
        for slug, app, parent in apps_of(name):
            print(f"  - apps/{slug} {app}" + (f" (clone of {parent})" if parent else ""))


def site(account):
    props = read_props(account_file(account))
    if not props:
        sys.exit(f"{os.path.relpath(account_file(account), ROOT)} not found (account.py init {account})")
    issues = [p for p in problems(props) if not p.startswith("SIGNING")]
    if issues:
        sys.exit("fix the account first:\n  " + "\n  ".join(issues))
    out = os.path.join(ACCOUNTS, account, "site")
    if os.path.isdir(out):
        shutil.rmtree(out)
    os.makedirs(out)
    with open(os.path.join(out, "app-ads.txt"), "w", encoding="utf-8") as f:
        f.write(f"google.com, {props['ADMOB_PUBLISHER_ID']}, DIRECT, f08c47fec0942fa0\n")
    rows = []
    for slug, app, _ in apps_of(account):
        src = os.path.join(ROOT, "apps", slug, "store", "privacy-policy", "index.html")
        if not os.path.isfile(src):
            print(f"warning: apps/{slug} has no store/privacy-policy/index.html, skipped")
            continue
        text = open(src, encoding="utf-8").read()
        if "{{" in text:
            print(f"warning: apps/{slug} privacy policy still has placeholders, skipped")
            continue
        dst = os.path.join(out, slug, "privacy")
        os.makedirs(dst)
        shutil.copyfile(src, os.path.join(dst, "index.html"))
        rows.append(f'<li>{html.escape(app)} · <a href="{slug}/privacy/">Privacy policy</a></li>')
    name = html.escape(props["DEVELOPER_NAME"])
    email = html.escape(props["FEEDBACK_EMAIL"])
    with open(os.path.join(out, "index.html"), "w", encoding="utf-8") as f:
        f.write(
            "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            f"<title>{name}</title></head><body style=\"font-family:system-ui;max-width:640px;margin:40px auto;padding:0 16px\">"
            f"<h1>{name}</h1><ul>{''.join(rows)}</ul><p>Contact: <a href=\"mailto:{email}\">{email}</a></p></body></html>\n"
        )
    print(f"wrote {os.path.relpath(out, ROOT)}/ ({len(rows)} app(s)); upload its contents to {props['WEBSITE_ROOT']}")
    print("each app's PRIVACY_URL should be " + props["WEBSITE_ROOT"] + "<slug>/privacy/")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "init":
        init(sys.argv[2])
    elif len(sys.argv) == 2 and sys.argv[1] == "list":
        list_accounts()
    elif len(sys.argv) == 3 and sys.argv[1] == "site":
        site(sys.argv[2])
    else:
        sys.exit(__doc__)
