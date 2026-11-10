"""每周回顾 + 每周 AI 真机状态机（Gate 3F，只跑 QA 包）。

只用 QA 假引擎（BuildConfig.QA_BUILD）与隔离 QA 数据：
- 成功：READY + 客户端从 factIds 构造来源；
- 失败 / 超时：FAILED，保留本地回顾，不伪造成功；
- 中断：进程被杀后 generating 不残留、额度不重复；
- READY → STALE：数据变化后缓存失效；
- 修改类别 / 撤回授权：旧结果不再作为有效内容；
- 自动开关关闭：后台 Worker 不再发送。

不访问真实模型、不使用真实 API Key、不发送任何真实隐私数据。
"""
import json, os, re, sqlite3, subprocess, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_weekly_ai.py")

WEEKLY_SCENARIOS = {"READY", "FAILED_401", "TIMEOUT", "INTERRUPT", "STALE", "CATEGORIES", "WITHDRAW", "AUTO_DISABLED"}
WEEKLY_CASE = os.environ.get("MENGYA_WEEKLY_SCENARIO", "").strip().upper()
if WEEKLY_CASE and WEEKLY_CASE not in WEEKLY_SCENARIOS:
    raise SystemExit(f"未知weekly scenario {WEEKLY_CASE}; allowed={sorted(WEEKLY_SCENARIOS)}")

def should_run_scenario(name):
    return not WEEKLY_CASE or WEEKLY_CASE == name

PKG_QA = QA_PKG
DB = "mengya-life-qa.db"
KEY = "sk-qa-weekly-fake-0001"
ENDPOINT = "https://api.example.com/v1"
GOAL = "WeeklyGoal"
DIARY = "WeeklyDiary"


# ---------------- 通用 UI ----------------

def checked(tag):
    for n in nodes(dump()):
        if n["rid"] == tag:
            return n["checked"] == "true"
    return False


def wait_checked(tag, timeout=8):
    end = time.time() + timeout
    while time.time() < end:
        if checked(tag):
            return True
        time.sleep(0.4)
    return False


def scroll_top():
    for _ in range(5):
        adb("shell input swipe 600 700 600 1900 220")
        time.sleep(0.35)


def ensure_visible(tag, max_swipes=14, safe_y=2150):
    """把元素滚到可点击区域；避免停在系统手势区/悬浮层下方。"""
    if not has_tag(tag):
        scroll_to_tag(tag, max_swipes)
    for _ in range(3):
        n = next((x for x in nodes(dump()) if x["rid"] == tag), None)
        if n and n["center"][1] < safe_y:
            return True
        adb("shell input swipe 600 1750 600 1250 220")
        time.sleep(0.5)
    n = next((x for x in nodes(dump()) if x["rid"] == tag), None)
    return bool(n and n["center"][1] < safe_y)


def tap_safe(tag):
    if not ensure_visible(tag):
        return False
    return tap_tag(tag)


def scroll_up_to_tag(tag, max_swipes=12):
    for _ in range(max_swipes):
        if has_tag(tag):
            return True
        adb("shell input swipe 600 800 600 1900 240")
        time.sleep(0.6)
    return has_tag(tag)


def open_settings():
    for _ in range(5):
        if has_tag("settings_back"):
            return True
        if has_tag("weekly_review_screen"):
            tap_tag("weekly_review_back")
            time.sleep(1.2)
            continue
        if has_tag("btn_settings"):
            tap_tag("btn_settings")
            time.sleep(1.2)
            continue
        if has_tag("tab_overview"):
            tap_tag("tab_overview")
            time.sleep(1.0)
            continue
        adb("shell input keyevent KEYCODE_BACK")
        time.sleep(1.0)
    return has_tag("settings_back")


def open_weekly():
    if not open_settings():
        return False
    scroll_top()
    for _ in range(24):
        if has_tag("settings_weekly_review"):
            break
        adb("shell input swipe 600 1900 600 700 240")
        time.sleep(0.7)
    if not tap_tag("settings_weekly_review"):
        return False
    return wait_tag("weekly_review_screen", 10)


def back_to_main():
    if has_tag("weekly_review_screen"):
        tap_tag("weekly_review_back")
        time.sleep(1.0)
    if has_tag("settings_back"):
        adb("shell input keyevent KEYCODE_BACK")
        time.sleep(1.0)


def replace_field(tag, text):
    if not tap_tag(tag):
        return False
    time.sleep(0.2)
    adb("shell input keyevent KEYCODE_MOVE_END")
    for _ in range(80):
        adb("shell input keyevent KEYCODE_DEL")
    adb("shell input text '%s'" % text)
    close_keyboard()
    return True


def set_ai_model(model):
    if not open_settings():
        return False
    scroll_top()
    if not scroll_to_tag("ai_model_input", 12):
        return False
    replace_field("ai_model_input", model)
    tap_tag("ai_save")
    return wait_text("配置已保存", 8)


# ---------------- 数据准备 ----------------

def goal_id():
    for n in nodes(dump()):
        m = re.match(r"goal_node_(\d+)", n["rid"])
        if m:
            return int(m.group(1))
    return None


def create_goal(title):
    if not has_tag("btn_settings"):
        back_to_main()
        open_goals()
    else:
        open_goals()
    time.sleep(1.2)
    if not tap_tag("goal_new_long_term"):
        return False
    if not wait_tag("goal_editor_title", 8):
        return False
    tap_tag("goal_editor_title")
    adb("shell input text '%s'" % title)
    close_keyboard()
    tap_tag("goal_editor_save")
    return wait_text(title, 10)


def add_checkin(title):
    gid = goal_id()
    if gid is None:
        return False
    for _ in range(3):
        if tap_tag("goal_menu_%d" % gid):
            time.sleep(0.7)
            if has_text("记录进展"):
                break
        time.sleep(0.6)
    if not tap_text("记录进展"):
        return False
    if not wait_tag("goal_checkin_note", 8):
        return False
    tap_tag("goal_checkin_note")
    adb("shell input text 'weekly%scheckin'")
    tap_tag("goal_checkin_metric")
    adb("shell input text '3'")
    tap_tag("goal_checkin_unit")
    adb("shell input text 'min'")
    close_keyboard()
    tap_tag("goal_checkin_save")
    time.sleep(1.5)
    return has_text(title)


def create_diary(title, body):
    back_to_main()
    tap_tag("tab_diary")
    time.sleep(1.2)
    tap_tag("diary_new")
    if not wait_tag("journal_body_input", 8):
        return False
    tap_tag("journal_title_input")
    adb("shell input text '%s'" % title)
    tap_tag("journal_body_input")
    adb("shell input text '%s'" % body)
    close_keyboard()
    tap_tag("journal_save")
    time.sleep(1.2)
    return wait_text(title, 8)


# ---------------- AI 配置 / 授权 ----------------

def configure_fake_ai(model="qa-fake"):
    if not open_settings():
        return False
    scroll_top()
    if not wait_tag("ai_endpoint_input", 10):
        return False
    replace_field("ai_endpoint_input", ENDPOINT)
    replace_field("ai_model_input", model)
    replace_field("ai_key_input", KEY)
    tap_tag("ai_save")
    if not wait_text("配置已保存", 8):
        return False
    if not ensure_visible("ai_fake_engine"):
        return False
    if not checked("ai_fake_engine"):
        tap_tag("ai_fake_engine")
        time.sleep(1.0)
    if not checked("ai_fake_engine"):
        return False
    if not scroll_up_to_tag("ai_test", 10):
        return False
    tap_tag("ai_test")
    return wait_text("QA 离线测试引擎连接成功", 12)


def grant_consent():
    back_to_main()
    if not tap_tag("tab_advisor"):
        return False
    time.sleep(1.2)
    if wait_tag("advisor_consent_panel", 8):
        tap_tag("advisor_consent_agree")
        time.sleep(1.2)
    return wait_tag("advisor_consent_summary", 8) or has_tag("advisor_input")


# ---------------- 每周开关 ----------------

def enable_weekly_and_ai():
    if not open_settings():
        return False
    scroll_top()
    for _ in range(24):
        if has_tag("settings_weekly_toggle"):
            break
        adb("shell input swipe 600 1900 600 700 240")
        time.sleep(0.6)
    if not checked("settings_weekly_toggle"):
        tap_safe("settings_weekly_toggle")
        time.sleep(2.0)
        if has_text("允许") and not has_tag("settings_back"):
            tap_text("允许")
            time.sleep(0.8)
        if has_text("Allow") and not has_tag("settings_back"):
            tap_text("Allow")
            time.sleep(0.8)
    if not checked("settings_weekly_ai_toggle"):
        tap_safe("settings_weekly_ai_toggle")
        time.sleep(1.0)
    return checked("settings_weekly_toggle") and checked("settings_weekly_ai_toggle")


# ---------------- DB ----------------

def ai_rows():
    d = tempfile.mkdtemp(prefix="g3f-weekly-db-")
    for f in (DB, DB + "-wal", DB + "-shm"):
        p = subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f}",
            shell=True, capture_output=True,
        )
        if p.stdout:
            open(os.path.join(d, f), "wb").write(p.stdout)
    path = os.path.join(d, DB)
    if not os.path.exists(path):
        return []
    con = sqlite3.connect(path)
    try:
        return con.execute(
            "SELECT period_key,status,summary,error,model,categories,fact_ids_json,highlights_json "
            "FROM weekly_ai_reviews ORDER BY generated_at"
        ).fetchall()
    finally:
        con.close()


def clear_ai_rows():
    adb(f"shell am force-stop {PKG}")
    time.sleep(0.8)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_clear_weekly_ai true")
    time.sleep(2.2)
    return len(ai_rows()) == 0


def trigger_worker():
    adb(f"shell am force-stop {PKG}")
    time.sleep(0.8)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_run_weekly_review true")
    time.sleep(1.0)


def wait_ai_rows(timeout=45):
    end = time.time() + timeout
    rows = []
    while time.time() < end:
        rows = ai_rows()
        if rows and rows[-1][1] in ("READY", "FAILED"):
            return rows
        time.sleep(4)
    return rows


def tap_generate_ai():
    return tap_safe("weekly_generate_ai")


def wait_status(status, timeout=30):
    return wait_text("状态：" + status, timeout)


# ================= 0. 准备 =================
print("========== 0. 准备隔离数据 / QA 假引擎 / 授权 ==========")
adb("shell logcat -b crash -c")
clear_qa_data()
adb(f"shell pm grant {PKG} android.permission.POST_NOTIFICATIONS")
restart_app()
check("QA 包已启动", ensure_foreground())
check("建立目标", create_goal(GOAL))
check("写入进展数值", add_checkin(GOAL))
check("建立日记", create_diary(DIARY, "weekly%sfake%sbody"))
check("AI 配置（QA 假引擎）", configure_fake_ai("qa-fake"))
check("首次授权", grant_consent())
check("每周回顾 + AI 开关开启", enable_weekly_and_ai())
check("打开每周回顾页", open_weekly())
scroll_to_tag("weekly_review_generate", 6)
tap_tag("weekly_review_generate")
check("本地回顾已生成", wait_text("周期", 12) or has_text("本周变化"))

if should_run_scenario("READY"):
    # ================= 1. 成功 =================
    print("========== 1. AI 成功（READY） ==========")
    check("AI 入口可见", scroll_to_tag("weekly_generate_ai", 8))
    check("点击 AI 增强", tap_generate_ai())
    check("状态 READY", wait_status("READY", 30))
    check("显示 QA 假引擎摘要", has_text("QA 离线测试引擎"))
    rows = ai_rows()
    check("恰好 1 条 AI 结果", len(rows) == 1, str(rows))
    row = rows[0] if rows else ("", "", "", "", "", "", "[]", "[]")
    check("DB 状态 READY", row[1] == "READY", row[1])
    check("模型记录为 qa-fake", row[4] == "qa-fake", str(row[4]))
    check("授权类别非空", bool(row[5]) and "GOALS" in row[5], row[5])
    raw_fact_ids = (row[6] or "").strip()
    if raw_fact_ids.startswith("["):
        fact_ids = json.loads(raw_fact_ids)
    else:
        fact_ids = [x for x in raw_fact_ids.split(",") if x]
    check("factIds 非空", len(fact_ids) > 0, str(fact_ids))
    highlights = json.loads(row[7] or "[]")
    sources = [s for h in highlights for s in (h.get("sources") or [])]
    check("来源由客户端 factIds 构造", len(sources) > 0, str(highlights)[:200])
    check("来源类型合法", all(s.get("type") in ("GOAL", "CHECKIN", "MEMORY") for s in sources), str(sources)[:200])

    prefs = subprocess.run(
        f"adb -s {DEV} exec-out run-as {PKG} cat files/datastore/mengya_ai_qa.preferences_pb",
        shell=True, capture_output=True,
    ).stdout
    check("DataStore 不含明文 Key", KEY.encode() not in prefs)
    pid = adb(f"shell pidof {PKG}").strip()
    logs = adb(f"logcat -d --pid={pid}") if pid else ""
    check("进程日志不含 Key", KEY not in logs)

    if os.environ.get("MENGYA_WEEKLY_SMOKE") == "1" and not WEEKLY_CASE:
        print("\n[SMOKE] L1/L2 只跑 READY 成功路径；完整95条状态机在 Gate/Release 执行。")
        restore_chrome()
        sys.exit(0 if summary() else 1)

if should_run_scenario("FAILED_401"):
    # ================= 2. 失败 =================
    print("========== 2. AI 失败（401） ==========")
    check("重置 AI 结果", clear_ai_rows())
    check("切换失败模型", set_ai_model("qa-fake-401"))
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("AI 入口可见", scroll_to_tag("weekly_generate_ai", 8))
    check("点击 AI", tap_generate_ai())
    check("状态 FAILED", wait_status("FAILED", 30))
    check("显示授权失败文案", has_text("API Key 无效或已过期"))
    rows = ai_rows()
    check("DB 记录 FAILED", len(rows) == 1 and rows[0][1] == "FAILED", str(rows))
    check("失败时 summary 为空", bool(rows) and rows[0][2] == "", str(rows)[:160])
    check("失败保留错误信息", bool(rows) and bool(rows[0][3]), str(rows)[:160])
    check("本地回顾仍可见", has_text("本周变化") or has_text("周期"))

if should_run_scenario("TIMEOUT"):
    # ================= 3. 超时 =================
    print("========== 3. AI 超时 ==========")
    check("重置 AI 结果", clear_ai_rows())
    check("切换超时模型", set_ai_model("qa-fake-timeout"))
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("AI 入口可见", scroll_to_tag("weekly_generate_ai", 8))
    check("点击 AI", tap_generate_ai())
    check("状态 FAILED（超时）", wait_status("FAILED", 40))
    check("显示超时文案", has_text("请求超时"))
    rows = ai_rows()
    check("DB 记录超时错误", len(rows) == 1 and rows[0][1] == "FAILED" and "超时" in (rows[0][3] or ""), str(rows)[:160])

if should_run_scenario("INTERRUPT"):
    # ================= 4. 中断 / generating 恢复 =================
    print("========== 4. 请求中断后的 generating 恢复 ==========")
    check("重置 AI 结果", clear_ai_rows())
    check("切换慢速模型", set_ai_model("qa-fake-slow"))
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("AI 入口可见", scroll_to_tag("weekly_generate_ai", 8))
    check("点击慢速 AI", tap_generate_ai())
    time.sleep(3.5)
    adb(f"shell am force-stop {PKG}")
    time.sleep(1.0)
    restart_app()
    check("中断后可重新进入周回顾", open_weekly())
    check("没有残留 generating 文案", not has_text("本地整理中…"))
    check("未显示可重复请求的 AI 按钮", not has_tag("weekly_generate_ai"))
    check("显示额度已预留/处理中", has_text("AI 已预留本周额度") or has_text("处理中"))
    rows = ai_rows()
    check("中断只占 1 次额度", len(rows) == 1, str(rows))
    check("中断后状态为 SENT/FAILED（不伪造 READY）", bool(rows) and rows[0][1] in ("SENT", "FAILED"), str(rows[:1]))
    check("中断不删除本地回顾", has_text("周期") or has_text("本周变化"))

if should_run_scenario("STALE"):
    # ================= 5. READY -> STALE（数据变化） =================
    print("========== 5. READY -> STALE（数据变化） ==========")
    check("重置 AI 结果", clear_ai_rows())
    check("切回成功模型", set_ai_model("qa-fake"))
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("AI 入口可见", scroll_to_tag("weekly_generate_ai", 8))
    check("再次生成 READY", tap_generate_ai() and wait_status("READY", 30))
    check("数据变化（新增日记）", create_diary("WeeklyStaleDiary", "stale%strigger"))
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("状态变为 STALE", wait_status("STALE", 20))
    check("显示缓存失效原因", has_text("本周数据/授权/模型已变化"))
    rows = ai_rows()
    check("STALE 不删除原 READY 行", len(rows) == 1 and rows[0][1] == "READY", str(rows[:1]))

if should_run_scenario("CATEGORIES"):
    # ================= 6. 修改类别 =================
    print("========== 6. 修改类别后失效 ==========")
    check("重置 AI 结果", clear_ai_rows())
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("AI 入口可见", scroll_to_tag("weekly_generate_ai", 8))
    check("生成 READY", tap_generate_ai() and wait_status("READY", 30))
    check("进入设置", open_settings())
    scroll_top()
    for _ in range(24):
        if has_tag("ai_category_JOURNAL"):
            break
        adb("shell input swipe 600 1900 600 700 240")
        time.sleep(0.6)
    check("找到 JOURNAL 类别开关", has_tag("ai_category_JOURNAL"))
    check("关闭 JOURNAL 类别", tap_safe("ai_category_JOURNAL"))
    time.sleep(1.2)
    check("类别已关闭", not checked("ai_category_JOURNAL"))
    restart_app()
    check("重新打开周回顾", open_weekly())
    check("改类别后 STALE", wait_status("STALE", 20))

if should_run_scenario("WITHDRAW"):
    # ================= 7. 撤回授权 =================
    print("========== 7. 撤回授权后不再发送 ==========")
    check("重置 AI 结果", clear_ai_rows())
    check("进入设置", open_settings())
    scroll_top()
    for _ in range(24):
        if has_tag("ai_withdraw"):
            break
        adb("shell input swipe 600 1900 600 700 240")
        time.sleep(0.6)
    check("找到撤回入口", has_tag("ai_withdraw"))
    check("点击撤回", tap_safe("ai_withdraw"))
    check("撤回确认框", wait_tag("ai_withdraw_dialog", 8))
    check("确认撤回", tap_tag("ai_withdraw_confirm"))
    check("授权状态变为未授权", wait_text("未授权", 10) or has_tag("ai_consent_status_none"))
    restart_app()
    check("撤回后可打开周回顾", open_weekly())
    check("本地回顾仍可用", has_text("周期") or has_text("本周变化"))
    check("AI 入口存在但不会发送", has_tag("weekly_generate_ai"))
    check("点击 AI（应被拒绝）", tap_generate_ai())
    time.sleep(3.0)
    check("撤回后 DB 无 AI 行", len(ai_rows()) == 0, str(ai_rows()[:1]))

if should_run_scenario("AUTO_DISABLED"):
    # ================= 8. 自动开关 =================
    print("========== 8. 自动开关关闭后停止后台发送 ==========")
    check("重新授权", grant_consent())
    if not open_settings():
        check("进入设置", False)
    scroll_top()
    for _ in range(24):
        if has_tag("settings_weekly_ai_toggle"):
            break
        adb("shell input swipe 600 1900 600 700 240")
        time.sleep(0.6)
    if not checked("settings_weekly_ai_toggle"):
        tap_tag("settings_weekly_ai_toggle")
        time.sleep(1.0)
    check("AI 自动开关已开", checked("settings_weekly_ai_toggle"))
    check("重置 AI 结果", clear_ai_rows())
    trigger_worker()
    rows = wait_ai_rows(45)
    check("自动路径确实会发送（正对照）", len(rows) == 1, str(rows[:1]))
    check("自动结果 READY", bool(rows) and rows[0][1] == "READY", str(rows[:1]))

    check("重置 AI 结果", clear_ai_rows())
    if not open_settings():
        check("进入设置", False)
    scroll_top()
    for _ in range(24):
        if has_tag("settings_weekly_ai_toggle"):
            break
        adb("shell input swipe 600 1900 600 700 240")
        time.sleep(0.6)
    check("关闭 AI 自动开关", tap_safe("settings_weekly_ai_toggle"))
    time.sleep(1.0)
    check("AI 自动开关已关", not checked("settings_weekly_ai_toggle"))
    trigger_worker()
    time.sleep(20)
    rows = ai_rows()
    check("自动关闭后不再发送（负对照）", len(rows) == 0, str(rows[:1]))

crash = adb("shell logcat -b crash -d")
check("无应用崩溃记录", "com.mengyalife.app" not in crash)
if WEEKLY_CASE:
    print(f"WEEKLY_SCENARIO={WEEKLY_CASE}")

sys.exit(0 if summary() else 1)
