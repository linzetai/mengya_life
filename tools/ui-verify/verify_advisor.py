"""Gate 3A 真实顾问基础闭环验收（只跑 QA 包）。

覆盖：BYOK 配置 / Keystore 加密 / 离线假引擎对话 / 失败与重试 / 取消 /
重启恢复 / 目标上下文授权 / 删除密钥 / 导出不含密钥。
离线引擎仅 QA 构建可用；真实网络 401 由 verify_advisor_network.py 覆盖。
"""
import os, sys, time, sqlite3, subprocess, json, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_advisor.py")

SECRET = "sk-qa-g3a-secret-123"
ENDPOINT = "https://api.example.com/v1"
MODEL = "qa-offline"
GOAL = "CtxGoal"
CONTEXT_Q = "CtxGoal status"
OUT_EXPORT = os.environ.get("EXPORT_OUT", "docs/reports/logs/g3a-export")
os.makedirs(OUT_EXPORT, exist_ok=True)


def db_snapshot():
    d = "/tmp/g3a-advisor-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    con = sqlite3.connect(f"{d}/mengya-life-qa.db")
    rows = con.execute(
        "SELECT role,status,content,error_code,context_goal_id FROM advisor_messages ORDER BY created_at"
    ).fetchall()
    goals = con.execute("SELECT id,title FROM goals ORDER BY id").fetchall()
    journals = con.execute("SELECT COUNT(*) FROM journal_entries").fetchone()[0]
    con.close()
    return rows, goals, journals


def pull_prefs():
    p = subprocess.run(
        f"adb -s {DEV} exec-out run-as {PKG} cat files/datastore/mengya_ai_qa.preferences_pb",
        shell=True, capture_output=True,
    )
    return p.stdout


def replace_field(tag, text):
    tap_tag(tag)
    time.sleep(0.2)
    adb("shell input keyevent KEYCODE_MOVE_END")
    for _ in range(80):
        adb("shell input keyevent KEYCODE_DEL")
    adb("shell input text '%s'" % text)
    close_keyboard()


def send(text, wait_reply=True):
    tap_tag("advisor_input")
    adb("shell input text '%s'" % text)
    close_keyboard()
    time.sleep(0.3)
    tap_tag("advisor_send")
    time.sleep(1.6)
    if wait_reply:
        return has_text("QA 离线测试引擎")
    return True


print("========== 0. 清空 QA 数据 ==========")
adb("shell logcat -b crash -c")
adb("logcat -c")
clear_qa_data()
restart_app()
tap_tag("tab_advisor")
time.sleep(1.2)
check("未配置时显示引导", wait_tag("advisor_not_configured", 8))
check("引导说明不发送日记/目标", has_text("不经过萌芽人生服务器"))
shot("advisor-01-unconfigured")

print("========== 1. BYOK 配置 + 测试连接 + 加密 ==========")
check("进入设置", tap_tag("advisor_open_settings") or tap_tag("btn_settings"))
check("设置页", wait_tag("settings_back", 8))
check("AI 待配置", wait_tag("ai_endpoint_input", 8))
check("启用 QA 离线引擎", tap_tag("ai_fake_engine"))
time.sleep(0.5)
replace_field("ai_endpoint_input", ENDPOINT)
replace_field("ai_model_input", MODEL)
replace_field("ai_key_input", SECRET)
tap_tag("ai_save")
time.sleep(2.0)
check("保存成功", has_text("配置已保存"))
check("key 不回显", not has_text(SECRET))
check("key 已保存标记", has_tag("ai_key_saved"))
shot("advisor-02-config-saved")
tap_tag("ai_test")
time.sleep(2.0)
check("测试连接成功", has_text("QA 离线测试引擎连接成功"))
shot("advisor-03-test-connection")

prefs = pull_prefs()
check("DataStore 含 encrypted_api_key", b"encrypted_api_key" in prefs)
check("DataStore 不含明文 key", SECRET.encode() not in prefs)
rows, goals, journals = db_snapshot()
check("Room 中没有 key", all(SECRET not in (r[2] or "") for r in rows))
pid = adb(f"shell pidof {PKG}").strip().split()[0] if adb(f"shell pidof {PKG}").strip() else ""
check("应用进程日志不含 key", SECRET not in (adb(f"logcat -d --pid={pid}") if pid else ""))

print("========== 2. 成功对话 + 重启恢复 ==========")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.2)
tap_tag("tab_advisor")
time.sleep(1.0)
# Gate 3B1 起首次对话前有全局授权页；本用例只验证顾问链路，授权细节由 verify_g3b1 覆盖。
if wait_tag("advisor_consent_panel", 6):
    tap_tag("advisor_consent_agree")
    check("首次授权完成", wait_tag("advisor_consent_summary", 10))
time.sleep(1.0)
check("已配置状态", has_text("已配置"))
send("hello_advisor_one")
check("收到真实链路回复", wait_text("QA 离线测试引擎", 12))
check("用户气泡", has_tag("advisor_user_message"))
check("助手气泡", has_tag("advisor_assistant_message"))
shot("advisor-04-success")
rows, _, _ = db_snapshot()
check("保存用户+助手", len(rows) == 2 and rows[0][0] == "USER" and rows[1][0] == "ASSISTANT", str(rows))

restart_app()
tap_tag("tab_advisor")
time.sleep(1.2)
check("重启后用户消息仍在", wait_text("hello_advisor_one", 8))
check("重启后助手消息仍在", has_text("QA 离线测试引擎"))
shot("advisor-05-restart-persist")

print("========== 3. 失败 / 重试 / 取消 ==========")
send("[401]", wait_reply=False)
check("401 错误文案", wait_text("API Key 无效或已过期", 8))
check("失败卡片", has_tag("advisor_retry_last"))
shot("advisor-06-401")
users_before = sum(1 for r in db_snapshot()[0] if r[0] == "USER")
tap_tag("advisor_retry_button_last")
time.sleep(2.0)
check("重试后仍失败但显示错误", wait_text("API Key 无效或已过期", 8))
users_after = sum(1 for r in db_snapshot()[0] if r[0] == "USER")
check("重试不产生重复用户消息", users_before == users_after, f"{users_before}->{users_after}")

send("[429]", wait_reply=False)
check("429 文案", wait_text("429", 8))
send("[timeout]", wait_reply=False)
check("超时文案", wait_text("请求超时", 8))
send("[offline]", wait_reply=False)
check("离线文案", wait_text("网络不可用", 8))
shot("advisor-07-errors")

tap_tag("advisor_input")
adb("shell input text '[slow]'")
close_keyboard()
time.sleep(0.2)
tap_tag("advisor_send")
check("等待状态", wait_tag("advisor_waiting", 8))
tap_tag("advisor_cancel")
time.sleep(2.0)
check("取消后显示取消卡片", has_tag("advisor_retry_last"))
rows, _, _ = db_snapshot()
check("取消写入 CANCELLED 且无假回复",
      any(r[0] == "ASSISTANT" and r[1] == "CANCELLED" and r[2] == "" for r in rows), str(rows[-3:]))
shot("advisor-08-cancelled")

print("========== 4. 目标上下文授权 ==========")
open_goals()
time.sleep(1.0)
tap_tag("goal_new_long_term")
wait_tag("goal_editor_title", 8)
tap_tag("goal_editor_title")
adb("shell input text '%s'" % GOAL)
close_keyboard()
tap_tag("goal_editor_save")
time.sleep(2.0)
check("目标已创建", wait_text(GOAL, 8))
tap_tag("tab_advisor")
time.sleep(1.0)
# Gate 3B1 起上下文改为「按授权自动检索」，手动选择器已移除；
# 这里验证自动来源入口与来源弹窗，不再断言已删除的 advisor_context_row。
time.sleep(0.5)
send("CtxGoal%sstatus")
check("上下文问题收到回复", wait_text("QA 离线测试引擎", 12))
check("自动来源入口可见", wait_tag("advisor_sources_last", 10))
tap_tag("advisor_sources_last")
check("来源弹窗出现", wait_tag("advisor_sources_dialog", 8))
check("来源弹窗包含目标", has_text("目标") or has_text(GOAL))
shot("advisor-09-sources")
tap_tag("advisor_sources_close")
time.sleep(0.8)
rows, goals, _ = db_snapshot()
ctx = [r for r in rows if r[2] == CONTEXT_Q]
check("用户消息已记录", len(ctx) == 1, str(ctx))
check("目标未被回复自动修改", len(goals) == 1, str(goals))
check("AI 没有自动新增目标/进展", len(goals) == 1, str(goals))
shot("advisor-10-context-sent")

print("========== 5. 导出不含密钥 ==========")
tap_tag("btn_settings")
time.sleep(1.2)
check("导出入口", scroll_to_tag("settings_export_data", 12))
tap_tag("settings_export_data")
check("导出提醒", wait_tag("export_warning", 8))
tap_tag("export_confirm")
time.sleep(2.5)
check("SAF 保存", has_text("保存"))
tap_text("保存")
ok = False
for _ in range(30):
    if has_text("导出完成并已校验"):
        ok = True
        break
    time.sleep(0.5)
check("导出成功", ok)
remote = adb("shell 'ls -t /sdcard/Download/mengya-life-export-*.json 2>/dev/null | head -1'").strip()
check("导出文件存在", bool(remote), remote)
if remote:
    local = os.path.join(OUT_EXPORT, os.path.basename(remote))
    adb(f"pull {remote} {local}")
    text = open(local, encoding="utf-8").read()
    check("导出不含 key", SECRET not in text)
    check("导出不含聊天正文", "context_question" not in text and "hello_advisor_one" not in text)
    doc = json.loads(text)
    EXPECTED_SCHEMA = int(os.environ.get("MENGYA_EXPECTED_SCHEMA", "11"))
    check("导出 db schema=%d" % EXPECTED_SCHEMA, doc.get("database", {}).get("schemaVersion") == EXPECTED_SCHEMA, str(doc.get("database")))
    adb(f"shell rm -f {remote}")

print("========== 6. 删除密钥 ==========")
# 回到设置顶部（此时停留在数据/导出分组）
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("btn_settings")
time.sleep(1.2)
check("回到 AI 配置", scroll_to_tag("ai_endpoint_input", 10) or has_tag("ai_endpoint_input"))
check("删除密钥入口", has_tag("ai_delete_key"))
tap_tag("ai_delete_key")
check("删除确认", wait_tag("ai_delete_dialog", 8))
tap_tag("ai_delete_confirm")
time.sleep(2.0)
check("删除提示", has_text("API Key 已删除"))
check("已无 key 标记", not has_tag("ai_key_saved"))
prefs = pull_prefs()
check("DataStore 已无密文", b"encrypted_api_key" not in prefs)
tap_tag("ai_test")
time.sleep(1.5)
check("删除后不能测试", has_text("请先填写并保存 API Key"))
shot("advisor-11-key-deleted")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("tab_advisor")
time.sleep(1.0)
check("顾问回到未配置", has_text("尚未配置模型服务"))

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
