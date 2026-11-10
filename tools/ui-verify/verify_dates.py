"""日期选择器真机用例（Gate 2C，只跑 QA 包）。

覆盖：目标编辑 → DatePicker 选日期 → 保存 → 列表展示 → 强制停止并冷启动后仍展示
→ 数据库里的 due_date 是「所选日期 UTC 零点」（不会因时区偏移一天）。
"""
import os, sys, time, re, sqlite3, subprocess, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_dates.py")

GOAL = "DateGoal"


def node_ids():
    return [int(m.group(1)) for n in nodes(dump())
            for m in [re.match(r"goal_node_(\d+)", n["rid"])] if m]


def db_due():
    d = "/tmp/g2c-date-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    con = sqlite3.connect(f"{d}/mengya-life-qa.db")
    rows = con.execute("SELECT id,due_date FROM goals ORDER BY id").fetchall()
    con.close()
    return rows


print("========== 0. 清空 QA 数据并建目标 ==========")
clear_qa_data()
restart_app()
open_goals()
time.sleep(1.2)
tap_tag("goal_new_long_term")
check("编辑面板", wait_tag("goal_editor_title", 8))
tap_tag("goal_editor_title")
adb("shell input text '%s'" % GOAL)
close_keyboard()
tap_tag("goal_editor_save")
check("目标已创建", wait_text(GOAL, 8))
gid = node_ids()[0]

print("========== 1. 打开日期选择器 ==========")
check("打开菜单", tap_tag("goal_menu_%d" % gid))
tap_text("编辑")
check("编辑面板出现", wait_tag("goal_editor_due_date", 8))
check("默认未设置日期", has_text("未设置"))
tap_tag("goal_editor_due_date")
time.sleep(1.8)
check("日期选择器出现", has_text("选择日期"))
shot("01-date-picker")

# 用设备当前年月，选 28 日（每月都存在，避免月末边界）
device_date = adb("shell date '+%Y-%m-%d'").strip()
year, month, _ = [int(x) for x in device_date.split("-")]
day = 28
expected_label = f"{year}年{month}月{day}日"
prefix = f"{year}年{month}月{day}日"

picked = False
for n in nodes(dump()):
    if n["text"].startswith(prefix):
        tap_xy(*n["center"], wait=1.0)
        picked = True
        break
check("点选 28 日", picked, expected_label)
check("确认日期", tap_text("确定", wait=1.5))
time.sleep(0.8)
check("编辑面板显示所选日期", has_text(expected_label))
shot("02-date-selected")

print("========== 2. 保存并展示 ==========")
tap_tag("goal_editor_save")
time.sleep(1.5)
check("面板关闭", wait_tag_gone("goal_editor_title", 8))
check("目标卡片显示截止日期", wait_text("截止 " + expected_label, 8))
shot("03-date-saved")

print("========== 3. 冷启动后仍展示 ==========")
adb(f"shell am force-stop {PKG}")
time.sleep(1.0)
adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity")
time.sleep(2.5)
open_goals()
time.sleep(1.2)
check("重启后目标仍在", wait_text(GOAL, 8))
check("重启后截止日期仍在", has_text("截止 " + expected_label))
shot("04-date-after-restart")

print("========== 4. 数据库时间戳语义 ==========")
rows = db_due()
check("库里有 1 个目标", len(rows) == 1, str(rows))
due = rows[0][1]
check("due_date 不为空", due is not None, str(rows))
expected_epoch = int(datetime.datetime(
    year, month, day, tzinfo=datetime.timezone.utc
).timestamp() * 1000)
check("due_date = 所选日期 UTC 零点", due == expected_epoch, f"db={due} expected={expected_epoch}")
if due is not None:
    rendered = datetime.datetime.fromtimestamp(due / 1000, tz=datetime.timezone.utc).strftime("%Y-%m-%d")
    check("按 UTC 渲染仍是所选日期", rendered == f"{year:04d}-{month:02d}-{day:02d}", rendered)

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
