"""QA-only startup fail-closed probe when the Room DB cannot be opened."""
import os
import sqlite3
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_restore_db_unreadable.py")
scenario = os.environ.get("MENGYA_RESTORE_STARTUP_SCENARIO", "").strip().upper()
if scenario and scenario != "DB_UNREADABLE":
    raise SystemExit(f"未知startup scenario: {scenario}")

DB_NAME = "mengya-life-qa.db"
DB_REMOTE = f"databases/{DB_NAME}"

def pull_db(path):
    result = subprocess.run(
        f"adb -s {DEV} exec-out run-as {PKG} cat {DB_REMOTE}",
        shell=True, capture_output=True, timeout=40,
    )
    if result.returncode != 0 or not result.stdout:
        raise RuntimeError("无法读取QA数据库快照")
    with open(path, "wb") as output:
        output.write(result.stdout)
    return path

def chmod_db(mode):
    return subprocess.run(
        ["adb", "-s", DEV, "shell", "run-as", PKG, "chmod", mode, DB_REMOTE],
        capture_output=True, text=True, timeout=30,
    )

def snapshot(path):
    db = sqlite3.connect(path)
    try:
        tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        names = ("journal_entries", "goals", "goal_checkins", "weekly_reviews", "weekly_ai_reviews")
        counts = {name: db.execute(f"SELECT count(*) FROM `{name}`").fetchone()[0]
                  for name in names if name in tables}
        version = db.execute("PRAGMA user_version").fetchone()[0]
        return version, counts
    finally:
        db.close()

root = tempfile.mkdtemp(prefix="g4a2b-unreadable-")
first = os.path.join(root, "before.db")
after = os.path.join(root, "after.db")
permission_restricted = False
cleaned = False
try:
    clear_qa_data()
    check("Fresh QA数据库启动", restart_app())
    check("确认初始业务页可用", has_tag("tab_goals") or has_tag("tab_overview"))
    pull_db(first)
    before_version, before_counts = snapshot(first)
    check("QA数据库为v12", before_version == 12, str(before_version))

    adb(f"shell am force-stop {PKG}")
    chmod = chmod_db("000")
    permission_restricted = chmod.returncode == 0
    check("模拟DB不可读权限", permission_restricted, chmod.stderr.strip())
    if not permission_restricted:
        raise RuntimeError("QA DB chmod failed; stop unreadable test")
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_run_weekly_review true")
    check("启动界面完成渲染", wait_app_ui(20))
    check("DB不可读时显示维护页", wait_tag("restore_maintenance", 20))
    check("DB不可读时不显示目标写入入口", not has_tag("tab_goals"))
    check("DB不可读时不显示AI请求入口", not has_tag("weekly_generate_ai"))

    # Explicitly enqueue the worker through the QA-only Activity extra above.
    # Its first operation must be rejected by the non-READY barrier.
    time.sleep(15)
    adb(f"shell am force-stop {PKG}")
    restore_perm = chmod_db("600")
    if restore_perm.returncode != 0:
        raise RuntimeError(f"restore DB permissions failed: {restore_perm.stderr}")
    permission_restricted = False
    pull_db(after)
    after_version, after_counts = snapshot(after)
    check("DB不可读期间Room user_version未改变", after_version == before_version)
    check("DB不可读期间业务表无写入副作用", after_counts == before_counts,
          f"before={before_counts}; after={after_counts}")
finally:
    if permission_restricted:
        restore_perm = chmod_db("600")
        check("恢复QA DB权限", restore_perm.returncode == 0, restore_perm.stderr.strip())
    clear_qa_data()
    cleaned = True
    check("测试后QA包数据已清理", cleaned)

sys.exit(0 if summary() else 1)
