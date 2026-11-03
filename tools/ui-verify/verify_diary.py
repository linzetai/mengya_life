"""日记回归（Gate 2C，只跑 QA 包）。

覆盖：空状态 → 空正文拦截 → 新建 → 查看 → 编辑 → 重启保持 → 删除确认 → 删除。
不使用任何真实用户数据：脚本开头会 `pm clear` QA 包（独立 applicationId + 独立数据库）。
"""
import os, sys, time, sqlite3, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_diary.py")

TITLE = "G2Cdiary"


def db_journals():
    d = "/tmp/g2c-diary-db"
    os.makedirs(d, exist_ok=True)
    for f in ["mengya-life-qa.db", "mengya-life-qa.db-wal", "mengya-life-qa.db-shm"]:
        subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f} > {d}/{f}",
            shell=True, capture_output=True,
        )
    con = sqlite3.connect(f"{d}/mengya-life-qa.db")
    rows = con.execute(
        "SELECT id,title,body,created_at,updated_at FROM journal_entries ORDER BY id"
    ).fetchall()
    con.close()
    return rows


print("========== 0. 清空 QA 数据 ==========")
clear_qa_data()
restart_app()
tap_tag("tab_diary")
time.sleep(1.2)
check("空状态显示「还没有记录」", wait_text("还没有记录", 8))
check("没有演示日记", not has_text("把节奏调慢了一档"))
check("无演示数据标签", not has_text("演示数据 · 非真实记录"))
shot("01-diary-empty")

print("========== 1. 空正文不可保存 ==========")
tap_tag("diary_new")
check("新建面板出现", wait_tag("journal_body_input", 8))
close_keyboard()
tap_tag("journal_save")
time.sleep(1.2)
check("空正文给出明确错误", has_text("正文不能为空"))
check("错误节点存在", has_tag("journal_body_error"))
check("面板未关闭", has_tag("journal_body_input"))
shot("02-diary-blank-error")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)

print("========== 2. 新建 ==========")
tap_tag("diary_new")
wait_tag("journal_body_input", 8)
tap_tag("journal_title_input")
adb("shell input text '%s'" % TITLE)
tap_tag("journal_body_input")
adb("shell input text 'g2cdiary%sv1'")
close_keyboard()
tap_tag("journal_save")
check("保存后面板关闭", wait_tag_gone("journal_body_input", 8))
time.sleep(1.0)
check("列表出现新日记", wait_text(TITLE, 12))
shot("03-diary-created")

rows = db_journals()
check("数据库里有 1 条日记", len(rows) == 1, str(rows))
check("正文与标题已入库", bool(rows) and rows[0][1] == TITLE and rows[0][2] == "g2cdiary v1", str(rows))
created_at = rows[0][3] if rows else 0

print("========== 3. 查看详情 ==========")
tap_text(TITLE)
time.sleep(1.2)
check("详情里有正文", wait_text("g2cdiary v1", 6))
check("详情有编辑入口", has_tag("journal_edit"))
shot("04-diary-detail")
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)

print("========== 4. 编辑 + 重启保持 ==========")
tap_text(TITLE)
time.sleep(1.0)
tap_tag("journal_edit")
wait_tag("journal_body_input", 8)
tap_tag("journal_body_input")
adb("shell input keyevent KEYCODE_MOVE_END")
adb("shell input text '%s'" % "%sedited")
close_keyboard()
tap_tag("journal_save")
wait_tag_gone("journal_body_input", 8)
check("编辑后仍显示标题", wait_text(TITLE, 8))

restart_app()
tap_tag("tab_diary")
check("重启后日记仍在", wait_text(TITLE, 8))
tap_text(TITLE)
time.sleep(1.2)
check("重启后能看到编辑后的正文", wait_text("g2cdiary v1 edited", 8))
adb("shell input keyevent KEYCODE_BACK")
time.sleep(1.0)
shot("05-diary-after-restart")

rows = db_journals()
check("数据库仍只有 1 条（编辑不新增）", len(rows) == 1, str(rows))
check("正文已更新", bool(rows) and rows[0][2].endswith("edited"), str(rows))
check("createdAt 未变", bool(rows) and rows[0][3] == created_at)
check("updatedAt 已刷新", bool(rows) and rows[0][4] > created_at, str(rows))

print("========== 5. 删除确认 ==========")
tap_text(TITLE)
time.sleep(1.0)
tap_tag("journal_delete")
check("删除确认框出现", wait_text("删除这条日记？", 6))
shot("06-diary-delete-confirm")
tap_text("取消")
time.sleep(1.0)
check("取消后记录仍在", has_text(TITLE))
tap_text(TITLE)
time.sleep(1.0)
tap_tag("journal_delete")
time.sleep(0.8)
tap_text("删除")
time.sleep(1.8)
check("删除后列表为空", wait_gone_text(TITLE, 8))
check("回到空状态", wait_text("还没有记录", 8))
shot("07-diary-deleted")
check("数据库里也删除了", len(db_journals()) == 0)

ok = summary()
restore_chrome()
sys.exit(0 if ok else 1)
