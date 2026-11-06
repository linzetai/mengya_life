"""QA 包安全前置校验（Gate 3F）。

在跑任何破坏性真机用例前，强制证明：
1. 目标包名是 com.mengyalife.app.qa（独立 applicationId）；
2. 已安装版本以 -qa 结尾，且是 debuggable 构建；
3. 界面出现「QA 测试构建」+ 独立库名，且存在「离线测试引擎（仅 QA）」开关
   —— 这个开关只在 BuildConfig.QA_BUILD=true 的构建里渲染。

不写数据、不清库、不卸载任何包。
"""
import os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_qa_preflight.py")


def pkg_info():
    out = adb(f"shell dumpsys package {PKG}", t=60)
    ver = re.search(r"versionName=(\S+)", out)
    flags = re.search(r"flags=\[([^\]]*)\]", out)
    return (ver.group(1) if ver else ""), (flags.group(1) if flags else "")


print("========== QA 包元数据 ==========")
ver, flags = pkg_info()
check("applicationId 为 QA 包", PKG == QA_PKG, PKG)
check("versionName 以 -qa 结尾", ver.endswith("-qa"), ver)
check("构建可调试（QA 继承 debug）", "DEBUGGABLE" in flags, flags)
check("未在正式包上运行", PKG != PROD_PKG)

print("========== QA_BUILD 界面标记 ==========")
check("应用可启动", restart_app())
entered = False
for _ in range(4):
    if has_tag("settings_back"):
        entered = True
        break
    if has_tag("btn_settings"):
        tap_tag("btn_settings")
    elif has_tag("tab_overview"):
        tap_tag("tab_overview")
        time.sleep(0.8)
    time.sleep(1.2)
check("进入设置", entered or wait_tag("settings_back", 8))
check("设置页出现", has_tag("settings_back"))

# 通过稳定 testTag 定位，不再全页22次dump；完整内容在独立设置测试覆盖。
check("QA 横幅定位", wait_tag("settings_qa_banner", 6))
seen = set(texts(dump()))
if "mengya-life-qa.db" not in " ".join(seen):
    check("滚动至QA标识", scroll_to_tag("settings_qa_banner", 3))
    seen |= set(texts(dump()))
joined = " ".join(sorted(seen))
check("显示「QA 测试构建」横幅", "QA 测试构建" in joined)
check("显示独立 QA 库名", "mengya-life-qa.db" in joined)
check("仅在 QA_BUILD 出现的离线测试引擎开关存在", scroll_to_tag("ai_fake_engine", 8))

# 回到一级页，避免影响后续脚本
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
sys.exit(0 if summary() else 1)
