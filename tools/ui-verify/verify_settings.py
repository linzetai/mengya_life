"""设置页文案验收（Gate 2C，只跑 QA 包）。

重点：主题已持久化，页面不得再出现「重启后恢复跟随系统」或「本轮不引入 DataStore」；
导出入口必须存在，导入必须明确标注尚未实现；导出敏感信息提醒必须可见。
"""
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_settings.py")

# 从干净、确定的状态开始：清 QA 数据并确保应用在前台。
clear_qa_data()

STALE = [
    "重启后恢复「跟随系统」",
    "本轮不引入 DataStore",
    "设置仅在本次运行有效",
    "当前不能导出或导入数据",
    "目前还不能导出或导入数据",
    "本轮仅完成页面结构与说明",
    "本应用未申请网络权限",
    "不会读取、存储或上传任何密钥",
]


def collect_all_texts(max_swipes=20):
    """从头滚到底，收集整页可见文字（避免「文字在屏幕外」造成假阴性）。"""
    seen = set()
    for _ in range(max_swipes):
        seen |= {t for t in texts(dump()) if t.strip()}
        adb("shell input swipe 600 1900 600 700 260")
        time.sleep(0.9)
    seen |= {t for t in texts(dump()) if t.strip()}
    return seen


restart_app()
check("被测应用已在前台", ensure_foreground())
# 明确从总览进入设置，避免上一轮测试停留在其它一级页/滚动位置
if has_tag("tab_overview"):
    tap_tag("tab_overview")
    time.sleep(1.0)
check("首页已渲染", wait_tag("btn_settings", 12))
tap_tag("btn_settings")
check("设置页已打开", wait_tag("settings_back", 8))
time.sleep(1.0)

print("========== 1. 整页文案扫描：过期文案必须消失 ==========")
# 设置页可能在上一轮测试后停在底部；先回到顶部，避免向下滑动无内容。
for _ in range(3):
    adb("shell input swipe 600 700 600 1900 260")
    time.sleep(0.5)
seen = collect_all_texts()
for text in STALE:
    check("不含过期文案：" + text, not any(text in t for t in seen))

print("========== 2. 期望文案必须存在 ==========")
expected = [
    "已实现的设置会立即生效并保存在本机",
    "导出我的数据",
    "导入数据",
    "导入恢复尚未实现",
    "本机数据库",
    "保存在本机（DataStore）",
    "重启后保持上次选择",
    "随系统深浅色变化",
    "INTERNET 权限",
    "API 服务地址（HTTPS）",
    "模型名称",
    "API Key",
    "AI 与隐私",
    "数据采集与授权",
]
for text in expected:
    check("包含文案：" + text, any(text in t for t in seen), text)

adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("btn_settings")
time.sleep(1.0)
check("滚动到数据区", scroll_to_tag("settings_export_data", 12))
check("QA 构建横幅", "QA 测试构建" in " ".join(seen) and "mengya-life-qa.db" in " ".join(seen))
check("版本号来自安装包", "0.12.0-g3d2-qa" in " ".join(seen), "应为 0.12.0-g3d2-qa")
check("BYOK 状态胶囊准确", "自带密钥" in " ".join(seen))

print("========== 3. 截图：数据区 ==========")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("btn_settings")
time.sleep(1.0)
check("数据区可见", scroll_to_tag("settings_export_data", 10))
check("导出入口可点击", has_tag("settings_export_data"))
shot("01-settings-data-export")

print("========== 4. 截图：主题说明 ==========")
check("主题区可见", scroll_to_tag("theme_system", 10))
check("主题选项齐全", all(has_tag(t) for t in ("theme_system", "theme_light", "theme_dark")))
check("主题说明包含持久化", has_text("保存在本机（DataStore）"))
check("主题说明包含重启保持", has_text("重启后保持上次选择"))
check("主题说明包含跟随系统", has_text("随系统深浅色变化"))
shot("02-settings-theme-note")

print("========== 5. 截图：关于 ==========")
adb("shell input swipe 600 1900 600 700 260")
time.sleep(1.0)
check("关于区显示版本", has_text("0.12.0-g3d2-qa"))
check("数据存储说明", has_text("本机（无云端同步）"))
shot("03-settings-about")

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
