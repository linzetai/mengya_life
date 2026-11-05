"""G4A-2A QA 真机：SAF 只读选择 v2 备份 → 口令 → 预览；断言无写入。"""
import json, os, re, shutil, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *
from verify_backup_v2_full import dump_db, pull_db, db_counts, TABLES

require_qa("verify_restore_preview.py")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SAMPLE = os.path.join(ROOT, "docs/reports/G4A1-1-acceptance/qa/sample-v2-full.mengyalife")
REMOTE = "/sdcard/Download/G4A2A-preview.mengyalife"
PASS = "BackupFull123!"
EVIDENCE = os.path.join(ROOT, "docs/reports/G4A2A-acceptance/qa")
os.makedirs(EVIDENCE, exist_ok=True)


def clickable(text):
    xml = dump()
    for m in re.finditer(r"<node[^>]*>", xml):
        tag = m.group(0)
        if f'text="{text}"' in tag:
            return 'clickable="true"' in tag
    return None


def main():
    started = time.time()
    clear_qa_data()
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_seed_backup_fixture true")
    wait_app_ui()
    prev = None
    before = None
    for _ in range(20):
        cur = dump_db(pull_db("/tmp/g4a2a-db"))
        if all(db_counts(cur)[t] > 0 for t in TABLES) and prev == cur:
            before = cur; break
        prev = cur; time.sleep(1.5)
    check("10 表 QA 夹具就绪", before is not None and all(db_counts(before)[t] > 0 for t in TABLES))
    adb(f"push {SAMPLE} {REMOTE}")
    check("样本已推到 Download", os.path.exists(SAMPLE) and adb(f"shell 'ls -l {REMOTE}'").strip() != "")

    if not has_tag("btn_settings"): tap_tag("tab_overview")
    check("设置页", tap_tag("btn_settings") and wait_tag("settings_back", 10))
    check("恢复入口", scroll_to_tag("settings_restore_v2", 20))
    tap_tag("settings_restore_v2")
    time.sleep(2)
    check("系统文件选择器", has_text("最近") or has_text("浏览"))
    if has_text("浏览"): tap_text("浏览")
    time.sleep(0.8)
    if has_text("下载"): tap_text("下载")
    time.sleep(0.8)
    check("选择样本文件", tap_text("G4A2A-preview.mengyalife"))
    check("确定选择", tap_text("确定"))
    check("恢复口令对话框", wait_text("输入备份口令", 10))
    tap_text("备份口令")
    adb(f"shell input text '{PASS}'")
    close_keyboard()
    check("校验并预览", tap_text("校验并预览"))
    check("预览出现", wait_text("恢复预览（只读）", 15))
    xml = dump()
    open(os.path.join(EVIDENCE, "preview-ui.xml"), "w").write(xml)
    check("显示 10 表计数", all(has_text(label) for label in [
        "日记：2 / 2", "目标：3 / 3", "进展：2 / 2", "对话：3 / 3", "草案：2 / 2",
        "成长线索：3 / 3", "成长记忆：2 / 2", "洞察：2 / 2", "周回顾：2 / 2",
        "历史 AI 周报（仅归档）：2 / 2（仅归档，不恢复）",
    ]))
    check("记录不恢复内容", all(has_text(x) for x in ["API Key", "AI 授权同意", "AI 自动发送开关", "系统通知权限"]))
    check("明确尚未执行", has_text("尚未执行恢复"))
    c = clickable("确认恢复（替换本机数据）")
    check("确认按钮未勾选不可点击", c is False, str(c))
    check("没有成功/执行提示", not has_text("恢复完成") and not has_text("已恢复"))
    after = dump_db(pull_db("/tmp/g4a2a-db-after"))
    check("预览后 DB 完全不变", after == before)
    check("预览后 10 表 counts 不变", db_counts(after) == db_counts(before))
    check("预览后仍无写入路径", not has_text("已恢复") and c is False)

    tap_text("关闭")
    time.sleep(1)
    # 关闭后重启：不得保留预览、口令或明文。
    adb(f"shell am force-stop {PKG}")
    time.sleep(0.8)
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity")
    wait_app_ui()
    if not has_tag("btn_settings"): tap_tag("tab_overview")
    tap_tag("btn_settings")
    wait_tag("settings_back", 8)
    scroll_to_tag("settings_restore_v2", 20)
    check("重启后不保留预览/口令", not has_text("恢复预览") and not has_text("输入备份口令") and not has_text(PASS))
    pid = adb(f"shell pidof {PKG}").strip().split()
    logcat = adb(f"shell logcat -d --pid={pid[0]}") if pid else ""
    check("重启后应用日志无口令", PASS not in logcat)
    adb(f"shell rm -f {REMOTE}")
    check("测试文件已清理", not os.path.exists("/sdcard/Download/G4A2A-preview.mengyalife"))
    elapsed = time.time() - started
    print(f"QA_RESTORE_PREVIEW_SECONDS={elapsed:.2f}")
    json.dump({"seconds": elapsed, "dbCounts": db_counts(before)}, open(os.path.join(EVIDENCE, "preview-result.json"), "w"),
              ensure_ascii=False, indent=2)
    restore_chrome()
    sys.exit(0 if summary() else 1)


if __name__ == "__main__":
    main()
