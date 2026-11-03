"""真实数值趋势验收（Gate 2C，只跑 QA 包）。

覆盖：
- 没有数值记录时的空状态；
- 同一目标、同一单位的 2 个点 → 折线 + 点 + 时间；
- 只有 1 个点 → 单点 + 「无法判断趋势」说明；
- 同一目标的不同单位 → 分开绘制，不混画、不换算；
- 趋势页真实区 + 明确标注的演示区；
- NaN / Infinity / 1e999 在真机界面被拒绝且不写库；
- 图表数据与 goal_checkins 逐项对应。
"""
import os, sys, time, re, sqlite3, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_trends.py")

GOAL_A = "TrendA"
GOAL_B = "TrendB"
GOAL_C = "TrendC"


def node_ids():
    ids = []
    for n in nodes(dump()):
        m = re.match(r"goal_node_(\d+)", n["rid"])
        if m and int(m.group(1)) not in ids:
            ids.append(int(m.group(1)))
    return ids


def wait_editor_closed(t=10):
    end = time.time() + t
    while time.time() < end:
        if not has_tag("goal_editor_title") and not has_tag("goal_checkin_note"):
            return True
        time.sleep(0.5)
    return False


def new_long(title):
    tap_tag("goal_new_long_term")
    time.sleep(0.8)
    check(f"{title} 编辑面板", wait_tag("goal_editor_title", 8))
    tap_tag("goal_editor_title")
    adb("shell input text '%s'" % title)
    close_keyboard()
    tap_tag("goal_editor_save")
    check(f"{title} 保存", wait_editor_closed())
    check(f"{title} 出现", wait_text(title, 8))
    time.sleep(0.8)


def add_checkin(gid, note, metric=None, unit=None):
    check("打开菜单", tap_tag("goal_menu_%d" % gid))
    tap_text("记录进展")
    time.sleep(0.8)
    check("进展面板", wait_tag("goal_checkin_note", 8))
    tap_tag("goal_checkin_note")
    adb("shell input text '%s'" % note)
    if metric is not None:
        tap_tag("goal_checkin_metric")
        adb("shell input text '%s'" % metric)
    if unit is not None:
        tap_tag("goal_checkin_unit")
        adb("shell input text '%s'" % unit)
    close_keyboard()
    tap_tag("goal_checkin_save")
    return wait_editor_closed()


def open_history(gid):
    check("打开菜单", tap_tag("goal_menu_%d" % gid))
    tap_text("进展历史")
    time.sleep(1.5)


def scroll_sheet(times=4):
    for _ in range(times):
        adb("shell input swipe 600 2250 600 1000 300")
        time.sleep(0.8)


def scroll_top(times=10):
    """把当前页面滚回顶部（LazyColumn 状态会在 tab 之间保留）。"""
    for _ in range(times):
        adb("shell input swipe 600 900 600 2200 250")
        time.sleep(0.5)


def db_trend_rows():
    d = "/tmp/g2c-trends-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    con = sqlite3.connect(f"{d}/mengya-life-qa.db")
    rows = con.execute(
        "SELECT id,goal_id,recorded_at,note,metric_value,metric_unit,source "
        "FROM goal_checkins ORDER BY id"
    ).fetchall()
    con.close()
    return rows


print("========== 0. 清空 QA 数据并检查空状态 ==========")
clear_qa_data()
restart_app()
tap_tag("tab_trends")
time.sleep(1.5)
check("趋势页空状态", wait_tag("trend_real_empty", 8))
check("空状态说明不推断趋势", has_text("不会给出「人生整体评分」"))
check("演示区仍保留（向下滚动）", scroll_to_tag("trend_demo_section", 10))
check("演示区有明确标注", has_text("演示区（非真实数据）"))
shot("01-trends-empty-and-demo")

print("========== 1. 建目标 + 真实数值记录 ==========")
tap_tag("tab_goals")
time.sleep(1.0)
new_long(GOAL_A)
a_id = node_ids()[0]
new_long(GOAL_B)
b_id = [i for i in node_ids() if i != a_id][0]
new_long(GOAL_C)
c_id = [i for i in node_ids() if i not in (a_id, b_id)][0]
print("ids", a_id, b_id, c_id)

check("A 第 1 条 (70 kg)", add_checkin(a_id, "week1", "70", "kg"))
check("A 第 2 条 (71.5 kg)", add_checkin(a_id, "week2", "71.5", "kg"))
check("A 第 3 条 (30 min，另一个单位)", add_checkin(a_id, "run30", "30", "min"))
check("B 第 1 条 (8 score，单点)", add_checkin(b_id, "onlyone", "8", "score"))
check("C 只写文字、无数值", add_checkin(c_id, "noteonly", None, None))
shot("02-goals-with-metrics")

print("========== 2. 历史里的真实趋势图 ==========")
open_history(a_id)
check("数据来源说明", wait_tag("goal_trend_source", 8))
check("同单位多点图", has_tag("goal_trend_chart_0"))
check("多点说明", has_tag("goal_trend_multi_note"))
check("图上有真实数值 70", has_text("70 kg"))
check("图上有真实数值 71.5", has_text("71.5 kg"))
check("同目标第二个单位单独成图", scroll_to_tag("goal_trend_chart_1", 6) or has_tag("goal_trend_chart_1"))
check("单点说明", has_tag("goal_trend_single_note"))
check("单位不混画说明", has_tag("goal_trend_unit_split"))
shot("03-history-multi-unit")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.2)

open_history(b_id)
check("B 单点图存在", wait_tag("goal_trend_chart_0", 8))
check("B 单点不伪造曲线", has_tag("goal_trend_single_note"))
shot("04-history-single")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.2)

# C 没有数值：历史里应显示空状态
c_id = [i for i in node_ids() if i not in (a_id, b_id)][0]
open_history(c_id)
check("C 无数值空状态", wait_tag("goal_history_trend_empty", 8))
check("C 空状态文案说明数值是选填", has_text("数值」是选填项"))
check("C 仍展示原始记录", has_text("noteonly"))
shot("05-history-empty-numeric")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.2)

print("========== 3. 趋势页真实区 ==========")
tap_tag("tab_trends")
time.sleep(1.5)
scroll_top()
time.sleep(0.8)
check("真实趋势区存在", wait_tag("trend_real_section", 8))
check("第一组真实趋势", has_tag("trend_block_0"))
check("真实来源标注", has_text("数据来源：你手动记录的进展"))

# 趋势页用了 LazyColumn，滚动位置会在 tab 之间保留。
# 从顶部开始逐屏收集文字，确认两个单位都确实出现在真实区（而不是漏看）。
seen = set()
for _ in range(10):
    seen |= {t for t in texts(dump()) if t.strip()}
    joined = " ".join(seen)
    if "kg" in joined and "min" in joined:
        break
    adb("shell input swipe 600 1900 600 800 260")
    time.sleep(0.8)
joined = " ".join(seen)
check("按单位展示 kg", "kg" in joined, joined[:200])
check("按单位展示 min", "min" in joined, joined[:200])
check("不合成整体评分", not has_text("人生评分") and not has_text("综合评分"))
shot("06-trends-real")

print("========== 4. 非法数值在真机被拒绝 ==========")
tap_tag("tab_goals")
time.sleep(1.2)
before = len(db_trend_rows())
for bad in ["NaN", "Infinity", "1e999"]:
    check("打开菜单（%s）" % bad, tap_tag("goal_menu_%d" % a_id))
    tap_text("记录进展")
    time.sleep(0.8)
    check("面板出现（%s）" % bad, wait_tag("goal_checkin_note", 8))
    tap_tag("goal_checkin_note")
    adb("shell input text 'invalid'")
    tap_tag("goal_checkin_metric")
    adb("shell input text '%s'" % bad)
    close_keyboard()
    tap_tag("goal_checkin_save")
    time.sleep(1.2)
    check("拒绝 %s" % bad, has_tag("goal_checkin_error") and has_text("指标数值不合法"))
    check("面板未关闭 %s" % bad, has_tag("goal_checkin_note"))
    if bad == "NaN":
        shot("07-invalid-metric")
    adb("shell input keyevent KEYCODE_BACK")
    time.sleep(1.0)
after = len(db_trend_rows())
check("非法数值没有写库", before == after, f"{before} -> {after}")

print("========== 5. 数据库逐项对应 ==========")
rows = db_trend_rows()
check("进展共 5 条", len(rows) == 5, str(rows))
a_rows = [r for r in rows if r[1] == a_id and r[4] is not None]
check("A 有 3 条数值记录", len(a_rows) == 3, str(a_rows))
check("A 的 kg 值 = 70 / 71.5", sorted(round(r[4], 1) for r in a_rows if r[5] == "kg") == [70.0, 71.5],
      str(a_rows))
check("A 的 min 值 = 30", [r[4] for r in a_rows if r[5] == "min"] == [30.0], str(a_rows))
check("B 单值 = 8 score", [(r[4], r[5]) for r in rows if r[1] == b_id] == [(8.0, "score")], str(rows))
check("C 没有数值", all(r[4] is None for r in rows if r[1] == c_id), str(rows))
check("全部来源是 manual", all(r[6] == "manual" for r in rows), str(rows))

print("========== 6. 演示区仍明确标注 ==========")
tap_tag("tab_trends")
time.sleep(1.2)
# 先回到顶部再向下找，避免上一轮已经滚过演示区导致 scroll_to_tag 失效
if not has_tag("trend_demo_section"):
    scroll_top()
    time.sleep(0.8)
check("滚动到演示区", scroll_to_tag("trend_demo_section", 14))
check("演示区标题", has_text("演示区（非真实数据）"))
check("演示区旁有「演示」标签", has_text("演示"))
shot("08-trends-demo-separated")

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
