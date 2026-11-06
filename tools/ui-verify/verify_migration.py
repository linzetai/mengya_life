"""Room 迁移真机验证（Gate 3F，只跑 QA 包）。

用仓库里的 Room schema 10.json 生成一个真实的 v10 数据库，写入样例数据，
推到 QA 包数据库位置后启动应用，让 Room 自己执行 10→11 迁移，再检查：
- user_version = 12；
- 日记/目标/进展/每周回顾行逐项保留；
- 新表/新列存在；
- 迁移后再次启动不崩溃、不重复迁移。

正式包不参与：脚本只操作 com.mengyalife.app.qa 的独立数据库。
"""
import json, os, sqlite3, subprocess, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness import *

require_qa("verify_migration.py")

SCHEMA_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "app", "schemas", "com.mengyalife.app.data.local.MengyaDatabase",
)
DB = "mengya-life-qa.db"
OLD_VERSION = 10


def build_fixture(path, version):
    schema = json.load(open(os.path.join(SCHEMA_DIR, f"{version}.json"), encoding="utf-8"))["database"]
    con = sqlite3.connect(path)
    cur = con.cursor()
    for e in schema["entities"]:
        cur.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for idx in e.get("indices", []):
            cur.execute(idx["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    for q in schema.get("setupQueries", []):
        cur.execute(q)
    cur.execute(
        "INSERT INTO journal_entries "
        "(id,title,body,created_at,updated_at,analysis_status,analysis_hash,analysis_error,analysis_attempts,analyzed_at,analysis_last_attempt_at) "
        "VALUES (1,'MigA','alpha v10 body',1700000000000,1700000000000,'NONE',NULL,NULL,0,NULL,NULL)"
    )
    cur.execute(
        "INSERT INTO journal_entries "
        "(id,title,body,created_at,updated_at,analysis_status,analysis_hash,analysis_error,analysis_attempts,analyzed_at,analysis_last_attempt_at) "
        "VALUES (2,'MigB','beta v10 body',1700000001000,1700000001000,'NONE',NULL,NULL,0,NULL,NULL)"
    )
    cur.execute(
        "INSERT INTO goals (id,title,description,level,parent_id,domain,status,start_date,due_date,created_at,updated_at) "
        "VALUES (1,'MigGoal','v10 goal','LONG_TERM',NULL,'HEALTH','IN_PROGRESS',NULL,NULL,1700000000000,1700000000000)"
    )
    cur.execute(
        "INSERT INTO goal_checkins (id,goal_id,recorded_at,note,metric_value,metric_unit,source) "
        "VALUES (1,1,1700000000000,'v10 checkin',3.0,'min','MANUAL')"
    )
    cur.execute(
        "INSERT INTO weekly_reviews "
        "(id,period_key,period_start,period_end,generated_at,version,data_version,ai_consent_version,facts_json,sections_json,notified_at) "
        "VALUES ('r1','2026-W44',1700000000000,1700600000000,1700000000000,1,'v10','', '[]','{}',NULL)"
    )
    con.commit()
    cur.execute(f"PRAGMA user_version={version}")
    con.commit()
    uv = cur.execute("PRAGMA user_version").fetchone()[0]
    con.close()
    return uv


def push_db(local):
    subprocess.run(f"adb -s {DEV} shell am force-stop {PKG}", shell=True, capture_output=True)
    time.sleep(1.0)
    subprocess.run(f"adb -s {DEV} shell pm clear {PKG}", shell=True, capture_output=True)
    time.sleep(1.0)
    data = open(local, "rb").read()
    subprocess.run(f"adb -s {DEV} shell \"run-as {PKG} mkdir -p databases\"",
                   shell=True, capture_output=True)
    cmd = f"adb -s {DEV} shell \"run-as {PKG} sh -c 'cat > databases/{DB}'\""
    p = subprocess.run(cmd, shell=True, input=data, capture_output=True)
    if p.returncode != 0:
        print("push stderr:", p.stderr.decode("utf-8", "replace")[:400])
        return False
    for suffix in ("-wal", "-shm"):
        subprocess.run(f"adb -s {DEV} shell \"run-as {PKG} rm -f databases/{DB}{suffix}\"",
                       shell=True, capture_output=True)
    return True


def pull_db():
    d = tempfile.mkdtemp(prefix="g3f-migration-db-")
    for f in (DB, DB + "-wal", DB + "-shm"):
        p = subprocess.run(
            f"adb -s {DEV} exec-out run-as {PKG} cat databases/{f}",
            shell=True, capture_output=True,
        )
        if p.stdout:
            open(os.path.join(d, f), "wb").write(p.stdout)
    return os.path.join(d, DB)


def snapshot(path):
    con = sqlite3.connect(path)
    try:
        uv = con.execute("PRAGMA user_version").fetchone()[0]
        tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        journals = con.execute("SELECT id,title,body FROM journal_entries ORDER BY id").fetchall()
        goals = con.execute("SELECT id,title,level,status FROM goals ORDER BY id").fetchall()
        checkins = con.execute("SELECT id,goal_id,metric_value,metric_unit FROM goal_checkins ORDER BY id").fetchall()
        weekly = con.execute("SELECT id,period_key FROM weekly_reviews ORDER BY id").fetchall()
        cols = [r[1] for r in con.execute("PRAGMA table_info(weekly_ai_reviews)")]
        return uv, tables, journals, goals, checkins, weekly, cols
    finally:
        con.close()


print("========== 0. 构造 v10 夹具 ==========")
tmp = tempfile.mkdtemp(prefix="g3f-v10-")
fixture = os.path.join(tmp, DB)
check("生成 v10 数据库", build_fixture(fixture, 10) == 10)
con = sqlite3.connect(fixture)
check("夹具含 2 条日记", con.execute("SELECT COUNT(*) FROM journal_entries").fetchone()[0] == 2)
check("夹具含 1 个目标", con.execute("SELECT COUNT(*) FROM goals").fetchone()[0] == 1)
check("夹具含 1 条进展", con.execute("SELECT COUNT(*) FROM goal_checkins").fetchone()[0] == 1)
con.close()

print("========== 1. 写入 QA 包并启动（Room 执行 10→11） ==========")
check("写入 QA 数据库", push_db(fixture))
adb("shell logcat -b crash -c")
check("应用启动", restart_app())
check("应用在前台", ensure_foreground())
time.sleep(1.5)

uv, tables, journals, goals, checkins, weekly, cols = snapshot(pull_db())
check("user_version 升级为 12", uv == 12, f"user_version={uv}")
check("日记 2 条完整保留", journals == [(1, "MigA", "alpha v10 body"), (2, "MigB", "beta v10 body")], str(journals))
check("目标保留", goals == [(1, "MigGoal", "LONG_TERM", "IN_PROGRESS")], str(goals))
check("进展保留", checkins == [(1, 1, 3.0, "min")], str(checkins))
check("迁移前的每周回顾行保留", ("r1", "2026-W44") in weekly, str(weekly))
restart_ok = restart_app()
tap_tag("tab_diary")
time.sleep(1.5)
check("迁移数据在 UI 可读（重启后）", restart_ok and has_text("MigA"))
check("无迁移崩溃", "com.mengyalife.app" not in adb("shell logcat -b crash -d"))

print("========== 2. 再次启动（幂等） ==========")
uv2, *_ = snapshot(pull_db())
check("再次启动 user_version 仍为 12", uv2 == 12, f"user_version={uv2}")
check("数据库包含 weekly_ai_reviews", "weekly_ai_reviews" in tables, str(sorted(tables)))
check("数据库包含 app_meta", "app_meta" in tables, str(sorted(tables)))
check("迁移新增 config_hash 列", "config_hash" in cols, str(cols))

print("========== 3. 独立 v11→v12 迁移 ==========")
v11_fixture = os.path.join(tmp, "mengya-life-v11.db")
check("生成 v11 数据库", build_fixture(v11_fixture, 11) == 11)
check("写入 v11 QA 数据库", push_db(v11_fixture))
check("v11 包启动", restart_app() and ensure_foreground())
time.sleep(1.0)
uv3, tables3, journals3, goals3, checkins3, weekly3, cols3 = snapshot(pull_db())
check("user_version v11→12", uv3 == 12, f"user_version={uv3}")
check("v11日记保持", journals3 == [(1, "MigA", "alpha v10 body"), (2, "MigB", "beta v10 body")], str(journals3))
check("v11 app_meta默认行", sqlite3.connect(pull_db()).execute("SELECT restore_attempt_id,restore_generation FROM app_meta WHERE id=1").fetchone() == (None,0))
check("v11周AI表/配置列", "weekly_ai_reviews" in tables3 and "config_hash" in cols3, str(cols3))
check("v11无迁移崩溃", "com.mengyalife.app" not in adb("shell logcat -b crash -d"))

# 清理本脚本产生的夹具；QA 数据由调度器/后续用例处理
for f in (fixture, v11_fixture):
    try: os.remove(f)
    except OSError: pass

sys.exit(0 if summary() else 1)
