"""G4A-2B QA 真机失败/崩溃收敛（R2/R3/R4/R5/R6/R7）。"""
import json, os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *
from verify_backup_v2_full import dump_db, pull_db, db_counts, TABLES

require_qa("verify_restore_failures.py")
PASS = "BackupFull123!"
SAMPLE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                      "docs/reports/G4A1-1-acceptance/qa/sample-v2-full.mengyalife")
REMOTE = "/sdcard/Download/G4A2B-A.mengyalife"
EVIDENCE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                        "docs/reports/G4A2B-acceptance/failures")
os.makedirs(EVIDENCE, exist_ok=True)
CORE = [t for t in TABLES if t not in ("weekly_ai_reviews", "weekly_reviews")]


def start(extra=""):
    adb(f"shell am force-stop {PKG}"); time.sleep(.8)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity {extra}")
    return wait_app_ui()


def wait_db(tag="x"):
    prev=None
    for _ in range(30):
        d=dump_db(pull_db(f"/tmp/g4a2b-{tag}"))
        if all(db_counts(d)[t] > 0 for t in TABLES) and prev == d: return d
        prev=d; time.sleep(1)
    return prev


def open_settings():
    if not has_tag("btn_settings"):
        tap_tag("tab_overview")
    return tap_tag("btn_settings") and wait_tag("settings_back", 10)


def checkbox():
    m=re.search(r'<node[^>]*class="android.widget.CheckBox"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',dump())
    if not m: return False
    x1,y1,x2,y2=map(int,m.groups()); tap_xy((x1+x2)//2,(y1+y2)//2); return True


def do_restore(flag_extra):
    clear_qa_data()
    check("启动B+安全+故障", start("--ez qa_seed_backup_fixture_b true --ez qa_seed_restore_security true " + flag_extra))
    time.sleep(5)
    before=wait_db("before")
    adb(f"push {SAMPLE} {REMOTE}")
    if not open_settings(): check("设置页",False); return before
    scroll_to_tag("settings_restore_v2",20); tap_tag("settings_restore_v2"); time.sleep(2)
    if has_text("浏览"): tap_text("浏览"); time.sleep(.8)
    if has_text("下载"): tap_text("下载"); time.sleep(.8)
    check("选择A",tap_text("G4A2B-A.mengyalife")); check("确定",tap_text("确定"))
    wait_text("输入备份口令",10); tap_text("备份口令"); adb(f"shell input text '{PASS}'"); close_keyboard()
    tap_text("校验并预览"); wait_text("恢复预览（只读）",15)
    check("确认勾选",checkbox()); time.sleep(.5); check("执行",tap_text("确认恢复（替换本机数据）"))
    return before


def status():
    raw=adb(f"shell run-as {PKG} cat no_backup/restore/qa-status.json 2>/dev/null").strip()
    return json.loads(raw) if raw else {}


def qa_after():
    raw=adb(f"shell run-as {PKG} cat no_backup/restore/qa-after.json 2>/dev/null").strip()
    return json.loads(raw) if raw else {}


def after_restart():
    start(); time.sleep(12)
    return dump_db(pull_db("/tmp/g4a2b-restart"))


def gate_exists():
    return bool(adb(f"shell run-as {PKG} ls no_backup/restore/gate.json 2>/dev/null").strip())

def main():
    started=time.time()
    # R2 transaction fail
    before=do_restore("--ez qa_restore_fail_transaction true"); time.sleep(4)
    db=dump_db(pull_db("/tmp/g4a2b-r2")); st=status()
    check("R2 事务回滚旧B数据", all(before[t]==db[t] for t in CORE))
    check("R2 旧Key仍存在", st.get("keyPresent") is True, str(st))
    check("R2 Gate已清理", not gate_exists())
    # R3 key delete fail -> gate held
    a=do_restore("--ez qa_restore_fail_key_delete true"); time.sleep(8)
    db=dump_db(pull_db("/tmp/g4a2b-r3")); st=status()
    check("R3 DB已提交A", all(a[t]==db[t] for t in CORE))
    check("R3 Gate保持", gate_exists())
    check("R3 Key删除失败保持", st.get("keyPresent") is True, str(st))
    check("R3 consent已撤销", st.get("consentGranted") is False)
    start(); time.sleep(12)
    st=qa_after(); check("R3 重启后收敛Key", st.get("keyPresent") is False, str(st))
    check("R3 重启后Gate释放", not gate_exists())
    # R4 crash after commit before journal
    a=do_restore("--ez qa_restore_pause_after_commit_ms 30000"); time.sleep(8)
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    start(); time.sleep(10)
    db=after_restart(); st=qa_after()
    check("R4 重启后A数据", all(a[t]==db[t] for t in CORE))
    check("R4 重启后Key删除", st.get("keyPresent") is False, str(st))
    check("R4 重启后Gate释放", not gate_exists())
    # R5 crash during security reset
    a=do_restore("--ez qa_restore_pause_after_consent_ms 30000"); time.sleep(8)
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    start(); time.sleep(10)
    db=after_restart(); st=qa_after()
    check("R5 重启后A数据", all(a[t]==db[t] for t in CORE))
    check("R5 重启后Key删除", st.get("keyPresent") is False, str(st))
    check("R5 重启后Gate释放", not gate_exists())
    # R6 journal missing after commit
    a=do_restore("--ez qa_restore_pause_after_commit_ms 30000"); time.sleep(8)
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    adb(f"shell run-as {PKG} rm -f no_backup/restore/journal.json")
    start(); time.sleep(12)
    db=after_restart(); st=qa_after()
    check("R6 journal缺失仍收敛A", all(a[t]==db[t] for t in CORE))
    check("R6 Gate释放", not gate_exists())
    # R7 gate missing after commit (journal present)
    a=do_restore("--ez qa_restore_pause_after_commit_ms 30000"); time.sleep(8)
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    adb(f"shell run-as {PKG} rm -f no_backup/restore/gate.json")
    start(); time.sleep(12)
    db=after_restart(); st=qa_after()
    check("R7 gate缺失仍收敛A", all(a[t]==db[t] for t in CORE))
    check("R7 Gate释放", not gate_exists())
    # cleanup app data, preserve sample for other scripts
    adb(f"shell rm -f {REMOTE}")
    elapsed=time.time()-started
    json.dump({"seconds":elapsed},open(os.path.join(EVIDENCE,"failures-result.json"),"w"))
    print(f"QA_FAILURE_SECONDS={elapsed:.2f}")
    restore_chrome(); sys.exit(0 if summary() else 1)

if __name__=="__main__": main()
