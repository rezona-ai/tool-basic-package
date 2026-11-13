#!/usr/bin/env python3
"""
生成并检查 Google Play 商店素材，输出到 apps/<slug>/store/。

用法（在仓库根目录运行；需要 Pillow 和 Google Chrome）：
  python3 .claude/skills/new-sub-app/store_assets.py graphics <slug>          # 应用图标 icon-512.png + 置顶大图 feature-graphic.png
  python3 .claude/skills/new-sub-app/store_assets.py demo on|off              # 截图模式：飞行模式（不出广告）+ 状态栏演示模式（固定时间、满电、无通知）
  python3 .claude/skills/new-sub-app/store_assets.py screenshot <slug> <name> # 截当前模拟器屏幕 → screenshots/<序号>-<name>.png（1080×1920）
  python3 .claude/skills/new-sub-app/store_assets.py check <slug>             # 按 Play 要求检查全部素材

素材来源：图标和大图由 src/main/res 里的自适应图标（ic_launcher_background / ic_launcher_foreground 矢量图）和
values/colors.xml 渲染，文字取 brand.properties 的 APP_NAME / APP_TAGLINE。截图必须是应用真实页面。

Play 要求（https://support.google.com/googleplay/android-developer/answer/9866151）：
  应用图标   512×512，32 位 PNG，≤1MB；满版正方形，不要自己加圆角和阴影（Play 会统一加）
  置顶大图   1024×500，JPEG 或 24 位 PNG（无透明通道）
  手机截图   至少 2 张、最多 8 张；JPEG 或 24 位 PNG；边长 320–3840，长边不超过短边 2 倍；
             想进推荐位，至少 4 张 9:16、短边 ≥1080
"""
import html
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

try:
    from PIL import Image
except ImportError:
    sys.exit("需要 Pillow：python3 -m pip install --user Pillow")

ROOT = os.getcwd()
ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "google-chrome", "chromium", "chromium-browser",
]
SHOT_W, SHOT_H = 1080, 1920


# ---------------------------------------------------------------- 读品牌资源

def app_dir(slug):
    d = os.path.join(ROOT, "apps", slug)
    if not os.path.isfile(os.path.join(d, "brand.properties")):
        sys.exit(f"apps/{slug}/brand.properties not found（请在仓库根目录运行）")
    return d


def brand_props(d):
    props = {}
    with open(os.path.join(d, "brand.properties"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props


def colors(d):
    path = os.path.join(d, "src/main/res/values/colors.xml")
    return {c.get("name"): c.text.strip() for c in ET.parse(path).getroot().iter("color")}


def css_color(value, palette):
    """#RGB / #RRGGBB / #AARRGGBB / @color/x → CSS 颜色"""
    if value is None:
        return "none"
    if value.startswith("@color/"):
        value = palette.get(value[len("@color/"):]) or sys.exit(f"colors.xml 里没有 {value}")
    v = value.lstrip("#")
    if len(v) == 3:
        v = "".join(ch * 2 for ch in v)
    if len(v) == 6:
        return "#" + v
    if len(v) == 8:
        a, r, g, b = (int(v[i:i + 2], 16) for i in range(0, 8, 2))
        return f"rgba({r},{g},{b},{a / 255:.3f})"
    sys.exit(f"无法识别的颜色：{value}")


def vector_to_svg_body(path, palette, prefix):
    """把 Android VectorDrawable 转成 SVG 片段（支持 path / group / 线性与径向渐变，够用即可）"""
    root = ET.parse(path).getroot()
    defs, body = [], []
    counter = [0]

    def gradient(el):
        g = el.find("gradient")
        counter[0] += 1
        gid = f"{prefix}g{counter[0]}"
        stops = [(s.get(ANDROID + "offset"), s.get(ANDROID + "color")) for s in g.findall("item")]
        if not stops:
            stops = [("0", g.get(ANDROID + "startColor")), ("1", g.get(ANDROID + "endColor"))]
            if g.get(ANDROID + "centerColor"):
                stops.insert(1, ("0.5", g.get(ANDROID + "centerColor")))
        stop_xml = "".join(f'<stop offset="{o}" stop-color="{css_color(c, palette)}"/>' for o, c in stops)
        if g.get(ANDROID + "type") == "radial":
            defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{g.get(ANDROID + "centerX", "54")}" '
                        f'cy="{g.get(ANDROID + "centerY", "54")}" r="{g.get(ANDROID + "gradientRadius", "54")}">{stop_xml}</radialGradient>')
        else:
            defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(ANDROID + "startX", "0")}" '
                        f'y1="{g.get(ANDROID + "startY", "0")}" x2="{g.get(ANDROID + "endX", "108")}" y2="{g.get(ANDROID + "endY", "108")}">'
                        f'{stop_xml}</linearGradient>')
        return f"url(#{gid})"

    def walk(el, out):
        for child in el:
            if child.tag == "group":
                tf = []
                tx, ty = child.get(ANDROID + "translateX", "0"), child.get(ANDROID + "translateY", "0")
                px, py = child.get(ANDROID + "pivotX", "0"), child.get(ANDROID + "pivotY", "0")
                sx, sy = child.get(ANDROID + "scaleX", "1"), child.get(ANDROID + "scaleY", "1")
                rot = child.get(ANDROID + "rotation", "0")
                tf.append(f"translate({tx} {ty})")
                tf.append(f"translate({px} {py}) rotate({rot}) scale({sx} {sy}) translate(-{px} -{py})")
                inner = []
                walk(child, inner)
                out.append(f'<g transform="{" ".join(tf)}">{"".join(inner)}</g>')
            elif child.tag == "path":
                fill, stroke = child.get(ANDROID + "fillColor"), child.get(ANDROID + "strokeColor")
                for attr in child.findall(AAPT + "attr"):
                    if attr.get("name") == "android:fillColor":
                        fill = gradient(attr)
                    elif attr.get("name") == "android:strokeColor":
                        stroke = gradient(attr)
                fill_css = fill if (fill or "").startswith("url(") else css_color(fill, palette) if fill else "none"
                stroke_css = stroke if (stroke or "").startswith("url(") else css_color(stroke, palette) if stroke else "none"
                attrs = [f'd="{html.escape(child.get(ANDROID + "pathData"))}"', f'fill="{fill_css}"', f'stroke="{stroke_css}"']
                if child.get(ANDROID + "strokeWidth"):
                    attrs.append(f'stroke-width="{child.get(ANDROID + "strokeWidth")}"')
                if child.get(ANDROID + "strokeLineCap"):
                    attrs.append(f'stroke-linecap="{child.get(ANDROID + "strokeLineCap")}"')
                if child.get(ANDROID + "strokeLineJoin"):
                    attrs.append(f'stroke-linejoin="{child.get(ANDROID + "strokeLineJoin")}"')
                if child.get(ANDROID + "fillType", "").lower() == "evenodd":
                    attrs.append('fill-rule="evenodd"')
                if child.get(ANDROID + "fillAlpha"):
                    attrs.append(f'fill-opacity="{child.get(ANDROID + "fillAlpha")}"')
                out.append(f"<path {' '.join(attrs)}/>")

    walk(root, body)
    return "".join(defs), "".join(body)


def icon_svg(d, palette, size_attr):
    """自适应图标：背景 + 前景，取启动器可见的 72×72 区域（108 画布的 18..90），满版正方形"""
    res = os.path.join(d, "src/main/res/drawable")
    bg_defs, bg = vector_to_svg_body(os.path.join(res, "ic_launcher_background.xml"), palette, "b")
    fg_path = os.path.join(res, "ic_launcher_foreground.xml")
    if not os.path.isfile(fg_path):
        sys.exit("前景不是矢量图（ic_launcher_foreground.xml 不存在），请手动提供 store/icon-512.png")
    fg_defs, fg = vector_to_svg_body(fg_path, palette, "f")
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" {size_attr}><defs>{bg_defs}{fg_defs}</defs>{bg}{fg}</svg>'


# ---------------------------------------------------------------- 渲染

def chrome():
    for c in CHROME_CANDIDATES:
        if os.path.isfile(c) or shutil.which(c):
            return c if os.path.isfile(c) else shutil.which(c)
    sys.exit("需要 Google Chrome（无头模式渲染图标和大图）。没有的话，请设计师提供 icon-512.png 和 feature-graphic.png")


def render(page_html, w, h, out_png):
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "page.html")
        shot = os.path.join(tmp, "shot.png")
        with open(src, "w", encoding="utf-8") as f:
            f.write(page_html)
        subprocess.run([chrome(), "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        f"--window-size={w},{h}", f"--user-data-dir={os.path.join(tmp, 'profile')}",
                        f"--screenshot={shot}", "file://" + src],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
        im = Image.open(shot)
        if im.size != (w, h):  # 个别版本的无头 Chrome 会差几个像素，裁到目标尺寸
            im = im.crop((0, 0, w, h)) if im.width >= w and im.height >= h else im.resize((w, h), Image.LANCZOS)
        im.save(out_png)


PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
html,body{{margin:0;padding:0;width:{w}px;height:{h}px;overflow:hidden;background:{bg}}}
{css}</style></head><body>{body}</body></html>"""


def graphics(slug):
    d = app_dir(slug)
    props, palette = brand_props(d), colors(d)
    store = os.path.join(d, "store")
    os.makedirs(store, exist_ok=True)

    # 应用图标：满版正方形，Play 会自己加圆角和阴影
    icon = os.path.join(store, "icon-512.png")
    render(PAGE.format(w=512, h=512, bg="#000", css="svg{display:block}",
                       body=icon_svg(d, palette, 'width="512" height="512"')), 512, 512, icon)
    Image.open(icon).convert("RGBA").save(icon)

    # 置顶大图：图标渐变铺底，左侧图标、右侧应用名和一句话介绍；主体留在中间安全区，边缘不放关键内容
    start, end = css_color(palette["icon_bg_start"], palette), css_color(palette["icon_bg_end"], palette)
    fg = css_color(palette["icon_fg"], palette)
    name, tagline = html.escape(props.get("APP_NAME", slug)), html.escape(props.get("APP_TAGLINE", ""))
    css = f"""
body{{background:linear-gradient(135deg,{start},{end});font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;color:{fg}}}
.wrap{{position:absolute;inset:0 96px;display:flex;align-items:center;gap:56px}}
.icon{{width:248px;height:248px;flex:none;border-radius:56px;overflow:hidden;box-shadow:0 18px 48px rgba(0,0,0,.28)}}
.icon svg{{display:block}}
h1{{margin:0 0 18px;font-size:68px;line-height:1.05;font-weight:800;letter-spacing:-1px}}
p{{margin:0;font-size:30px;line-height:1.3;opacity:.86;max-width:520px}}"""
    small_icon = icon_svg(d, palette, 'width="248" height="248"')
    body = f'<div class="wrap"><div class="icon">{small_icon}</div><div><h1>{name}</h1><p>{tagline}</p></div></div>'
    feature = os.path.join(store, "feature-graphic.png")
    render(PAGE.format(w=1024, h=500, bg=start, css=css, body=body), 1024, 500, feature)
    Image.open(feature).convert("RGB").save(feature)
    print(f"wrote apps/{slug}/store/icon-512.png, apps/{slug}/store/feature-graphic.png")


# ---------------------------------------------------------------- 截图

def adb(*args, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=60)
    if r.returncode != 0:
        sys.exit(f"adb {' '.join(args)} 失败：{r.stderr.decode(errors='ignore').strip()}")
    return r.stdout if binary else r.stdout.decode(errors="ignore")


def demo(state):
    """
    截图模式：
    - 飞行模式断网，广告加载不出来（商店截图里不能有广告）；打开后要冷启动应用再截，已加载的横幅不会自己消失
    - 系统界面演示模式：状态栏固定 12:00、满格 Wi-Fi、满电、不显示通知和飞行模式图标
    """
    if state == "on":
        adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
        for extra in (["-e", "command", "enter"],
                      ["-e", "command", "clock", "-e", "hhmm", "1200"],
                      ["-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false"],
                      ["-e", "command", "network", "-e", "wifi", "show", "-e", "level", "4", "-e", "fully", "true",
                       "-e", "mobile", "hide", "-e", "airplane", "hide"],
                      ["-e", "command", "notifications", "-e", "visible", "false"]):
            adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", *extra)
        print("screenshot mode on: airplane mode + demo status bar. Cold-start the app (am force-stop, then launch) before capturing.")
    else:
        adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit")
        adb("shell", "settings", "put", "global", "sysui_demo_allowed", "0")
        adb("shell", "cmd", "connectivity", "airplane-mode", "disable")
        print("screenshot mode off")


def screenshot(slug, name):
    """截当前屏幕，按比例缩放后居中放进 1080×1920（9:16）画布，两侧用截图顶部的底色补齐，满足 Play 的长宽比要求"""
    d = app_dir(slug)
    out_dir = os.path.join(d, "store", "screenshots")
    os.makedirs(out_dir, exist_ok=True)
    if not re.fullmatch(r"[a-z0-9-]{1,40}", name):
        sys.exit("name 只用小写字母、数字和连字符，例如 home、result")
    # 商店截图不能出现广告（开屏、插屏都在广告 SDK 的 Activity 里展示）
    top = adb("shell", "dumpsys", "activity", "activities")
    resumed = next((l for l in top.splitlines() if "topResumedActivity" in l or "mResumedActivity" in l), "")
    if re.search(r"AdActivity|AppLovinFullscreenActivity|MaxFullscreen", resumed):
        sys.exit("当前前台是广告页，先关掉广告再截图：" + resumed.strip())
    # 等画面稳定：连续两次截屏完全一样才用，避开页面切换、加载动画的中间帧
    shot, previous = None, None
    for _ in range(12):
        current = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", binary=True))).convert("RGB")
        if previous is not None and current.tobytes() == previous.tobytes():
            shot = current
            break
        previous = current
        time.sleep(0.5)
    if shot is None:
        sys.exit("画面一直在变（动画或加载中），等页面静止后再截")
    scale = min(SHOT_W / shot.width, SHOT_H / shot.height)
    resized = shot.resize((round(shot.width * scale), round(shot.height * scale)), Image.LANCZOS)
    canvas = Image.new("RGB", (SHOT_W, SHOT_H), shot.getpixel((shot.width // 2, shot.height // 40)))
    x, y = (SHOT_W - resized.width) // 2, (SHOT_H - resized.height) // 2
    if x > 0:  # 两侧补边：把每一行最左 / 最右的像素横向拉伸，导航栏、卡片底色都能接上
        canvas.paste(resized.crop((0, 0, 1, resized.height)).resize((x, resized.height)), (0, y))
        right = SHOT_W - x - resized.width
        canvas.paste(resized.crop((resized.width - 1, 0, resized.width, resized.height)).resize((right, resized.height)), (x + resized.width, y))
    canvas.paste(resized, (x, y))
    index = len([f for f in os.listdir(out_dir) if f.endswith(".png")]) + 1
    out = os.path.join(out_dir, f"{index:02d}-{name}.png")
    canvas.save(out)
    print(f"wrote {os.path.relpath(out, ROOT)} (from {shot.width}×{shot.height})")


# ---------------------------------------------------------------- 检查

def check(slug):
    d = app_dir(slug)
    store = os.path.join(d, "store")
    problems, notes = [], []

    def need(path, label):
        if not os.path.isfile(path):
            problems.append(f"缺少{label}：{os.path.relpath(path, ROOT)}")
            return None
        return Image.open(path)

    icon = need(os.path.join(store, "icon-512.png"), "应用图标")
    if icon:
        if icon.size != (512, 512):
            problems.append(f"应用图标必须是 512×512，现在是 {icon.width}×{icon.height}")
        if icon.format != "PNG":
            problems.append("应用图标必须是 PNG")
        if os.path.getsize(os.path.join(store, "icon-512.png")) > 1024 * 1024:
            problems.append("应用图标超过 1MB")

    feature = need(os.path.join(store, "feature-graphic.png"), "置顶大图")
    if feature:
        if feature.size != (1024, 500):
            problems.append(f"置顶大图必须是 1024×500，现在是 {feature.width}×{feature.height}")
        if feature.mode in ("RGBA", "LA", "P"):
            problems.append("置顶大图不能带透明通道（要 24 位 PNG 或 JPEG）")

    shots_dir = os.path.join(store, "screenshots")
    shots = sorted(f for f in os.listdir(shots_dir) if f.lower().endswith((".png", ".jpg", ".jpeg"))) if os.path.isdir(shots_dir) else []
    if len(shots) < 2:
        problems.append(f"手机截图至少 2 张，现在 {len(shots)} 张（store/screenshots/）")
    if len(shots) > 8:
        problems.append(f"手机截图最多 8 张，现在 {len(shots)} 张")
    promo = 0
    for f in shots:
        im = Image.open(os.path.join(shots_dir, f))
        w, h = im.size
        if im.mode in ("RGBA", "LA", "P"):
            problems.append(f"{f}：不能带透明通道")
        if min(w, h) < 320 or max(w, h) > 3840:
            problems.append(f"{f}：边长要在 320–3840 之间，现在 {w}×{h}")
        if max(w, h) > 2 * min(w, h):
            problems.append(f"{f}：长边不能超过短边 2 倍，现在 {w}×{h}")
        if min(w, h) >= 1080 and abs(max(w, h) / min(w, h) - 16 / 9) < 0.01:
            promo += 1
    if promo < 4:
        notes.append(f"9:16 且短边 ≥1080 的截图有 {promo} 张，想进 Play 推荐位需要至少 4 张")

    for n in notes:
        print("提示：" + n)
    if problems:
        print("\n".join("✗ " + p for p in problems))
        sys.exit(1)
    print(f"✓ apps/{slug}/store 素材符合 Play 要求（图标、置顶大图、{len(shots)} 张截图）")


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) == 2 and a[0] == "graphics":
        graphics(a[1])
    elif len(a) == 2 and a[0] == "demo" and a[1] in ("on", "off"):
        demo(a[1])
    elif len(a) == 3 and a[0] == "screenshot":
        screenshot(a[1], a[2])
    elif len(a) == 2 and a[0] == "check":
        check(a[1])
    else:
        sys.exit(__doc__)
