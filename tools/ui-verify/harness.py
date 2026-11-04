"""真机 UI 验证公共库（adb + UIAutomator）。

用法：
    export DEV=192.168.x.x:PORT      # 无线调试端口，见「开发者选项 → 无线调试」
    python3 tools/ui-verify/verify_goals.py

注意：
- 所有定位都基于 Compose testTag 映射出的 resource-id 或文本，不使用固定坐标；
- ModalBottomSheet / DropdownMenu / Dialog 是独立窗口，脚本会等窗口就位再断言。
"""
import re, subprocess, time, os, struct, zlib


DEV = os.environ.get("DEV", "")
if not DEV:
    raise SystemExit("请先设置设备地址：export DEV=<手机IP>:<无线调试端口>")

# 被测包名。**所有会写数据的脚本默认只跑 QA 包**（com.mengyalife.app.qa），
# 它有自己的 applicationId 与独立数据库；正式包只在只读冒烟脚本里出现。
PKG = os.environ.get("MENGYA_PKG", "com.mengyalife.app.qa")
QA_PKG = "com.mengyalife.app.qa"
PROD_PKG = "com.mengyalife.app"


def is_qa():
    return PKG == QA_PKG


def require_qa(script: str):
    """破坏性脚本的硬闸门：只允许 QA applicationId + QA_BUILD 元数据。

    不再提供正式包覆盖开关：数据库/隐私安全不能靠环境变量绕过。
    """
    if not is_qa():
        raise SystemExit(
            f"{script} 会清空被测应用的数据，只允许跑 QA 包（{QA_PKG}）。\n"
            f"当前 MENGYA_PKG={PKG}。正式包禁止执行破坏性用例。"
        )
    try:
        out = adb(f"shell dumpsys package {PKG}", t=60)
        ver = re.search(r"versionName=(\S+)", out)
        flags = re.search(r"flags=\[([^\]]*)\]", out)
        if not ver or not ver.group(1).endswith("-qa"):
            raise SystemExit(f"{script}：{PKG} 的 versionName 不是 -qa（{ver.group(1) if ver else 'unknown'}），拒绝执行")
        if not flags or "DEBUGGABLE" not in flags.group(1):
            raise SystemExit(f"{script}：{PKG} 不是可调试 QA 构建，拒绝执行")
    except SystemExit:
        raise
    except Exception as e:
        raise SystemExit(f"{script}：无法校验 QA 包元数据：{e}")


def require_prod(script: str):
    """只读冒烟脚本：只允许在正式包上跑。"""
    if is_qa():
        raise SystemExit(
            f"{script} 是正式包只读冒烟用例，但当前 MENGYA_PKG={PKG}（QA）。\n"
            f"请设置 MENGYA_PKG={PROD_PKG}"
        )


def clear_qa_data():
    """清空 QA 包数据（只删测试数据）；正式包永远不走这里。"""
    require_qa("clear_qa_data")
    adb(f"shell pm clear {PKG}", t=30)
    time.sleep(1.0)

OUT = os.environ.get("SHOT_DIR", "docs/reports/logs/verify-shots")
os.makedirs(OUT, exist_ok=True)

def adb(cmd, t=120, binary=False):
    r = subprocess.run(f"adb -s {DEV} {cmd}", shell=True, capture_output=True, timeout=t)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")

def focus_app():
    """确保被测应用在前台；被切到别的应用（例如 Chrome）时拉回来。"""
    try:
        out = adb("shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus", t=25)
    except subprocess.TimeoutExpired:
        out = ""
    if PKG not in out:
        try:
            adb(f"shell am start -n {PKG}/com.mengyalife.app.MainActivity", t=30)
        except subprocess.TimeoutExpired:
            pass
        time.sleep(2.0)

def dump(retries=4):
    xml = ""
    for attempt in range(retries):
        try:
            adb("shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1", t=30)
        except subprocess.TimeoutExpired:
            # 最可能是前台不是被测应用（uiautomator 在 Chrome 上会卡住）
            focus_app()
            time.sleep(1.0)
            continue
        xml = adb("shell cat /sdcard/u.xml", t=25)
        if xml.strip().startswith("<?xml"):
            return xml
        focus_app()
        time.sleep(0.8)
    return xml

def nodes(xml):
    out = []
    for m in re.finditer(r"<node[^>]*?/?>", xml):
        t = m.group(0)
        def g(k):
            mm = re.search(k + r'="([^"]*)"', t)
            return mm.group(1) if mm else ""
        mb = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', t)
        x1 = y1 = x2 = y2 = 0
        if mb:
            x1, y1, x2, y2 = map(int, mb.groups())
        out.append(dict(text=g("text"), rid=g("resource-id"), desc=g("content-desc"),
                        checked=g("checked"), center=((x1 + x2) // 2, (y1 + y2) // 2)))
    return out

def texts(xml):
    return [n["text"] for n in nodes(xml) if n["text"].strip()]

def close_keyboard():
    for _ in range(3):
        if "mInputShown=true" not in adb("shell dumpsys input_method"):
            return True
        adb("shell input keyevent KEYCODE_BACK")
        time.sleep(0.8)
    return "mInputShown=true" not in adb("shell dumpsys input_method")

def tap_xy(x, y, wait=1.2):
    adb(f"shell input tap {x} {y}")
    time.sleep(wait)

def tap_tag(tag, wait=1.2):
    for _ in range(4):
        for n in nodes(dump()):
            if n["rid"] == tag:
                tap_xy(*n["center"], wait=wait)
                return True
        time.sleep(0.6)
    return False

def tap_text(text, wait=1.2):
    for _ in range(4):
        for n in nodes(dump()):
            if n["text"] == text:
                tap_xy(*n["center"], wait=wait)
                return True
        time.sleep(0.6)
    return False

def has_tag(tag):
    return any(n["rid"] == tag for n in nodes(dump()))

def has_text(text):
    return any(text in t for t in texts(dump()))

def wait_text(text, timeout=8.0):
    end = time.time() + timeout
    while time.time() < end:
        if has_text(text):
            return True
        time.sleep(0.7)
    return False

def wait_gone_text(text, timeout=8.0):
    end = time.time() + timeout
    while time.time() < end:
        if not has_text(text):
            return True
        time.sleep(0.7)
    return False

def shot(name):
    d = adb("exec-out screencap -p", binary=True)
    i = d.find(b"\x89PNG")
    path = f"{OUT}/{name}.png"
    open(path, "wb").write(d[i:])
    return path

def png_luminance(path):
    d = open(path, "rb").read()
    if d[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    pos, idat, w, h, ct = 8, b"", 0, 0, 6
    while pos < len(d):
        ln = struct.unpack(">I", d[pos:pos+4])[0]
        typ = d[pos+4:pos+8]
        data = d[pos+8:pos+8+ln]
        if typ == b"IHDR":
            w, h, bd, ct = struct.unpack(">IIBB", data[:10])
        elif typ == b"IDAT":
            idat += data
        elif typ == b"IEND":
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    ch = {0:1, 2:3, 3:1, 4:2, 6:4}[ct]
    stride = w * ch
    prev = bytearray(stride)
    p = 0
    total, n = 0.0, 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i-ch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                b = prev[i]
                c = prev[i-ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp-a), abs(pp-b), abs(pp-c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        prev = line
        if y % 10 == 0:
            for x in range(0, w, 19):
                o = x * ch
                if ch >= 3:
                    r, g, b = line[o], line[o+1], line[o+2]
                else:
                    r = g = b = line[o]
                total += 0.2126*r + 0.7152*g + 0.0722*b
                n += 1
    return total / n if n else None

def resumed_package():
    """当前处于 resumed 状态的包名（比 mCurrentFocus 更稳）。"""
    out = adb(
        "shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'",
        t=25,
    )
    m = re.search(r"([A-Za-z0-9_.]+)/[A-Za-z0-9_.]+", out)
    return m.group(1) if m else ""


def ensure_foreground(retries=6):
    """把被测应用拉到前台；避免脚本在 DSH/系统设置界面上空跑。"""
    if resumed_package() == PKG:
        return True
    for _ in range(retries):
        adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity", t=30)
        time.sleep(1.6)
        if resumed_package() == PKG:
            return True
    return False


def wait_app_ui(timeout=15):
    """等应用真正渲染出内容（冷启动时 resumed 不等于 UI 可用）。"""
    end = time.time() + timeout
    while time.time() < end:
        xml = dump()
        if f'package="{PKG}"' in xml:
            usable = [n for n in nodes(xml) if n["text"] or n["rid"]]
            if len(usable) >= 3:
                return True
        time.sleep(0.6)
    return False


def restart_app():
    # 先唤醒：长时间跑 UI 测试时屏幕可能已经熄灭
    adb("shell input keyevent KEYCODE_WAKEUP", t=20)
    adb("shell wm dismiss-keyguard", t=20)
    adb(f"shell am force-stop {PKG}")
    time.sleep(1.0)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity")
    time.sleep(2.5)
    return ensure_foreground() and wait_app_ui()

RESULTS = []
def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    print(("PASS  " if cond else "FAIL  ") + name + (("  " + detail) if detail else ""))

def summary():
    p = sum(1 for _, ok, _ in RESULTS if ok)
    print(f"\n==== SUMMARY: {p}/{len(RESULTS)} passed ====")
    for n, ok, d in RESULTS:
        if not ok:
            print("FAILED:", n, d)
    return p == len(RESULTS)

def scroll_to_tag(tag, max_swipes=8):
    for _ in range(max_swipes):
        if any(n["rid"] == tag for n in nodes(dump())):
            return True
        adb("shell input swipe 600 1900 600 800 260")
        time.sleep(1.0)
    return False

def wait_tag(tag, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        if has_tag(tag):
            return True
        time.sleep(0.6)
    return False

def wait_tag_gone(tag, timeout=10.0):
    end = time.time() + timeout
    while time.time() < end:
        if not has_tag(tag):
            return True
        time.sleep(0.6)
    return False

def ensure_light_theme():
    """把主题归位到「跟随系统」，避免上一轮测试遗留深色影响截图。"""
    if not has_tag("btn_settings"):
        tap_tag("tab_overview")
    tap_tag("btn_settings")
    time.sleep(1.0)
    scroll_to_tag("theme_system", 8)
    ok = tap_tag("theme_system", wait=1.2)
    time.sleep(0.8)
    adb("shell input keyevent KEYCODE_BACK")
    time.sleep(1.2)
    return ok


def restore_chrome():
    """跑完把 Chrome（DSH 界面）切回前台，方便用户查看进度。"""
    try:
        adb("shell am start -n com.android.chrome/com.google.android.apps.chrome.Main", t=30)
        return True
    except subprocess.TimeoutExpired:
        return False
