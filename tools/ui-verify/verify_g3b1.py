"""Gate 3B-1 真机验收（QA 包 + 离线引擎）。

覆盖：首次授权/拒绝、四类开关、撤回、换服务地址重授权、自动上下文、
日记/目标来源、历史 epoch 隔离、目标草案与重启恢复、业务数据不被修改。
"""
import os, sys, time, sqlite3, subprocess, json, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_g3b1.py")
SECRET = "sk-g3b1-qa-secret"
ENDPOINT = "https://api.example.com/v1"
ENDPOINT2 = "https://api2.example.com/v1"
MODEL = "qa-offline"


def db():
    d = "/tmp/g3b1-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    con = sqlite3.connect(f"{d}/mengya-life-qa.db")
    messages = con.execute(
        "SELECT role,status,content,context_epoch,sources_json FROM advisor_messages ORDER BY created_at"
    ).fetchall()
    goals = con.execute("SELECT id,title FROM goals ORDER BY id").fetchall()
    checkins = con.execute("SELECT COUNT(*) FROM goal_checkins").fetchone()[0]
    journals = con.execute("SELECT id,title FROM journal_entries ORDER BY id").fetchall()
    proposals = con.execute("SELECT COUNT(*) FROM goal_proposals").fetchone()[0]
    con.close()
    return messages, goals, checkins, journals, proposals


def replace_field(tag, text):
    tap_tag(tag)
    time.sleep(0.2)
    adb("shell input keyevent KEYCODE_MOVE_END")
    for _ in range(80):
        adb("shell input keyevent KEYCODE_DEL")
    adb("shell input text '%s'" % text)
    close_keyboard()


def send(text):
    tap_tag("advisor_input")
    adb("shell input text '%s'" % text)
    close_keyboard()
    time.sleep(0.3)
    tap_tag("advisor_send")
    time.sleep(1.8)
    return wait_text("QA 离线测试引擎", 12)


def open_settings():
    tap_tag("btn_settings")
    time.sleep(1.2)


print("========== 0. 清空 QA 并配置模型 ==========")
adb("shell logcat -b crash -c")
clear_qa_data()
restart_app()
tap_tag("tab_advisor")
time.sleep(1.0)
check("未配置引导", wait_tag("advisor_not_configured", 8))
tap_tag("advisor_open_settings")
check("设置页", wait_tag("ai_endpoint_input", 8))
tap_tag("ai_fake_engine")
time.sleep(0.5)
replace_field("ai_endpoint_input", ENDPOINT)
replace_field("ai_model_input", MODEL)
replace_field("ai_key_input", SECRET)
tap_tag("ai_save")
time.sleep(2)
check("模型配置保存", has_text("配置已保存"))
check("界面不回显 key", not has_text(SECRET))
shot("g3b1-01-settings")

print("========== 1. 首次授权：拒绝与同意 ==========")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("tab_advisor")
time.sleep(1.0)
check("首次授权页", wait_tag("advisor_consent_panel", 8))
check("授权文案包含四类", has_text("目标、进展、日记、历史对话"))
check("说明第三方接收", has_text("第三方 AI 服务"))
check("说明不能撤回已发数据", has_text("无法从对方撤回"))
shot("g3b1-02-consent")
tap_tag("advisor_consent_decline")
time.sleep(1.0)
check("拒绝后仍在授权页", has_tag("advisor_consent_panel"))
check("拒绝提示", has_tag("advisor_consent_info"))
check("拒绝后没有消息", len(db()[0]) == 0)
tap_tag("advisor_consent_agree")
time.sleep(1.5)
check("同意后进入对话", has_tag("advisor_consent_summary"))
shot("g3b1-03-granted")

print("========== 2. 创建目标/进展/日记 ==========")
tap_tag("tab_goals")
time.sleep(1.0)
tap_tag("goal_new_long_term")
wait_tag("goal_editor_title", 8)
tap_tag("goal_editor_title")
adb("shell input text 'G3B1Health'")
close_keyboard()
tap_tag("goal_editor_save")
time.sleep(2)
check("目标创建", wait_text("G3B1Health", 8))
ids = [int(m.group(1)) for n in nodes(dump()) for m in [re.match(r"goal_node_(\d+)", n["rid"])] if m]
gid = ids[0]
tap_tag("goal_menu_%d" % gid)
time.sleep(0.7)
tap_text("记录进展")
time.sleep(1.0)
wait_tag("goal_checkin_note", 8)
tap_tag("goal_checkin_note")
adb("shell input text 'run_three_times'")
tap_tag("goal_checkin_metric")
adb("shell input text '3'")
tap_tag("goal_checkin_unit")
adb("shell input text 'times'")
close_keyboard()
tap_tag("goal_checkin_save")
time.sleep(2)

tap_tag("tab_diary")
time.sleep(1.0)
tap_tag("diary_new")
wait_tag("journal_body_input", 8)
tap_tag("journal_title_input")
adb("shell input text 'SleepNote'")
tap_tag("journal_body_input")
adb("shell input text 'sleep_bad_anxiety'")
close_keyboard()
tap_tag("journal_save")
time.sleep(2)
check("日记创建", wait_text("SleepNote", 8))

print("========== 3. 自动上下文：日记来源 ==========")
tap_tag("tab_advisor")
time.sleep(1.0)
check("日记相关回复", send("sleep%sanxiety"))
check("来源入口", has_tag("advisor_sources_last"))
shot("g3b1-04-context-journal")
messages, goals, checkins, journals, proposals = db()
last_sources = json.loads(messages[-1][4] or "[]")
check("上下文引用日记", any(s["type"] == "JOURNAL" for s in last_sources), str(last_sources))

print("========== 4. 关闭日记：未来不再发送日记 ==========")
open_settings()
check("滚动到日记开关", scroll_to_tag("ai_category_JOURNAL", 12))
tap_tag("ai_category_JOURNAL")
time.sleep(1.2)
check("关闭提示", has_tag("ai_privacy_status") and has_text("已关闭"))
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("tab_advisor")
time.sleep(1.0)
check("关闭后仍可对话", send("sleep%sanxiety"))
messages, _, _, _, _ = db()
last_sources = json.loads(messages[-1][4] or "[]")
check("关闭后不再引用日记", all(s["type"] != "JOURNAL" for s in last_sources), str(last_sources))
check("epoch 已递增", messages[-1][3] > messages[0][3], str([(m[3]) for m in messages]))
shot("g3b1-05-journal-off")

print("========== 5. 关闭目标：未来不再发送目标/进展 ==========")
open_settings()
scroll_to_tag("ai_category_GOALS", 10)
tap_tag("ai_category_GOALS")
time.sleep(1.2)
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("tab_advisor")
time.sleep(1.0)
check("关闭目标后对话", send("health%goal"))
messages, _, _, _, _ = db()
last_sources = json.loads(messages[-1][4] or "[]")
check("关闭后不再引用目标/进展",
      all(s["type"] not in ("GOAL", "PROGRESS") for s in last_sources), str(last_sources))

print("========== 6. 撤回授权：保留本地数据 ==========")
before_withdraw = db()
open_settings()
check("撤回入口", scroll_to_tag("ai_withdraw", 12))
tap_tag("ai_withdraw")
check("撤回确认框", wait_tag("ai_withdraw_dialog", 8))
tap_tag("ai_withdraw_confirm")
time.sleep(1.5)
check("撤回状态", has_text("未授权") or has_tag("ai_consent_status_none"))
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("tab_advisor")
time.sleep(1.0)
check("撤回后回到授权页", wait_tag("advisor_consent_panel", 8))
after_withdraw = db()
check("撤回不删除消息", len(after_withdraw[0]) == len(before_withdraw[0]))
check("撤回不删除目标/日记", after_withdraw[1] == before_withdraw[1] and after_withdraw[3] == before_withdraw[3])
shot("g3b1-06-withdrawn")

print("========== 7. 更换服务地址：重新授权 ==========")
tap_tag("advisor_consent_agree")
time.sleep(1.5)
open_settings()
check("地址输入", scroll_to_tag("ai_endpoint_input", 10))
replace_field("ai_endpoint_input", ENDPOINT2)
tap_tag("ai_save")
time.sleep(2)
check("新地址保存", has_text("配置已保存"))
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("tab_advisor")
time.sleep(1.0)
check("换地址后要求重新授权", has_tag("advisor_consent_panel") and has_text("需要重新确认数据接收方"))
check("显示新接收方", has_text(ENDPOINT2))
shot("g3b1-07-reconsent")
tap_tag("advisor_consent_agree")
time.sleep(1.5)

print("========== 8. 目标草案（QA 引擎） ==========")
tap_tag("advisor_make_proposal")
time.sleep(2)
check("草案入口", wait_tag("advisor_proposal_open", 12))
tap_tag("advisor_proposal_open")
time.sleep(1.5)
check("草案弹窗", wait_tag("goal_proposal_dialog", 8))
check("草案未保存标记", has_text("目标草案（尚未保存）"))
check("草案层级标题", has_text("阶段目标") or has_text("[阶段]"))
shot("g3b1-08-proposal")
tap_tag("goal_proposal_close")
time.sleep(0.8)
messages, goals, checkins, journals, proposals = db()
check("草案已持久化", proposals == 1, str(proposals))
check("没有写入正式目标", len(goals) == 1, str(goals))
check("没有写入进展", checkins == 1, str(checkins))
check("草案消息存在", any(m[0] == "ASSISTANT" for m in messages))

print("========== 9. 重启后草案与对话保留 ==========")
restart_app()
tap_tag("tab_advisor")
time.sleep(1.2)
check("重启后对话仍在", has_tag("advisor_assistant_message"))
check("重启后草案入口仍在", wait_tag("advisor_proposal_open", 10))
tap_tag("advisor_proposal_open")
time.sleep(1.2)
check("重启后草案可查看", has_tag("goal_proposal_dialog"))
shot("g3b1-09-proposal-restart")
tap_tag("goal_proposal_close")

print("========== 10. 稳定性 ==========")
crash = adb("shell logcat -b crash -d")
check("无应用崩溃", "mengyalife" not in crash.lower(), crash[:120])
check("进程存活", PKG in adb("shell ps -A"))

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
