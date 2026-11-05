"""G4A-1.1：10 表非空 QA 夹具 + 真机 v2 备份 + 逐字段无损性验证。

前置：QA 包；脚本会 pm clear（只清 QA 假数据），通过 MainActivity 的
`--ez qa_seed_backup_fixture true` 注入 10 表夹具，然后走真实设置页/SAF。
不读取、不复制正式用户数据；不实现恢复。
"""
import hashlib, json, math, os, shutil, sqlite3, subprocess, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_backup_v2_full.py")

PASS = "BackupFull123!"
DB = "mengya-life-qa.db"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
EVIDENCE = os.path.join(ROOT, "docs/reports/G4A1-1-acceptance/qa")
TABLES = [
    "journal_entries", "goals", "goal_checkins", "advisor_messages", "goal_proposals",
    "growth_signals", "growth_memories", "insight_reports", "weekly_reviews", "weekly_ai_reviews",
]
BACKUP_TABLES = {
    "journal_entries": "journalEntries", "goals": "goals", "goal_checkins": "goalCheckins",
    "advisor_messages": "advisorMessages", "goal_proposals": "goalProposals",
    "growth_signals": "growthSignals", "growth_memories": "growthMemories",
    "insight_reports": "insightReports", "weekly_reviews": "weeklyReviews",
    "weekly_ai_reviews": "weeklyAiReviews",
}
# 120 原始列 → v2 字段；weekly_ai_reviews.fact_ids_json 是显式转换（factIds + legacyFactIdsRaw）。
FIELD_MAP = {
    "journal_entries": {
        "id": "id", "title": "title", "body": "body", "created_at": "createdAt", "updated_at": "updatedAt",
        "analysis_status": "analysisStatus", "analysis_hash": "analysisHash",
        "analysis_error": "analysisError", "analysis_attempts": "analysisAttempts",
        "analyzed_at": "analyzedAt", "analysis_last_attempt_at": "analysisLastAttemptAt",
    },
    "goals": {
        "id": "id", "title": "title", "description": "description", "level": "level",
        "parent_id": "parentId", "domain": "domain", "status": "status",
        "start_date": "startDate", "due_date": "dueDate", "created_at": "createdAt", "updated_at": "updatedAt",
    },
    "goal_checkins": {
        "id": "id", "goal_id": "goalId", "recorded_at": "recordedAt", "note": "note",
        "metric_value": "metricValue", "metric_unit": "metricUnit", "source": "source",
    },
    "advisor_messages": {
        "id": "id", "role": "role", "content": "content", "created_at": "createdAt",
        "status": "status", "error_code": "errorCode", "error_message": "errorMessage",
        "context_goal_id": "contextGoalId", "context_epoch": "contextEpoch",
        "sources_json": "sourcesJson", "proposal_error": "proposalError", "proposal_mode": "proposalMode",
    },
    "goal_proposals": {
        "id": "id", "message_id": "messageId", "created_at": "createdAt", "schema_version": "schemaVersion",
        "status": "status", "summary": "summary", "assumptions_json": "assumptionsJson",
        "risks_json": "risksJson", "nodes_json": "nodesJson", "selected_temp_ids_json": "selectedTempIdsJson",
        "applied_at": "appliedAt", "applied_goal_ids_json": "appliedGoalIdsJson", "undone_at": "undoneAt",
    },
    "growth_signals": {
        "id": "id", "source_type": "sourceType", "source_id": "sourceId",
        "source_version": "sourceVersion", "category": "category", "content": "content",
        "evidence": "evidence", "is_inference": "isInference", "confidence": "confidence",
        "extracted_at": "extractedAt", "status": "status", "origin": "origin",
        "source_title": "sourceTitle", "content_hash": "contentHash", "updated_at": "updatedAt",
    },
    "growth_memories": {
        "id": "id", "theme": "theme", "content": "content", "is_inference": "isInference",
        "status": "status", "created_at": "createdAt", "updated_at": "updatedAt",
        "signal_ids_json": "signalIdsJson", "source_diary_ids_json": "sourceDiaryIdsJson",
        "user_corrected": "userCorrected", "version": "version", "confidence": "confidence",
        "insufficient": "insufficient", "recent_change": "recentChange", "fingerprint": "fingerprint",
        "deleted_at": "deletedAt",
    },
    "insight_reports": {
        "id": "id", "data_version": "dataVersion", "status": "status", "summary": "summary",
        "facts_json": "factsJson", "insights_json": "insightsJson", "period_start": "periodStart",
        "period_end": "periodEnd", "generated_at": "generatedAt", "error": "error", "model": "model",
    },
    "weekly_reviews": {
        "id": "id", "period_key": "periodKey", "period_start": "periodStart",
        "period_end": "periodEnd", "generated_at": "generatedAt", "version": "version",
        "data_version": "dataVersion", "ai_consent_version": "aiConsentVersion",
        "facts_json": "factsJson", "sections_json": "sectionsJson", "notified_at": "notifiedAt",
    },
    "weekly_ai_reviews": {
        "period_key": "periodKey", "local_data_version": "localDataVersion",
        "ai_consent_version": "aiConsentVersion", "status": "status", "generated_at": "generatedAt",
        "summary": "summary", "highlights_json": "highlightsJson", "suggestion": "suggestion",
        "fact_ids_json": "factIds", "categories": "categories", "error": "error",
        "model": "model", "config_hash": "configHash",
    },
}
BOOL_COLUMNS = {"is_inference", "user_corrected", "insufficient", "proposal_mode"}
JSON_FIELDS = {
    "sources_json", "assumptions_json", "risks_json", "nodes_json", "selected_temp_ids_json",
    "applied_goal_ids_json", "signal_ids_json", "source_diary_ids_json", "facts_json",
    "insights_json", "sections_json", "highlights_json",
}


def pull_db(directory):
    os.makedirs(directory, exist_ok=True)
    for f in (DB, DB + "-wal", DB + "-shm"):
        old = os.path.join(directory, f)
        if os.path.exists(old):
            os.remove(old)
        r = subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f}",
            shell=True, capture_output=True,
        )
        if r.stdout:
            open(os.path.join(directory, f), "wb").write(r.stdout)
    return os.path.join(directory, DB)


def dump_db(db_path):
    con = sqlite3.connect(db_path)
    con.row_factory = sqlite3.Row
    out = {}
    for t in TABLES:
        cols = con.execute(f"PRAGMA table_info(`{t}`)").fetchall()
        pk = "period_key" if t == "weekly_ai_reviews" else "id"
        rows = con.execute(f"SELECT * FROM `{t}` ORDER BY `{pk}`").fetchall()
        out[t] = {
            "columns": [{"name": c["name"], "type": c["type"]} for c in cols],
            "rows": [dict(r) for r in rows],
        }
    con.close()
    return out


def db_counts(dump):
    return {t: len(dump[t]["rows"]) for t in TABLES}


def decode_fact_ids(raw):
    raw = (raw or "").strip()
    if not raw:
        return []
    if raw.startswith("["):
        try:
            return [str(x) for x in json.loads(raw)]
        except Exception:
            pass
    return [x for x in raw.split("\u001f") if x.strip()]


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def expected_value(table, column, raw):
    if raw is None:
        return None
    if column in JSON_FIELDS:
        return json.loads(raw) if isinstance(raw, str) and raw.strip() else None
    return raw


def compare_field(table, column, raw, actual, errors):
    if column == "fact_ids_json":
        expected_ids = decode_fact_ids(raw)
        if not isinstance(actual, dict):
            errors.append(f"{table}.{column}: backup field missing (expected factIds/legacyFactIdsRaw)")
            return
        if actual.get("factIds") != expected_ids:
            errors.append(f"{table}.{column}.factIds: {expected_ids!r} != {actual.get('factIds')!r}")
        if actual.get("legacyFactIdsRaw") != raw:
            errors.append(f"{table}.{column}.legacyFactIdsRaw changed")
        return
    expected = expected_value(table, column, raw)
    if column in JSON_FIELDS and isinstance(actual, str):
        try:
            actual = json.loads(actual)
        except Exception:
            errors.append(f"{table}.{column}: backup JSON invalid")
            return
    if actual is None and expected is None:
        return
    if isinstance(expected, bool):
        if not isinstance(actual, bool) or actual != expected:
            errors.append(f"{table}.{column}: {expected!r} != {actual!r}")
    elif isinstance(expected, int):
        if not isinstance(actual, int) or isinstance(actual, bool) or actual != expected:
            errors.append(f"{table}.{column}: {expected!r} != {actual!r}")
    elif isinstance(expected, float):
        if not isinstance(actual, (int, float)) or isinstance(actual, bool) or not math.isclose(float(actual), expected, rel_tol=0, abs_tol=0):
            errors.append(f"{table}.{column}: {expected!r} != {actual!r}")
    elif isinstance(expected, list) or isinstance(expected, dict):
        if actual != expected:
            errors.append(f"{table}.{column}: JSON mismatch")
    else:
        if actual != expected:
            errors.append(f"{table}.{column}: {expected!r} != {actual!r}")


def compare_payload(before, payload):
    errors = []
    mapped = sum(len(FIELD_MAP[t]) for t in TABLES)
    if mapped != 120:
        errors.append(f"mapping columns {mapped} != 120")
    data = payload.get("data", {})
    for t in TABLES:
        bkey = BACKUP_TABLES[t]
        if t not in before:
            errors.append(f"missing DB table {t}")
            continue
        rows = before[t]["rows"]
        cols = [c["name"] for c in before[t]["columns"]]
        missing_map = [c for c in cols if c not in FIELD_MAP[t]]
        if missing_map:
            errors.append(f"{t}: unmapped columns {missing_map}")
        if len(data.get(bkey, [])) != len(rows):
            errors.append(f"{t}: count {len(rows)} != backup {len(data.get(bkey, []))}")
            continue
        pk = "period_key" if t == "weekly_ai_reviews" else "id"
        pk_field = "periodKey" if t == "weekly_ai_reviews" else "id"
        by_pk = {str(r[pk_field]): r for r in data[bkey]}
        for dbrow in rows:
            key = str(dbrow[pk])
            brow = by_pk.get(key)
            if brow is None:
                errors.append(f"{t}: backup missing PK {key}")
                continue
            for col in cols:
                raw = bool(dbrow[col]) if col in BOOL_COLUMNS and dbrow[col] is not None else dbrow[col]
                if t == "weekly_ai_reviews" and col == "fact_ids_json":
                    compare_field(t, col, raw, brow, errors)
                else:
                    field = FIELD_MAP[t][col]
                    if field not in brow:
                        errors.append(f"{t}.{col}: backup field {field} missing")
                    else:
                        compare_field(t, col, raw, brow[field], errors)
    return errors, mapped


def main():
    os.makedirs(EVIDENCE, exist_ok=True)
    print("========== 0. 清 QA + 注入 10 表夹具 ==========")
    clear_qa_data()
    adb(f"shell am start -W -n {PKG}/com.mengyalife.app.MainActivity --ez qa_seed_backup_fixture true")
    wait_app_ui()
    deadline = time.time() + 30
    before = None
    while time.time() < deadline:
        d = dump_db(pull_db("/tmp/g4a11-db-before"))
        counts = db_counts(d)
        if all(v > 0 for v in counts.values()):
            before = d
            break
        time.sleep(1)
    check("10 表均非空", before is not None, str(db_counts(before) if before else None))
    if before is None:
        sys.exit(1)
    total_cols = sum(len(before[t]["columns"]) for t in TABLES)
    check("原始列数 120", total_cols == 120, str(total_cols))
    json.dump(before, open(os.path.join(EVIDENCE, "db-before.json"), "w"), ensure_ascii=False, indent=2)
    before_counts = db_counts(before)

    print("========== 1. 设置页完整备份 ==========")
    if not has_tag("btn_settings"):
        tap_tag("tab_overview")
    check("设置页", tap_tag("btn_settings") and wait_tag("settings_back", 10))
    check("完整备份入口", scroll_to_tag("settings_backup_v2", 20))
    before_files = set()
    out = adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'")
    before_files = {x.strip() for x in out.splitlines() if x.strip()}
    tap_tag("settings_backup_v2")
    check("口令对话框", wait_text("设置备份口令", 8))
    tap_text("备份口令（至少 10 个字符）")
    adb(f"shell input text '{PASS}'")
    close_keyboard()
    tap_text("再次输入口令")
    adb(f"shell input text '{PASS}'")
    close_keyboard()
    started = time.time()
    check("开始备份", tap_text("开始备份"))
    check("口令对话框关闭", wait_gone_text("设置备份口令", 8))
    check("SAF 保存按钮", wait_text("保存", 15))
    tap_text("保存")
    check("备份写入并校验成功", wait_text("完整备份已写入并校验", 30))
    elapsed = time.time() - started
    print(f"QA_BACKUP_SECONDS={elapsed:.2f}")

    print("========== 2. 拉取文件并独立解密 ==========")
    out = adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'")
    new_files = [x.strip() for x in out.splitlines() if x.strip() and x.strip() not in before_files]
    check("生成新文件", bool(new_files), str(new_files))
    remote = new_files[-1] if new_files else ""
    local = "/tmp/g4a11-backup.mengyalife"
    if remote:
        adb(f"pull {remote} {local}")
    check("文件拉取成功", os.path.exists(local) and os.path.getsize(local) > 0,
          str(os.path.getsize(local) if os.path.exists(local) else 0))
    payload_path = "/tmp/g4a11-payload.json"
    probe = subprocess.run(
        ["node", os.path.join(ROOT, "tools/ui-verify/backup_v2_probe.js"), local, PASS, payload_path],
        capture_output=True, text=True,
    )
    check("独立解密/哈希/表校验", probe.returncode == 0, (probe.stdout + probe.stderr)[-600:])
    if probe.returncode != 0:
        print((probe.stdout + probe.stderr)[-2000:])
        sys.exit(1)
    probe_summary = json.loads(probe.stdout)
    payload = json.load(open(payload_path))
    open(os.path.join(EVIDENCE, "probe-full.json"), "w").write(probe.stdout)
    shutil.copyfile(local, os.path.join(EVIDENCE, "sample-v2-full.mengyalife"))
    shutil.copyfile(payload_path, os.path.join(EVIDENCE, "payload-full.json"))

    print("========== 3. 逐表逐字段比较 ==========")
    errors, mapped = compare_payload(before, payload)
    check("原始列映射 120/120", mapped == 120, f"{mapped}/120")
    check("逐字段无损（10/10 表）", not errors, f"{len(errors)} differences")
    for e in errors[:20]:
        print("DIFF", e)
    json.dump({"mappedColumns": mapped, "errors": errors}, open(os.path.join(EVIDENCE, "field-diff.json"), "w"),
              ensure_ascii=False, indent=2)
    check("payloadSha256 由独立探针验证", probe_summary.get("ok") is True)
    expected_counts = {BACKUP_TABLES[t]: before_counts[t] for t in TABLES}
    check("10 表 counts 一致", probe_summary.get("counts") == expected_counts,
          f"{probe_summary.get('counts')} vs {expected_counts}")

    print("========== 4. 导出后 DB 不变 / 清理 ==========")
    after = dump_db(pull_db("/tmp/g4a11-db-after"))
    json.dump(after, open(os.path.join(EVIDENCE, "db-after.json"), "w"), ensure_ascii=False, indent=2)
    check("导出后原 DB 完全不变", after == before)
    check("导出后 counts 不变", db_counts(after) == before_counts)
    if remote:
        adb(f"shell rm -f {remote}")
    try:
        os.remove(local)
    except OSError:
        pass
    check("本次新文件已清理", remote not in {x.strip() for x in adb("shell 'ls /sdcard/Download/mengya-life-backup-v2-*.mengyalife 2>/dev/null'").splitlines() if x.strip()})
    restore_chrome()
    print(f"QA_BACKUP_SECONDS={elapsed:.2f}")
    sys.exit(0 if summary() else 1)


if __name__ == "__main__":
    main()
