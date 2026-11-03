"""Gate 3A 真实网络请求验收（QA 包，使用**无效的测试密钥**）。

目的：验证应用真的发出了 HTTPS 请求，并把 401 映射为可读错误；
不会使用、读取或记录任何真实用户的 API Key。
"""
import os, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_advisor_network.py")

ENDPOINT = "https://api.deepseek.com/v1"
MODEL = "deepseek-chat"
DUMMY_KEY = "sk-invalid-g3a-network-test-000"


def replace_field(tag, text):
    tap_tag(tag)
    time.sleep(0.2)
    adb("shell input keyevent KEYCODE_MOVE_END")
    for _ in range(80):
        adb("shell input keyevent KEYCODE_DEL")
    adb("shell input text '%s'" % text)
    close_keyboard()


print("========== 0. 清空 QA 并打开 AI 配置 ==========")
adb("logcat -c")
clear_qa_data()
restart_app()
tap_tag("tab_advisor")
time.sleep(1.0)
tap_tag("advisor_open_settings")
check("设置页", wait_tag("settings_back", 8))
check("AI 配置区", wait_tag("ai_endpoint_input", 8))
# 默认关闭 QA 假引擎；显式确认它不在开启状态
check("使用真实网络引擎（未开 QA 假引擎）", not has_text("QA 离线测试引擎连接成功"))

print("========== 1. 配置真实 HTTPS 地址 + 无效测试密钥 ==========")
replace_field("ai_endpoint_input", ENDPOINT)
replace_field("ai_model_input", MODEL)
replace_field("ai_key_input", DUMMY_KEY)
tap_tag("ai_save")
time.sleep(2.0)
check("配置已保存", has_text("配置已保存"))
check("界面不回显密钥", not has_text(DUMMY_KEY))

print("========== 2. 测试连接：真实请求返回 401 ==========")
tap_tag("ai_test")
time.sleep(12.0)
texts_now = [t for t in texts(dump()) if t]
joined = " ".join(texts_now)
check("显示真实错误（401）", "401" in joined or "API Key 无效" in joined, joined[-200:])
check("未伪造成功", "连接成功" not in joined)
shot("advisor-12-real-401")

print("========== 3. 清理测试配置 ==========")
if has_tag("ai_delete_key"):
    tap_tag("ai_delete_key")
    check("删除确认", wait_tag("ai_delete_dialog", 8))
    tap_tag("ai_delete_confirm")
    time.sleep(1.5)
    check("测试密钥已删除", has_text("API Key 已删除"))
pid = adb(f"shell pidof {PKG}").strip().split()[0] if adb(f"shell pidof {PKG}").strip() else ""
app_logs = adb(f"logcat -d --pid={pid}") if pid else ""
check("应用进程日志不含测试密钥", DUMMY_KEY not in app_logs)

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
