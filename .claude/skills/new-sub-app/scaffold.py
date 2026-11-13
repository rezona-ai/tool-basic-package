#!/usr/bin/env python3
"""
从模板生成一个品牌应用模块 apps/<slug>。

用法（在仓库根目录运行）：
  python3 .claude/skills/new-sub-app/scaffold.py suggest <slug> "<应用名>"   # 打印一份随机占位配置（JSON）
  python3 .claude/skills/new-sub-app/scaffold.py create <config.json>      # 按配置生成 apps/<slug>

config.json 字段：
  slug            模块目录名，小写字母开头，只含小写字母和数字（3–30 位）
  appName         应用名（≤30 字符）
  tagline         一句话介绍（≤80 字符）
  directionName   方向名称（写进注释和占位首页）
  applicationId   上架包名；占位用 com.example.<slug>
  privacyUrl / websiteUrl / feedbackEmail
  admob           {appId, banner, interstitial, appOpen}；省略时用 Google 测试 ID。
                  brand.properties（入库）始终写测试 ID；填了真实 ID 时另写 apps/<slug>/brand.local.properties（不入库，构建时优先读它）
  palette / style presets.json 里的 id
  codePackage     可选，代码包名，默认 com.appbase.<slug>
  permissions     可选，清单里要加的 <uses-permission .../> 行（字符串列表）
  iconGlyph       可选，启动图标前景的 <path .../> 片段（108 画布，安全区 21..87）；颜色写 @color/icon_fg
"""
import datetime
import json
import os
import re
import shutil
import sys

ROOT = os.getcwd()
SKILL = os.path.dirname(os.path.abspath(__file__))
TEMPLATE = os.path.join(SKILL, "template")

TEST_ADMOB = {
    "appId": "ca-app-pub-3940256099942544~3347511713",
    "banner": "ca-app-pub-3940256099942544/9214589741",
    "interstitial": "ca-app-pub-3940256099942544/1033173712",
    "appOpen": "ca-app-pub-3940256099942544/9257395921",
}

# 占位图形：圆角方块描边 + 中心圆点（生成后应按方向重画）
DEFAULT_GLYPH = (
    '<path android:fillColor="#00000000" android:strokeColor="@color/icon_fg" android:strokeWidth="5" '
    'android:pathData="M38,32h32a6,6 0,0 1,6 6v32a6,6 0,0 1,-6 6h-32a6,6 0,0 1,-6 -6v-32a6,6 0,0 1,6 -6z" />\n'
    '    <path android:fillColor="@color/icon_fg" android:pathData="M54,46a8,8 0,1 1,0 16a8,8 0,1 1,0 -16z" />'
)

SLUG = re.compile(r"^[a-z][a-z0-9]{2,29}$")
PACKAGE = re.compile(r"^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$")


def presets():
    with open(os.path.join(SKILL, "presets.json"), encoding="utf-8") as f:
        return json.load(f)


def suggest(slug, app_name):
    if not SLUG.match(slug):
        sys.exit(f"slug must match {SLUG.pattern}: {slug}")
    print(json.dumps({
        "slug": slug,
        "appName": app_name,
        "applicationId": f"com.example.{slug}",
        "privacyUrl": f"https://example.com/{slug}/privacy/",
        "websiteUrl": f"https://example.com/{slug}/",
        "feedbackEmail": f"{slug}@example.com",
        "admob": TEST_ADMOB,
    }, ensure_ascii=False, indent=2))


def kt_bool(v):
    return "true" if v else "false"


def create(config_path):
    with open(config_path, encoding="utf-8") as f:
        c = json.load(f)
    slug = c["slug"]
    if not SLUG.match(slug):
        sys.exit(f"slug must match {SLUG.pattern}: {slug}")
    target = os.path.join(ROOT, "apps", slug)
    if os.path.exists(target):
        sys.exit(f"apps/{slug} already exists")
    if not PACKAGE.match(c["applicationId"]):
        sys.exit(f"invalid applicationId: {c['applicationId']}")
    for existing in os.listdir(os.path.join(ROOT, "apps")):
        props = os.path.join(ROOT, "apps", existing, "brand.properties")
        if os.path.isfile(props) and f"APPLICATION_ID={c['applicationId']}\n" in open(props, encoding="utf-8").read():
            sys.exit(f"applicationId {c['applicationId']} is already used by apps/{existing}")

    p = presets()
    palette = next((x for x in p["palettes"] if x["id"] == c["palette"]), None) or sys.exit(f"unknown palette {c['palette']}")
    style = next((x for x in p["styles"] if x["id"] == c["style"]), None) or sys.exit(f"unknown style {c['style']}")
    code_package = c.get("codePackage") or f"com.appbase.{slug}"
    if not PACKAGE.match(code_package):
        sys.exit(f"invalid codePackage: {code_package}")
    admob = {**TEST_ADMOB, **(c.get("admob") or {})}
    glyph = c.get("iconGlyph") or DEFAULT_GLYPH
    mono = re.sub(r'@color/icon_fg', "#FFFFFFFF", glyph)
    today = datetime.date.today()

    def argb_to_hex(v):  # 0xAARRGGBB -> #AARRGGBB
        return "#" + v[2:]

    tokens = {
        "SLUG": slug,
        "CODE_PACKAGE": code_package,
        "APPLICATION_ID": c["applicationId"],
        "APP_NAME": c["appName"],
        "TAGLINE": c.get("tagline", ""),
        "DIRECTION_NAME": c.get("directionName", c["appName"]),
        "PRIVACY_URL": c["privacyUrl"],
        "WEBSITE_URL": c["websiteUrl"] if c["websiteUrl"].endswith("/") else c["websiteUrl"] + "/",
        "FEEDBACK_EMAIL": c["feedbackEmail"],
        "ADMOB_APP_ID": TEST_ADMOB["appId"],
        "ADMOB_BANNER": TEST_ADMOB["banner"],
        "ADMOB_INTERSTITIAL": TEST_ADMOB["interstitial"],
        "ADMOB_APP_OPEN": TEST_ADMOB["appOpen"],
        "DATE": today.isoformat(),
        "DATE_LONG": today.strftime("%B %-d, %Y"),
        "PERMISSIONS": "\n    ".join(c.get("permissions", [])) or "本应用不需要额外权限",
        "PALETTE_NAME": palette["name"],
        "STYLE_NAME": style["name"],
        "IS_DARK": kt_bool(palette["isDark"]),
        "WINDOW_THEME": "android:Theme.Material.NoActionBar" if palette["isDark"] else "android:Theme.Material.Light.NoActionBar",
        "BG": palette["bg"], "TEXT_PRIMARY": palette["textPrimary"], "TEXT_SOFT": palette["textSoft"],
        "TEXT_SECONDARY": palette["textSecondary"], "TEXT_DIM": palette["textDim"], "PRIMARY": palette["primary"],
        "SECONDARY": palette["secondary"], "DANGER": palette["danger"], "ON_ACCENT": palette["onAccent"],
        "CARD": palette["card"], "LINE": palette["line"], "DIALOG": palette["dialog"],
        "GRADIENT_START": palette["gradientStart"], "GRADIENT_END": palette["gradientEnd"],
        "BG_HEX": argb_to_hex(palette["bg"]),
        "ICON_BG_START": palette["iconBgStart"], "ICON_BG_END": palette["iconBgEnd"], "ICON_FG": palette["iconFg"],
        "BRAND_HEX": palette["hex"], "BRAND_DARK_HEX": palette["hexDark"],
        "CORNER_SCALE": str(style["cornerScale"]), "SURFACE": style["surface"], "BUTTON": style["button"],
        "PILL_BUTTON": kt_bool(style["pillButton"]), "UPPERCASE_BUTTON": kt_bool(style["uppercaseButton"]),
        "CIRCLE_BADGE": kt_bool(style["circleBadge"]), "FONT_FAMILY": style["fontFamily"],
        "BUTTON_TRACKING": str(style["buttonTracking"]),
        "ICON_GLYPH": glyph,
        "ICON_GLYPH_MONO": mono,
    }
    # 这些占位留给 Claude 按方向填写（隐私政策、商店页、提审材料的正文）
    keep = {"SUMMARY", "WHAT_IT_DOES_HTML", "PERMISSION_ROWS", "LOCAL_DATA", "FULL_DESCRIPTION",
            "APP_DATA", "PERMISSION_ANSWERS", "CORE_FLOW"}

    package_path = code_package.replace(".", "/")
    for dirpath, _, files in os.walk(TEMPLATE):
        rel = os.path.relpath(dirpath, TEMPLATE).replace("__PACKAGE_PATH__", package_path)
        out_dir = os.path.normpath(os.path.join(target, rel))
        os.makedirs(out_dir, exist_ok=True)
        for name in files:
            src = os.path.join(dirpath, name)
            dst = os.path.join(out_dir, name)
            if name.endswith((".png", ".webp", ".jpg", ".ttf")):
                shutil.copyfile(src, dst)
                continue
            text = open(src, encoding="utf-8").read()

            def sub(m):
                key = m.group(1)
                if key in keep:
                    return m.group(0)
                if key not in tokens:
                    sys.exit(f"unknown token {{{{{key}}}}} in {src}")
                return tokens[key]

            text = re.sub(r"\{\{([A-Z_]+)\}\}", sub, text)
            with open(dst, "w", encoding="utf-8") as f:
                f.write(text)
    print(f"created apps/{slug} (module :apps:{slug}, code package {code_package})")
    # 真实广告 ID 不入库：只写进本应用的 brand.local.properties（已在 .gitignore 里）
    keys = {"appId": "ADMOB_APP_ID", "banner": "ADMOB_BANNER_AD_UNIT_ID",
            "interstitial": "ADMOB_INTERSTITIAL_AD_UNIT_ID", "appOpen": "ADMOB_APP_OPEN_AD_UNIT_ID"}
    real = {keys[k]: v for k, v in admob.items() if k in keys and v != TEST_ADMOB[k]}
    if real:
        with open(os.path.join(target, "brand.local.properties"), "w", encoding="utf-8") as f:
            f.write("# 本应用的正式广告 ID（不入库）。构建时优先于 brand.properties 和同名 Gradle 属性。\n")
            f.write("".join(f"{k}={v}\n" for k, v in real.items()))
        print(f"wrote apps/{slug}/brand.local.properties ({len(real)} real AdMob IDs, not committed)")
    left = []
    for dirpath, _, files in os.walk(target):
        for name in files:
            if name.endswith((".png", ".webp", ".jpg", ".ttf")):
                continue
            path = os.path.join(dirpath, name)
            for key in sorted(set(re.findall(r"\{\{([A-Z_]+)\}\}", open(path, encoding="utf-8").read()))):
                left.append(f"{os.path.relpath(path, ROOT)}: {{{{{key}}}}}")
    if left:
        print("placeholders to fill by hand:")
        print("\n".join("  " + x for x in left))


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "suggest":
        suggest(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else sys.argv[2].capitalize())
    elif len(sys.argv) == 3 and sys.argv[1] == "create":
        create(sys.argv[2])
    else:
        sys.exit(__doc__)
