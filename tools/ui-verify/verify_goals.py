"""三层目标回归（Gate 2C，只跑 QA 包）。

覆盖：空状态 → 长期/阶段/行动三层 → 禁止跳级 → 进展记录 → 历史 → 状态 → 编辑
→ 数据库父子关系 → 重启保持 → 总览联动 → 删除影响范围 → 删除后空状态。

⚠️ 脚本开头会 `pm clear` QA 包（com.mengyalife.app.qa，独立数据库）。
正式包 com.mengyalife.app 不会被清空；require_qa 会在包名不对时直接退出。
"""
import os, sys, time, re, sqlite3, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_goals.py")

LONG, STAGE, ACTION = "LongHealth", "StageA", "ActionA"


def goal_ids():
    ids = []
    for n in nodes(dump()):
        m = re.match(r"goal_node_(\d+)", n["rid"])
        if m and int(m.group(1)) not in ids:
            ids.append(int(m.group(1)))
    return ids


_EXPANDED = set()


def wait_goals_ready(timeout=15):
    """冷启动后等目标页真正就绪，避免在启动动画/空白页上 expand。"""
    end = time.time() + timeout
    while time.time() < end:
        if has_tag("goal_summary") or has_tag("goal_new_long_term"):
            return True
        time.sleep(0.7)
    return False


def expand_all():
    """幂等展开：同一次运行里每个节点只点一次（重复点会收起）。"""
    for _ in range(6):
        ids = goal_ids()
        fresh = [g for g in ids if g not in _EXPANDED]
        if not fresh:
            break
        for gid in fresh:
            tap_tag("goal_expand_%d" % gid, wait=0.6)
            _EXPANDED.add(gid)
    return goal_ids()


def wait_sheet_closed(t=10):
    end = time.time() + t
    while time.time() < end:
        if not has_tag("goal_editor_title") and not has_tag("goal_checkin_note"):
            return True
        time.sleep(0.5)
    return False


def open_menu(gid, tries=4):
    for _ in range(tries):
        if tap_tag("goal_menu_%d" % gid):
            time.sleep(0.7)
            if has_text("记录进展") or has_text("删除"):
                return True
        time.sleep(0.8)
    return False


def menu_items():
    keys = ("记录进展", "进展历史", "修改状态", "编辑", "新建阶段目标", "新建短期行动", "删除")
    return [t for t in texts(dump()) if t in keys]


def fill_and_save(label, title, append=False):
    check(f"{label}：面板出现", wait_tag("goal_editor_title", 8))
    tap_tag("goal_editor_title")
    if append:
        adb("shell input keyevent KEYCODE_MOVE_END")
        for _ in range(40):
            adb("shell input keyevent KEYCODE_DEL")
    adb("shell input text '%s'" % title)
    close_keyboard()
    tap_tag("goal_editor_save")
    closed = wait_sheet_closed()
    if not closed:
        err = [t for t in texts(dump()) if "不能为空" in t or "过长" in t or "不合法" in t]
        print("    [保存未关闭] 错误提示:", err)
    return closed


def db_dump():
    d = "/tmp/g2c-goals-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    con = sqlite3.connect(f"{d}/mengya-life-qa.db")
    cur = con.cursor()
    cur.execute("PRAGMA user_version")
    uv = cur.fetchone()[0]
    cur.execute("SELECT id,title,level,parent_id,status FROM goals ORDER BY id")
    goals = cur.fetchall()
    cur.execute("SELECT id,goal_id,note,metric_value,metric_unit,source FROM goal_checkins ORDER BY id")
    checkins = cur.fetchall()
    cur.execute("SELECT id,title,body,created_at,updated_at FROM journal_entries ORDER BY id")
    journals = cur.fetchall()
    con.close()
    return uv, goals, checkins, journals


print("========== 0. 清空 QA 数据 ==========")
adb("shell logcat -b crash -c")
clear_qa_data()
restart_app()
tap_tag("tab_goals")
time.sleep(1.5)
check("目标库已清空", wait_text("还没有目标", 10))
check("空状态无预置示例目标", not has_text("把身体状态维持在可持续的基准线上"))
shot("01-goals-empty")

print("========== 1. 建立三层目标 ==========")
tap_tag("goal_new_long_term")
time.sleep(0.8)
fill_and_save("长期目标", LONG)
check("长期目标出现在列表", wait_text(LONG, 10))
long_id = goal_ids()[0]

check("长期菜单可打开", open_menu(long_id))
items = menu_items()
check("长期菜单含「新建阶段目标」", "新建阶段目标" in items, str(items))
check("长期菜单不含「新建短期行动」（禁止跳级）", "新建短期行动" not in items, str(items))
tap_text("新建阶段目标")
time.sleep(0.8)

fill_and_save("阶段目标", STAGE)
restart_app()
tap_tag("tab_goals")
time.sleep(1.3)
_EXPANDED.clear()
ids = expand_all()
check("树上是两层", len(ids) >= 2, str(ids))
stage_id = ids[1]
check("阶段标题正确", has_text(STAGE))
shot("02-two-levels")

check("阶段菜单可打开", open_menu(stage_id))
items = menu_items()
check("阶段菜单含「新建短期行动」", "新建短期行动" in items, str(items))
check("阶段菜单不含「新建阶段目标」（禁止跳级）", "新建阶段目标" not in items, str(items))
tap_text("新建短期行动")
time.sleep(0.8)

fill_and_save("短期行动", ACTION)
restart_app()
tap_tag("tab_goals")
check("目标页已就绪（冷启动后）", wait_goals_ready(15))
time.sleep(0.8)
_EXPANDED.clear()
ids = expand_all()
check("树上是三层", len(ids) >= 3, str(ids))
if len(ids) < 3:
    # 一次温和重试：只重开目标页并重新展开，不重建数据
    restart_app()
    tap_tag("tab_goals")
    wait_goals_ready(15)
    _EXPANDED.clear()
    ids = expand_all()
    check("重试后树上是三层", len(ids) >= 3, str(ids))
action_id = ids[-1]
check("三层同时可见", has_text(LONG) and has_text(STAGE) and has_text(ACTION))
shot("03-three-levels")

print("========== 2. 进展记录 + 历史（真实数值趋势） ==========")
check("打开阶段菜单", open_menu(stage_id))
tap_text("记录进展")
time.sleep(0.8)
check("进展面板出现", wait_tag("goal_checkin_note", 8))
tap_tag("goal_checkin_note")
adb("shell input text 'did%sthree%ssessions'")
tap_tag("goal_checkin_metric")
adb("shell input text '3'")
tap_tag("goal_checkin_unit")
adb("shell input text 'times'")
close_keyboard()
tap_tag("goal_checkin_save")
wait_sheet_closed()
check("进展计数出现", wait_text("1 次进展", 10))
shot("04-checkin")

check("打开阶段菜单", open_menu(stage_id))
tap_text("进展历史")
time.sleep(1.5)
check("历史含说明", wait_text("did three sessions", 8))
check("历史含数值", has_text("3 times"))
check("历史出现真实趋势图", has_tag("goal_trend_chart_0"))
check("单点有明确说明", has_tag("goal_trend_single_note"))
check("标注数据来源", has_tag("goal_trend_source"))
shot("05-history-single-point")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.2)

print("========== 3. 状态 + 编辑 ==========")
check("打开行动菜单", open_menu(action_id))
tap_text("修改状态")
time.sleep(1.0)
check("状态对话框出现", wait_tag("status_option_DONE", 8))
check("点选「已完成」", tap_tag("status_option_DONE", wait=1.5))
check("对话框已关闭", not has_tag("status_option_DONE"))
time.sleep(1.0)
shot("06-status-done")

check("打开行动菜单", open_menu(action_id))
tap_text("编辑")
time.sleep(0.8)
fill_and_save("编辑行动", "ActionEdited", append=True)
edited_title = None
for _ in range(10):
    for n in texts(dump()):
        if "ActionEdited" in n:
            edited_title = n
            break
    if edited_title:
        break
    time.sleep(0.8)
check("编辑生效（标题已变化）", edited_title is not None and edited_title != ACTION, str(edited_title))
shot("07-edited")

print("========== 4. 数据库校验 ==========")
uv, goals, checkins, journals = db_dump()
check("schema 版本为 7", uv == 7, f"user_version={uv}")
check("目标共 3 条", len(goals) == 3, str(goals))
by_id = {g[0]: g for g in goals}
check("阶段父级=长期", by_id[stage_id][3] == long_id, str(by_id.get(stage_id)))
check("行动父级=阶段", by_id[action_id][3] == stage_id, str(by_id.get(action_id)))
check("行动状态持久化为 DONE", by_id[action_id][4] == "DONE", by_id[action_id][4])
check("行动标题已编辑并持久化", by_id[action_id][1] == "ActionEdited", by_id[action_id][1])
check("进展 1 条、来源 manual、绑定阶段",
      len(checkins) == 1 and checkins[0][5] == "manual" and checkins[0][1] == stage_id, str(checkins))
check("进展数值与单位正确", abs(checkins[0][3] - 3.0) < 1e-6 and checkins[0][4] == "times", str(checkins[0]))
check("QA 库的日记表为空（测试隔离）", len(journals) == 0, str(journals))

print("========== 5. 重启后保留 ==========")
restart_app()
tap_tag("tab_goals")
time.sleep(1.5)
check("长期目标仍在", wait_text(LONG, 8))
_EXPANDED.clear()
expand_all()
check("阶段仍在", has_text(STAGE))
check("行动标题仍是编辑后的", has_text("ActionEdited"))
check("进展计数仍在", has_text("1 次进展"))
shot("08-after-restart")

print("========== 6. 总览联动 + 统计口径 ==========")
tap_tag("tab_overview")
time.sleep(1.2)
check("总览显示真实长期目标", wait_text(LONG, 8))
check("总览显示长期目标数量", has_text("长期目标共 1 个"))
check("总览明确「长期目标进行中」（长期仍为未开始）", has_text("长期目标进行中 0"))
check("总览写明全部层级统计口径", has_text("全部层级共 3 个"))
check("总览写明进行中口径", has_text("「进行中」= 状态为「进行中」的目标"))
check("总览仍标注演示区块", has_text("演示"))
shot("09-overview")

print("========== 7. 删除影响范围 ==========")
tap_tag("tab_goals")
time.sleep(1.2)
check("打开长期菜单", open_menu(long_id))
tap_text("删除")
time.sleep(1.2)
check("删除确认框", has_text("删除这个目标？"))
impact = [t for t in texts(dump()) if "一并删除" in t or "没有子目标" in t]
check("明确列出影响范围", bool(impact), impact[0] if impact else "")
shot("10-delete-impact")
tap_text("取消")
time.sleep(1.2)
check("取消后目标仍在", has_text(LONG))
check("再次删除", open_menu(long_id) and (tap_text("删除") is not None))
time.sleep(1.0)
tap_text("删除")
time.sleep(2.0)
check("确认后已删除", wait_gone_text(LONG, 10))
check("回到空状态", wait_text("还没有目标", 10))
shot("11-after-delete")
restart_app()
tap_tag("tab_goals")
time.sleep(1.3)
check("重启后仍为空", wait_text("还没有目标", 10))

uv2, goals2, checkins2, journals2 = db_dump()
check("数据库里目标已清空", len(goals2) == 0, str(goals2))
check("数据库里无孤儿进展", len(checkins2) == 0, str(checkins2))

print("========== 8. 稳定性 ==========")
crash = adb("shell logcat -b crash -d")
check("本应用无崩溃记录", "mengyalife" not in crash.lower(), crash[:120])
check("应用进程存活", PKG in adb("shell ps -A"))

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
