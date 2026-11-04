import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

# 导航脚本只读，但默认仍跑 QA 包，避免误动正式版。
require_qa("verify_navigation.py")

restart_app()
time.sleep(2.0)
EXPECT = [("tab_overview", "你正在走的方向"), ("tab_goals", "把方向拆成能推进的层级"),
          ("tab_trends", "看见变化，而不是凭感觉"), ("tab_diary", "把今天留下来")]
for tag, headline in EXPECT:
    check(f"切换到 {tag}", tap_tag(tag))
    check(f"{tag} 内容正常", any(headline in t for t in texts(dump())), headline)
check("顾问页可进入", tap_tag("tab_advisor") and (
    has_tag("advisor_input") or has_tag("advisor_consent_panel") or has_tag("advisor_not_configured")
))
# 首次运行/清空数据后可能停在配置或授权页，这是正确的产品状态；不强制可发送。
check("顾问页状态正常", has_tag("advisor_input") or has_tag("advisor_consent_panel") or has_tag("advisor_not_configured"))
# 输入会弹起软键盘，底栏会被顶起；先关键盘再继续导航，否则点击坐标会落空
close_keyboard()
time.sleep(1.5)

for tag, _ in EXPECT:
    tap_tag(tag)
    entered = False
    for _ in range(3):
        if tap_tag("btn_settings") and wait_tag("settings_back", 6):
            entered = True
            break
    check(f"{tag} 可进入设置", entered)
    appeared = entered
    check(f"{tag} 设置页已就位", appeared)
    check(f"{tag} 设置页隐藏底栏", not any(n["rid"].startswith("tab_") for n in nodes(dump())))
    adb("shell input keyevent KEYCODE_BACK"); time.sleep(1.2)
    check(f"{tag} 返回后底栏恢复", any(n["rid"].startswith("tab_") for n in nodes(dump())))

# 导航脚本不依赖任何业务数据；只验证日记页仍然可用（回归不破坏功能入口）
check("日记页仍可打开", tap_tag("tab_diary") and has_tag("diary_new"))
check("无崩溃日志", "com.mengyalife.app" not in adb("shell logcat -b crash -d"))
sys.exit(0 if summary() else 1)
