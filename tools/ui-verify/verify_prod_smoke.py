"""正式包只读冒烟（Gate 2C）。

与 QA 脚本不同：**不清数据、不新建日记/目标、不切换主题**，
只验证「升级安装后旧数据还在 + 冷启动无崩溃 + 设置文案准确 + 导出可用」。

前置：正式包已安装，且本轮验收数据（2 条 v1 迁移日记）仍在。
"""
import os, sys, time, json, re, sqlite3, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_prod("verify_prod_smoke.py")

VERSION = "0.13.0-g3e1"
EXPORT_DIR = os.environ.get("EXPORT_OUT", os.path.join(OUT, "export-prod"))
os.makedirs(EXPORT_DIR, exist_ok=True)


def db_file():
    d = "/tmp/g2c-prod-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life.db", "mengya-life.db-wal", "mengya-life.db-shm"]:
        p = subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f}",
            shell=True, capture_output=True,
        )
        open(f"{d}/{f}", "wb").write(p.stdout)
    return f"{d}/mengya-life.db"


def db_snapshot(path):
    con = sqlite3.connect(path)
    journals = con.execute(
        "SELECT id,title,body,created_at,updated_at FROM journal_entries ORDER BY id"
    ).fetchall()
    goals = con.execute("SELECT id,title,level,parent_id FROM goals ORDER BY id").fetchall()
    checkins = con.execute("SELECT id,goal_id,metric_value,metric_unit FROM goal_checkins ORDER BY id").fetchall()
    con.close()
    return journals, goals, checkins


print("========== 0. 冷启动与版本 ==========")
adb("shell logcat -b crash -c")
adb(f"shell am force-stop {PKG}")
time.sleep(1.0)
out = adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity")
check("冷启动成功", "Status: ok" in out and "COLD" in out, out.replace("\n", " ")[:120])
time.sleep(2.5)
check("进程存活", bool(adb(f"shell pidof {PKG}").strip()))
pkginfo = adb(f"shell dumpsys package {PKG}")
check("版本名 = " + VERSION, f"versionName={VERSION}" in pkginfo, "")
check("包名正确", f"Package [{PKG}]" in pkginfo)
shot("01-prod-cold-start")

print("========== 1. 旧日记仍在（逐字段） ==========")
tap_tag("tab_diary")
time.sleep(1.5)
check("MigrationA 在界面显示", wait_text("MigrationA", 8))
check("MigrationB 在界面显示", wait_text("MigrationB", 8))
check("列表计数 2 条", has_text("2 条"))
check("无演示数据标签", not has_text("演示数据 · 非真实记录"))
shot("02-prod-diary-restored")

journals, goals, checkins = db_snapshot(db_file())
check("数据库日记 2 条", len(journals) == 2, str(journals))
check("日记标题未变", [r[1] for r in journals] == ["MigrationA", "MigrationB"], str(journals))
check("日记正文未变", [r[2] for r in journals] == ["alpha v1 content", "beta v1 content"], str(journals))
check("日记时间戳未变",
      [r[3] for r in journals] == [1793636289310, 1793636317600], str(journals))
check("目标仍为空（未被测试写入）", len(goals) == 0, str(goals))
check("进展仍为空", len(checkins) == 0, str(checkins))
journals_before = journals

print("========== 2. 总览 / 目标 / 趋势 / 顾问 ==========")
tap_tag("tab_overview")
time.sleep(1.2)
check("总览空状态", wait_text("还没有长期目标", 8))
check("总览演示区仍标注", has_text("演示"))
shot("03-prod-overview")

tap_tag("tab_goals")
time.sleep(1.2)
check("目标空状态", wait_text("还没有目标", 8))
check("没有预置示例目标", not has_text("把身体状态维持在可持续的基准线上"))

tap_tag("tab_trends")
time.sleep(1.2)
check("真实趋势空状态", wait_tag("trend_real_empty", 8))
check("演示区仍在", scroll_to_tag("trend_demo_section", 10))
check("演示区标注", has_text("演示区（非真实数据）"))

tap_tag("tab_advisor")
time.sleep(1.0)
check("顾问页可进入", has_tag("advisor_input"))
close_keyboard()

print("========== 3. 设置页文案与版本 ==========")
tap_tag("tab_overview")
time.sleep(0.8)
tap_tag("btn_settings")
check("设置页", wait_tag("settings_back", 8))
time.sleep(1.0)
seen = set()
for _ in range(10):
    seen |= {t for t in texts(dump()) if t.strip()}
    adb("shell input swipe 600 1900 600 700 260")
    time.sleep(0.9)
joined = " ".join(seen)
for stale in ["重启后恢复「跟随系统」", "本轮不引入 DataStore", "设置仅在本次运行有效"]:
    check("不含过期文案：" + stale, stale not in joined)
check("当前版本显示", VERSION in joined, VERSION)
check("AI 服务真实配置入口", "AI 服务" in joined and "API 服务地址（HTTPS）" in joined)
# 设置页在 Gate 3A 变长：重新进入（滚动位置复位）再定位数据区
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("btn_settings")
time.sleep(1.2)
check("设置页数据区", scroll_to_tag("settings_export_data", 14))
check("导出我的数据入口", has_text("导出我的数据"))
check("导入数据入口", scroll_to_tag("settings_import_data", 4))
check("导入恢复尚未实现", has_tag("settings_import_data"))
shot("04-prod-settings")

# 重新进入设置（滚动位置复位），再进入导出流程
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
tap_tag("btn_settings")
time.sleep(1.0)
check("导出入口可见", scroll_to_tag("settings_export_data", 10))

print("========== 4. 正式包导出（只读数据库） ==========")
tap_tag("settings_export_data")
check("导出提醒", wait_tag("export_warning", 8))
check("提醒含敏感信息", has_tag("export_sensitive_warning"))
check("条数正确（日记 2 / 目标 0 / 进展 0）",
      has_text("日记 2 条") and has_text("目标 0 个") and has_text("进展记录 0 条"))
shot("05-prod-export-warning")
tap_tag("export_confirm")
time.sleep(2.5)
check("SAF 保存按钮", has_text("保存"))
tap_text("保存")
success = False
for _ in range(30):
    if has_text("导出完成并已校验"):
        success = True
        break
    time.sleep(0.5)
check("导出成功提示", success)

listing = adb("shell 'ls -t /sdcard/Download/mengya-life-export-*.json 2>/dev/null | head -n 1'").strip()
check("生成导出文件", bool(listing), listing)
remote = listing.splitlines()[-1].strip() if listing else ""
local = os.path.join(EXPORT_DIR, os.path.basename(remote)) if remote else ""
if remote:
    adb(f"pull {remote} {local}")
    doc = json.loads(open(local, encoding="utf-8").read())
    check("正式包导出可解析", doc.get("format") == "mengyalife-backup")
    check("正式包导出 schemaVersion", doc.get("schemaVersion") == 1)
    check("正式包导出日记 2 条", len(doc["data"]["journalEntries"]) == 2, str(doc.get("counts")))
    check("正式包导出目标 0", len(doc["data"]["goals"]) == 0)
    check("正式包导出进展 0", len(doc["data"]["goalCheckins"]) == 0)
    check("正式包导出正文完整",
          [x["body"] for x in doc["data"]["journalEntries"]] == ["alpha v1 content", "beta v1 content"])
    adb(f"shell rm -f {remote}")

journals_after, goals_after, checkins_after = db_snapshot(db_file())
check("导出后日记逐字段未变", journals_after == journals_before)
check("导出后目标仍为空", len(goals_after) == 0)
check("导出后进展仍为空", len(checkins_after) == 0)

print("========== 5. 稳定性 ==========")
crash = adb("shell logcat -b crash -d")
check("本应用无崩溃记录", "mengyalife" not in crash.lower(), crash[:160])
check("进程仍存活", bool(adb(f"shell pidof {PKG}").strip()))

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
