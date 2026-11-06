"""G4A-2B QA：fresh v12数据库创建app_meta并从UNKNOWN收敛READY。"""
import os, sqlite3, subprocess, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_restore_fresh_db.py")

clear_qa_data()
check("冷启动应用", restart_app())
check("设置页可用（未误入维护）", tap_tag("btn_settings") and wait_tag("settings_back", 10))
root = tempfile.mkdtemp(prefix="g4a2b-fresh-")
local = os.path.join(root, "mengya-life-qa.db")
for name in ("mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"):
    result = subprocess.run(
        f"adb -s {DEV} exec-out run-as {PKG} cat databases/{name}",
        shell=True, capture_output=True,
    )
    if result.stdout:
        open(os.path.join(root, name), "wb").write(result.stdout)
con = sqlite3.connect(local)
check("fresh库v12", con.execute("PRAGMA user_version").fetchone()[0] == 12)
meta = con.execute("SELECT restore_attempt_id, restore_generation, restore_converged_at FROM app_meta WHERE id=1").fetchone()
check("fresh app_meta初始化", meta == (None, 0, None), repr(meta))
check("fresh Gate不存在", not adb(f"shell run-as {PKG} ls no_backup/restore/gate.json 2>/dev/null").strip())
con.close()
restore_chrome()
sys.exit(0 if summary() else 1)
