#!/usr/bin/env python3
"""
在模拟器上按文字操作应用（第 8 步验证、截图前准备内容时用）。依赖 adb；shot 需要 Pillow。

用法（在仓库根目录运行）：
  python3 .claude/skills/new-sub-app/emu.py list                       # 列出屏幕上带文字 / 描述的控件和坐标
  python3 .claude/skills/new-sub-app/emu.py tap "<文字>" [序号]         # 点击：先找完全相同的文字，没有再找包含它的；序号从 0 开始
  python3 .claude/skills/new-sub-app/emu.py type "<输入框标签>" "<内容>" # 点输入框 → 分段输入 → 核对内容，不对就清空重输（最多 3 次）
  python3 .claude/skills/new-sub-app/emu.py hide-keyboard              # 只在键盘弹出时收起（键盘没弹出时按返回会退出页面）
  python3 .claude/skills/new-sub-app/emu.py wait "<文字>" [秒]          # 等屏幕上出现这段文字（默认 15 秒）
  python3 .claude/skills/new-sub-app/emu.py scroll up|down              # 在屏幕中部滑动半屏（down = 看下面的内容），等惯性停下
  python3 .claude/skills/new-sub-app/emu.py top                        # 当前前台 Activity
  python3 .claude/skills/new-sub-app/emu.py close-ad                   # 前台是广告时关掉它（Close / Continue to app / Skip，或右上角关闭按钮）
  python3 .claude/skills/new-sub-app/emu.py consent [yes|no]           # 回答 GDPR 授权表单（debug 包模拟欧洲地区，全新安装首次启动必弹）；默认 yes
  python3 .claude/skills/new-sub-app/emu.py shot <out.png>             # 截屏并缩到宽 540，便于查看（商店截图用 store_assets.py）
  python3 .claude/skills/new-sub-app/emu.py record start [秒]          # 后台开始录屏（默认最长 30 秒），期间照常 tap / wait
  python3 .claude/skills/new-sub-app/emu.py record stop <out.png>      # 停止录屏，拼成一张帧图（每秒 4 帧，需要 ffmpeg），视频存在旁边；用来检查动画

踩过的坑（这些命令已经处理）：
  - 按包含匹配会点错（"Wi-Fi" 命中了介绍文字），所以先精确匹配
  - adb input text 太快会被截断（"Cafe_Guest" 只输入了 "Cafe_G"），所以分段输入并核对
  - 键盘弹出时页面会上移，按旧坐标点会点偏；每次点击都重新取控件坐标
  - 键盘没弹出时按返回键会退出当前页面，所以 hide-keyboard 先检查键盘状态
  - 列表项被底部按钮栏挡住时（系统文件夹选择器里很常见），先 scroll 把它滚出来再 tap，不要按坐标点：
    坐标点击曾经点到键盘的表情栏，把表情字符输进了规则，又误触了执行按钮
  - 滑动后列表还在惯性滚动时，第一次点击只会让列表停下；scroll 会等惯性停下再返回
  - 输入法偶尔弹出引导层（Gboard 的「Try out your stylus」）吞掉输入，而且 uiautomator 看不到它；
    type 发现一个字都没进去时，会在键盘弹出状态下按返回收起它，再重新点输入框重试
"""
import io
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

AD_ACTIVITIES = re.compile(r"AdActivity|AppLovinFullscreenActivity|MaxFullscreen")
AD_CLOSE_LABELS = ["Close", "Close ad", "Continue to app", "Skip", "Skip Ad"]


def adb(*args, binary=False, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=60)
    if check and r.returncode != 0:
        sys.exit(f"adb {' '.join(args)} 失败：{r.stderr.decode(errors='ignore').strip()}")
    return r.stdout if binary else r.stdout.decode(errors="ignore")


# ---------------------------------------------------------------- 读界面

def dump():
    """uiautomator 当前界面；界面在动画中时偶尔会失败，重试几次"""
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/emu_ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/emu_ui.xml", check=False)
        if "<?xml" in xml:
            try:
                return ET.fromstring(xml[xml.index("<?xml"):])
            except ET.ParseError:
                pass
        time.sleep(0.5)
    sys.exit("读不到界面（uiautomator dump 失败）")


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "0,0,0,0"))[:4])
    return (x1 + x2) // 2, (y1 + y2) // 2


def labeled(root):
    for n in root.iter("node"):
        label = n.get("text") or n.get("content-desc") or ""
        if label:
            yield label, n


def find(text, index=0, root=None):
    root = root if root is not None else dump()
    nodes = list(labeled(root))
    exact = [n for label, n in nodes if label.strip().lower() == text.strip().lower()]
    hits = exact or [n for label, n in nodes if text.lower() in label.lower()]
    return hits[index] if len(hits) > index else None


def keyboard_shown():
    return "mInputShown=true" in adb("shell", "dumpsys", "input_method")


def top_activity():
    out = adb("shell", "dumpsys", "activity", "activities")
    line = next((l for l in out.splitlines() if "topResumedActivity" in l or "mResumedActivity" in l), "")
    m = re.search(r"\s(\S+/\S+)\s", line)
    return m.group(1) if m else line.strip()


# ---------------------------------------------------------------- 操作

def tap(text, index=0):
    node = find(text, index)
    if node is None:
        sys.exit(f"没找到：{text}（用 list 看屏幕上有什么）")
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))
    print(f"tapped {(node.get('text') or node.get('content-desc'))!r} at {x},{y}")
    time.sleep(0.6)


def _escape(chunk):
    # adb input text：空格写成 %s，shell 特殊字符加反斜杠
    out = []
    for ch in chunk:
        if ch == " ":
            out.append("%s")
        elif ch in "\\'\"`$&|;<>()[]{}*?!~#%":
            out.append("\\" + ch)
        else:
            out.append(ch)
    return "".join(out)


def _focused():
    for n in dump().iter("node"):
        if n.get("focused") == "true" and "EditText" in (n.get("class") or ""):
            return n
    return None


def _clear(node):
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    count = len(node.get("text") or "") + 5
    adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * count))


def _matches(field, value):
    text = field.get("text") or ""
    if field.get("password") == "true":
        # 密码框读不到明文，只能读到同样长度的掩码
        return len(text) == len(value)
    return text == value


def type_into(label, value):
    if any(ord(c) > 127 for c in value):
        sys.exit("adb input text 只能输入 ASCII；中文等字符请改用 ASCII 示例内容")
    tap(label)
    for attempt in range(3):
        field = _focused()
        if field is None:
            tap(label)
            field = _focused()
        if field is None:
            sys.exit(f"点了「{label}」但没有输入框获得焦点")
        before = field.get("text") or ""
        if before and before != label:
            _clear(field)
        for i in range(0, len(value), 8):
            adb("shell", "input", "text", _escape(value[i:i + 8]))
            time.sleep(0.15)
        time.sleep(0.5)
        field = _focused()
        if field is None:
            sys.exit("输入后输入框失去了焦点")
        if _matches(field, value):
            print(f"typed {'*' * len(value) if field.get('password') == 'true' else repr(value)} into {label!r}")
            return
        after = field.get("text") or ""
        if after == before and keyboard_shown():
            # 一个字都没进去：通常是输入法弹出了引导层（例如 Gboard 的「Try out your stylus」），它在输入法窗口里，
            # uiautomator 看不到。键盘弹出时按返回会连同引导层一起收起，再重新点输入框
            print("输入没有进到输入框（输入法可能弹出了引导层），收起后重试…")
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            time.sleep(0.8)
            tap(label)
            continue
        print(f"内容不对（{after!r}），清空重输…")
    sys.exit(f"重试 3 次后「{label}」里还是不对；用 shot 截图看看是不是有弹层挡着")


def hide_keyboard():
    if keyboard_shown():
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.5)
        print("keyboard hidden")
    else:
        print("keyboard not shown, nothing to do")


def wait(text, seconds=15.0):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if find(text) is not None:
            print(f"found {text!r}")
            return
        time.sleep(0.8)
    if AD_ACTIVITIES.search(top_activity()):
        sys.exit(f"{seconds:.0f} 秒内没出现：{text}。前台是广告，先运行 close-ad")
    if find("Do not consent") is not None:
        sys.exit(f"{seconds:.0f} 秒内没出现：{text}。屏幕上是 GDPR 授权表单，先运行 consent")
    sys.exit(f"{seconds:.0f} 秒内没出现：{text}")


def consent(answer="yes"):
    root = dump()
    if find("Do not consent", root=root) is None:
        print("no consent form on screen")
        return
    label = "Consent" if answer == "yes" else "Do not consent"
    node = find(label, root=root)
    x, y = center(node)
    adb("shell", "input", "tap", str(x), str(y))
    print(f"answered consent form: {label}")
    time.sleep(1.5)


def scroll(direction):
    w, h = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
    x = w // 2
    top, bottom = int(h * 0.35), int(h * 0.75)
    start, end = (bottom, top) if direction == "down" else (top, bottom)
    adb("shell", "input", "swipe", str(x), str(start), str(x), str(end), "500")
    time.sleep(1.5)
    print(f"scrolled {direction}")


def close_ad():
    if not AD_ACTIVITIES.search(top_activity()):
        print("no ad in front")
        return
    root = dump()
    for label in AD_CLOSE_LABELS:
        node = find(label, root=root)
        if node is not None:
            x, y = center(node)
            adb("shell", "input", "tap", str(x), str(y))
            print(f"closed ad via {label!r}")
            break
    else:
        # 测试插屏的关闭按钮常常没有文字：点右上角
        w, h = map(int, re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))[-1])
        adb("shell", "input", "tap", str(int(w * 0.94)), str(int(h * 0.085)))
        print("tapped top-right close button")
    time.sleep(1.0)
    if AD_ACTIVITIES.search(top_activity()):
        sys.exit("广告还在前台，用 shot 截图看关闭按钮在哪")


def shot(out):
    from PIL import Image
    im = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", binary=True))).convert("RGB")
    im.resize((540, round(im.height * 540 / im.width))).save(out)
    print(f"saved {out}")


REC_REMOTE = "/sdcard/emu_rec.mp4"


def record_start(seconds=30):
    """后台开始录屏（最长 [seconds] 秒），期间照常用 tap / wait 操作；用 record stop 结束"""
    adb("shell", "rm", "-f", REC_REMOTE, check=False)
    subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(seconds), "--bit-rate", "6000000", REC_REMOTE],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.0)
    print(f"recording (max {seconds}s)… run: emu.py record stop <out.png>")


def record_stop(out, fps=4):
    """停止录屏，拉回视频，拼成一张帧图（每秒 [fps] 帧，按时间从左到右、从上到下）"""
    import shutil
    import tempfile
    adb("shell", "pkill", "-INT", "screenrecord", check=False)
    time.sleep(2.0)  # 等 screenrecord 写完文件尾
    tmp = tempfile.mkdtemp()
    video = f"{tmp}/rec.mp4"
    adb("pull", REC_REMOTE, video)
    adb("shell", "rm", "-f", REC_REMOTE, check=False)
    if not shutil.which("ffmpeg"):
        shutil.copyfile(video, out.rsplit(".", 1)[0] + ".mp4")
        sys.exit("没装 ffmpeg，只保存了视频：" + out.rsplit(".", 1)[0] + ".mp4（brew install ffmpeg）")
    frames = subprocess.run(
        ["ffprobe", "-v", "error", "-count_frames", "-select_streams", "v:0", "-show_entries", "format=duration",
         "-of", "default=nw=1:nk=1", video], capture_output=True, text=True).stdout.strip()
    total = max(1, int(float(frames or "1") * fps))
    cols = 8
    rows = (total + cols - 1) // cols
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", video, "-vf",
                    f"fps={fps},scale=240:-1,tile={cols}x{rows}", "-frames:v", "1", out], check=True)
    shutil.copyfile(video, out.rsplit(".", 1)[0] + ".mp4")
    print(f"saved {out} ({total} frames, {fps}/s) and the video next to it")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["list"]:
        for label, n in labeled(dump()):
            x, y = center(n)
            print(f"{x:5d} {y:5d}  {label[:80]!r}")
    elif len(a) in (2, 3) and a[0] == "tap":
        tap(a[1], int(a[2]) if len(a) == 3 else 0)
    elif len(a) == 3 and a[0] == "type":
        type_into(a[1], a[2])
    elif a[:1] == ["hide-keyboard"]:
        hide_keyboard()
    elif len(a) in (2, 3) and a[0] == "wait":
        wait(a[1], float(a[2]) if len(a) == 3 else 15.0)
    elif len(a) == 2 and a[0] == "scroll" and a[1] in ("up", "down"):
        scroll(a[1])
    elif a[:1] == ["top"]:
        print(top_activity())
    elif a[:1] == ["close-ad"]:
        close_ad()
    elif a[:1] == ["consent"] and len(a) <= 2 and (len(a) == 1 or a[1] in ("yes", "no")):
        consent(a[1] if len(a) == 2 else "yes")
    elif len(a) == 2 and a[0] == "shot":
        shot(a[1])
    elif a[:2] == ["record", "start"] and len(a) <= 3:
        record_start(int(a[2]) if len(a) == 3 else 30)
    elif a[:2] == ["record", "stop"] and len(a) == 3:
        record_stop(a[2])
    else:
        sys.exit(__doc__)
