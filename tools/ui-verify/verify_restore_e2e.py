"""G4A-2B QA 端到端：A→导出→B→恢复A→Key/consent重置→重启可用。"""
import json, os, re, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *
from verify_backup_v2_full import dump_db, pull_db, db_counts, TABLES

require_qa("verify_restore_e2e.py")
PASS = "BackupFull123!"
EVIDENCE = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
                        "docs/reports/G4A2B-acceptance/qa")
os.makedirs(EVIDENCE, exist_ok=True)


def start(extra=""):
    adb(f"shell am force-stop {PKG}")
    time.sleep(0.8)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity {extra}")
    return wait_app_ui()


def stable_db(tag):
    prev = None
    for _ in range(30):
        cur = dump_db(pull_db(f"/tmp/g4a2b-{tag}"))
        if all(db_counts(cur)[t] > 0 for t in TABLES) and prev == cur:
            return cur
        prev = cur; time.sleep(1)
    return prev


def open_settings():
    if not has_tag("btn_settings"):
        tap_tag("tab_overview")
    return tap_tag("btn_settings") and wait_tag("settings_back", 10)


def export_current():
    check("打开设置", open_settings())
    check("备份入口", scroll_to_tag("settings_backup_v2", 20))
    before = set()
    out = adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'")
    before = {x.strip() for x in out.splitlines() if x.strip()}
    tap_tag("settings_backup_v2")
    check("导出密码框", wait_text("设置备份口令", 8))
    tap_text("备份口令（至少 10 个字符）")
    adb(f"shell input text '{PASS}'"); close_keyboard()
    tap_text("再次输入口令")
    adb(f"shell input text '{PASS}'"); close_keyboard()
    tap_text("开始备份")
    check("SAF保存", wait_text("保存", 15)); tap_text("保存")
    check("导出成功", wait_text("完整备份已写入并校验", 30))
    out = adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'")
    new = [x.strip() for x in out.splitlines() if x.strip() and x.strip() not in before]
    return new[-1] if new else ""


def checkbox_tap():
    xml = dump()
    m = re.search(r'<node[^>]*class="android.widget.CheckBox"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    if not m:
        return False
    x1,y1,x2,y2 = map(int,m.groups())
    tap_xy((x1+x2)//2,(y1+y2)//2)
    return True


def restore_file(remote):
    check("恢复入口", scroll_to_tag("settings_restore_v2", 20))
    tap_tag("settings_restore_v2"); time.sleep(2)
    check("文件选择器", has_text("浏览") or has_text("最近"))
    if has_text("浏览"): tap_text("浏览"); time.sleep(.8)
    if has_text("下载"): tap_text("下载"); time.sleep(.8)
    name = os.path.basename(remote)
    check("选择A备份", tap_text(name)); check("确认选择", tap_text("确定"))
    check("恢复密码框", wait_text("输入备份口令", 10))
    tap_text("备份口令"); adb(f"shell input text '{PASS}'"); close_keyboard()
    check("预览", tap_text("校验并预览") and wait_text("恢复预览（只读）", 15))
    check("勾选风险确认", checkbox_tap()); time.sleep(.5)
    check("执行确认", tap_text("确认恢复（替换本机数据）"))
    end = time.time()+180
    while time.time()<end:
        if has_text("恢复完成") or has_text("正在完成安全恢复") or has_text("恢复失败") or has_text("备份失败"):
            break
        time.sleep(1)
    if has_text("恢复完成"):
        return True
    # 成功横幅可能在上方被滚动出屏幕；滚回恢复入口再断言。
    scroll_to_tag("restore_success_banner", 20)
    return has_text("恢复完成")


def main():
    started=time.time()
    clear_qa_data()
    check("A数据+安全状态", start("--ez qa_seed_backup_fixture true --ez qa_seed_restore_security true"))
    time.sleep(6)
    a = stable_db("a")
    check("A夹具就绪", a is not None and all(db_counts(a)[t] > 0 for t in TABLES))
    remote = export_current()
    check("A备份文件", bool(remote), remote)
    check("B数据+安全状态", start("--ez qa_seed_backup_fixture_b true --ez qa_seed_restore_security true"))
    time.sleep(6)
    b = stable_db("b")
    check("B与A内容不同", b["journal_entries"] != a["journal_entries"])
    # from settings? B start is main; open settings restore
    if not open_settings(): check("B设置页", False)
    check("A文件仍存在", adb(f"shell 'ls {remote}'").strip() != "")
    ok = restore_file(remote)
    after = dump_db(pull_db("/tmp/g4a2b-after"))
    check("恢复成功提示", ok)
    core = [t for t in TABLES if t not in ("weekly_ai_reviews", "weekly_reviews")]
    check("核心8表A逐字段恢复", all(a[t] == after[t] for t in core))
    check("周回顾已恢复", len(after["weekly_reviews"]["rows"]) == len(a["weekly_reviews"]["rows"]))
    check("weekly AI 未恢复旧行", after["weekly_ai_reviews"]["rows"] != a["weekly_ai_reviews"]["rows"])
    check("A日记内容回来", after["journal_entries"]["rows"][0].get("title") == a["journal_entries"]["rows"][0].get("title"))
    qa = adb(f"shell run-as {PKG} cat no_backup/restore/qa-after.json 2>/dev/null").strip()
    check("安全状态文件", bool(qa), qa)
    if qa:
        s=json.loads(qa); check("Key不存在", s["keyPresent"] is False, str(s)); check("授权已撤销", s["consentGranted"] is False)
        check("自动发送关闭", s["aiEnabled"] is False); check("快照存在", s["snapshotExists"] is True); check("Gate释放", s["gateActive"] is False)
    # restart
    check("重启", start())
    time.sleep(2)
    after2 = dump_db(pull_db("/tmp/g4a2b-after2"))
    check("重启后核心8表A保持", all(a[t] == after2[t] for t in core))
    check("重启后周回顾仍存在", len(after2["weekly_reviews"]["rows"]) > 0)
    check("重启后weekly AI仍为0", len(after2["weekly_ai_reviews"]["rows"]) == 0)
    check("重启后无Gate", not adb(f"shell run-as {PKG} ls no_backup/restore/gate.json 2>/dev/null").strip())
    # backup cleanup only our A file
    adb(f"shell rm -f {remote}")
    elapsed=time.time()-started
    print(f"QA_E2E_SECONDS={elapsed:.2f}")
    json.dump({"seconds":elapsed,"backup":remote,"aCounts":db_counts(a),"bCounts":db_counts(b)},
              open(os.path.join(EVIDENCE,"e2e-result.json"),"w"),ensure_ascii=False,indent=2)
    restore_chrome()
    sys.exit(0 if summary() else 1)

if __name__=="__main__": main()
