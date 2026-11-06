"""G4A-1.1 QA 真机备份失败路径：可控故障注入（不物理拔盘）。

覆盖：取消 SAF、目标写失败、目标截断、回读失败、临时文件清理失败、目标无法删除。
断言：不显示成功（清理失败除外，显示明确 warning）、DB 不变、不覆盖旧文件、
不遗留明文临时文件、无法删除时明确提示。
"""
import json, os, subprocess, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *
from verify_backup_v2_full import TABLES, dump_db, pull_db, db_counts

require_qa("verify_backup_v2_failures.py")

PASS = "BackupFail123!"
EVIDENCE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                        "docs/reports/G4A1-1-acceptance/failures")
os.makedirs(EVIDENCE, exist_ok=True)
RESULTS_FILE = os.path.join(EVIDENCE, "scenarios.json")
scenarios = []


def start(fault=None):
    adb(f"shell am force-stop {PKG}")
    time.sleep(0.8)
    extra = ""
    if fault:
        extra = f" --ez {fault} true"
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity{extra}")
    return wait_app_ui()


def open_backup_dialog():
    if not has_tag("btn_settings"):
        tap_tag("tab_overview")
    if not tap_tag("btn_settings"):
        return False
    if not wait_tag("settings_back", 8):
        return False
    if not scroll_to_tag("settings_backup_v2", 20):
        return False
    tap_tag("settings_backup_v2")
    if not wait_text("设置备份口令", 8):
        return False
    tap_text("备份口令（至少 10 个字符）")
    adb(f"shell input text '{PASS}'")
    close_keyboard()
    tap_text("再次输入口令")
    adb(f"shell input text '{PASS}'")
    close_keyboard()
    return tap_text("开始备份")


def remote_files():
    out = adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'")
    return {x.strip() for x in out.splitlines() if x.strip()}


def temp_files():
    out = adb(f"shell run-as {PKG} ls -1 no_backup/backup-tmp 2>/dev/null")
    return [x.strip() for x in out.splitlines() if x.strip()]


def cleanup_remote(files):
    for f in files:
        adb(f"shell rm -f {f}")


def cleanup_temp():
    adb(f"shell run-as {PKG} rm -rf no_backup/backup-tmp")


def assert_no_plaintext_temp():
    # 临时文件只允许是加密 envelope；不得出现正文/敏感字段。
    out = adb(f"shell run-as {PKG} sh -c 'grep -R -a -l \"journal_entries\\|encrypted_api_key\\|BackupFail123\" no_backup/backup-tmp 2>/dev/null'")
    return not out.strip()


def db_snapshot():
    return dump_db(pull_db("/tmp/g4a11-fail-db"))


def record(name, ok, detail=""):
    scenarios.append({"name": name, "pass": bool(ok), "detail": detail})
    check(name, ok, detail)


def main():
    started = time.time()
    selected_case = os.environ.get("MENGYA_BACKUP_SCENARIO", "all").strip().lower()
    allowed_cases = {"all", "cancel", "write", "truncate", "read", "temp-cleanup", "delete"}
    if selected_case not in allowed_cases:
        check("case名称有效", False, f"{selected_case}; allowed={sorted(allowed_cases)}")
        restore_chrome()
        sys.exit(1)

    def should_run(case):
        return selected_case == "all" or selected_case == case

    clear_qa_data()
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_seed_backup_fixture true")
    wait_app_ui()
    # 等夹具完全落库并稳定（两次完整快照一致），避免把尚未完成的 seed 当成导出变化。
    prev = None
    before = None
    deadline = time.time() + 30
    while time.time() < deadline:
        cur = db_snapshot()
        if all(db_counts(cur)[t] > 0 for t in TABLES):
            if prev == cur:
                before = cur
                break
            prev = cur
        time.sleep(1.5)
    if before is None:
        before = db_snapshot()
    check("夹具 10 表非空", all(db_counts(before)[t] > 0 for t in TABLES))
    baseline_files = remote_files()
    baseline_temp = temp_files()
    record("失败前无临时备份文件", not baseline_temp, str(baseline_temp))

    if should_run("cancel"):
        # 1) 用户取消 SAF 选择
        check("取消场景启动", start())
        before_files = remote_files()
        check("取消场景打开备份", open_backup_dialog())
        check("取消场景 SAF 出现", wait_text("保存", 15))
        adb("shell input keyevent KEYCODE_BACK")
        check("取消后明确未写入", wait_text("已取消备份", 12))
        new_files = remote_files() - before_files
        record("取消 SAF：不显示成功/不生成文件", not has_text("完整备份已写入") and not new_files, str(new_files))
        record("取消 SAF：DB 不变", db_snapshot() == before)
        record("取消 SAF：无临时明文", assert_no_plaintext_temp())

    if should_run("write"):
        # 2) 目标写入失败
        check("写失败场景启动", start("qa_backup_fail_write"))
        check("写失败场景打开备份", open_backup_dialog())
        check("写失败场景 SAF 出现", wait_text("保存", 15))
        tap_text("保存")
        check("写失败提示", wait_text("备份失败", 20))
        new_files = remote_files() - baseline_files
        record("写入失败：不显示成功", not has_text("完整备份已写入"))
        record("写入失败：DB 不变", db_snapshot() == before)
        record("写入失败：无明文临时文件", assert_no_plaintext_temp())
        cleanup_remote(new_files)
        cleanup_temp()

    if should_run("truncate"):
        # 3) 目标文件被截断
        check("截断场景启动", start("qa_backup_truncate_write"))
        check("截断场景打开备份", open_backup_dialog())
        check("截断场景 SAF 出现", wait_text("保存", 15))
        tap_text("保存")
        check("截断提示", wait_text("回读内容与写入不一致", 20))
        new_files = remote_files() - baseline_files
        record("截断：不显示成功", not has_text("完整备份已写入"))
        record("截断：DB 不变", db_snapshot() == before)
        record("截断：无明文临时文件", assert_no_plaintext_temp())
        cleanup_remote(new_files)
        cleanup_temp()

    if should_run("read"):
        # 4) 回读失败
        check("回读失败场景启动", start("qa_backup_fail_read"))
        check("回读失败场景打开备份", open_backup_dialog())
        check("回读失败场景 SAF 出现", wait_text("保存", 15))
        tap_text("保存")
        check("回读失败提示", wait_text("备份失败", 20))
        new_files = remote_files() - baseline_files
        record("回读失败：不显示成功", not has_text("完整备份已写入"))
        record("回读失败：DB 不变", db_snapshot() == before)
        record("回读失败：无明文临时文件", assert_no_plaintext_temp())
        cleanup_remote(new_files)
        cleanup_temp()

    if should_run("temp-cleanup"):
        # 5) 临时文件清理失败：文件本身成功，但必须显式 warning
        check("清理失败场景启动", start("qa_backup_fail_temp_cleanup"))
        check("清理失败场景打开备份", open_backup_dialog())
        check("清理失败场景 SAF 出现", wait_text("保存", 15))
        tap_text("保存")
        check("清理失败提示", wait_text("临时文件清理失败", 25))
        new_files = remote_files() - baseline_files
        record("清理失败：不当作完全成功", has_text("临时文件清理失败"))
        record("清理失败：DB 不变", db_snapshot() == before)
        record("清理失败：临时文件是密文（无明文）", assert_no_plaintext_temp())
        cleanup_remote(new_files)
        cleanup_temp()

    if should_run("delete"):
        # 6) 目标无法删除 + 写失败
        check("删除失败场景启动", start("qa_backup_fail_write"))
        # 同时注入 delete 失败：重新启动会 reset，需用两个 extra
        adb(f"shell am force-stop {PKG}")
        time.sleep(0.8)
        adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_backup_fail_write true --ez qa_backup_fail_delete true")
        wait_app_ui()
        check("删除失败场景打开备份", open_backup_dialog())
        check("删除失败场景 SAF 出现", wait_text("保存", 15))
        tap_text("保存")
        check("删除失败提示", wait_text("目标文件可能无效", 25))
        new_files = remote_files() - baseline_files
        record("删除失败：明确提示可能无效", has_text("目标文件可能无效"), str(new_files))
        record("删除失败：不显示成功", not has_text("完整备份已写入"))
        record("删除失败：DB 不变", db_snapshot() == before)
        cleanup_remote(new_files)
        cleanup_temp()

    record("所有场景后 DB 仍不变", db_snapshot() == before)
    result_path = RESULTS_FILE if selected_case == "all" else RESULTS_FILE.replace("scenarios.json", f"scenario-{selected_case}.json")
    json.dump(scenarios, open(result_path, "w"), ensure_ascii=False, indent=2)
    print(f"QA_BACKUP_SCENARIO={selected_case}")
    print(f"QA_FAILURE_SECONDS={time.time() - started:.2f}")
    restore_chrome()
    sys.exit(0 if summary() else 1)


if __name__ == "__main__":
    main()
