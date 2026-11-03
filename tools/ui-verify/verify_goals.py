import os, sys, time, re, sqlite3, os, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *


# ---------------------------------------------------------------------------
# ⚠️ 破坏性脚本：为了从干净状态验证，它会**删除设备上已有的全部目标**（含子目标与进展）。
# 只在测试机上运行，并且必须显式确认：
#     export MENGYA_TEST_DESTRUCTIVE=1
# ---------------------------------------------------------------------------
if os.environ.get("MENGYA_TEST_DESTRUCTIVE") != "1":
    raise SystemExit(
        "本脚本会清空设备上的目标数据。确认在测试机上运行后，请设置：\n"
        "    export MENGYA_TEST_DESTRUCTIVE=1"
    )

LONG, STAGE, ACTION = "LongHealth", "StageA", "ActionA"
J_TITLE, J_BODY = "G2Bnote", "g2b%sv1%stext"

def goal_ids():
    ids = []
    for n in nodes(dump()):
        m = re.match(r"goal_node_(\d+)", n["rid"])
        if m and int(m.group(1)) not in ids:
            ids.append(int(m.group(1)))
    return ids

_EXPANDED = set()

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
        # 点到输入框右端，让光标落在末尾，避免插入到开头
        for n in nodes(dump()):
            if n["rid"] == "goal_editor_title":
                adb(f"shell input tap {n['center'][0] + 300} {n['center'][1]}")
                break
        time.sleep(0.4)
    adb("shell input text '%s'" % title)
    close_keyboard()
    tap_tag("goal_editor_save")
    closed = wait_sheet_closed()
    if not closed:
        err = [t for t in texts(dump()) if "不能为空" in t or "过长" in t or "不合法" in t]
        print("    [保存未关闭] 错误提示:", err)
    return closed

def db_dump():
    d = "/tmp/g2bdb"; os.makedirs(d, exist_ok=True)
    for f in ["mengya-life.db", "mengya-life.db-wal", "mengya-life.db-shm"]:
        subprocess.run(f"adb -s {DEV} exec-out run-as com.mengyalife.app cat databases/{f} > {d}/{f}",
                       shell=True, capture_output=True)
    con = sqlite3.connect(f"{d}/mengya-life.db"); cur = con.cursor()
    cur.execute("PRAGMA user_version"); uv = cur.fetchone()[0]
    cur.execute("SELECT id,title,level,parent_id,status FROM goals ORDER BY id"); goals = cur.fetchall()
    cur.execute("SELECT id,goal_id,note,metric_value,metric_unit,source FROM goal_checkins ORDER BY id")
    checkins = cur.fetchall()
    cur.execute("SELECT id,title,body,created_at,updated_at FROM journal_entries ORDER BY id")
    journals = cur.fetchall()
    con.close()
    return uv, goals, checkins, journals

print("========== 0. 清理遗留目标 ==========")
restart_app(); tap_tag("tab_goals"); time.sleep(1.5)
for _ in range(5):
    ids = goal_ids()
    if not ids: break
    expand_all()
    targets = goal_ids()
    root = targets[0]
    if not open_menu(root): break
    tap_text("删除"); time.sleep(1.0); tap_text("删除"); time.sleep(2.0)
    restart_app(); tap_tag("tab_goals"); time.sleep(1.2)
check("目标库已清空", wait_text("还没有目标", 10))
check("空状态无预置示例目标", not has_text("把身体状态维持在可持续的基准线上"))
shot("01-goals-empty")

print("========== 1. 建立三层目标 ==========")
tap_tag("goal_new_long_term"); time.sleep(0.8)
fill_and_save("长期目标", LONG)
check("长期目标出现在列表", wait_text(LONG, 10))
long_id = goal_ids()[0]

check("长期菜单可打开", open_menu(long_id))
items = menu_items()
check("长期菜单含「新建阶段目标」", "新建阶段目标" in items, str(items))
check("长期菜单不含「新建短期行动」（禁止跳级）", "新建短期行动" not in items, str(items))
tap_text("新建阶段目标"); time.sleep(0.8)

fill_and_save("阶段目标", STAGE)
# 新建后重启一次再展开：避免列表尚未重排导致展开点击落空
restart_app(); tap_tag("tab_goals"); time.sleep(1.3)
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
tap_text("新建短期行动"); time.sleep(0.8)

fill_and_save("短期行动", ACTION)
restart_app(); tap_tag("tab_goals"); time.sleep(1.3)
_EXPANDED.clear()
ids = expand_all()
check("树上是三层", len(ids) >= 3, str(ids))
action_id = ids[-1]
check("三层同时可见", has_text(LONG) and has_text(STAGE) and has_text(ACTION))
shot("03-three-levels")

print("========== 2. 进展记录 + 历史 ==========")
check("打开阶段菜单", open_menu(stage_id))
tap_text("记录进展"); time.sleep(0.8)
check("进展面板出现", wait_tag("goal_checkin_note", 8))
tap_tag("goal_checkin_note"); adb("shell input text 'did%sthree%ssessions'")
tap_tag("goal_checkin_metric"); adb("shell input text '3'")
tap_tag("goal_checkin_unit"); adb("shell input text 'times'")
close_keyboard(); tap_tag("goal_checkin_save"); wait_sheet_closed()
check("进展计数出现", wait_text("1 次进展", 10))
shot("04-checkin")

check("打开阶段菜单", open_menu(stage_id))
tap_text("进展历史"); time.sleep(1.0)
check("历史含说明", wait_text("did three sessions", 8))
check("历史含数值", has_text("3 times"))
shot("05-history")
adb("shell input keyevent KEYCODE_BACK"); time.sleep(1.2)

print("========== 3. 状态 + 编辑 ==========")
check("打开行动菜单", open_menu(action_id))
tap_text("修改状态"); time.sleep(1.0)
check("状态对话框出现", wait_tag("status_option_DONE", 8))
check("点选「已完成」", tap_tag("status_option_DONE", wait=1.5))
check("对话框已关闭", wait_tag_gone if False else (not has_tag("status_option_DONE")))
time.sleep(1.0)
shot("06-status-done")

check("打开行动菜单", open_menu(action_id))
tap_text("编辑"); time.sleep(0.8)
fill_and_save("编辑行动", "B", append=True)
edited_title = None
for _ in range(10):
    for n in texts(dump()):
        if "B" in n and "ction" in n:
            edited_title = n
            break
    if edited_title: break
    time.sleep(0.8)
check("编辑生效（标题已变化）", edited_title is not None and edited_title != ACTION, str(edited_title))
shot("07-edited")

print("========== 4. 数据库校验 ==========")
uv, goals, checkins, journals = db_dump()
check("schema 版本为 2", uv == 2, f"user_version={uv}")
check("目标共 3 条", len(goals) == 3, str(goals))
by_id = {g[0]: g for g in goals}
check("阶段父级=长期", by_id[stage_id][3] == long_id, str(by_id.get(stage_id)))
check("行动父级=阶段", by_id[action_id][3] == stage_id, str(by_id.get(action_id)))
check("行动状态持久化为 DONE", by_id[action_id][4] == "DONE", by_id[action_id][4])
check("行动标题已编辑并持久化", by_id[action_id][1] != ACTION and "B" in by_id[action_id][1], by_id[action_id][1])
check("进展 1 条、来源 manual、绑定阶段", len(checkins) == 1 and checkins[0][5] == "manual" and checkins[0][1] == stage_id, str(checkins))
check("进展数值与单位正确", abs(checkins[0][3] - 3.0) < 1e-6 and checkins[0][4] == "times", str(checkins[0]))
check("v1 迁移来的日记 2 条且内容未变",
      len(journals) == 2 and journals[0][1] == "MigrationA" and journals[1][1] == "MigrationB", str(journals))

print("========== 5. 重启后保留 ==========")
restart_app(); tap_tag("tab_goals"); time.sleep(1.5)
check("长期目标仍在", wait_text(LONG, 8))
_EXPANDED.clear()
expand_all()
check("阶段仍在", has_text(STAGE))
check("行动标题仍是编辑后的", any("B" in t and "ction" in t for t in texts(dump())))
check("进展计数仍在", has_text("1 次进展"))
shot("08-after-restart")

print("========== 6. 总览联动 ==========")
tap_tag("tab_overview"); time.sleep(1.2)
check("总览显示真实长期目标", wait_text(LONG, 8))
check("总览显示真实计数", has_text("共 1 个长期目标"))
check("总览仍标注演示区块", has_text("演示"))
shot("09-overview")

print("========== 7. 删除影响范围 ==========")
tap_tag("tab_goals"); time.sleep(1.2)
check("打开长期菜单", open_menu(long_id))
tap_text("删除"); time.sleep(1.2)
check("删除确认框", has_text("删除这个目标？"))
impact = [t for t in texts(dump()) if "一并删除" in t or "没有子目标" in t]
check("明确列出影响范围", bool(impact), impact[0] if impact else "")
shot("10-delete-impact")
tap_text("取消"); time.sleep(1.2)
check("取消后目标仍在", has_text(LONG))
check("再次删除", open_menu(long_id) and (tap_text("删除") is not None))
time.sleep(1.0)
tap_text("删除"); time.sleep(2.0)
check("确认后已删除", wait_gone_text(LONG, 10))
check("回到空状态", wait_text("还没有目标", 10))
shot("11-after-delete")
restart_app(); tap_tag("tab_goals"); time.sleep(1.3)
check("重启后仍为空", wait_text("还没有目标", 10))

uv2, goals2, checkins2, journals2 = db_dump()
check("数据库里目标已清空", len(goals2) == 0, str(goals2))
check("数据库里无孤儿进展", len(checkins2) == 0, str(checkins2))
check("日记仍未受影响", len(journals2) == 2, str(len(journals2)))

print("========== 8. 总览空状态 ==========")
tap_tag("tab_overview"); time.sleep(1.2)
check("总览目标区显示空状态", has_text("还没有任何目标"))
check("总览提供真实创建入口", has_tag("overview_create_goal"))
shot("12-overview-empty")

print("========== 9. 日记回归 ==========")
tap_tag("tab_diary"); time.sleep(1.2)
tap_tag("diary_new"); wait_tag("journal_body_input", 8)
tap_tag("journal_title_input"); adb("shell input text '%s'" % J_TITLE)
tap_tag("journal_body_input"); adb("shell input text '%s'" % J_BODY)
close_keyboard(); tap_tag("journal_save"); time.sleep(1.5)
check("日记新建成功", wait_text(J_TITLE, 10))
restart_app(); tap_tag("tab_diary"); time.sleep(1.3)
check("日记重启后仍在", wait_text(J_TITLE, 10))
tap_text(J_TITLE); time.sleep(1.0)
tap_tag("journal_delete"); check("日记删除确认", wait_text("删除这条日记？", 6))
tap_text("删除"); time.sleep(1.5)
check("日记删除成功", wait_gone_text(J_TITLE, 10))
shot("13-diary-regression")

print("========== 10. 主题回归 ==========")
if not has_tag("btn_settings"): tap_tag("tab_overview")
tap_tag("btn_settings"); time.sleep(1.0)
scroll_to_tag("theme_dark", 8)
check("设为深色", tap_tag("theme_dark", wait=1.5))
time.sleep(1.0)
L_dark = png_luminance(shot("14-theme-dark"))
restart_app()
tap_tag("btn_settings"); time.sleep(1.0)
scroll_to_tag("theme_dark", 8)
check("重启后仍是深色", any(n["rid"]=="theme_dark" and n["checked"]=="true" for n in nodes(dump())))
L_dark2 = png_luminance(shot("15-theme-dark-restart"))
check("深色外观仍在", L_dark2 is not None and L_dark2 < 120, f"L={L_dark2}")
tap_tag("theme_system", wait=1.2); time.sleep(0.8)
adb("shell input keyevent KEYCODE_BACK"); time.sleep(1.2)

print("========== 11. 导航回归 ==========")
EXPECT = [("tab_overview","你正在走的方向"),("tab_goals","把方向拆成能推进的层级"),
          ("tab_trends","看见变化，而不是凭感觉"),("tab_diary","把今天留下来")]
for tag, head in EXPECT:
    check(f"切换到 {tag}", tap_tag(tag))
    check(f"{tag} 内容正常", any(head in t for t in texts(dump())), head)
check("顾问页可进入", tap_tag("tab_advisor") and has_tag("advisor_input"))
close_keyboard()

close_keyboard()
tap_tag("tab_overview")
check("可进入设置", tap_tag("btn_settings"))
check("设置页已就位", wait_tag("settings_back", 8))
check("设置页隐藏底栏", not any(n["rid"].startswith("tab_") for n in nodes(dump())))
adb("shell input keyevent KEYCODE_BACK"); time.sleep(1.3)
check("返回后底栏恢复", any(n["rid"].startswith("tab_") for n in nodes(dump())))

print("========== 12. 稳定性 ==========")
crash = adb("shell logcat -b crash -d")
check("本应用无崩溃记录", "mengyalife" not in crash.lower(), crash[:120])
check("应用进程存活", "com.mengyalife.app" in adb("shell ps -A"))

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
