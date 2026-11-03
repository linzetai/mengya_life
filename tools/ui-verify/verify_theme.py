import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

# 主题测试会写 DataStore；只允许跑 QA 包，避免改动正式版偏好。
require_qa("verify_theme.py")

def open_theme_section():
    if not has_tag("btn_settings"):
        tap_tag("tab_overview")
    tap_tag("btn_settings")
    time.sleep(1.0)
    scroll_to_tag("theme_system", 8)
    time.sleep(0.5)

def checked():
    return {n["rid"]: n["checked"] for n in nodes(dump()) if n["rid"].startswith("theme_")}

def back():
    adb("shell input keyevent KEYCODE_BACK"); time.sleep(1.3)

def set_system_night(v):
    adb(f"shell cmd uimode night {v}")
    time.sleep(2.0)

print(">>> 显式浅色基线")
restart_app()
open_theme_section()
tap_tag("theme_light", wait=1.5); time.sleep(1.0)
check("浅色被选中", checked().get("theme_light") == "true", str(checked()))
L_light = png_luminance(shot("12-theme-light"))
check("浅色外观很亮", L_light is not None and L_light > 150, f"L={L_light:.1f}")
back()

print(">>> 显式深色")
open_theme_section()
tap_tag("theme_dark", wait=1.5); time.sleep(1.0)
check("深色被选中", checked().get("theme_dark") == "true")
L_dark = png_luminance(shot("13-theme-dark"))
check("深色显著变暗", L_dark is not None and L_dark < L_light - 60, f"light={L_light:.1f} dark={L_dark:.1f}")

print(">>> 重启后仍是深色")
restart_app()
open_theme_section()
check("重启后仍选中深色", checked().get("theme_dark") == "true", str(checked()))
L_dark2 = png_luminance(shot("14-theme-dark-after-restart"))
check("重启后仍是深色外观", L_dark2 is not None and L_dark2 < L_light - 60, f"L={L_dark2:.1f}")
back()
check("一级页面也是深色", png_luminance(shot("15-main-dark")) < L_light - 60)

print(">>> 跟随系统：随系统切换而变")
open_theme_section()
tap_tag("theme_system", wait=1.2); time.sleep(0.8)
check("跟随系统被选中", checked().get("theme_system") == "true")
back()
set_system_night("no")
time.sleep(1.5)
L_sys_light = png_luminance(shot("16-system-light"))
check("系统转浅色后 App 跟随变亮", L_sys_light is not None and L_sys_light > 150, f"L={L_sys_light:.1f}")
set_system_night("yes")
time.sleep(1.5)
L_sys_dark = png_luminance(shot("17-system-dark"))
check("系统转深色后 App 跟随变暗", L_sys_dark is not None and L_sys_light is not None and L_sys_dark < L_sys_light - 60,
      f"light={L_sys_light:.1f} dark={L_sys_dark:.1f}")

print(">>> 还原系统 auto + 重启后仍跟随系统")
set_system_night("auto")
restart_app()
open_theme_section()
check("重启后仍选中跟随系统", checked().get("theme_system") == "true", str(checked()))
back()

sys.exit(0 if summary() else 1)
