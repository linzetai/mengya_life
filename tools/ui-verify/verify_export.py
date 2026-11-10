"""本地数据导出验收（Gate 2C，只跑 QA 包）。

覆盖：
- 设置页「导出我的数据」入口；
- 导出前敏感信息提醒（日记正文等）；
- 系统文件选择器（SAF，不申请存储权限）；
- 导出文件包含日记、三层目标、进展记录、关联 ID、时间戳、schemaVersion；
- 文件可解析，且与数据库逐项一致；
- 导出后数据库内容不变（只读快照）；
- 文件不包含 API Key 等字段。

QA 包自身数据在脚本开头清空；正式包不受影响。
"""
import os, sys, time, json, re, sqlite3, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_export.py")

EXPORT_DIR = os.environ.get("EXPORT_OUT", os.path.join(OUT, "export"))
os.makedirs(EXPORT_DIR, exist_ok=True)

DIARY_TITLE = "ExportDiary"
DIARY_BODY = "g2c%sexport%sv1"
GOAL = "ExportGoal"


def db_paths():
    d = "/tmp/g2c-export-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    return f"{d}/mengya-life-qa.db"


def db_snapshot(path):
    con = sqlite3.connect(path)
    journals = con.execute(
        "SELECT id,title,body,created_at,updated_at FROM journal_entries ORDER BY id"
    ).fetchall()
    goals = con.execute(
        "SELECT id,title,description,level,parent_id,domain,status,start_date,due_date,created_at,updated_at "
        "FROM goals ORDER BY id"
    ).fetchall()
    checkins = con.execute(
        "SELECT id,goal_id,recorded_at,note,metric_value,metric_unit,source "
        "FROM goal_checkins ORDER BY id"
    ).fetchall()
    con.close()
    return journals, goals, checkins


def wait_editor_closed(t=10):
    end = time.time() + t
    while time.time() < end:
        if not has_tag("goal_editor_title") and not has_tag("goal_checkin_note") \
                and not has_tag("journal_body_input"):
            return True
        time.sleep(0.5)
    return False


print("========== 0. 准备 QA 数据（日记 + 目标 + 进展） ==========")
clear_qa_data()
restart_app()

# 日记
tap_tag("tab_diary")
time.sleep(1.2)
tap_tag("diary_new")
check("日记面板", wait_tag("journal_body_input", 8))
tap_tag("journal_title_input")
adb("shell input text '%s'" % DIARY_TITLE)
tap_tag("journal_body_input")
adb("shell input text '%s'" % DIARY_BODY)
close_keyboard()
tap_tag("journal_save")
check("日记面板关闭", wait_tag_gone("journal_body_input", 8))
time.sleep(1.0)
check("日记已保存", wait_text(DIARY_TITLE, 12))

# 目标 + 两条进展
open_goals()
time.sleep(1.2)
tap_tag("goal_new_long_term")
check("目标面板", wait_tag("goal_editor_title", 8))
tap_tag("goal_editor_title")
adb("shell input text '%s'" % GOAL)
close_keyboard()
tap_tag("goal_editor_save")
check("目标已保存", wait_editor_closed())
check("目标出现", wait_text(GOAL, 8))
gid = None
for n in nodes(dump()):
    m = re.match(r"goal_node_(\d+)", n["rid"])
    if m:
        gid = int(m.group(1))
        break
check("拿到目标 id", gid is not None, str(gid))

for note, value, unit in [("week1", "3.5", "kg"), ("week2", "4", "kg")]:
    tap_tag("goal_menu_%d" % gid)
    tap_text("记录进展")
    check("进展面板", wait_tag("goal_checkin_note", 8))
    tap_tag("goal_checkin_note")
    adb("shell input text '%s'" % note)
    tap_tag("goal_checkin_metric")
    adb("shell input text '%s'" % value)
    tap_tag("goal_checkin_unit")
    adb("shell input text '%s'" % unit)
    close_keyboard()
    tap_tag("goal_checkin_save")
    check("进展已保存", wait_editor_closed())
check("进展计数", wait_text("2 次进展", 8))

db_file = db_paths()
journals_before, goals_before, checkins_before = db_snapshot(db_file)
check("准备数据：1 条日记", len(journals_before) == 1, str(journals_before))
check("准备数据：1 个目标", len(goals_before) == 1, str(goals_before))
check("准备数据：2 条进展", len(checkins_before) == 2, str(checkins_before))
shot("01-export-source-data")

print("========== 1. 打开导出入口 + 敏感信息提醒 ==========")
existing_exports = set(
    adb("shell 'ls /sdcard/Download/mengya-life-export-*.json 2>/dev/null'").split()
)
tap_tag("btn_settings")
time.sleep(1.2)
check("设置页已打开", wait_tag("settings_back", 8))
check("设置页导出入口", scroll_to_tag("settings_export_data", 12))
tap_tag("settings_export_data")
check("导出确认框", wait_tag("export_warning", 8))
check("敏感信息提醒", has_tag("export_sensitive_warning"))
check("提醒包含「敏感个人信息」", has_text("敏感个人信息"))
check("提醒包含「不会上传或自动分享」", has_text("不会上传或自动分享"))
check("明确导出范围", has_text("日记 1 条") and has_text("目标 1 个") and has_text("进展记录 2 条"))
check("明确导入尚未实现", has_text("导入恢复尚未实现"))
shot("02-export-warning")

print("========== 2. SAF 保存 ==========")
tap_tag("export_confirm")
time.sleep(2.5)
check("系统文件选择器出现「保存」", has_text("保存"))
shot("03-export-saf-picker")
tap_text("保存")

# 文件写入 + 回读校验需要一点时间；Snackbar 是瞬时的，尽快轮询
success = False
for _ in range(30):
    if has_text("导出完成并已校验"):
        success = True
        break
    time.sleep(0.5)
check("导出成功提示", success)
shot("04-export-success")

print("========== 3. 拉取并解析导出文件 ==========")
all_exports = adb("shell 'ls -t /sdcard/Download/mengya-life-export-*.json 2>/dev/null'").split()
# 只认本次新生成的文件，避免误读上一轮遗留的导出
new_exports = [p for p in all_exports if p not in existing_exports]
remote = new_exports[0].strip() if new_exports else ""
check("设备上出现本次新导出文件（不读取旧文件）", bool(remote), str(sorted(existing_exports)))
if not remote:
    summary()
    sys.exit(1)
local = os.path.join(EXPORT_DIR, os.path.basename(remote))
adb(f"pull {remote} {local}")
check("文件已拉取到本机", os.path.exists(local) and os.path.getsize(local) > 0,
      f"{local} {os.path.getsize(local) if os.path.exists(local) else 0} bytes")

text = open(local, "r", encoding="utf-8").read()
doc = json.loads(text)
check("JSON 可解析", isinstance(doc, dict))
check("format 正确", doc.get("format") == "mengyalife-backup", str(doc.get("format")))
check("schemaVersion = 1", doc.get("schemaVersion") == 1, str(doc.get("schemaVersion")))
EXPECTED_SCHEMA = int(os.environ.get("MENGYA_EXPECTED_SCHEMA", "11"))
check("database.schemaVersion = %d" % EXPECTED_SCHEMA, doc.get("database", {}).get("schemaVersion") == EXPECTED_SCHEMA)
check("包含版本信息", bool(doc.get("app", {}).get("versionName")), str(doc.get("app")))
check("包含导出时间", isinstance(doc.get("exportedAt"), int) and doc["exportedAt"] > 0)
check("包含 ISO 时间", "T" in str(doc.get("exportedAtIso", "")))

data = doc.get("data", {})
j = data.get("journalEntries", [])
g = data.get("goals", [])
c = data.get("goalCheckins", [])
check("counts 与实际条数一致",
      doc.get("counts") == {"journalEntries": len(j), "goals": len(g), "goalCheckins": len(c)},
      str(doc.get("counts")))
check("导出包含日记", len(j) == len(journals_before) == 1)
check("导出包含目标", len(g) == len(goals_before) == 1)
check("导出包含进展", len(c) == len(checkins_before) == 2)

check("日记标题/正文完整", j[0].get("title") == journals_before[0][1] and j[0].get("body") == journals_before[0][2],
      str(j))
check("日记时间戳完整", j[0].get("createdAt") == journals_before[0][3] and j[0].get("updatedAt") == journals_before[0][4])
check("目标字段完整",
      g[0].get("id") == goals_before[0][0] and g[0].get("title") == goals_before[0][1]
      and g[0].get("level") == goals_before[0][3] and g[0].get("parentId") == goals_before[0][4]
      and g[0].get("status") == goals_before[0][6],
      str(g[0]))
check("目标父子关系与库一致（根为 null）", g[0].get("parentId") is None)
ok_checkins = all(
    item.get("id") == row[0] and item.get("goalId") == row[1]
    and item.get("recordedAt") == row[2] and item.get("note") == row[3]
    and abs(float(item.get("metricValue")) - float(row[4])) < 1e-9
    and item.get("metricUnit") == row[5] and item.get("source") == row[6]
    for item, row in zip(c, checkins_before)
)
check("进展记录与库逐字段一致", ok_checkins, str(c))

lower = text.lower()
check("导出不含 API Key 字段", "apikey" not in lower and "api_key" not in lower
      and "secret" not in lower and "password" not in lower)
check("导出不含 token", "\"token\"" not in lower)

print("========== 4. 导出后数据库未变 ==========")
journals_after, goals_after, checkins_after = db_snapshot(db_paths())
check("日记未变", journals_after == journals_before)
check("目标未变", goals_after == goals_before)
check("进展未变", checkins_after == checkins_before)

print("========== 5. 清理设备上的测试导出文件 ==========")
adb(f"shell rm -f {remote}")
remaining = adb("shell 'ls /sdcard/Download/%s 2>/dev/null'" % os.path.basename(remote)).strip()
check("测试导出文件已清理", remaining == "", remaining)
print("导出样本保留在:", local)

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
