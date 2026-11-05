"""完整加密备份 v2 QA 真机验证（G4A-1，只跑 QA 包）。

覆盖：设置入口 → 口令对话框 → SAF 保存 → 回读成功 → 拉取文件 →
独立 Node 探针解密/校验 payload → 与 QA 数据库计数对照 → 清理。
不使用真实用户数据；不调用恢复；不触碰正式包。
"""
import json, os, shutil, sqlite3, subprocess, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_backup_v2.py")

PASS = "BackupPass123!"
DB = "mengya-life-qa.db"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def create_goal():
    tap_tag("tab_goals")
    time.sleep(1.2)
    tap_tag("goal_new_long_term")
    if not wait_tag("goal_editor_title", 8):
        return False
    tap_tag("goal_editor_title")
    adb("shell input text 'BackupV2Goal'")
    close_keyboard()
    tap_tag("goal_editor_save")
    return wait_text("BackupV2Goal", 10)


def create_diary():
    tap_tag("tab_diary")
    time.sleep(1.2)
    tap_tag("diary_new")
    if not wait_tag("journal_body_input", 8):
        return False
    tap_tag("journal_title_input")
    adb("shell input text 'BackupV2Diary'")
    tap_tag("journal_body_input")
    adb("shell input text 'backup%sv2%sbody'")
    close_keyboard()
    tap_tag("journal_save")
    return wait_text("BackupV2Diary", 8)


def db_counts():
    d = tempfile.mkdtemp(prefix="g4a1-db-")
    for f in (DB, DB + "-wal", DB + "-shm"):
        p = subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f}",
            shell=True, capture_output=True,
        )
        if p.stdout:
            open(os.path.join(d, f), "wb").write(p.stdout)
    con = sqlite3.connect(os.path.join(d, DB))
    try:
        return {t: con.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0] for t in [
            "journal_entries", "goals", "goal_checkins", "advisor_messages", "goal_proposals",
            "growth_signals", "growth_memories", "insight_reports", "weekly_reviews", "weekly_ai_reviews",
        ]}
    finally:
        con.close()


def remote_files():
    out = adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'")
    return [x.strip() for x in out.splitlines() if x.strip()]


print("========== 0. 准备 QA 假数据 ==========")
clear_qa_data()
restart_app()
check("目标已建立", create_goal())
check("日记已建立", create_diary())
before_counts = db_counts()
check("准备数据非空", before_counts["journal_entries"] >= 1 and before_counts["goals"] >= 1, str(before_counts))

print("========== 1. 完整备份 v2 入口与口令 ==========")
tap_tag("tab_overview")
time.sleep(0.8)
tap_tag("btn_settings")
check("设置页", wait_tag("settings_back", 10))
check("完整备份入口", scroll_to_tag("settings_backup_v2", 20))
before_files = set(remote_files())
tap_tag("settings_backup_v2")
check("口令对话框", wait_tag("backup_password_dialog", 8))
tap_tag("backup_password_input")
adb("shell input text '%s'" % PASS)
close_keyboard()
tap_tag("backup_password_confirm_input")
adb("shell input text '%s'" % PASS)
close_keyboard()
tap_tag("backup_password_confirm")

print("========== 2. SAF 保存 ==========")
check("系统保存按钮出现", wait_text("保存", 15))
tap_text("保存")
check("备份写入并校验成功", wait_text("完整备份已写入并校验", 30))

print("========== 3. 拉取并用独立探针解密 ==========")
files = remote_files()
new_files = [f for f in files if f not in before_files]
check("生成新的 v2 备份文件（不碰旧文件）", bool(new_files), str(sorted(before_files)))
remote = new_files[-1] if new_files else ""
local = "/tmp/g4a1-backup.mengyalife"
if remote:
    adb(f"pull {remote} {local}")
check("文件已拉取", bool(remote) and os.path.exists(local) and os.path.getsize(local) > 0,
      f"{local} {os.path.getsize(local) if os.path.exists(local) else 0}")
probe = subprocess.run(
    ["node", os.path.join(ROOT, "tools/ui-verify/backup_v2_probe.js"), local, PASS],
    capture_output=True, text=True,
)
check("独立探针解密/哈希/表校验", probe.returncode == 0, (probe.stdout + probe.stderr)[-500:])
summary = {}
if probe.returncode == 0:
    summary = json.loads(probe.stdout)
check("QA 构建标记", summary.get("qaBuild") is True, str(summary.get("qaBuild")))
check("备份包含 10 表计数", len(summary.get("counts", {})) == 10, str(summary.get("counts")))
mapping = {
    "journalEntries": "journal_entries", "goals": "goals", "goalCheckins": "goal_checkins",
    "advisorMessages": "advisor_messages", "goalProposals": "goal_proposals",
    "growthSignals": "growth_signals", "growthMemories": "growth_memories",
    "insightReports": "insight_reports", "weeklyReviews": "weekly_reviews",
    "weeklyAiReviews": "weekly_ai_reviews",
}
for key, table in mapping.items():
    check("计数一致：" + key, summary.get("counts", {}).get(key) == before_counts.get(table),
          f"{summary.get('counts', {}).get(key)} vs {before_counts.get(table)}")

check("文件不是明文（无敏感字段）", "encrypted_api_key" not in open(local, "rb").read().decode("utf-8", "replace"))

print("========== 4. 清理测试文件 ==========")
if remote:
    adb(f"shell rm -f {remote}")
check("QA 导出文件已清理", remote not in remote_files(), remote)
try:
    os.remove(local)
except OSError:
    pass

# 不恢复：备份是只读快照，数据库计数不变
check("导出未改变 QA 数据库", db_counts() == before_counts)
restore_chrome()
sys.exit(0 if summary() else 1)
