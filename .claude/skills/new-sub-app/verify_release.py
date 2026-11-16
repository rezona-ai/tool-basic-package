#!/usr/bin/env python3
"""
release 包（R8 混淆 + 资源压缩后）的装机验证。审核员装的就是 release 包，只在混淆后出现的崩溃只能这样发现。

用法（在仓库根目录运行，模拟器已启动）：
  python3 .claude/skills/new-sub-app/verify_release.py start <slug>   # 打 release APK → 安装 → 清空崩溃日志 → 冷启动 → 检查权限
  （然后用 emu.py 把核心流程完整走一遍：开屏 → 首页 → 核心操作 → 结果页插屏 → 返回 → 设置页）
  python3 .claude/skills/new-sub-app/verify_release.py finish <slug>  # 检查崩溃日志 → 卸载验证包

brand.properties 还是占位值（com.example.*、example.com）时，validateBrand 会拦下 release 构建。
start 会用 -P 临时换成能通过检查的值（不改任何文件），包名改成 com.verify.<slug>，可以和 debug 包同时装着。
这条 gradle 命令只打 release：-P 对同一次构建里的所有任务都生效，和 debug 放在一起会把 debug 包的包名也改掉。
"""
import os
import re
import subprocess
import sys
import time

ROOT = os.getcwd()

# 需要在 Play Console 声明、或者审核时要说明理由的权限；出现任何一个都要确认是核心功能必需的
SENSITIVE = [
    "MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
    "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "READ_MEDIA_VISUAL_USER_SELECTED",
    "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
    "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS", "READ_CALL_LOG", "WRITE_CALL_LOG", "READ_PHONE_STATE",
    "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "QUERY_ALL_PACKAGES", "REQUEST_INSTALL_PACKAGES",
    "SYSTEM_ALERT_WINDOW", "BIND_ACCESSIBILITY_SERVICE", "USE_FULL_SCREEN_INTENT", "SCHEDULE_EXACT_ALARM",
    "BODY_SENSORS", "PACKAGE_USAGE_STATS",
]
SENSITIVE_PREFIX = ["FOREGROUND_SERVICE_"]


def run(cmd, check=True, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if check and r.returncode != 0:
        sys.exit(f"{' '.join(cmd[:4])}… 失败：\n{(r.stdout + r.stderr)[-3000:]}")
    return r.stdout


def adb(*args, check=True):
    return run(["adb", *args], check=check, timeout=120)


def props(slug):
    path = os.path.join(ROOT, "apps", slug, "brand.properties")
    if not os.path.isfile(path):
        sys.exit(f"apps/{slug}/brand.properties not found（请在仓库根目录运行）")
    out = {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def placeholder(p):
    return (p.get("APPLICATION_ID", "").startswith("com.example")
            or "example." in p.get("PRIVACY_URL", "") or "example." in p.get("WEBSITE_URL", "")
            or p.get("FEEDBACK_EMAIL", "").endswith("example.com"))


def package_of(slug):
    p = props(slug)
    return f"com.verify.{slug}" if placeholder(p) else p["APPLICATION_ID"]


def aapt():
    sdk = next((l.split("=", 1)[1].strip() for l in open(os.path.join(ROOT, "local.properties")) if l.startswith("sdk.dir")), None)
    if not sdk:
        sys.exit("local.properties 里没有 sdk.dir")
    tools = sorted(os.listdir(os.path.join(sdk, "build-tools")))
    return os.path.join(sdk, "build-tools", tools[-1], "aapt")


def start(slug):
    if "device" not in adb("devices").split("\n", 1)[-1]:
        sys.exit("没有已连接的模拟器（adb devices 为空）")
    p = props(slug)
    cmd = ["./gradlew", f":apps:{slug}:assembleRelease", "-q"]
    if placeholder(p):
        cmd += [f"-PAPPLICATION_ID=com.verify.{slug}", "-PPRIVACY_URL=https://verify.invalid/privacy/",
                "-PWEBSITE_URL=https://verify.invalid/", "-PFEEDBACK_EMAIL=verify@verify.invalid"]
        print(f"brand.properties 是占位值：临时用包名 com.verify.{slug} 打 release（不改文件）")
    print("building release APK…")
    run(cmd, timeout=900)
    apk = os.path.join(ROOT, "apps", slug, "build", "outputs", "apk", "release", f"{slug}-release.apk")
    pkg = package_of(slug)

    perms = re.findall(r"uses-permission: name='([^']+)'", run([aapt(), "dump", "permissions", apk]))
    flagged = [x for x in perms if x.split(".")[-1] in SENSITIVE or any(x.split(".")[-1].startswith(s) for s in SENSITIVE_PREFIX)]
    print("permissions:\n  " + "\n  ".join(perms))
    if flagged:
        print("⚠ 需要确认 / 可能要在 Play Console 声明的权限：" + ", ".join(flagged))
    else:
        print("✓ 没有需要声明的敏感权限")

    adb("install", "-r", apk)
    adb("logcat", "-b", "crash", "-c")
    adb("shell", "am", "force-stop", pkg)
    activity = adb("shell", "cmd", "package", "resolve-activity", "--brief", pkg).strip().splitlines()[-1]
    adb("shell", "am", "start", "-W", "-n", activity)
    print(f"installed and cold-started {activity}")
    print("接下来用 emu.py 把核心流程走一遍（冷启动闪屏最多等 8 秒开屏广告，用 emu.py close-ad 关掉），"
          f"走完运行：python3 .claude/skills/new-sub-app/verify_release.py finish {slug}")


def finish(slug):
    pkg = package_of(slug)
    crashes = adb("logcat", "-b", "crash", "-d", check=False).strip()
    if pkg in crashes:
        print("✗ release 包有崩溃：\n" + crashes[-4000:])
        print("常见原因：新依赖用到反射或序列化，缺 R8 keep 规则（加到 apps/<slug>/proguard-rules.pro）")
        sys.exit(1)
    print("✓ 崩溃日志里没有本应用的崩溃")
    if pkg.startswith("com.verify."):
        adb("uninstall", pkg, check=False)
        print(f"uninstalled {pkg}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) == 2 and a[0] == "start":
        start(a[1])
    elif len(a) == 2 and a[0] == "finish":
        finish(a[1])
    else:
        sys.exit(__doc__)
