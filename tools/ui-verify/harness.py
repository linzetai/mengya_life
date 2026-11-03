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
    if "mengyalife" not in out:
        try:
            adb("shell am start -n com.mengyalife.app/.MainActivity", t=30)
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

def restart_app():
    adb("shell am force-stop com.mengyalife.app")
    time.sleep(1.0)
    adb("shell am start -W -n com.mengyalife.app/.MainActivity")
    time.sleep(2.5)

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
