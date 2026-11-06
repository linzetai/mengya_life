"""G4A-2B QA 真机失败/崩溃收敛（R2/R3/R4/R5/R6/R7）。"""
import json, os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *
from verify_backup_v2_full import dump_db, pull_db, db_counts, TABLES
from restore_failure_assertions import table_differences, tables_equal

require_qa("verify_restore_failures.py")
PASS = "BackupFull123!"
SAMPLE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                      "docs/reports/G4A1-1-acceptance/qa/sample-v2-full.mengyalife")
REMOTE = "/sdcard/Download/G4A2B-A.mengyalife"
EVIDENCE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                        "docs/reports/G4A2B-acceptance/failures")
os.makedirs(EVIDENCE, exist_ok=True)
CORE = [t for t in TABLES if t not in ("weekly_ai_reviews", "weekly_reviews")]
EXPECTED_A = json.load(open(os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "docs/reports/G4A2B-acceptance/qa/db-A.json")))

BASE_CHECK = check
CURRENT_STAGE = "setup"
FAILURE_INDEX = 0

def collect_failure_diagnostics(name, detail):
    global FAILURE_INDEX
    FAILURE_INDEX += 1
    dest = os.path.join(EVIDENCE, f"failure-{FAILURE_INDEX:02d}")
    os.makedirs(dest, exist_ok=True)
    record = {"stage": CURRENT_STAGE, "assertion": name, "detail": detail}
    try:
        record["db"] = dump_db(pull_db(os.path.join("/tmp", f"g4a2b-failure-{FAILURE_INDEX}")))
    except Exception as e:
        record["dbReadError"] = str(e)
    for filename in ("gate.json", "journal.json", "qa-status.json", "qa-after.json", "qa-phase.json"):
        text = adb(f"shell run-as {PKG} cat no_backup/restore/{filename} 2>/dev/null").strip()
        if text: record[filename] = text
    meta_path = os.path.join("/tmp", f"g4a2b-failure-meta-{FAILURE_INDEX}")
    try:
        db = pull_db(meta_path)
        import sqlite3
        con = sqlite3.connect(db)
        record["app_meta"] = con.execute("SELECT * FROM app_meta").fetchall()
        con.close()
    except Exception as e:
        record["appMetaReadError"] = str(e)
    log = adb("shell logcat -d -t 300 | grep -E 'Restore|AndroidRuntime|Room|SQLite' | tail -80")
    record["logcat"] = log.replace(PASS, "<REDACTED>").replace("qa-restore-key-123456", "<REDACTED>")
    record["ui"] = dump()
    json.dump(record, open(os.path.join(dest, "diagnostics.json"), "w"), ensure_ascii=False, indent=2)
    with open(os.path.join(dest, "ui.xml"), "w") as f: f.write(record["ui"])

def check(name, cond, detail=""):
    BASE_CHECK(name, cond, detail)
    if not cond:
        try:
            collect_failure_diagnostics(name, detail)
        except Exception as e:
            print("DIAGNOSTICS_COLLECTION_FAILED:", type(e).__name__, str(e)[:200])


def start(extra=""):
    adb(f"shell am force-stop {PKG}"); time.sleep(.8)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity {extra}")
    return wait_app_ui()


def wait_db(tag="x"):
    prev=None
    for _ in range(45):
        try:
            d=dump_db(pull_db(f"/tmp/g4a2b-{tag}"))
        except Exception:
            d=None
        if d is not None and all(db_counts(d)[t] > 0 for t in TABLES) and prev == d: return d
        if d is not None: prev=d
        time.sleep(1)
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
    global CURRENT_STAGE
    CURRENT_STAGE = flag_extra or "normal"
    clear_qa_data()
    check("启动B+安全+故障", start("--ez qa_seed_backup_fixture_b true --ez qa_seed_restore_security true " + flag_extra))
    time.sleep(5)
    before=wait_db("before")
    if before is None or not all(db_counts(before)[t] > 0 for t in TABLES):
        check("B夹具就绪", False, str(db_counts(before) if before else None))
        sys.exit(1)
    check("B夹具10表非空", True, str(db_counts(before)))
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


def wait_phase(expected, timeout=60):
    end=time.time()+timeout
    while time.time()<end:
        if adb(f"shell run-as {PKG} ls no_backup/restore/qa-phase-{expected}.flag 2>/dev/null").strip():
            return True
        time.sleep(.5)
    return False


def wait_ready(timeout=60):
    end=time.time()+timeout
    while time.time()<end:
        if not gate_exists() and qa_after().get("keyPresent") is False: return True
        time.sleep(.5)
    return False


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


def wait_status_label(label, timeout=35):
    end = time.time() + timeout
    while time.time() < end:
        if status().get("label") == label:
            return True
        time.sleep(.5)
    return False


def assert_recovered_after_restart(scenario, db, state):
    check(f"{scenario} 重启后A核心数据", tables_equal(EXPECTED_A, db, CORE))
    check(f"{scenario} 历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
    check(f"{scenario} 重启后Key删除", state.get("keyPresent") is False, str(state))
    check(f"{scenario} 重启后consent撤销", state.get("consentGranted") is False, str(state))
    check(f"{scenario} 重启后自动AI关闭", state.get("aiEnabled") is False, str(state))
    check(f"{scenario} 重启后Gate释放", not gate_exists())


def run_single_scenario(scenario):
    """Run one isolated fault case without replaying earlier R2-R6 cases."""
    scenario = scenario.upper()
    started = time.time()
    allowed = {"R2", "R3", "R4", "R5", "R6", "R7"}
    if scenario not in allowed:
        check("case名称有效", False, f"{scenario}; allowed={sorted(allowed)}")
        restore_chrome(); sys.exit(1)
    wrong = json.loads(json.dumps(EXPECTED_A))
    wrong["journal_entries"]["rows"][0]["title"] = "WRONG_EXPECTATION"
    check("自测：错误预期会被比较捕获", table_differences(wrong, EXPECTED_A, CORE) == ["journal_entries"])

    if scenario == "R2":
        before_b = do_restore("--ez qa_restore_fail_transaction true")
        check("R2事务失败到达", wait_status_label("FAILED_BEFORE_COMMIT"))
        db = dump_db(pull_db("/tmp/g4a2b-r2-single")); state = status()
        check("R2失败后10表全部仍是B", tables_equal(before_b, db, TABLES))
        check("R2 Key保留", state.get("keyPresent") is True, str(state))
        check("R2 consent保留", state.get("consentGranted") is True, str(state))
        check("R2 Gate清理", not gate_exists())
    elif scenario == "R3":
        do_restore("--ez qa_restore_fail_key_delete true")
        check("R3提交后Key删除失败", wait_status_label("COMMITTED_NEEDS_CONVERGENCE"))
        db = dump_db(pull_db("/tmp/g4a2b-r3-single")); state = status()
        check("R3 DB已提交A", tables_equal(EXPECTED_A, db, CORE))
        check("R3历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
        check("R3 Gate保持", gate_exists())
        check("R3 Key仍在", state.get("keyPresent") is True, str(state))
        check("R3 consent已撤销", state.get("consentGranted") is False, str(state))
        start(); time.sleep(12)
        assert_recovered_after_restart("R3", dump_db(pull_db("/tmp/g4a2b-r3-single-restart")), qa_after())
    elif scenario in ("R4", "R5"):
        phase = "DB_COMMITTED" if scenario == "R4" else "CONSENT_DONE"
        extra = "--ez qa_restore_pause_after_commit_ms 30000" if scenario == "R4" else "--ez qa_restore_pause_after_consent_ms 30000"
        do_restore(extra)
        check(f"{scenario}到达{phase}", wait_phase(phase))
        adb(f"shell am force-stop {PKG}"); time.sleep(1)
        start(); check(f"{scenario}重启收敛", wait_ready())
        tag = scenario.lower() + "-single-restart"
        assert_recovered_after_restart(scenario, dump_db(pull_db(f"/tmp/g4a2b-{tag}")), qa_after())
    elif scenario in ("R6", "R7"):
        do_restore("--ez qa_restore_pause_after_commit_ms 30000")
        check(f"{scenario}到达DB_COMMITTED", wait_phase("DB_COMMITTED"))
        adb(f"shell am force-stop {PKG}"); time.sleep(1)
        if scenario == "R6":
            adb(f"shell run-as {PKG} rm -f no_backup/restore/journal.json")
        else:
            adb(f"shell run-as {PKG} rm -f no_backup/restore/gate.json")
        start(); check(f"{scenario}重启收敛", wait_ready())
        tag = scenario.lower() + "-single-restart"
        assert_recovered_after_restart(scenario, dump_db(pull_db(f"/tmp/g4a2b-{tag}")), qa_after())

    adb(f"shell rm -f {REMOTE}")
    elapsed = time.time() - started
    json.dump({"scenario": scenario, "seconds": elapsed, "passed": summary()},
              open(os.path.join(EVIDENCE, f"failure-{scenario}-result.json"), "w"),
              ensure_ascii=False, indent=2)
    print(f"QA_FAILURE_{scenario}_SECONDS={elapsed:.2f}")
    restore_chrome()
    sys.exit(0 if summary() else 1)


def main():
    started=time.time()
    wrong=json.loads(json.dumps(EXPECTED_A))
    wrong["journal_entries"]["rows"][0]["title"]="WRONG_EXPECTATION"
    check("自测：错误预期会被比较捕获", table_differences(wrong, EXPECTED_A, CORE) == ["journal_entries"])
    scenario = os.environ.get("MENGYA_RESTORE_SCENARIO", "").strip().upper()
    if scenario:
        run_single_scenario(scenario)
        return
    # R2 transaction fail
    beforeB=do_restore("--ez qa_restore_fail_transaction true")
    deadline=time.time()+30
    while time.time()<deadline and status().get("label")!="FAILED_BEFORE_COMMIT": time.sleep(.5)
    db=dump_db(pull_db("/tmp/g4a2b-r2")); st=status()
    check("R2 事务回滚全部10表旧B数据", tables_equal(beforeB, db, TABLES))
    check("R2 旧Key仍存在", st.get("keyPresent") is True, str(st))
    check("R2 旧consent仍存在", st.get("consentGranted") is True, str(st))
    check("R2 Gate已清理", not gate_exists())
    # R3 key delete fail -> gate held
    do_restore("--ez qa_restore_fail_key_delete true")
    deadline=time.time()+30
    while time.time()<deadline and status().get("label")!="COMMITTED_NEEDS_CONVERGENCE": time.sleep(.5)
    db=dump_db(pull_db("/tmp/g4a2b-r3")); st=status()
    check("R3 DB已提交A", tables_equal(EXPECTED_A, db, CORE))
    check("历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
    check("R3 Gate保持", gate_exists())
    check("R3 Key删除失败保持", st.get("keyPresent") is True, str(st))
    check("R3 consent已撤销", st.get("consentGranted") is False)
    start(); time.sleep(12)
    st=qa_after(); check("R3 重启后收敛Key", st.get("keyPresent") is False, str(st))
    check("R3 重启后Gate释放", not gate_exists())
    # R4 crash after commit before journal
    do_restore("--ez qa_restore_pause_after_commit_ms 30000")
    check("R4 到达DB_COMMITTED", wait_phase("DB_COMMITTED"))
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    start(); check("R4 启动收敛", wait_ready())
    db=dump_db(pull_db("/tmp/g4a2b-r4")); st=qa_after()
    check("R4 重启后A数据", tables_equal(EXPECTED_A, db, CORE))
    check("历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
    check("R4 重启后Key删除", st.get("keyPresent") is False, str(st))
    check("R4 重启后Gate释放", not gate_exists())
    # R5 crash during security reset
    do_restore("--ez qa_restore_pause_after_consent_ms 30000")
    check("R5 到达CONSENT_DONE", wait_phase("CONSENT_DONE"))
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    start(); check("R5 启动收敛", wait_ready())
    db=dump_db(pull_db("/tmp/g4a2b-r5")); st=qa_after()
    check("R5 重启后A数据", tables_equal(EXPECTED_A, db, CORE))
    check("历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
    check("R5 重启后Key删除", st.get("keyPresent") is False, str(st))
    check("R5 重启后Gate释放", not gate_exists())
    # R6 journal missing after commit
    do_restore("--ez qa_restore_pause_after_commit_ms 30000")
    check("R6 到达DB_COMMITTED", wait_phase("DB_COMMITTED"))
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    adb(f"shell run-as {PKG} rm -f no_backup/restore/journal.json")
    start(); check("R6 启动收敛", wait_ready())
    db=dump_db(pull_db("/tmp/g4a2b-r6")); st=qa_after()
    check("R6 journal缺失仍收敛A", tables_equal(EXPECTED_A, db, CORE))
    check("历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
    check("R6 Gate释放", not gate_exists())
    # R7 gate missing after commit (journal present)
    do_restore("--ez qa_restore_pause_after_commit_ms 30000")
    check("R7 到达DB_COMMITTED", wait_phase("DB_COMMITTED"))
    adb(f"shell am force-stop {PKG}"); time.sleep(1)
    adb(f"shell run-as {PKG} rm -f no_backup/restore/gate.json")
    start(); check("R7 启动收敛", wait_ready())
    db=dump_db(pull_db("/tmp/g4a2b-r7")); st=qa_after()
    check("R7 gate缺失仍收敛A", tables_equal(EXPECTED_A, db, CORE))
    check("历史weekly AI未恢复", len(db["weekly_ai_reviews"]["rows"]) == 0)
    check("R7 Gate释放", not gate_exists())
    # cleanup app data, preserve sample for other scripts
    adb(f"shell rm -f {REMOTE}")
    elapsed=time.time()-started
    json.dump({"seconds":elapsed},open(os.path.join(EVIDENCE,"failures-result.json"),"w"))
    print(f"QA_FAILURE_SECONDS={elapsed:.2f}")
    restore_chrome(); sys.exit(0 if summary() else 1)

if __name__=="__main__": main()
